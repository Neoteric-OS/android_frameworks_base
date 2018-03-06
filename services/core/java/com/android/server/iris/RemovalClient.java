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
import android.os.UserHandle;
import android.util.Slog;
import com.android.internal.logging.MetricsLogger;

/**
 * A class to keep track of the remove state for a given client.
 */
public abstract class RemovalClient extends ClientMonitor {
    private int mIrisId;

    public RemovalClient(Context context, long halDeviceId, IBinder token,
            IIrisServiceReceiver receiver, int irisId, int groupId, int userId,
            boolean restricted, String owner) {
        super(context, halDeviceId, token, receiver, userId, groupId, restricted, owner);
        mIrisId = irisId;
    }

    @Override
    public int start() {
        IBiometricsIris daemon = getIrisDaemon();
        // The iris template ids will be removed when we get confirmation from the HAL
        try {
            final int result = daemon.remove(getGroupId(), mIrisId);
            if (result != 0) {
                Slog.w(TAG, "startRemove with id = " + mIrisId + " failed, result=" + result);
                MetricsLogger.histogram(getContext(), "irisd_remove_start_error", result);
                onError(IrisManager.IRIS_ERROR_HW_UNAVAILABLE, 0 /* vendorCode */);
                return result;
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "startRemove failed", e);
        }
        return 0;
    }

    @Override
    public int stop(boolean initiatedByClient) {
        if (mAlreadyCancelled) {
            Slog.w(TAG, "stopRemove: already cancelled!");
            return 0;
        }
        IBiometricsIris daemon = getIrisDaemon();
        if (daemon == null) {
            Slog.w(TAG, "stopRemoval: no iris HAL!");
            return ERROR_ESRCH;
        }
        try {
            final int result = daemon.cancel();
            if (result != 0) {
                Slog.w(TAG, "stopRemoval failed, result=" + result);
                return result;
            }
            if (DEBUG) Slog.w(TAG, "client " + getOwnerString() + " is no longer removing");
        } catch (RemoteException e) {
            Slog.e(TAG, "stopRemoval failed", e);
            return ERROR_ESRCH;
        }
        mAlreadyCancelled = true;
        return 0; // success
    }

    /*
     * @return true if we're done.
     */
    private boolean sendRemoved(int irisId, int groupId, int remaining) {
        IIrisServiceReceiver receiver = getReceiver();
        try {
            if (receiver != null) {
                receiver.onRemoved(getHalDeviceId(), irisId, groupId, remaining);
            }
        } catch (RemoteException e) {
            Slog.w(TAG, "Failed to notify Removed:", e);
        }
        return remaining == 0;
    }

    @Override
    public boolean onRemoved(int irisId, int groupId, int remaining) {
        if (irisId != 0) {
            IrisUtils.getInstance().removeIrisIdForUser(getContext(), irisId,
                    getTargetUserId());
        }
        return sendRemoved(irisId, getGroupId(), remaining);
    }

    @Override
    public boolean onEnrollResult(int irisId, int groupId, int rem) {
        if (DEBUG) Slog.w(TAG, "onEnrollResult() called for remove!");
        return true; // Invalid for Remove
    }

    @Override
    public boolean onAuthenticated(int irisId, int groupId) {
        if (DEBUG) Slog.w(TAG, "onAuthenticated() called for remove!");
        return true; // Invalid for Remove.
    }

    @Override
    public boolean onEnumerationResult(int irisId, int groupId, int remaining) {
        if (DEBUG) Slog.w(TAG, "onEnumerationResult() called for remove!");
        return true; // Invalid for Remove.
    }


}
