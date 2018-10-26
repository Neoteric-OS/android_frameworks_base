/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include "android-base/logging.h"
#include "dex_builder.h"

#include <fstream>
#include <string>

using namespace startop::dex;
using namespace std;

void GenerateTrivialDexFile(const string& outdir) {
  DexBuilder dex_file;

  ClassBuilder cbuilder{dex_file.MakeClass("android.startop.test.testcases.Trivial")};
  cbuilder.set_source_file("dex_testcase_generator.cc#GenerateTrivialDexFile");

  slicer::MemView image{dex_file.CreateImage()};
  std::ofstream out_file(outdir + "/trivial.dex");
  out_file.write(image.ptr<const char>(), image.size());
}

int main(int argc, char** argv) {
  CHECK_EQ(argc, 2);

  string outdir = argv[1];

  GenerateTrivialDexFile(outdir);
}
