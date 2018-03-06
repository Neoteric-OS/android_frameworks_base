/**
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.iris;

import android.content.Context;
import android.hardware.biometrics.iris.V1_0.IBiometricsIris;
import android.hardware.iris.IrisManager;
import android.hardware.iris.IIrisServiceReceiver;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Slog;
import com.android.internal.logging.MetricsLogger;

/**
 * A class to keep track of the enumeration state for a given client.
 */
public abstract class EnumerateClient extends ClientMonitor {
    public EnumerateClient(Context context, long halDeviceId, IBinder token,
        IIrisServiceReceiver receiver, int groupId, int userId,
        boolean restricted, String owner) {
        super(context, halDeviceId, token, receiver, userId, groupId, restricted, owner);
    }

    @Override
    public int start() {
        IBiometricsIris daemon = getIrisDaemon();
        // The iris template ids will be removed when we get confirmation from the HAL
        try {
            final int result = daemon.enumerate();
            if (result != 0) {
                Slog.w(TAG, "start enumerate for user " + getTargetUserId()
                    + " failed, result=" + result);
                MetricsLogger.histogram(getContext(), "irisd_enum_start_error", result);
                onError(IrisManager.IRIS_ERROR_HW_UNAVAILABLE, 0 /* vendorCode */);
                return result;
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "startEnumeration failed", e);
        }
        return 0;
    }

    @Override
    public int stop(boolean initiatedByClient) {
        if (mAlreadyCancelled) {
            Slog.w(TAG, "stopEnumerate: already cancelled!");
            return 0;
        }
        IBiometricsIris daemon = getIrisDaemon();
        if (daemon == null) {
            Slog.w(TAG, "stopEnumeration: no iris HAL!");
            return ERROR_ESRCH;
        }
        try {
            final int result = daemon.cancel();
            if (result != 0) {
                Slog.w(TAG, "stop enumeration failed, result=" + result);
                return result;
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "stopEnumeration failed", e);
            return ERROR_ESRCH;
        }

        // We don't actually stop enumerate, but inform the client that the cancel operation
        // succeeded so we can start the next operation.
        if (initiatedByClient) {
            onError(IrisManager.IRIS_ERROR_CANCELED, 0 /* vendorCode */);
        }
        mAlreadyCancelled = true;
        return 0; // success
    }

    @Override
    public boolean onEnumerationResult(int irisId, int groupId, int remaining) {
        IIrisServiceReceiver receiver = getReceiver();
        if (receiver == null)
            return true; // client not listening
        try {
            receiver.onEnumerated(getHalDeviceId(), irisId, groupId, remaining);
        } catch (RemoteException e) {
            Slog.w(TAG, "Failed to notify enumerated:", e);
        }
        return remaining == 0;
    }

    @Override
    public boolean onAuthenticated(int irisId, int groupId) {
        if (DEBUG) Slog.w(TAG, "onAuthenticated() called for enumerate!");
        return true; // Invalid for Enumerate.
    }

    @Override
    public boolean onEnrollResult(int irisId, int groupId, int rem) {
        if (DEBUG) Slog.w(TAG, "onEnrollResult() called for enumerate!");
        return true; // Invalid for Enumerate.
    }

    @Override
    public boolean onRemoved(int irisId, int groupId, int remaining) {
        if (DEBUG) Slog.w(TAG, "onRemoved() called for enumerate!");
        return true; // Invalid for Enumerate.
    }
}
