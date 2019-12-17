#!/usr/bin/env python
#
# Copyright (C) 2020 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
Extracts compat_config.xml from built jar files and merges them into a single
XML file.
"""

import sys
import xml.etree.ElementTree as ET
from zipfile import ZipFile

def extract_compat_config(jarfile):
    """
    Reads all compat_config.xml files from a jarfile.

    Yields: open filehandles for each XML file found.
    """
    with ZipFile(jarfile, 'r') as jar:
        for info in jar.infolist():
            if info.filename.endswith("_compat_config.xml"):
                with jar.open(info.filename, 'r') as xml:
                    yield xml

def merge_compat_config_xml(jarfile):
    tree = ET.ElementTree()
    tree._setroot(ET.Element('config'))
    for xmlFile in extract_compat_config(jarfile):
        xml = ET.parse(xmlFile)
        for child in xml.getroot():
            tree.getroot().append(child)
    tree.write("/dev/stdout", encoding='utf-8', xml_declaration=True)


def main(argv):
    merge_compat_config_xml(argv[1])

if __name__ == "__main__":
    main(sys.argv)
