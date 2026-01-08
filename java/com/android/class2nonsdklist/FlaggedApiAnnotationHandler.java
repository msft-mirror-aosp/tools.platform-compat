/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.class2nonsdklist;

import com.android.annotationvisitor.AnnotationConsumer;
import com.android.annotationvisitor.AnnotationContext;
import com.android.annotationvisitor.AnnotationHandler;

import com.google.common.collect.ImmutableSet;

import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.ElementValuePair;

/**
 * Processes {@code FlaggedApi} annotations to generate flagged API entries.
 */
public class FlaggedApiAnnotationHandler extends AnnotationHandler {

    private final AnnotationConsumer mAnnotationConsumer;

    public FlaggedApiAnnotationHandler(AnnotationConsumer annotationConsumer) {
        mAnnotationConsumer = annotationConsumer;
    }

    @Override
    public void handleAnnotation(AnnotationEntry annotation, AnnotationContext context) {
        String signature = context.getMemberDescriptor();
        String flag = null;

        for (ElementValuePair property : annotation.getElementValuePairs()) {
            if ("value".equals(property.getNameString())) {
                flag = property.getValue().stringifyValue();
            }
        }

        if (flag != null) {
            mAnnotationConsumer.consume(signature, stringifyAnnotationProperties(annotation),
                    ImmutableSet.of(flag));
        }
    }
}
