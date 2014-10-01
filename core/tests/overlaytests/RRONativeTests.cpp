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
#include <androidfw/Asset.h>
#include <androidfw/AssetManager.h>
#include <androidfw/ResourceTypes.h>

#include "RROBase.h"

using namespace android;

namespace {
    static const char *LOREM_IPSUM = "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";

    class RRONativeTests: public RROBase {
        private:
            int32_t identifierForName(const ResTable& table, const char *package,
                    const char *type, const char *name) {
                const String16 p(package);
                const String16 t(type);
                const String16 n(name);
                return table.identifierForName(n, n.size(), t, t.size(), p, p.size());
            }

        protected:
            String8 scratch;
            struct {
                struct {
                    struct {
                        int32_t config_annoy_dianne;
                    } boolean;
                } R;
            } android;
            struct {
                struct {
                    int32_t i;
                } integer;
                struct {
                    int32_t s;
                } string;
            } R;

            virtual void SetUp() {
                scratch = PATH_ROOT_DIR.appendPathCopy("scratch");
                EXPECT_EQ(mkdir_p(scratch), NO_ERROR);

                AssetManager am;
                int32_t cookie;
                EXPECT_TRUE(am.addAssetPath(PATH_ANDROID_APK, &cookie));
                EXPECT_TRUE(am.addAssetPath(PATH_TARGET_APK, &cookie));
                const ResTable& table = am.getResources();

                android.R.boolean.config_annoy_dianne =
                    identifierForName(table, "android", "bool", "config_annoy_dianne");
                R.integer.i = identifierForName(table, "com.android.rrotests", "integer", "i");
                R.string.s = identifierForName(table, "com.android.rrotests", "string", "s");
            }

            virtual void TearDown() {
                EXPECT_EQ(rm_rf(scratch), NO_ERROR);
            }

            void verifyIntegerResource(const AssetManager& am, int32_t resid, uint32_t expected) {
                EXPECT_NE(resid, 0x00000000);
                const ResTable& table = am.getResources();
                Res_value value;
                ssize_t block = table.getResource(resid, &value);
                EXPECT_GE(block, 0);
                EXPECT_EQ(value.dataType, Res_value::TYPE_INT_DEC);
                EXPECT_EQ(value.data, expected);
            }

            void verifyStringResource(const AssetManager& am, int32_t resid,
                    const String8& expected) {
                EXPECT_NE(resid, 0x00000000);
                const ResTable& table = am.getResources();
                Res_value value;
                ssize_t block = table.getResource(resid, &value);
                EXPECT_GE(block, 0);
                EXPECT_EQ(value.dataType, Res_value::TYPE_STRING);
                const ResStringPool *pool = table.getTableStringBlock(block);
                EXPECT_TRUE(pool != NULL);
                EXPECT_LT(value.data, pool->size());
                EXPECT_EQ(expected, pool->string8ObjectAt(value.data));
            }

            void verifyBooleanResource(const AssetManager& am, int32_t resid, bool expected) {
                EXPECT_NE(resid, 0x00000000);
                const ResTable& table = am.getResources();
                Res_value value;
                ssize_t block = table.getResource(resid, &value);
                EXPECT_GE(block, 0);
                EXPECT_EQ(value.dataType, Res_value::TYPE_INT_BOOLEAN);
                EXPECT_EQ(value.data == 0xffffffff, expected);
            }

            void verifyNonAssetResource(const AssetManager& am, const char *path,
                    const char *expected) {
                Asset *a =
                    const_cast<AssetManager *>(&am)->openNonAsset(path, Asset::ACCESS_BUFFER);
                EXPECT_TRUE(a != NULL);
                const off64_t N = a->getLength() - 1;
                EXPECT_EQ(strlen(expected), N);
                EXPECT_EQ(memcmp(a->getBuffer(true), expected, N), 0);
            }

            void setConfiguration(AssetManager& am, const char *country) {
                ResTable_config config;
                am.getConfiguration(&config);
                am.setConfiguration(config, country);
            }
    };

