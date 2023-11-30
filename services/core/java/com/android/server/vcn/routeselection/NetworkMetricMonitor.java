/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.vcn.routeselection;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.IpSecTransform;
import android.util.CloseGuard;
import android.util.Log;

import com.android.server.vcn.VcnContext;

/**
 * NetworkMetricMonitor is responsible for managing metric monitoring and tracking the validation
 * result.
 */
abstract class NetworkMetricMonitor implements AutoCloseable {
    private static final boolean VDBG = false; // STOPSHIP: if true

    private final CloseGuard mCloseGuard = new CloseGuard();

    private final VcnContext mVcnContext;

    private boolean mIsSelected;
    private boolean mIsValidationFailed;
    private IpSecTransform mTransformIn;

    final NetworkMetricMonitorCallback mCallback;

    NetworkMetricMonitor(VcnContext vcnContext, NetworkMetricMonitorCallback callback) {
        mVcnContext = vcnContext;
        mIsValidationFailed = false;
        mIsSelected = false;
        mCallback = callback;
    }

    interface NetworkMetricMonitorCallback {
        /**
         * Called when there is a change in the validation result
         *
         * <p>When receiving this call, UnderlyingNetworkEvaluator should reevaluate the its network
         * selection factors by checking the priority class and querying #isValidationFailed() of
         * all NetworkMetricMonitors
         */
        void onValidationResultChanged();
    }

    abstract String getTag();

    abstract void startOrStop();

    abstract void stop();

    abstract boolean isStarted();

    void setSelected(boolean isSelected) {
        mIsSelected = isSelected;

        // Null-out the transform to stop the monitoring
        mTransformIn = null;
        startOrStop();
    }

    void setIpSecTransform(@NonNull IpSecTransform inTransform) {
        mTransformIn = inTransform;
        startOrStop();
    }

    // /**
    //  * Mark the network being “selected” or “unselected”
    //  *
    //  * <p>This method will be called by UnderlyingNetworkEvaluator when there is any network
    //  * selection change
    //  */
    // void setNetworkSelectedByCaller(boolean isSelected, @Nullable IpSecTransform in) {
    //     mIsSelected = isSelected;
    //     mTransformIn = in;

    //     startOrStop();
    // }

    void onValidationResultReceived(boolean isFailed) {
        boolean wasFailed = mIsValidationFailed;
        mIsValidationFailed = isFailed;

        if (wasFailed != mIsValidationFailed) {
            mCallback.onValidationResultChanged();
        }
        // TODO: Notify caller of back-to-back failures to support back-off penalty timer
    }

    VcnContext getVcnContext() {
        return mVcnContext;
    }

    /**
     * Return whether the metrics is penalized
     *
     * <p>This method will be called by UnderlyingNetworkEvaluator to reevaluate the its network
     * selection factors
     */
    boolean isValidationFailed() {
        return mIsValidationFailed;
    }

    boolean isSelected() {
        return mIsSelected;
    }

    @Nullable
    IpSecTransform getTransformIn() {
        return mTransformIn;
    }

    @Override
    public void close() {
        mCloseGuard.close();

        stop();
        mTransformIn.close();
    }

    @Override
    public void finalize() {
        if (mCloseGuard != null) {
            mCloseGuard.warnIfOpen();
        }
    }

    void logV(String details) {
        if (VDBG) {
            Log.v(getTag(), details);
        }
    }

    void logD(String details) {
        Log.d(getTag(), details);
    }

    void logE(String details) {
        Log.e(getTag(), details);
    }

    void logWtf(String details) {
        Log.wtf(getTag(), details);
    }
}
