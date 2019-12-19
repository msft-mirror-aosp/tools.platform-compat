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

import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.assertThat;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;


public class XmlWriterTest {

    private static final String HEADER =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>";

    private OutputStream mOutputStream = new ByteArrayOutputStream();

    @Test
    public void testNoChanges() {
        XmlWriter writer = new XmlWriter();
        writer.write(mOutputStream);

        String expected = HEADER + "<config/>";

        assertThat(mOutputStream.toString(), startsWith(expected));
    }

    @Test
    public void testOneChange() {
        XmlWriter writer = new XmlWriter();
        Change c = new Change(123456789L, "change-name", false, null, null, "pkg", "cls");

        writer.addChange(c);
        writer.write(mOutputStream);

        String expected = HEADER + "<config>"
                + "<compat-change id=\"123456789\" name=\"change-name\"/>"
                + "</config>";

        assertThat(mOutputStream.toString(), startsWith(expected));
    }

    @Test
    public void testSomeChanges() {
        XmlWriter writer = new XmlWriter();
        Change c = new Change(111L, "change-name1", false, null, "my nice change", "pkg", "cls");
        Change disabled = new Change(222L, "change-name2", true, null, null, "pkg", "cls");
        Change sdkRestricted = new Change(333L, "change-name3", false, 28, "", "pkg", "cls");
        Change both = new Change(444L, "change-name4", true, 29, null, "pkg", "cls");

        writer.addChange(c);
        writer.addChange(disabled);
        writer.addChange(sdkRestricted);
        writer.addChange(both);
        writer.write(mOutputStream);

        String expected = HEADER + "<config>"
                + "<compat-change description=\"my nice change\" id=\"111\" name=\"change-name1\"/>"
                + "<compat-change disabled=\"true\" id=\"222\" name=\"change-name2\"/>"
                + "<compat-change description=\"\" enableAfterTargetSdk=\"28\" id=\"333\" "
                + "name=\"change-name3\"/>"
                + "<compat-change disabled=\"true\" enableAfterTargetSdk=\"29\" id=\"444\" "
                + "name=\"change-name4\"/>"
                + "</config>";

        assertThat(mOutputStream.toString(), startsWith(expected));
    }
}