    TEST_F(RRONativeTests, idmapDashDashPathTest) {
        const String8 idmap = scratch.appendPathCopy("1.idmap");

        const char *argv[] = {
            "/system/bin/idmap",
            "--path",
            PATH_TARGET_APK,
            PATH_APP_OVERLAY_1_APK,
            idmap,
            NULL
        };
        EXPECT_EQ(exec(argv), NO_ERROR);

        String8 output;
        const char *argv1[] = {
            "/system/bin/idmap",
            "--inspect",
            idmap,
            NULL
        };
        EXPECT_EQ(exec(argv1, &output), NO_ERROR);
        EXPECT_NE(output.find("integer/i"), -1);
    }

    TEST_F(RRONativeTests, idmapDashDashScanOneDirectoryTest) {
        const String8 dir = PATH_APP_OVERLAY_1_APK.getPathDir();
        const char *argv[] = {
            "/system/bin/idmap",
            "--scan",
            "com.android.rrotests", // target-package-name-to-look-for
            PATH_TARGET_APK, // path-to-target-apk
            scratch, // dir-to-hold-idmaps
            dir.string(), // dir-to-scan
            NULL
        };
        EXPECT_EQ(exec(argv), NO_ERROR);

        const String8 overlays_list = scratch.appendPathCopy("overlays.list");
        static const char *expected = "/data/nativetest/rro_tests/data/rro_tests_app_overlay_1/rro_tests_app_overlay_1.apk /data/nativetest/rro_tests/scratch/data@nativetest@rro_tests@data@rro_tests_app_overlay_1@rro_tests_app_overlay_1.apk@idmap\n";
        String8 contents;
        EXPECT_EQ(readFile(overlays_list, contents), NO_ERROR);
        EXPECT_STREQ(contents.string(), expected);
    }

    TEST_F(RRONativeTests, idmapDashDashScanTwoDirectoriesTest) {
        const String8 dir1 = PATH_APP_OVERLAY_1_APK.getPathDir();
        const String8 dir2 = PATH_APP_OVERLAY_2_APK.getPathDir();
        const char *argv[] = {
            "/system/bin/idmap",
            "--scan",
            "com.android.rrotests", // target-package-name-to-look-for
            PATH_TARGET_APK, // path-to-target-apk
            scratch, // dir-to-hold-idmaps
            dir1.string(), // dir-to-scan
            dir2.string(), // dir-to-scan
            NULL
        };
        EXPECT_EQ(exec(argv), NO_ERROR);

        // both expected1 and expected2 should be part of overlays.list, but
        // the order is unspecified
        static const char *expected1 = "/data/nativetest/rro_tests/data/rro_tests_app_overlay_1/rro_tests_app_overlay_1.apk /data/nativetest/rro_tests/scratch/data@nativetest@rro_tests@data@rro_tests_app_overlay_1@rro_tests_app_overlay_1.apk@idmap\n";
        static const char *expected2 = "/data/nativetest/rro_tests/data/rro_tests_app_overlay_2/rro_tests_app_overlay_2.apk /data/nativetest/rro_tests/scratch/data@nativetest@rro_tests@data@rro_tests_app_overlay_2@rro_tests_app_overlay_2.apk@idmap\n";
        const String8 overlays_list = scratch.appendPathCopy("overlays.list");
        String8 contents;
        EXPECT_EQ(readFile(overlays_list, contents), NO_ERROR);
        EXPECT_NE(contents.find(expected1), -1);
        EXPECT_NE(contents.find(expected2), -1);
    }

    TEST_F(RRONativeTests, idmapDashDashScanMissingDirectoryTest) {
        const char *argv[] = {
            "/system/bin/idmap",
            "--scan",
            "com.android.rrotests", // target-package-name-to-look-for
            PATH_TARGET_APK, // path-to-target-apk
            scratch, // dir-to-hold-idmaps
            "/this/directory/does/not/exist", // dir-to-scan
            NULL
        };
        EXPECT_NE(exec(argv), NO_ERROR);
    }

