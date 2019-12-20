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

import argparse
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

class ConfigMerger(object):

    def __init__(self):
        self.tree = ET.ElementTree()
        self.tree._setroot(ET.Element("config"))

    def merge(self, xmlFile):
        xml = ET.parse(xmlFile)
        for child in xml.getroot():
            self.tree.getroot().append(child)

    def write(self, filename):
        self.tree.write(filename, encoding='utf-8', xml_declaration=True)

    def write_device_config(self, filename):
        self.strip_config_for_device().write(filename, encoding='utf-8', xml_declaration=True)

    def strip_config_for_device(self):
        new_tree = ET.ElementTree()
        new_tree._setroot(ET.Element("config"))
        for change in self.tree.getroot():
            new_change = ET.Element("compat-change")
            new_change.attrib = change.attrib.copy()
            new_tree.getroot().append(new_change)
        return new_tree

def main(argv):
    parser = argparse.ArgumentParser(
        description="Processes compat config XML files")
    parser.add_argument("--jar", type=argparse.FileType('rb'), action='append',
        help="Specifies a jar file to extract compat_config.xml from.")
    parser.add_argument("--xml", type=argparse.FileType('rb'), action='append',
        help="Specifies an xml file to read compat_config from.")
    parser.add_argument("--device-config", dest="device_config", type=argparse.FileType('wb'),
        help="Specify where to write config for embedding on the device to. "
        "Meta data not needed on the devivce is stripped from this.")
    parser.add_argument("--merged-config", dest="merged_config", type=argparse.FileType('wb'),
        help="Specify where to write merged config to. This will include metadata.")

    args = parser.parse_args()

    config = ConfigMerger()
    if args.jar:
        for jar in args.jar:
            for xml in extract_compat_config(jar):
                config.merge(xml)
    if args.xml:
        for xml in args.xml:
            config.merge(xml)

    if args.device_config:
        config.write_device_config(args.device_config)

    if args.merged_config:
        config.write(args.merged_config)



if __name__ == "__main__":
    main(sys.argv)
