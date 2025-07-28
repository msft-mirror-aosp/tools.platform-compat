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

import android.processor.compat.SingleAnnotationProcessor;

import com.google.common.base.Joiner;
import com.google.common.collect.Table;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;

/**
 * Annotation processor for {@code UnsupportedAppUsage} annotation.
 *
 * <p>This processor generates a CSV file with a mapping of dex signatures of elements annotated
 * with @UnsupportedAppUsage to corresponding source positions for their UnsupportedAppUsage
 * annotation.
 */
@SupportedAnnotationTypes({"android.compat.annotation.UnsupportedAppUsage"})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public final class UnsupportedAppUsageProcessor extends SingleAnnotationProcessor {

    private static final String GENERATED_INDEX_FILE_EXTENSION = ".uau";

    /**
     * CSV header line for the columns returned by {@link #getAnnotationCsvRecord(String, TypeElement,
     * Element)}.
     */
    private static final String CSV_HEADER = Joiner.on(',').join(
            "signature",
            "properties"
    );

    @Override
    protected void process(TypeElement annotation,
            Table<PackageElement, String, List<Element>> annotatedElements) {
        SignatureConverter signatureConverter = new SignatureConverter(messager);

        for (PackageElement packageElement : annotatedElements.rowKeySet()) {
            Map<String, List<Element>> row = annotatedElements.row(packageElement);
            for (String enclosingElementName : row.keySet()) {
                List<String> content = new ArrayList<>();
                for (Element annotatedElement : row.get(enclosingElementName)) {
                    String signature = signatureConverter.getSignature(
                            types, annotation, annotatedElement);
                    if (signature != null) {
                        String csvRecord = getAnnotationCsvRecord(signature, annotation,
                                annotatedElement);
                        if (csvRecord != null) {
                            content.add(csvRecord);
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
                }
            }
        }
    }

    @Override
    protected boolean ignoreAnnotatedElement(Element element, AnnotationMirror mirror) {
        // Implicit member refers to member not present in code, ignore.
        return hasElement(mirror, "implicitMember");
    }

    /**
     * Maps an annotated element to a CSV record containing its signature and properties.
     *
     * <p>It returns CSV in the format:
     * dex-signature,properties
     *
     * @return A single line of CSV text
     */
    @Nullable
    private String getAnnotationCsvRecord(String signature, TypeElement annotation, Element element) {
        AnnotationMirror annotationMirror = getSupportedAnnotationMirror(annotation, element);
        return Joiner.on(",").join(signature, getAllProperties(annotationMirror));
    }

    private boolean hasElement(AnnotationMirror annotation, String elementName) {
        return annotation.getElementValues().keySet().stream().anyMatch(
                key -> elementName.equals(key.getSimpleName().toString()));
    }

    private String getAllProperties(AnnotationMirror annotation) {
        return annotation.getElementValues().keySet().stream()
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
