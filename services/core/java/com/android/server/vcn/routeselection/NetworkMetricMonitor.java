/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.server.VcnManagementService.LOCAL_LOG;
import static com.android.server.vcn.util.PersistableBundleUtils.PersistableBundleWrapper;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.IpSecTransform;
import android.net.IpSecTransformState;
import android.net.Network;
import android.os.OutcomeReceiver;
import android.util.CloseGuard;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.annotations.VisibleForTesting.Visibility;
import com.android.server.vcn.VcnContext;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * NetworkMetricMonitor is responsible for managing metric monitoring and tracking validation
 * results.
 *
 * <p>This class is flag gated by "network_metric_monitor"
 */
public abstract class NetworkMetricMonitor implements AutoCloseable {
    private static final String TAG = NetworkMetricMonitor.class.getSimpleName();

    private static final boolean VDBG = false; // STOPSHIP: if true

    @NonNull private final CloseGuard mCloseGuard = new CloseGuard();

    @NonNull private final VcnContext mVcnContext;
    @NonNull private final Network mNetwork;
    @NonNull private final NetworkMetricMonitorCallback mCallback;

    @Nullable private IpSecTransformWrapper mTransformIn;

    private boolean mIsSelected;
    private boolean mIsStarted;
    private boolean mIsValidationFailed;

    protected NetworkMetricMonitor(
            @NonNull VcnContext vcnContext,
            @NonNull Network network,
            @Nullable PersistableBundleWrapper carrierConfig,
            @NonNull NetworkMetricMonitorCallback callback) {
        if (!vcnContext.isFlagNetworkMetricMonitorEnabled()) {
            // Caller error
            throw new IllegalStateException("networkMetricMonitor flag disabled");
        }

        mVcnContext = vcnContext;
        mNetwork = network;
        mCallback = callback;

        mTransformIn = null;
        mIsSelected = false;
        mIsStarted = false;
        mIsValidationFailed = false;
    }

    /** Callback to notify caller of the validation result change */
    public interface NetworkMetricMonitorCallback {
        /** Called when there is a change in the validation result */
        void onValidationResultChanged();
    }

    protected abstract String getClassName();

    protected abstract void startOrStop();

    /**
     * Start monitoring
     *
     * <p>Subclasses MUST call super.start() when overriding this method
     */
    protected void start() {
        mIsStarted = true;
        mIsValidationFailed = false;
    }

    /**
     * Stop monitoring
     *
     * <p>Subclasses MUST call super.stop() when overriding this method
     */
    public void stop() {
        mIsStarted = false;
        mIsValidationFailed = false;
    }

    /** Called by the subclasses when the validation result is ready */
    protected void onValidationResultReceivedInternal(boolean isFailed) {
        boolean wasFailed = mIsValidationFailed;
        mIsValidationFailed = isFailed;

        if (wasFailed != mIsValidationFailed) {
            mCallback.onValidationResultChanged();
        }
        // TODO: Notify caller of back-to-back failures to support back-off penalty timer
    }

    /** Mark the network being monitored selected or unselected */
    public void setIsSelected(boolean isSelected) {
        if (mIsSelected == isSelected) {
            return;
        }

        mIsSelected = isSelected;
        mTransformIn = null;
        startOrStop();
    }

    /** Wrapper that allows injection for testing purposes */
    @VisibleForTesting(visibility = Visibility.PROTECTED)
    public static class IpSecTransformWrapper {
        @NonNull public final IpSecTransform ipSecTransform;

        public IpSecTransformWrapper(@NonNull IpSecTransform ipSecTransform) {
            this.ipSecTransform = ipSecTransform;
        }

        /** Poll an IpSecTransformState */
        public void getIpSecTransformState(
                @NonNull Executor executor,
                @NonNull OutcomeReceiver<IpSecTransformState, RuntimeException> callback) {
            ipSecTransform.getIpSecTransformState(executor, callback);
        }

        /** Close this instance and release the underlying resources */
        public void close() {
            ipSecTransform.close();
        }
    }

    /** Set the IpSecTransform that applied to the Network being monitored */
    public void setIpSecTransform(@NonNull IpSecTransform inTransform) {
        // When multiple parallel inbound transforms are created, NetworkMetricMonitor will be
        // enabled on the last one as a sample
        setIpSecTransformInternal(new IpSecTransformWrapper(inTransform));
    }

    /** Set the IpSecTransform that applied to the Network being monitored */
    @VisibleForTesting(visibility = Visibility.PRIVATE)
    public void setIpSecTransformInternal(@NonNull IpSecTransformWrapper inTransform) {
        Objects.requireNonNull(inTransform, "inTransform is null");

        if (!mIsSelected) {
            logWtf("setIpSecTransform called but network not selected");
            return;
        }

        mTransformIn = inTransform;
        startOrStop();
    }

    public boolean isValidationFailed() {
        return mIsValidationFailed;
    }

    public boolean isSelected() {
        return mIsSelected;
    }

    public boolean isStarted() {
        return mIsStarted;
    }

    @NonNull
    public VcnContext getVcnContext() {
        return mVcnContext;
    }

    @VisibleForTesting(visibility = Visibility.PROTECTED)
    @Nullable
    public IpSecTransformWrapper getTransformInInternal() {
        return mTransformIn;
    }

    @Nullable
    public IpSecTransform getTransform() {
        return getTransformInInternal().ipSecTransform;
    }

    // Override methods for AutoCloseable
    @Override
    public void close() {
        mCloseGuard.close();

        stop();
        if (mTransformIn != null) {
            mTransformIn.close();
        }
    }

    @Override
    public void finalize() {
        if (mCloseGuard != null) {
            mCloseGuard.warnIfOpen();
        }
    }

    protected String getLogPrefix() {
        return " [Network " + mNetwork + "] ";
    }

    protected void logV(String msg) {
        if (VDBG) {
            Slog.v(getClassName(), getLogPrefix() + msg);
            LOCAL_LOG.log("[VERBOSE ] " + getClassName() + getLogPrefix() + msg);
        }
    }

    protected void logInfo(String msg) {
        Slog.i(getClassName(), getLogPrefix() + msg);
        LOCAL_LOG.log("[INFO ] " + getClassName() + getLogPrefix() + msg);
    }

    protected void logW(String msg) {
        Slog.w(getClassName(), getLogPrefix() + msg);
        LOCAL_LOG.log("[WARN ] " + getClassName() + getLogPrefix() + msg);
    }

    protected void logWtf(String msg) {
        Slog.wtf(getClassName(), getLogPrefix() + msg);
        LOCAL_LOG.log("[WTF ] " + getClassName() + getLogPrefix() + msg);
    }

    protected static void logV(String className, String msgWithPrefix) {
        if (VDBG) {
            Slog.wtf(className, msgWithPrefix);
            LOCAL_LOG.log("[VERBOSE ] " + className + msgWithPrefix);
        }
    }

    protected static void logWtf(String className, String msgWithPrefix) {
        Slog.wtf(className, msgWithPrefix);
        LOCAL_LOG.log("[WTF ] " + className + msgWithPrefix);
    }
}
