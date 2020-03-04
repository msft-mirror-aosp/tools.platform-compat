/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.processor.compat.unsupportedappusage;

import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.StandardLocation.CLASS_OUTPUT;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Table;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.FileObject;

/**
 * Annotation processor for {@code UnsupportedAppUsage} annotation.
 *
 * <p>This processor generates a CSV file with a mapping of dex signatures of elements annotated
 * with @UnsupportedAppUsage to corresponding source positions for their UnsupportedAppUsage
 * annotation.
 */
@SupportedAnnotationTypes({"android.compat.annotation.UnsupportedAppUsage"})
@SupportedSourceVersion(SourceVersion.RELEASE_9)
public class UnsupportedAppUsageProcessor extends AbstractProcessor {

    // Package name for writing output. Output will be written to the "class output" location within
    // this package.
    private static final String PACKAGE = "unsupportedappusage";
    private static final String INDEX_CSV = "unsupportedappusage_index.csv";

    private static final String GENERATED_INDEX_FILE_EXTENSION = ".uau";

    private static final String OVERRIDE_SOURCE_POSITION_PROPERTY = "overrideSourcePosition";
    private static final Pattern OVERRIDE_SOURCE_POSITION_PROPERTY_PATTERN = Pattern.compile(
            "^[^:]+:\\d+:\\d+:\\d+:\\d+$");

    /**
     * CSV header line for the columns returned by {@link #getAnnotationIndex(String, TypeElement,
     * Element)}.
     */
    private static final String CSV_HEADER = Joiner.on(',').join(
            "signature",
            "file",
            "startline",
            "startcol",
            "endline",
            "endcol",
            "properties"
    );

    private Elements elements;
    private Messager messager;
    private SourcePositions sourcePositions;
    private Trees trees;
    private Types types;

    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        this.elements = processingEnv.getElementUtils();
        this.messager = processingEnv.getMessager();
        this.trees = Trees.instance(processingEnv);
        this.types = processingEnv.getTypeUtils();

