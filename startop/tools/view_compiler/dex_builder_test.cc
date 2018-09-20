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

#include "dex_builder.h"

#include "dex/art_dex_file_loader.h"
#include "dex/dex_file.h"
#include "gtest/gtest.h"

using namespace startop::dex;

// Takes a DexBuilder, encodes it into an in-memory DEX file, verifies the resulting DEX file and
// returns whether the verification was successful.
bool EncodeAndVerify(const std::shared_ptr<DexBuilder>& dex_file) {
  ::dex::Writer writer(dex_file);
  TrackingAllocator allocator;
  size_t image_size{0};
  ::dex::u1* image = writer.CreateImage(&allocator, &image_size);

  art::ArtDexFileLoader loader;
  std::string error_msg;
  std::unique_ptr<const art::DexFile> loaded_dex_file =
      loader.Open(static_cast<const uint8_t*>(image),
                  image_size,
                  /*location=*/"",
                  /*location_checksum=*/0,
                  /*oat_dex_file=*/nullptr,
                  /*verify=*/true,
                  /*verify_checksum=*/false,
                  &error_msg);
  return loaded_dex_file != nullptr;
}

TEST(DexBuilderTest, VerifyDexWithClassMethod) {
  std::shared_ptr<DexBuilder> dex_file{std::make_shared<DexBuilder>()};

  auto* class_def = dex_file->MakeClass("dextest.DexTest");
  class_def->source_file = dex_file->GetOrAddString("dextest.java");

  ClassBuilder cbuilder{dex_file, class_def};
  auto method{cbuilder.CreateMethod("foo", Prototype{TypeDescriptor::VOID})};
  method.Encode();

  EXPECT_TRUE(EncodeAndVerify(dex_file));
}

// Makes sure a bad DEX class fails to verify.
TEST(DexBuilderTest, VerifyBadDexWithClassMethod) {
  std::shared_ptr<DexBuilder> dex_file{std::make_shared<DexBuilder>()};

  auto* class_def = dex_file->MakeClass("dextest.DexTest");
  class_def->source_file = dex_file->GetOrAddString("dextest.java");

  ClassBuilder cbuilder{dex_file, class_def};
  // This method has the error, because methods cannot take VOID as a parameter.
  auto method{cbuilder.CreateMethod("foo", Prototype{TypeDescriptor::VOID, TypeDescriptor::VOID})};
  method.Encode();

  EXPECT_FALSE(EncodeAndVerify(dex_file));
}
