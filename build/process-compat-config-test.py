#!/usr/bin/env python
#
# Copyright (C) 2019 The Android Open Source Project
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
#
"""Unit tests for process_compat_config.py."""

import difflib
import io
import unittest
import xml.dom.minidom

import process_compat_config

class ProcessCompatConfigTest(unittest.TestCase):

    def setUp(self):
        self.merger = process_compat_config.ConfigMerger()
        self.xml = io.BytesIO()

    def assert_same_xml(self, got, expected):
        got = xml.dom.minidom.parseString(got).toprettyxml()
        expected = xml.dom.minidom.parseString(expected).toprettyxml()
        diffs = [diff for diff in difflib.ndiff(got.split('\n'), expected.split('\n')) if not diff.startswith(" ")]
        self.assertEqual("", "\n".join(diffs), msg="Got unexpected diffs in XML")

    def test_no_config_to_merge(self):
        self.merger.write(self.xml)
        self.assert_same_xml(self.xml.getvalue(), "<config />")

    def test_merge_one_file(self):
        self.merger.merge(io.BytesIO(b'<config><compat-change id="1234" name="TEST_CHANGE" /></config>'))
        self.merger.write(self.xml)
        self.assert_same_xml(self.xml.getvalue(), '<config><compat-change id="1234" name="TEST_CHANGE" /></config>')

    def test_merge_two_files(self):
        self.merger.merge(io.BytesIO(b'<config><compat-change id="1234" name="TEST_CHANGE" /></config>'))
        self.merger.merge(io.BytesIO(b'<config><compat-change id="1235" name="TEST_CHANGE2" /></config>'))
        self.merger.write(self.xml)
        self.assert_same_xml(self.xml.getvalue(),
            '<config><compat-change id="1234" name="TEST_CHANGE" /><compat-change id="1235" name="TEST_CHANGE2" /></config>')

    def test_merge_two_files_metadata(self):
        self.merger.merge(io.BytesIO(
            b'<config><compat-change id="1234" name="TEST_CHANGE"><metadata definedIn="some.Class" />'
            b'</compat-change></config>'))
        self.merger.merge(io.BytesIO(
            b'<config><compat-change id="1235" name="TEST_CHANGE2"><metadata definedIn="other.Class" />'
            b'</compat-change></config>'))
        self.merger.write(self.xml)
        self.assert_same_xml(self.xml.getvalue(), b'<config>'
            b'<compat-change id="1234" name="TEST_CHANGE"><metadata definedIn="some.Class" /></compat-change>'
            b'<compat-change id="1235" name="TEST_CHANGE2"><metadata definedIn="other.Class" /></compat-change>'
            b'</config>')

    def test_write_device_config_metadata_stripped(self):
        self.merger.merge(io.BytesIO(
            b'<config><compat-change id="1234" name="TEST_CHANGE"><metadata definedIn="some.Class" />'
            b'</compat-change></config>'))
        self.merger.write_device_config(self.xml)
        self.assert_same_xml(self.xml.getvalue(), b'<config>'
            b'<compat-change id="1234" name="TEST_CHANGE" />'
            b'</config>')


if __name__ == '__main__':
  unittest.main(verbosity=2)