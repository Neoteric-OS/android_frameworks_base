/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include "androidfw/LocaleDataLookup.h"

#include <cstddef>
#include <string>

#include "gtest/gtest.h"
#include "gmock/gmock.h"


namespace android {

constexpr const char NULL_TWO_CHARS[2] = {'\0', '\0'};
constexpr const char NULL_SCRIPT[4] = {'\0', '\0', '\0','\0' };

#define EXPECT_SCEIPT_EQ(ex, s) EXPECT_EQ(0, s == nullptr ? -1 : memcmp(ex, s, 4))

TEST(LocaleDataLookupTest, lookupLikelyScript) {
  EXPECT_EQ(nullptr, lookupLikelyScript(packLocale(NULL_TWO_CHARS, NULL_TWO_CHARS)));
  EXPECT_SCEIPT_EQ("Latn", lookupLikelyScript(packLocale("en", NULL_TWO_CHARS)));
  EXPECT_EQ(nullptr, lookupLikelyScript(packLocale("en", "\0a")));
  EXPECT_EQ(nullptr, lookupLikelyScript(packLocale("en", "US")));
  EXPECT_EQ(nullptr, lookupLikelyScript(packLocale("en", "GB")));
  EXPECT_SCEIPT_EQ("Latn", lookupLikelyScript(packLocale("fr", NULL_TWO_CHARS)));
  EXPECT_EQ(nullptr, lookupLikelyScript(packLocale("fr", "FR")));


  EXPECT_SCEIPT_EQ("~~~A", lookupLikelyScript(packLocale("en", "XA")));
  EXPECT_SCEIPT_EQ("Latn", lookupLikelyScript(packLocale("ha", NULL_TWO_CHARS)));
  EXPECT_SCEIPT_EQ("Arab", lookupLikelyScript(packLocale("ha", "SD")));
  EXPECT_EQ(nullptr, lookupLikelyScript(packLocale("ha", "Sd"))); // case sensitive
  EXPECT_SCEIPT_EQ("Hans", lookupLikelyScript(packLocale("zh", NULL_TWO_CHARS)));
  EXPECT_EQ(nullptr, lookupLikelyScript(packLocale("zh", "CN")));
  EXPECT_SCEIPT_EQ("Hant", lookupLikelyScript(packLocale("zh", "HK")));

  EXPECT_SCEIPT_EQ("Hans", lookupLikelyScript(packLocale("zhx", NULL_TWO_CHARS)));
  EXPECT_SCEIPT_EQ("Nshu", lookupLikelyScript(0xDCF90000u)); // encoded "zhx"

//   EXPECT_SCEIPT_EQ("Hans", lookupLikelyScript(packLocale("zhd", NULL_TWO_CHARS)));
//   EXPECT_SCEIPT_EQ("Hani", lookupLikelyScript(0x8CF90000u)); // encoded "zhd"
  
//   EXPECT_SCEIPT_EQ("Hans", lookupLikelyScript(packLocale("zhi", NULL_TWO_CHARS)));
//   EXPECT_SCEIPT_EQ("Latn", lookupLikelyScript(0xA0F90000u)); // encoded "zhi"
}

TEST(LocaleDataLookupTest, isLocaleRepresentative) {
  EXPECT_TRUE(isLocaleRepresentative(packLocale("en", "US"), "Latn"));
  EXPECT_TRUE(isLocaleRepresentative(packLocale("en", "GB"), "Latn"));
  EXPECT_FALSE(isLocaleRepresentative(packLocale("en", "US"), NULL_SCRIPT));
  EXPECT_FALSE(isLocaleRepresentative(packLocale("en", NULL_SCRIPT), "Latn"));
  EXPECT_FALSE(isLocaleRepresentative(packLocale("en", NULL_SCRIPT), NULL_SCRIPT));
  EXPECT_FALSE(isLocaleRepresentative(packLocale("en", "US"), "Arab"));

  EXPECT_TRUE(isLocaleRepresentative(packLocale("fr", "FR"), "Latn"));

  EXPECT_TRUE(isLocaleRepresentative(packLocale("zh", "CN"), "Hans"));
  EXPECT_FALSE(isLocaleRepresentative(packLocale("zh", "TW"), "Hans"));
  EXPECT_TRUE(isLocaleRepresentative(packLocale("zhx", "CN"), "Hans"));
  EXPECT_FALSE(isLocaleRepresentative(0xDCF9434E, "Hans"));
  EXPECT_FALSE(isLocaleRepresentative(packLocale("zhx", "CN"), "Nshu"));
  EXPECT_TRUE(isLocaleRepresentative(0xDCF9434E, "Nshu"));
}

TEST(LocaleDataLookupTest, findParentLocalePackedKey) {
  EXPECT_NE(packLocale("en", "001"), findParentLocalePackedKey("Latn", packLocale("en", "GB")));
  EXPECT_EQ(0x656E8400u, findParentLocalePackedKey("Latn", packLocale("en", "GB")));

  EXPECT_EQ(packLocale("en", "IN"), findParentLocalePackedKey("Deva", packLocale("hi", NULL_TWO_CHARS)));

  EXPECT_NE(packLocale("ar", "015"), findParentLocalePackedKey("Arab", packLocale("ar", "AE")));
  EXPECT_EQ(0x61729420u, findParentLocalePackedKey("Arab", packLocale("ar", "AE")));

  EXPECT_NE(packLocale("ar", "015"), findParentLocalePackedKey("~~~B", packLocale("ar", "XB")));
  EXPECT_EQ(0x61729420u, findParentLocalePackedKey("Arab", packLocale("ar", "AE")));

  EXPECT_EQ(packLocale("zh", "HK"), findParentLocalePackedKey("Hant", packLocale("zh", "MO")));
}

}  // namespace android
