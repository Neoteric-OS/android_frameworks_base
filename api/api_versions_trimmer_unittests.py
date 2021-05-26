#!/usr/bin/env python3
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

import io
import unittest
import xml.etree.ElementTree as ET
import zipfile

import api_versions_trimmer

class virtual_zip_file:
  def __init__(self):
    self.zip = zipfile.ZipFile()

def create_in_memory_zip_file(files):
  f = io.BytesIO()
  #f = io.StringIO()
  with zipfile.ZipFile(f, "w") as z:
    for fname in files:
      path = zipfile.Path(z, fname)
      with path.open("w") as class_file:
        class_file.write("")
  return f

class api_versions_trimmer_unittest(unittest.TestCase):

  def test_file_to_class(self):
    name = api_versions_trimmer.file_to_class("a/b/C.class")
    self.assertEqual("a/b/C", name)

  def test_read_classes(self):
    f = create_in_memory_zip_file(
      [ "a/b/C.class",
        "a/b/D.class",
        "a/b/E.dex",
        "f.dex",
        "META-INFO/G.class"
      ]
    )
    res = api_versions_trimmer.read_classes(f)
    self.assertEqual({"a/b/C", "a/b/D"}, res)

  def test_filter_method_signature(self):
    xml = """
    <method name="dispatchGesture(Landroid/accessibilityservice/GestureDescription;Landroid/accessibilityservice/AccessibilityService$GestureResultCallback;Landroid/os/Handler;)Z" since="24"/>
    """
    method = ET.fromstring(xml)
    classes_to_remove = {"android/accessibilityservice/GestureDescription"}
    expected = "dispatchGesture(Ljava/lang/Object;Landroid/accessibilityservice/AccessibilityService$GestureResultCallback;Landroid/os/Handler;)Z"
    api_versions_trimmer.filter_method_signature(method, classes_to_remove)
    self.assertEqual(expected, method.get("name"))

  def test_filter_lint_database(self):
    xml = """
    <api version="2">
      <!-- will be removed -->
      <class name="a/b/C" since="1">
        <extends name="java/lang/Object"/>
      </class>

      <class name="a/b/D" since="1">
        <extends name="java/lang/Object"/>
        <implements name="android/os/Parcelable"/>
        <method name="dispatchGesture(Landroid/accessibilityservice/GestureDescription;Landroid/accessibilityservice/AccessibilityService$GestureRe
sultCallback;Landroid/os/Handler;)Z" since="24"/>
      </class>

      <class name="a/b/E" since="1">
        <!-- extends will be modified -->
        <extends name="a/b/C"/>
        <!-- first parameter will be modified -->
        <method name="dispatchGesture(La/b/C;Landroid/os/Handler;)Z" since="24"/>
        <!-- second should remain untouched -->
        <method name="dispatchGesture(Landroid/accessibilityservice/GestureDescription;Landroid/accessibilityservice/AccessibilityService$GestureRe
sultCallback;Landroid/os/Handler;)Z" since="24"/>
      </class>

      <class name="a/b/F" since="1">
        <extends name="java/lang/Object"/>
        <!-- implements will be modified -->
        <implements name="a/b/C"/>
      </class>
    </api>
    """
    database = io.StringIO(xml)
    classes_to_remove = {"a/b/C"}
    output = io.BytesIO()
    api_versions_trimmer.filter_lint_database(database, classes_to_remove,
      output)
    expected = """
    <api version="2">

      <class name="a/b/D" since="1">
        <extends name="java/lang/Object"/>
        <implements name="android/os/Parcelable"/>
        <method name="dispatchGesture(Landroid/accessibilityservice/GestureDescription;Landroid/accessibilityservice/AccessibilityService$GestureRe
sultCallback;Landroid/os/Handler;)Z" since="24"/>
      </class>

      <class name="a/b/E" since="1">
        <extends name="java/lang/Object"/>
        <method name="dispatchGesture(Ljava/lang/Object;Landroid/os/Handler;)Z" since="24"/>
        <method name="dispatchGesture(Landroid/accessibilityservice/GestureDescription;Landroid/accessibilityservice/AccessibilityService$GestureRe
sultCallback;Landroid/os/Handler;)Z" since="24"/>
      </class>

      <class name="a/b/F" since="1">
        <extends name="java/lang/Object"/>
        <implements name="java/lang/Object"/>
      </class>
    </api>
    """
    clean_whitespace = lambda x: x.replace(" ", "").replace("\n", "")
    expected = clean_whitespace(expected)
    res = clean_whitespace(output.getvalue().decode("utf-8"))

    self.assertEqual(expected, res)


if __name__ == '__main__':
  unittest.main()
