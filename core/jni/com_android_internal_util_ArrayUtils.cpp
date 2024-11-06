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

#define LOG_TAG "ArrayUtils"

#include <jni.h>
#include <nativehelper/JNIHelp.h>
#include <string.h>

namespace android {

#ifdef __aarch64__
static size_t GetCacheLineSize() {
    // Read the CTR_EL0 register.
    uint64_t ctr_el0;
    asm volatile("mrs %0, ctr_el0" : "=r"(ctr_el0));
    // Extract the "DminLine" field from CTR_EL0.  It contains the log2 of the number of 32-bit
    // words in the smallest cache line of all caches the CPU controls.
    size_t DminLine = (ctr_el0 >> 16) & 0xF;
    // Convert DminLine to bytes.  This doesn't necessarily give the L1 data cache line size, but it
    // will at least be a divisor of it, so it will still work correctly for cache cleaning.
    return 4 << DminLine;
}

static void CleanDataCache(const uint8_t* p, size_t size) {
    // Execute 'dc cvac' at least once on each cache line in the memory region.
    //
    // 'dc cvac' stands for "Data Cache line Clean by Virtual Address to point-of-Coherency".
    // It writes the cache line back to the "point-of-coherency", i.e. main memory.
    static const size_t cache_line_size = GetCacheLineSize();
    for (size_t i = 0; i < size; i += cache_line_size) {
        asm volatile("dc cvac, %0" ::"r"(p + i));
    }
    asm volatile("dc cvac, %0" ::"r"(p + size - 1));
}
#elif defined(__i386__) || defined(__x86_64__)
static size_t GetCacheLineSize() {
    uint32_t a, b, c, d;

    // Execute CPUID with EAX=1 to get the "Processor Info and Feature Bits".
    asm volatile("cpuid" : "=a"(a), "=b"(b), "=c"(c), "=d"(d) : "a"(1), "c"(0));

    // Return the CLFLUSH line size in bytes.
    return 8 * ((b >> 8) & 0xFF);
}

static void CleanDataCache(const uint8_t* p, size_t size) {
    // Execute clflush at least once on each cache line in the memory region.
    static const size_t cache_line_size = GetCacheLineSize();
    for (size_t i = 0; i < size; i += cache_line_size) {
        asm volatile("clflush (%0)" ::"r"(p + i));
    }
    asm volatile("clflush (%0)" ::"r"(p + size - 1));
}
#else
static void CleanDataCache(const uint8_t* p, size_t size) {}
#endif

static void DoZeroize(void* p, size_t size) {
    if (size == 0) {
        return;
    }
#ifdef __BIONIC__
    memset_explicit(p, 0, size);
#else
    memset(p, 0, size);
#endif

    // Clean the data cache so that the data gets zeroized in main memory right away.  Without this,
    // it might not be written to main memory until the cache line happens to be evicted.
    //
    // Note that if GetByteArrayElements() made a temporary copy of the array, then the data cache
    // will be cleaned only for the temporary copy of the array, not for the "real" copy.  The array
    // should be allocated as non-movable to prevent this from happening as well as prevent the JVM
    // from creating additional copies during garbage collection.
    CleanDataCache(static_cast<const uint8_t*>(p), size);
}

static void ZeroizeByteArray(JNIEnv* env, jclass, jbyteArray array) {
    jbyte* elems = env->GetByteArrayElements(array, /* isCopy= */ nullptr);
    DoZeroize(elems, env->GetArrayLength(array) * sizeof(elems[0]));
    env->ReleaseByteArrayElements(array, elems, /* mode= */ 0);
}

static void ZeroizeCharArray(JNIEnv* env, jclass, jcharArray array) {
    jchar* elems = env->GetCharArrayElements(array, /* isCopy= */ nullptr);
    DoZeroize(elems, env->GetArrayLength(array) * sizeof(elems[0]));
    env->ReleaseCharArrayElements(array, elems, /* mode= */ 0);
}

static const JNINativeMethod sMethods[] = {
        {"zeroize", "([B)V", (void*)ZeroizeByteArray},
        {"zeroize", "([C)V", (void*)ZeroizeCharArray},
};

int register_com_android_internal_util_ArrayUtils(JNIEnv* env) {
    return jniRegisterNativeMethods(env, "com/android/internal/util/ArrayUtils", sMethods,
                                    NELEM(sMethods));
}

} // namespace android
