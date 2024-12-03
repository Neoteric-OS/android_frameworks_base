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

package android.app;

import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.util.ArrayMap;

import com.android.internal.pm.RoSystemFeatures;

import java.util.Arrays;
import java.util.Map;

/**
 * Simple cache for SDK-defined feature versions.
 *
 * This is a compact cache of dense versions to minimize per-process memory impact.
 *
 * @hide
 */
class SystemFeatureCache {

    private static final int UNAVAILABLE_FEATURE_VERSION = Integer.MIN_VALUE;

    private final int[] mSdkFeatureVersions;

    SystemFeatureCache(ArrayMap<String, FeatureInfo> availableFeatures) {
        // First set all SDK-defined features as unavailable.
        mSdkFeatureVersions = new int[PackageManager.SDK_FEATURE_COUNT];
        Arrays.fill(mSdkFeatureVersions, UNAVAILABLE_FEATURE_VERSION);

        // Then populate SDK-defined feature versions from the full set of runtime features.
        for (Map.Entry<String, FeatureInfo> e : availableFeatures.entrySet()) {
            int sdkFeatureIndex = PackageManager.maybeGetSdkFeatureIndex(e.getKey());
            if (sdkFeatureIndex >= 0) {
                mSdkFeatureVersions[sdkFeatureIndex] = e.getValue().version;
            }
        }
    }

    SystemFeatureCache(final int[] sdkFeatureVersions) {
        if (sdkFeatureVersions.length != PackageManager.SDK_FEATURE_COUNT) {
            throw new IllegalArgumentException(
                    String.format(
                            "Unexpected SDK feature count: %d (expected %d)",
                            sdkFeatureVersions.length, PackageManager.SDK_FEATURE_COUNT));
        }
        this.mSdkFeatureVersions = sdkFeatureVersions;
    }

    int[] getSdkFeatureVersions() {
        return mSdkFeatureVersions;
    }

    Boolean maybeHasSystemFeature(String name, int version) {
        // First check compile-time system features.
        Boolean maybeHasFeature = RoSystemFeatures.maybeHasFeature(name, version);
        if (maybeHasFeature != null) {
            return maybeHasFeature;
        }

        // Then check SDK-defined cached system features.
        return maybeHasSdkSystemFeature(name, version);

        // TODO: Fall back to the static IPC cache.
        // return mSystemFeatureIpcCache.query(new HasSystemFeatureQuery(name, version));
    }

    private Boolean maybeHasSdkSystemFeature(String name, int version) {
        // Ignore pathological checks against a version that matches our sentinel unavailable value.
        if (version == UNAVAILABLE_FEATURE_VERSION) {
            return null;
        }

        // Features defined outside of the SDK aren't cached.
        int sdkFeatureIndex = PackageManager.maybeGetSdkFeatureIndex(name);
        if (sdkFeatureIndex < 0) {
            return null;
        }

        return mSdkFeatureVersions[sdkFeatureIndex] >= version;
    }

}
