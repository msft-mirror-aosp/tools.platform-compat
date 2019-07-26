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

import com.google.common.annotations.VisibleForTesting;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.OutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * <p>Writes an XML config file containing provided changes.</p>
 * <p>Output example:</p>
 * <pre>
 * {@code
 * <config>
 *     <compat-change id="111" name="change-name1"/>
 *     <compat-change disabled="true" id="222" name="change-name2"/>
 *     <compat-change enableAfterTargetSdk="28" id="333" name="change-name3"/>
 * </config>
 *  }
 *
 * </pre>
 *
 */
@VisibleForTesting
public final class XmlWriter {
    //XML tags
    private static final String XML_ROOT = "config";
    private static final String XML_CHANGE_ELEMENT = "compat-change";
    private static final String XML_NAME_ATTR = "name";
    private static final String XML_ID_ATTR = "id";
    private static final String XML_DISABLED_ATTR = "disabled";
    private static final String XML_ENABLED_AFTER_ATTR = "enableAfterTargetSdk";

    private Document mDocument;
    private Element mRoot;

    @VisibleForTesting
    public XmlWriter() {
        mDocument = createDocument();
        mRoot = mDocument.createElement(XML_ROOT);
        mDocument.appendChild(mRoot);
    }

    @VisibleForTesting
    public void addChange(Change change) {
        Element newElement = mDocument.createElement(XML_CHANGE_ELEMENT);
        newElement.setAttribute(XML_NAME_ATTR, change.name);
        newElement.setAttribute(XML_ID_ATTR, change.id.toString());
        if (change.disabled) {
            newElement.setAttribute(XML_DISABLED_ATTR, "true");
        }
        if (change.enabledAfter != null) {
            newElement.setAttribute(XML_ENABLED_AFTER_ATTR, change.enabledAfter.toString());
        }
        mRoot.appendChild(newElement);
    }

    @VisibleForTesting
    public void write(OutputStream output) {
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource domSource = new DOMSource(mDocument);

            StreamResult result = new StreamResult(output);

            transformer.transform(domSource, result);
        } catch (TransformerException e) {
            throw new RuntimeException("Failed to write output", e);
        }
    }

    private Document createDocument() {
        try {
            DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentFactory.newDocumentBuilder();
            return documentBuilder.newDocument();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("Failed to create a new document", e);
        }
    }
}
