/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.compat.annotation;

import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.StandardLocation.CLASS_OUTPUT;


import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.Map;
import java.util.regex.Pattern;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.annotation.processing.Messager;
import javax.lang.model.element.ElementKind;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Elements;

/**
 * Annotation processor for ChangeId annotations.
 *
 * This processor outputs an XML file containing all the changeIds defined by this
 * annotation. The file is bundled into the pratform image and used by the system server.
 * Design doc: go/gating-and-logging.
 */
@SupportedAnnotationTypes({"android.compat.annotation.ChangeId"})
public class ChangeIdProcessor extends AbstractProcessor {

    private static final String PACKAGE = "compat";
    private static final String CONFIG_XML = "compat_config.xml";

    private static final ImmutableSet<String> IGNORED_METHOD_NAMES =
            ImmutableSet.of("reportChange", "isChangeEnabled");
    private static final String IGNORED_CLASS = "android.compat.Compatibility";

    private static final String SUPPORTED_ANNOTATION =
            "android.compat.annotation.ChangeId";

    private static final String DISABLED_CLASS_NAME = "android.compat.annotation.Disabled";
    private static final String ENABLED_AFTER_CLASS_NAME = "android.compat.annotation.EnabledAfter";
    private static final String TARGET_SDK_VERSION = "targetSdkVersion";

    private static final Pattern JAVADOC_SANITIZER = Pattern.compile("^\\s|\\n|\\r",
            Pattern.MULTILINE);

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    /**
     * This is the main entry point in the processor, called by the compiler.
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(
                processingEnv.getElementUtils().getTypeElement(
                        SUPPORTED_ANNOTATION));
        if (annotatedElements.isEmpty()) {
            return true;
        }

        XmlWriter writer = new XmlWriter();

        for (Element e : annotatedElements) {
            if (!isValidChangeId(e, processingEnv.getMessager())) {
                continue;
            }
            Change change = createChange(e, processingEnv.getMessager(),
                    processingEnv.getElementUtils().getDocComment(e));
            writer.addChange(change);
        }

        try (OutputStream output = processingEnv.getFiler().createResource(
                CLASS_OUTPUT,
                PACKAGE,
                CONFIG_XML)
                .openOutputStream()) {
            writer.write(output);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write output", e);
        }


        return true;
    }


    private boolean shouldIgnoreAnnotation(Element e) {
        // Just ignore the annotations on function known methods in package android.compat
        // (libcore/luni/src/main/java/android/compat/Compatibility.java)
        // without generating an error.
        return (e.getKind() == ElementKind.PARAMETER
                && e.getEnclosingElement().getKind() == ElementKind.METHOD
                && IGNORED_METHOD_NAMES.contains(e.getEnclosingElement().getSimpleName().toString())
                && e.getEnclosingElement().getEnclosingElement().getKind() == ElementKind.CLASS
                && ((TypeElement) e.getEnclosingElement().getEnclosingElement()).getQualifiedName()
                .toString().equals(IGNORED_CLASS));
    }

    /**
     * Checks if the provided java element is a valid change id (i.e. a long parameter with a
     * constant value).
     *
     * @param e        java element to check.
     * @param messager updated with compilation errors if the annotated element is not valid.
     * @return true if the provided element is a legal change id that should be added to the
     * produced XML file. If true is returned it's guaranteed that the following
     * operations are safe.
     */
    private boolean isValidChangeId(Element e, Messager messager) {
        if (shouldIgnoreAnnotation(e)) {
            return false;
        }
        if (e.getKind() != ElementKind.FIELD) {
            messager.printMessage(
                    ERROR,
                    String.format(
                            "Non field element %s annotated with @ChangeId. Got type "
                                    + "%s, expected FIELD.",
                            e.getSimpleName().toString(), e.getKind().toString()),
                    e);
            return false;
        }
        if (!(e instanceof VariableElement)) {
            messager.printMessage(
                    ERROR,
                    String.format(
                            "Non variable %s annotated with @ChangeId.",
                            e.getSimpleName().toString()),
                    e);
            return false;
        }
        if (((VariableElement) e).getConstantValue() == null) {
            messager.printMessage(
                    ERROR,
                    String.format(
                            "Non constant/final variable %s (or non constant value) "
                                    + "annotated with @ChangeId.",
                            e.getSimpleName().toString()),
                    e);
            return false;
        }
        if (e.asType().getKind() != TypeKind.LONG) {
            messager.printMessage(
                    ERROR,
                    "Variables annotated with @ChangeId should be of type long.",
                    e);
            return false;
        }
        if (!e.getModifiers().contains(Modifier.STATIC)) {
            messager.printMessage(
                    ERROR,
                    String.format(
                            "Non static variable %s annotated with @ChangeId.",
                            e.getSimpleName().toString()),
                    e);
            return false;
        }
        return true;
    }

    private Change createChange(Element e, Messager messager, String comment) {
        Long id = (Long) ((VariableElement) e).getConstantValue();
        String name = e.getSimpleName().toString();
        boolean disabled = false;
        Integer enabledAfter = null;

        for (AnnotationMirror m : e.getAnnotationMirrors()) {
            String type =
                    ((TypeElement) m.getAnnotationType().asElement()).getQualifiedName().toString();
            if (type.equals(DISABLED_CLASS_NAME)) {
                disabled = true;
            } else if (type.equals(ENABLED_AFTER_CLASS_NAME)) {
                for (Map.Entry<?, ?> entry : m.getElementValues().entrySet()) {
                    String key = ((ExecutableElement) entry.getKey()).getSimpleName().toString();
                    if (key.equals(TARGET_SDK_VERSION)) {
                        enabledAfter = (Integer) ((AnnotationValue) entry.getValue()).getValue();
                    }
                }
            }
        }

        String description = null;
        if (comment != null) {
            description = JAVADOC_SANITIZER.matcher(comment).replaceAll("").trim();
        }

        if (disabled && enabledAfter != null) {
            messager.printMessage(
                    ERROR,
                    "ChangeId cannot be annotated with both @Disabled and @EnabledAfter.",
                    e);
        }
        return new Change(id, name, disabled, enabledAfter, description);
    }
}
