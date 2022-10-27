/*
 * Copyright (C) 2014 The Android Open Source Project
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

#include <nativehelper/JNIHelp.h>
#include <openssl/sha.h>

#include <string>

#include "core_jni_helpers.h"

namespace android {

struct Sha256Context {
    SHA256_CTX sha256;

    Sha256Context() { SHA256_Init(&sha256); }
};

void sha256(const std::string& str, unsigned char hash[SHA256_DIGEST_LENGTH]) {
    static Sha256Context sha256Context;
    SHA256_Update(&sha256Context.sha256, str.c_str(), str.size());
    SHA256_Final(hash, &sha256Context.sha256);
}

int64_t hash64(const std::string& str) {
    unsigned char hash[SHA256_DIGEST_LENGTH];
    sha256(str, hash);
    int64_t result = (hash[0] & 0xFF);
    for (int i = 1; i < 8; i++) {
        result |= (hash[i] & 0xFFL) << (i * 8);
    }
    return result;
}

// ----------------------------------------------------------------------------
// JNI Glue
// ----------------------------------------------------------------------------

/*
 * Class:     com_android_internal_expresslog_Counter
 * Method:    hashString
 * Signature: (Ljava/lang/String;)J
 */
extern "C" JNIEXPORT jlong JNICALL Java_com_android_internal_expresslog_Counter_hashString(
        JNIEnv* env, jclass /*class*/, jstring metricNameObj) {
    const char* metricnamechars =
            (metricNameObj) ? env->GetStringUTFChars(metricNameObj, NULL) : NULL;

    const std::string metricName(metricnamechars ? metricnamechars : "");

    if (metricnamechars) env->ReleaseStringUTFChars(metricNameObj, metricnamechars);

    return (jlong)hash64(metricName);
}

} // namespace android
