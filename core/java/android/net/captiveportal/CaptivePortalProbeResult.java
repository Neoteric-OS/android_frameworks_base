/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.net.captiveportal;

import android.annotation.Nullable;

/** @hide */
public class CaptivePortalProbeResult {
    private final Result mResult;
    @Nullable
    private final CaptivePortalProbeSpec mProbeSpec;
    @Nullable
    private final String mRedirectUrl;

    public static final CaptivePortalProbeResult FAILED = new CaptivePortalProbeResult(
            null, null, Result.FAILED);
    public static final CaptivePortalProbeResult SUCCESS = new CaptivePortalProbeResult(
            null, null, Result.SUCCESS);

    public enum Result {
        /** The probe detected no portal */
        SUCCESS,
        /** The probe detected a portal */
        PORTAL,
        /** The probe failed */
        FAILED
    }

    public CaptivePortalProbeResult(CaptivePortalProbeSpec probeSpec,
            String redirectUrl, Result result) {
        mProbeSpec = probeSpec;
        mRedirectUrl = redirectUrl;
        mResult = result;
    }

    @Nullable
    public CaptivePortalProbeSpec getProbeSpec() {
        return mProbeSpec;
    }

    @Nullable
    public String getRedirectUrl() {
        return mRedirectUrl;
    }

    public Result getResult() {
        return mResult;
    }

    public boolean isSuccessful() {
        return mResult == Result.SUCCESS;
    }

    public boolean isPortal() {
        return mResult == Result.PORTAL;
    }
}