        this.sourcePositions = trees.getSourcePositions();
    }

    /**
     * This is the main entry point in the processor, called by the compiler.
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.size() == 0) {
            return true;
        }

        SignatureConverter signatureConverter = new SignatureConverter(messager);
        TypeElement annotation = Iterables.getOnlyElement(annotations);
        Table<PackageElement, String, List<Element>> annotatedElements = HashBasedTable.create();

        for (Element annotatedElement : roundEnv.getElementsAnnotatedWith(annotation)) {
            AnnotationMirror annotationMirror =
                    getUnsupportedAppUsageAnnotationMirror(annotation, annotatedElement);
            if (hasElement(annotationMirror, "implicitMember")) {
                // Implicit member refers to member not present in code, ignore.
                continue;
            }
            PackageElement packageElement = elements.getPackageOf(annotatedElement);
            String enclosingElementName = getEnclosingElementName(annotatedElement);
            Preconditions.checkNotNull(packageElement);
            Preconditions.checkNotNull(enclosingElementName);
            if (!annotatedElements.contains(packageElement, enclosingElementName)) {
                annotatedElements.put(packageElement, enclosingElementName, new ArrayList<>());
            }
            annotatedElements.get(packageElement, enclosingElementName).add(annotatedElement);
        }

        Map<String, Element> signatureMap = new TreeMap<>();
        for (PackageElement packageElement : annotatedElements.rowKeySet()) {
            Map<String, List<Element>> row = annotatedElements.row(packageElement);
            for (String enclosingElementName : row.keySet()) {
                List<String> content = new ArrayList<>();
                for (Element annotatedElement : row.get(enclosingElementName)) {
                    String signature = signatureConverter.getSignature(
                            types, annotation, annotatedElement);
                    if (signature != null) {
                        String annotationIndex = getAnnotationIndex(signature, annotation,
                                annotatedElement);
                        if (annotationIndex != null) {
                            content.add(annotationIndex);
                            signatureMap.put(signature, annotatedElement);
                        }
                    }
                }

                if (content.isEmpty()) {
                    continue;
                }

                try {
                    FileObject resource = processingEnv.getFiler().createResource(
                            CLASS_OUTPUT,
                            packageElement.toString(),
                            enclosingElementName + GENERATED_INDEX_FILE_EXTENSION);
                    try (PrintStream outputStream = new PrintStream(resource.openOutputStream())) {
                        outputStream.println(CSV_HEADER);
                        content.forEach(outputStream::println);
                    }
                } catch (IOException exception) {
                    messager.printMessage(ERROR, "Could not write CSV file: " + exception);
                    return false;
                }
            }
        }

        // TODO(satayev): remove merged csv file, after soong starts merging individual csvs.
        if (!signatureMap.isEmpty()) {
            try {
                writeToFile(INDEX_CSV,
                        CSV_HEADER,
                        signatureMap.entrySet()
                                .stream()
                                .map(e -> getAnnotationIndex(e.getKey(), annotation,
                                        e.getValue())));
            } catch (IOException e) {
                throw new RuntimeException("Failed to write output", e);
            }
        }
        return true;
    }

    /**
     * Write the contents of a stream to a text file, with one line per item.
     */
    private void writeToFile(String name,
            String headerLine,
            Stream<?> contents) throws IOException {
        PrintStream out = new PrintStream(processingEnv.getFiler().createResource(
                CLASS_OUTPUT,
                PACKAGE,
                name)
                .openOutputStream());
        out.println(headerLine);
        contents.forEach(o -> out.println(o));
        if (out.checkError()) {
            throw new IOException("Error when writing to " + name);
        }
        out.close();
    }

    /**
     * Returns a name of an enclosing element without the package name.
     *
     * <p>This would return names of all enclosing classes, e.g. <code>Outer.Inner.Foo</code>.
     */
    private String getEnclosingElementName(Element element) {
        String fullQualifiedName =
                ((QualifiedNameable) element.getEnclosingElement()).getQualifiedName().toString();
        String packageName = elements.getPackageOf(element).toString();
        return fullQualifiedName.substring(packageName.length() + 1);
    }

    /**
     * Maps an annotated element to the source position of the @UnsupportedAppUsage annotation
     * attached to it.
     *
     * <p>It returns CSV in the format:
     * dex-signature,filename,start-line,start-col,end-line,end-col,properties
     *
     * <p>The positions refer to the annotation itself, *not* the annotated member. This can
     * therefore be used to read just the annotation from the file, and to perform in-place
     * edits on it.
     *
     * @return A single line of CSV text
     */
    private String getAnnotationIndex(String signature, TypeElement annotation, Element element) {
        AnnotationMirror annotationMirror =
                getUnsupportedAppUsageAnnotationMirror(annotation, element);

        String position = getSourcePositionOverride(element, annotationMirror);
        if (position == null) {
            position = getSourcePosition(element, annotationMirror);
            if (position == null) {
                return null;
            }
        }
        return Joiner.on(",").join(
                signature,
                position,
                getProperties(annotationMirror));
    }

    /**
     * Find the annotation mirror for the @UnsupportedAppUsage annotation on the given element.
     */
    private AnnotationMirror getUnsupportedAppUsageAnnotationMirror(TypeElement annotation,
            Element element) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            TypeElement type = (TypeElement) mirror.getAnnotationType().asElement();
            if (types.isSameType(annotation.asType(), mirror.getAnnotationType())) {
                return mirror;
            }
        }
        return null;
    }

    private String getSourcePosition(Element element, AnnotationMirror annotationMirror) {
        TreePath path = trees.getPath(element, annotationMirror);
        if (path == null) {
            return null;
        }
        CompilationUnitTree compilationUnit = path.getCompilationUnit();
        Tree tree = path.getLeaf();
        long startPosition = sourcePositions.getStartPosition(compilationUnit, tree);
        long endPosition = sourcePositions.getEndPosition(compilationUnit, tree);

        LineMap lineMap = path.getCompilationUnit().getLineMap();
        return Joiner.on(",").join(
                compilationUnit.getSourceFile().getName(),
                lineMap.getLineNumber(startPosition),
                lineMap.getColumnNumber(startPosition),
                lineMap.getLineNumber(endPosition),
                lineMap.getColumnNumber(endPosition));
    }

    private String getSourcePositionOverride(Element annotatedElement,
            AnnotationMirror annotation) {
        Optional<? extends AnnotationValue> annotationValue =
                annotation.getElementValues().keySet().stream()
                        .filter(key -> key.getSimpleName().toString().equals(
                                OVERRIDE_SOURCE_POSITION_PROPERTY))
                        .map(key -> annotation.getElementValues().get(key))
                        .reduce((a, b) -> {
                            throw new IllegalStateException(
                                    String.format("Only one %s expected, found %s in %s",
                                            OVERRIDE_SOURCE_POSITION_PROPERTY, annotation,
                                            annotatedElement));
                        });

        if (!annotationValue.isPresent()) {
            return null;
        }

        String parameterValue = annotationValue.get().getValue().toString();

        if (!OVERRIDE_SOURCE_POSITION_PROPERTY_PATTERN.matcher(parameterValue).matches()) {
            messager.printMessage(ERROR, String.format(
                    "Expected %s to have format string:int:int:int:int",
                    OVERRIDE_SOURCE_POSITION_PROPERTY), annotatedElement, annotation);
            return null;
        }

        return parameterValue.replace(':', ',');
    }

    private boolean hasElement(AnnotationMirror annotation, String elementName) {
        return annotation.getElementValues().keySet().stream().anyMatch(
                key -> elementName.equals(key.getSimpleName().toString()));
    }

    private String getProperties(AnnotationMirror annotation) {
        return annotation.getElementValues().keySet().stream()
                .filter(key -> !key.getSimpleName().toString().equals(
                        OVERRIDE_SOURCE_POSITION_PROPERTY))
                .map(key -> String.format(
                        "%s=%s",
                        key.getSimpleName(),
                        getAnnotationElementValue(annotation, key)))
                .collect(Collectors.joining("&"));
    }

    private String getAnnotationElementValue(AnnotationMirror annotation,
            ExecutableElement element) {
        try {
            return URLEncoder.encode(annotation.getElementValues().get(element).toString(),
                    "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }


}
