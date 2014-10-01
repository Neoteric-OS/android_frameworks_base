/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
#ifndef RROBASE_H
#define RROBASE_H

#include <utils/Errors.h>
#include <utils/String8.h>

#include <gtest/gtest.h>

using namespace android;

class RROBase: public testing::Test {
    public:
        static const String8 PATH_ANDROID_APK;
        static const String8 PATH_APP_OVERLAY_1_APK;
        static const String8 PATH_APP_OVERLAY_2_APK;
        static const String8 PATH_ROOT_DIR;
        static const String8 PATH_SYSTEM_OVERLAY_1_APK;
        static const String8 PATH_SYSTEM_OVERLAY_2_APK;
        static const String8 PATH_TARGET_APK;

    protected:
        static status_t cp(const String8& src, const String8& dest);
        static status_t exec(const char *argv[], String8* out = NULL);
        static status_t execInstrumentation(int whichSetup);
        static status_t mkdir_p(const String8& path);
        static status_t readFile(const String8& path, String8& out);
        static status_t rm_rf(const String8& path);
        static status_t startDevice();
        static status_t stopDevice();
};

#endif
