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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.annotationvisitor.AnnotationHandlerTestBase;
import com.android.annotationvisitor.AnnotationVisitor;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;

public class FlaggedApiAnnotationHandlerTest extends AnnotationHandlerTestBase {

    private static final String ANNOTATION = "Landroid/annotation/FlaggedApi;";

    @Before
    public void setup() throws IOException {
        mJavac.addSource("android.annotation.FlaggedApi",
                """
                package android.annotation;
                import static java.lang.annotation.RetentionPolicy.CLASS;
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.Target;
                @Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
                @Retention(CLASS)
                public @interface FlaggedApi {
                  String value();
                }
                """);
    }

    @Test
    public void testFlaggedApiMethod() throws IOException {
        mJavac.addSource("a.b.Class",
                """
                package a.b;
                import android.annotation.FlaggedApi;
                public class Class {
                  @FlaggedApi(\"my_flag\")
                  public void method() {}
                }
                """);
        mJavac.compile();

        new AnnotationVisitor(mJavac.getCompiledClass("a.b.Class"), mStatus,
                ImmutableMap.of(ANNOTATION, new FlaggedApiAnnotationHandler(mConsumer))
        ).visit();

        assertNoErrors();
        ArgumentCaptor<String> signature = ArgumentCaptor.forClass(String.class);
        verify(mConsumer, times(1)).consume(signature.capture(), any(), eq(ImmutableSet.of("my_flag")));
        assertThat(signature.getValue()).isEqualTo("La/b/Class;->method()V");
    }

    @Test
    public void testFlaggedApiField() throws IOException {
        mJavac.addSource("a.b.Class",
                """
                package a.b;
                import android.annotation.FlaggedApi;
                public class Class {
                  @FlaggedApi(\"my_field_flag\")
                  public int field;
                }
                """);
        mJavac.compile();

        new AnnotationVisitor(mJavac.getCompiledClass("a.b.Class"), mStatus,
                ImmutableMap.of(ANNOTATION, new FlaggedApiAnnotationHandler(mConsumer))
        ).visit();

        assertNoErrors();
        ArgumentCaptor<String> signature = ArgumentCaptor.forClass(String.class);
        verify(mConsumer, times(1)).consume(signature.capture(), any(), eq(ImmutableSet.of("my_field_flag")));
        assertThat(signature.getValue()).isEqualTo("La/b/Class;->field:I");
    }

    @Test
    public void testFlaggedApiClass() throws IOException {
        mJavac.addSource("a.b.Class",
                """
                package a.b;
                import android.annotation.FlaggedApi;
                @FlaggedApi(\"my_class_flag\")
                public class Class {
                }
                """);
        mJavac.compile();

        new AnnotationVisitor(mJavac.getCompiledClass("a.b.Class"), mStatus,
                ImmutableMap.of(ANNOTATION, new FlaggedApiAnnotationHandler(mConsumer))
        ).visit();

        assertNoErrors();
        ArgumentCaptor<String> signature = ArgumentCaptor.forClass(String.class);
        verify(mConsumer, times(1)).consume(signature.capture(), any(), eq(ImmutableSet.of("my_class_flag")));
        assertThat(signature.getValue()).isEqualTo("La/b/Class;");
    }
}
