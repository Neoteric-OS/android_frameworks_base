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

package com.android.server.hdmi;

import android.os.Handler;
import android.os.Looper;
import android.os.MessageQueue;
import android.util.Slog;
import android.util.SparseArray;
import com.android.internal.util.IndentingPrintWriter;
import com.android.server.hdmi.HdmiAnnotations.IoThreadOnly;
import com.android.server.hdmi.HdmiAnnotations.ServiceThreadOnly;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

import libcore.util.EmptyArray;

/**
 * Manages HDMI eARC behaviors.
 * <p>It should be careful to access member variables on IO thread because
 * it can be accessed from system thread as well.
 *
 * <p>It can be created only by {@link HdmiEarcController#create}
 *
 * <p>Declared as package-private, accessed by {@link HdmiControlService} only.
 */
final class HdmiEarcController {
    private static final String TAG = "HdmiEarcController";

    // Handler instance to process synchronous I/O (mainly send) message.
    private Handler mIoHandler;

    // Handler instance to get eARC information
    private Handler mControlHandler;

    // Stores the pointer to the native implementation of the service that
    // interacts with HAL.
    private volatile long mNativePtr;

    private final HdmiAudioService mService;

    // Private constructor.  Use HdmiEarcController.create().
    private HdmiEarcController(HdmiAudioService service) {
        mService = service;
    }

    /**
     * A factory method to get {@link HdmiEarcController}. If it fails to initialize
     * inner device or has no device it will return {@code null}.
     *
     * <p>Declared as package-private, accessed by {@link HdmiControlService} only.
     * @param service {@link HdmiControlService} instance used to create internal handler
     *                and to pass callback for incoming message or event.
     * @return {@link HdmiEarcController} if device is initialized successfully. Otherwise,
     *         returns {@code null}.
     */
    static HdmiEarcController create(HdmiAudioService service) {
        HdmiEarcController controller = new HdmiEarcController(service);
        long nativePtr = nativeInit(controller, service.getServiceLooper().getQueue());
        if (nativePtr == 0L) {
            controller = null;
            return null;
        }

        controller.init(nativePtr);
        return controller;
    }

    private void init(long nativePtr) {
        mIoHandler = new Handler(mService.getIoLooper());
        mControlHandler = new Handler(mService.getServiceLooper());
        mNativePtr = nativePtr;
    }

    @ServiceThreadOnly
    boolean isSupported() {
        assertRunOnServiceThread();
        return nativeIsSupported(mNativePtr);
    }

    @ServiceThreadOnly
    int getPortId() {
        assertRunOnServiceThread();
        return nativeGetPortId(mNativePtr);
    }

    /**
     * Return eARC status of the device.
     *
    */
    int getStatus(int port) {
        return nativeGetStatus(mNativePtr, port);
    }

    byte[] getCapability(int port) {
        return nativeGetCapability(mNativePtr, port);
    }

    int getLatency(int port) {
        return nativeGetLatency(mNativePtr, port);
    }

    @ServiceThreadOnly
    int controlAudioLatency(int latency) {
        assertRunOnServiceThread();
        return nativeControlAudioLatency(mNativePtr, latency);
    }

    @ServiceThreadOnly
    int controlFeature(int control) {
        assertRunOnServiceThread();
        return nativeControlFeature(mNativePtr, control);
    }

    private void assertRunOnIoThread() {
        if (Looper.myLooper() != mIoHandler.getLooper()) {
            throw new IllegalStateException("Should run on io thread.");
        }
    }

    private void assertRunOnServiceThread() {
        if (Looper.myLooper() != mControlHandler.getLooper()) {
            throw new IllegalStateException("Should run on service thread.");
        }
    }

    // Run a Runnable on IO thread.
    // It should be careful to access member variables on IO thread because
    // it can be accessed from system thread as well.
    private void runOnIoThread(Runnable runnable) {
        mIoHandler.post(runnable);
    }

    private void runOnServiceThread(Runnable runnable) {
        mControlHandler.post(runnable);
    }

    @ServiceThreadOnly
    void flush(final Runnable runnable) {
        assertRunOnServiceThread();
        runOnIoThread(new Runnable() {
            @Override
            public void run() {
                // This ensures the runnable for cleanup is performed after all the pending
                // commands are processed by IO thread.
                runOnServiceThread(runnable);
            }
        });
    }


    /**
     * Called by native when a eARC status change event issues.
     */
    @ServiceThreadOnly
    private void handleEarcStatusChanged(int earcStatus) {
        assertRunOnServiceThread();
        HdmiLogger.debug("eARC status changed to:%d", earcStatus);
        mService.onEarcStatus(earcStatus);
    }

    /**
     * Called by native when a eARC status change event issues.
     */
    @ServiceThreadOnly
    private void handleEarcAudioLatencyChanged(int flag) {
        assertRunOnServiceThread();
        HdmiLogger.debug("handleEarcAudioLatencyChanged:%d", flag);
        mService.onEarcAudioLatency(flag);
    }

    /**
     * Called by native when a eARC status change event issues.
     */
    @ServiceThreadOnly
    private void handleEarcCapsChanged(int flag) {
        assertRunOnServiceThread();
        HdmiLogger.debug("handleEarcCapsChanged:%d", flag);
        mService.onEarcCaps(flag);
    }

    private static native long nativeInit(HdmiEarcController handler, MessageQueue messageQueue);
    private static native int nativeGetStatus(long controllerPtr, int port);
    private static native byte[] nativeGetCapability(long controllerPtr, int port);
    private static native int nativeGetPortId(long controllerPtr);
    private static native boolean nativeIsSupported(long controllerPtr);
    private static native int nativeGetLatency(long controllerPtr, int port);
    private static native int nativeControlAudioLatency(long controllerPtr, int latency);
    private static native int nativeControlFeature(long controllerPtr, int control);
}