    TEST_F(RRONativeTests, resourcesWithoutOverlayTest) {
        AssetManager am;
        int32_t cookie;
        EXPECT_TRUE(am.addAssetPath(PATH_ANDROID_APK, &cookie));
        EXPECT_TRUE(am.addAssetPath(PATH_TARGET_APK, &cookie));

        EXPECT_NO_FATAL_FAILURE(verifyIntegerResource(am, R.integer.i, 0));
        EXPECT_NO_FATAL_FAILURE(verifyStringResource(am, R.string.s, String8("a")));
        EXPECT_NO_FATAL_FAILURE(verifyNonAssetResource(am, "assets/lorem-ipsum.txt", LOREM_IPSUM));
        EXPECT_NO_FATAL_FAILURE(
                verifyBooleanResource(am, android.R.boolean.config_annoy_dianne, true));

        setConfiguration(am, "sv");

        EXPECT_NO_FATAL_FAILURE(verifyIntegerResource(am, R.integer.i, 0));
        EXPECT_NO_FATAL_FAILURE(verifyStringResource(am, R.string.s, String8("A")));
        EXPECT_NO_FATAL_FAILURE(verifyNonAssetResource(am, "assets/lorem-ipsum.txt", LOREM_IPSUM));
        EXPECT_NO_FATAL_FAILURE(
                verifyBooleanResource(am, android.R.boolean.config_annoy_dianne, true));
    }

    TEST_F(RRONativeTests, resourcesWithSingleOverlayTest) {
        const String8 system_idmap("/data/resource-cache/data@nativetest@rro_tests@data@rro_tests_system_overlay_1@rro_tests_system_overlay_1.apk@idmap");
        const String8 app_idmap("/data/resource-cache/data@nativetest@rro_tests@data@rro_tests_app_overlay_1@rro_tests_app_overlay_1.apk@idmap");

        const char *argv1[] = {
            "/system/bin/idmap",
            "--path",
            PATH_ANDROID_APK,
            PATH_SYSTEM_OVERLAY_1_APK,
            system_idmap,
            NULL
        };
        EXPECT_EQ(exec(argv1), NO_ERROR);

        const char *argv2[] = {
            "/system/bin/idmap",
            "--path",
            PATH_TARGET_APK,
            PATH_APP_OVERLAY_1_APK,
            app_idmap,
            NULL
        };
        EXPECT_EQ(exec(argv2), NO_ERROR);

        AssetManager am;
        int32_t cookie;
        EXPECT_TRUE(am.addAssetPath(PATH_ANDROID_APK, &cookie));
        EXPECT_TRUE(am.addOverlayPath(PATH_SYSTEM_OVERLAY_1_APK, &cookie));
        EXPECT_TRUE(am.addAssetPath(PATH_TARGET_APK, &cookie));
        EXPECT_TRUE(am.addOverlayPath(PATH_APP_OVERLAY_1_APK, &cookie));

        EXPECT_NO_FATAL_FAILURE(verifyIntegerResource(am, R.integer.i, 1));
        EXPECT_NO_FATAL_FAILURE(verifyStringResource(am, R.string.s, String8("b")));
        EXPECT_NO_FATAL_FAILURE(verifyNonAssetResource(am, "assets/lorem-ipsum.txt", "foobar"));
        EXPECT_NO_FATAL_FAILURE(
                verifyBooleanResource(am, android.R.boolean.config_annoy_dianne, false));

        setConfiguration(am, "sv");

        EXPECT_NO_FATAL_FAILURE(verifyIntegerResource(am, R.integer.i, 1));
        EXPECT_NO_FATAL_FAILURE(verifyStringResource(am, R.string.s, String8("B")));
        EXPECT_NO_FATAL_FAILURE(verifyNonAssetResource(am, "assets/lorem-ipsum.txt", "foobar"));
        EXPECT_NO_FATAL_FAILURE(
                verifyBooleanResource(am, android.R.boolean.config_annoy_dianne, false));

        EXPECT_EQ(rm_rf(app_idmap), NO_ERROR);
        EXPECT_EQ(rm_rf(system_idmap), NO_ERROR);
    }
}
