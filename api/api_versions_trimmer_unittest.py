#!/usr/bin/env python
#
# Copyright (C) 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import unittest
import xml.etree.ElementTree as ET


import api_versions_trimmer


class api_versions_trimmer_unittest(unittest.TestCase):

  def test_file_to_class(self):
    name = api_versions_trimmer.file_to_class("a/b/C.class")
    self.assertEqual("a/b/C", name)

  def test_filter_method_signature(self):
    xml = """
    <method name="dispatchGesture(Landroid/accessibilityservice/GestureDescription;Landroid/accessibilityservice/AccessibilityService$GestureResultCallback;Landroid/os/Handler;)Z" since="24"/>
    """
    method = ET.fromstring(xml)
    classes_to_remove = {"android/accessibilityservice/GestureDescription"}
    expected = "dispatchGesture(Ljava/lang/Object;Landroid/accessibilityservice/AccessibilityService$GestureResultCallback;Landroid/os/Handler;)Z"
    api_versions_trimmer.filter_method_signature(method, classes_to_remove)
    self.assertEqual(expected, method.get("name"))


if __name__ == '__main__':
  unittest.main()
