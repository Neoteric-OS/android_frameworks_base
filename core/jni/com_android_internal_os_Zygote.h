/*
 * Copyright (C) 2008 The Android Open Source Project
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

#ifndef _COM_ANDROID_INTERNAL_OS_ZYGOTE_H
#define _COM_ANDROID_INTERNAL_OS_ZYGOTE_H

#define LOG_TAG "Zygote"
#define ATRACE_TAG ATRACE_TAG_DALVIK

/* Functions in the callchain during the fork shall not be protected with
   Armv8.3-A Pointer Authentication, otherwise child will not be able to return. */
#ifdef __ARM_FEATURE_PAC_DEFAULT
#ifdef __ARM_FEATURE_BTI_DEFAULT
#define NO_PAC_FUNC __attribute__((target("branch-protection=bti")))
#else
#define NO_PAC_FUNC __attribute__((target("branch-protection=none")))
#endif /* __ARM_FEATURE_BTI_DEFAULT */
#else /* !__ARM_FEATURE_PAC_DEFAULT */
#define NO_PAC_FUNC
#endif /* __ARM_FEATURE_PAC_DEFAULT */

#include <jni.h>
#include <vector>

namespace android {
namespace zygote {

NO_PAC_FUNC
pid_t ForkCommon(JNIEnv* env, bool is_system_server,
                 const std::vector<int>& fds_to_close,
                 const std::vector<int>& fds_to_ignore,
                 bool is_priority_fork,
                 bool purge = true);

}  // namespace zygote
}  // namespace android

#endif // _COM_ANDROID_INTERNAL_OS_ZYGOTE_
