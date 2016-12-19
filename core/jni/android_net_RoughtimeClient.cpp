/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include <openssl/curve25519.h>
#include <vector>

using std::vector;

#include "core_jni_helpers.h"

namespace android {

static jboolean android_net_RoughtimeClient_verifySignature(JNIEnv* env,
    jobject clazz, jbyteArray signatureArray, jbyteArray keyArray,
    jbyteArray dataArray) {

    jsize signatureLength = env->GetArrayLength(signatureArray);
    jsize keyLength = env->GetArrayLength(keyArray);
    jsize dataLength = env->GetArrayLength(dataArray);

    jclass illegalArgument = env->FindClass(
        "java/lang/IllegalArgumentException");

    if (keyLength != 32) {
        env->ThrowNew(illegalArgument, "Key should be exactly 32 bytes");
        return JNI_FALSE;
    }

    if (signatureLength != 64) {
        env->ThrowNew(illegalArgument, "Signature should be exactly 64 bytes");
        return JNI_FALSE;
    }

    jbyte signature[64];
    env->GetByteArrayRegion(signatureArray, 0, 64, signature);

    jbyte key[32];
    env->GetByteArrayRegion(keyArray, 0, 32, key);

    vector<jbyte> data;
    data.resize(dataLength);
    env->GetByteArrayRegion(dataArray, 0, dataLength, data.data());

    return ED25519_verify((uint8_t*)data.data(), dataLength,
                          (uint8_t*)signature, (uint8_t*)key)
        ? JNI_TRUE : JNI_FALSE;
}

static jboolean android_net_RoughtimeClient_sign(JNIEnv* env,
    jobject clazz, jbyteArray outSignatureArray, jbyteArray privateKeyArray,
    jbyteArray messageArray) {

    jclass illegalArgument = env->FindClass(
        "java/lang/IllegalArgumentException");

    jsize outSignatureLength = env->GetArrayLength(outSignatureArray);
    jsize privateKeyLength = env->GetArrayLength(privateKeyArray);
    jsize messageLength = env->GetArrayLength(messageArray);

    if (outSignatureLength != 64) {
        env->ThrowNew(illegalArgument, "Output should be exactly 64 bytes");
        return JNI_FALSE;
    }

    if (privateKeyLength != 64) {
        env->ThrowNew(illegalArgument, "Private key should be 64 bytes");
        return JNI_FALSE;
    }

    jbyte* outSignature = env->GetByteArrayElements(outSignatureArray, 0);

    jbyte privateKey[64];
    env->GetByteArrayRegion(privateKeyArray, 0, 64, privateKey);

    vector<jbyte> message;
    message.resize(messageLength);
    env->GetByteArrayRegion(messageArray, 0, messageLength, message.data());

    jboolean ret = ED25519_sign((uint8_t*)outSignature,
                                (uint8_t*)message.data(),
                                messageLength,
                                (uint8_t*)privateKey)
        ? JNI_TRUE : JNI_FALSE;

    env->ReleaseByteArrayElements(outSignatureArray, outSignature, 0);
    return ret;
}

// ----------------------------------------------------------------------------

const char* const kRoughtimeClientPathName = "android/net/RoughtimeClient";

static const JNINativeMethod gRoughtimeClientMethods[] = {
    {"verifySignature", "([B[B[B)Z",
        (void*)android_net_RoughtimeClient_verifySignature},
    {"sign", "([B[B[B)Z",
        (void*)android_net_RoughtimeClient_sign},
};

int register_android_net_RoughtimeClient(JNIEnv* env)
{
    return RegisterMethodsOrDie(env, kRoughtimeClientPathName,
                                gRoughtimeClientMethods,
                                NELEM(gRoughtimeClientMethods));

}

};
