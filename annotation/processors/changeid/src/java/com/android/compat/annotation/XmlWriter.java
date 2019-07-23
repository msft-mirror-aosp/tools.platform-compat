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

import static javax.tools.StandardLocation.CLASS_OUTPUT;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.OutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.annotation.processing.Filer;

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
final class XmlWriter {
    //XML tags
    private static final String XML_ROOT = "config";
    private static final String XML_CHANGE_ELEMENT = "compat-change";
    private static final String XML_NAME_ATTR = "name";
    private static final String XML_ID_ATTR = "id";
    private static final String XML_DISABLED_ATTR = "disabled";
    private static final String XML_ENABLED_AFTER_ATTR = "enableAfterTargetSdk";

    private Document document;
    private Element root;

    XmlWriter() {
        document = createDocument();
        root = document.createElement(XML_ROOT);
        document.appendChild(root);
    }

    void addChange(Change change) {
        Element newElement = document.createElement(XML_CHANGE_ELEMENT);
        newElement.setAttribute(XML_NAME_ATTR, change.name);
        newElement.setAttribute(XML_ID_ATTR, change.id.toString());
        if (change.disabled) {
            newElement.setAttribute(XML_DISABLED_ATTR, "true");
        }
        if (change.enabledAfter != null) {
            newElement.setAttribute(XML_ENABLED_AFTER_ATTR, change.enabledAfter.toString());
        }
        root.appendChild(newElement);
    }

    void write(String packageName, String fileName, Filer filer) {
        try (OutputStream output = filer.createResource(
                CLASS_OUTPUT,
                packageName,
                fileName)
                .openOutputStream()) {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource domSource = new DOMSource(document);

            StreamResult result = new StreamResult(output);

            transformer.transform(domSource, result);
        } catch (IOException | TransformerException e) {
            throw new RuntimeException("Failed to write output", e);
        }
    }

    private Document createDocument() {
        try {
            DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentFactory.newDocumentBuilder();
            Document document = documentBuilder.newDocument();
            return document;
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("Failed to create a new document", e);
        }
    }
}
