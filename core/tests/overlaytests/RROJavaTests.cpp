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
#include "RROBase.h"

namespace {
    class RROJavaTests: public RROBase {
        protected:
            static void SetUpTestCase() {
                stopDevice();
            }

            static void TearDownTestCase() {
                startDevice();
            }
    };

    static const String8 pathInstalledApp("/data/app/target/target.apk");
    static const String8 pathInstalledSystemOverlay1("/vendor/overlay/system-overlay-1.apk");
    static const String8 pathInstalledSystemOverlay2("/vendor/overlay/system-overlay-2.apk");
    static const String8 pathInstalledAppOverlay1("/vendor/overlay/app-overlay-1.apk");
    static const String8 pathInstalledAppOverlay2("/vendor/overlay/app-overlay-2.apk");
    static const String8 pathIdmapSystem1(
            "/data/resource-cache/vendor@overlay@system-overlay-1.apk@idmap");
    static const String8 pathIdmapApp1(
            "/data/resource-cache/vendor@overlay@app-overlay-1.apk@idmap");
    static const String8 pathIdmapApp2(
            "/data/resource-cache/vendor@overlay@app-overlay-2.apk@idmap");

    TEST_F(RROJavaTests, instrumentationSetup0Test) {
        cp(PATH_TARGET_APK, pathInstalledApp);
        startDevice();

        EXPECT_EQ(execInstrumentation(0), NO_ERROR);

        stopDevice();
        rm_rf(pathInstalledApp.getPathDir());
    }

    TEST_F(RROJavaTests, instrumentationSetup1Test) {
        cp(PATH_TARGET_APK, pathInstalledApp);
        cp(PATH_APP_OVERLAY_1_APK, pathInstalledAppOverlay1);
        cp(PATH_SYSTEM_OVERLAY_1_APK, pathInstalledSystemOverlay1);
        startDevice();

        EXPECT_EQ(execInstrumentation(1), NO_ERROR);

        stopDevice();
        rm_rf(pathIdmapSystem1);
        rm_rf(pathIdmapApp1);
        rm_rf(pathInstalledSystemOverlay1);
        rm_rf(pathInstalledAppOverlay1);
        rm_rf(pathInstalledApp.getPathDir());
    }

    TEST_F(RROJavaTests, instrumentationSetup2Test) {
        cp(PATH_TARGET_APK, pathInstalledApp);
        cp(PATH_APP_OVERLAY_1_APK, pathInstalledAppOverlay1);
        cp(PATH_APP_OVERLAY_2_APK, pathInstalledAppOverlay2);
        cp(PATH_SYSTEM_OVERLAY_1_APK, pathInstalledSystemOverlay1);
        cp(PATH_SYSTEM_OVERLAY_2_APK, pathInstalledSystemOverlay2);
        startDevice();

        EXPECT_EQ(execInstrumentation(2), NO_ERROR);

        stopDevice();
        rm_rf(pathIdmapSystem1);
        rm_rf(pathIdmapApp2);
        rm_rf(pathIdmapApp1);
        rm_rf(pathInstalledSystemOverlay2);
        rm_rf(pathInstalledSystemOverlay1);
        rm_rf(pathInstalledAppOverlay2);
        rm_rf(pathInstalledAppOverlay1);
        rm_rf(pathInstalledApp.getPathDir());
    }
}
