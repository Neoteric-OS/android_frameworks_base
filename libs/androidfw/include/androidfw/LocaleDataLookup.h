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

inline uint32_t packLocale(const char* language, const char* region) {
    return (((uint8_t) language[0]) << 24u) | (((uint8_t) language[1]) << 16u) |
           (((uint8_t) region[0]) << 8u) | ((uint8_t) region[1]);
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
