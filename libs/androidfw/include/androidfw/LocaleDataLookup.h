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


#ifndef _LIBS_UTILS_LOCALE_DATA_LOOKUP_H
#define _LIBS_UTILS_LOCALE_DATA_LOOKUP_H

#include <stddef.h>
#include <cstdint>

namespace android {

namespace hidden {
    bool isRepresentative(uint64_t packed_locale);
}

constexpr size_t SCRIPT_LENGTH = 4;

inline void packLanguageOrRegion(const char* in, const char base, char out[2]) {
  if (in[2] == 0 || in[2] == '-') {
      out[0] = in[0];
      out[1] = in[1];
  } else {
      uint8_t first = (in[0] - base) & 0x007f;
      uint8_t second = (in[1] - base) & 0x007f;
      uint8_t third = (in[2] - base) & 0x007f;

      out[0] = (0x80 | (third << 2) | (second >> 3));
      out[1] = ((second << 5) | first);
  }
}

uint32_t inline packLanguage(const char* language) {
    char out[2];
    packLanguageOrRegion(language, 'a', out);
    return (((uint8_t) out[0]) << 8u) | ((uint8_t) out[1]);
}

uint32_t inline packRegion(const char* region) {
    char out[2];
    packLanguageOrRegion(region, '0', out);
    return (((uint8_t) out[0]) << 8u) | ((uint8_t) out[1]);
}

inline uint32_t packLocale(const char* language, const char* region) {
    return ((packLanguage(language)) << 16u) | packRegion(region);
}

inline uint32_t dropRegion(uint32_t packed_locale) {
    return packed_locale & 0xFFFF0000LU;
}

inline bool hasRegion(uint32_t packed_locale) {
    return (packed_locale & 0x0000FFFFLU) != 0;
}

/**
 * Return nullptr if the key isn't found. The input packed_lang_region can be computed
 * by android::packLocale.
 * Note that the returned char* is either nullptr or 4-byte char seqeuence, but isn't
 * a null-terminated string.
 */
const char* lookupLikelyScript(uint32_t packed_lang_region);
/**
 * Return false if the key isn't representative. The input lookup key can be computed
 * by android::packLocale.
 */
bool inline isLocaleRepresentative(uint32_t language_and_region, const char* script) {
    const uint64_t packed_locale = (
            (((uint64_t) language_and_region) << 32u) |
            (((uint64_t) script[0]) << 24u) |
            (((uint64_t) script[1]) << 16u) |
            (((uint64_t) script[2]) <<  8u) |
            ((uint64_t) script[3]));

    return hidden::isRepresentative(packed_locale);
}

/**
 * Return a parent packed key for a given script and child packed key. Return 0 if
 * no parent is found.
 */
uint32_t findParentLocalePackedKey(const char* script, uint32_t packed_lang_region);

uint32_t getMaxAncestorTreeDepth();

} // namespace android

#endif // _LIBS_UTILS_LOCALE_DATA_LOOKUP_H
