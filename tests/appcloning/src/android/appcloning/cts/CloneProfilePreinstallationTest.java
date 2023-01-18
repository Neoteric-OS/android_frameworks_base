/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.appcloning.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.Presubmit;
import android.support.test.uiautomator.UiDevice;
import android.util.ArraySet;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bedstead.harrier.annotations.RequireMultiUserSupport;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.SystemUtil;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Presubmit
@RunWith(AndroidJUnit4.class)
public final class CloneProfilePreinstallationTest extends AppCloningDeviceTestBase {

    private Context mContext;
    private UserManager mUserManager;

    private static String runShellCommand(String cmd) throws Exception {
        return UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .executeShellCommand(cmd);
    }

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mUserManager = mContext.getSystemService(UserManager.class);

        assumeTrue(SdkLevel.isAtLeastU());
        assertWithMessage("UserManager service").that(mUserManager).isNotNull();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#createProfile"})
    @RequireMultiUserSupport
    public void testUserSystemPackageAllowlistProblems_ForCloneProfile_HasNoLauncherApps()
            throws Exception {
        int cloneUserId = createAndStartUser("testCloneUser",
                UserManager.USER_TYPE_PROFILE_CLONE, "0");
        try {
            SystemUtil.runWithShellPermissionIdentity(() ->
                    determinePackageConfigurationIssues(cloneUserId)
            );
        } finally {
            removeUser(cloneUserId);
        }
    }

    private void determinePackageConfigurationIssues(int cloneProfileId) throws Exception {
        // run command to get critical package whitelist configuration issues. These
        // are packages that are installed but not present in the pre-installation configs
        String cmd = "cmd user report-system-user-package-whitelist-problems --critical-only "
                + "--mode 1";
        final String result = runShellCommand(cmd);
        String[] results = result.split("\\R");

        final ArraySet<String> launcherPackages = getPackagesWithLauncherComponentForUser(
                cloneProfileId);
        List<String> errors = new ArrayList<>();
        Pattern p = Pattern.compile("(([a-z]+\\.)+[a-z]+)");
        // Select only errors from the above launcher packages
        for (String res : results) {
            Matcher m = p.matcher(res);
            if (m.find()) {
                String packageName = m.group(0);
                if (launcherPackages.contains(packageName)) {
                    errors.add(res);
                }
            }
        }

        if (!errors.isEmpty()) {
            fail("Command '" + cmd + "' reported errors:\n" + String.join("\n", errors));
        }
    }

    private ArraySet<String> getPackagesWithLauncherComponentForUser(int userId) {
        LauncherApps mLauncherApps = mContext.getSystemService(LauncherApps.class);
        List<LauncherActivityInfo> launcherActivities = mLauncherApps.getActivityList(
                null, UserHandle.of(ActivityManager.getCurrentUser()));
        ArraySet<String> launcherPackages = new ArraySet<>();

        for (LauncherActivityInfo launcherActivity : launcherActivities) {
            launcherPackages.add(launcherActivity.getActivityInfo().packageName);
        }
        return launcherPackages;
    }
}
