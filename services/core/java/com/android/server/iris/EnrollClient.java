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
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import java.util.Arrays;

/**
 * A class to keep track of the enrollment state for a given client.
 */
public abstract class EnrollClient extends ClientMonitor {
    private static final long MS_PER_SEC = 1000;
    private static final int ENROLLMENT_TIMEOUT_MS = 60 * 1000; // 1 minute
    private byte[] mCryptoToken;

    public EnrollClient(Context context, long halDeviceId, IBinder token,
            IIrisServiceReceiver receiver, int userId, int groupId, byte [] cryptoToken,
            boolean restricted, String owner) {
        super(context, halDeviceId, token, receiver, userId, groupId, restricted, owner);
        mCryptoToken = Arrays.copyOf(cryptoToken, cryptoToken.length);
    }

    @Override
    public boolean onEnrollResult(int irisId, int groupId, int remaining) {
        if (groupId != getGroupId()) {
            Slog.w(TAG, "groupId != getGroupId(), groupId: " + groupId +
                    " getGroupId():" + getGroupId());
        }
        if (remaining == 0) {
            IrisUtils.getInstance().addIrisForUser(getContext(), irisId,
                    getTargetUserId());
        }
        return sendEnrollResult(irisId, groupId, remaining);
    }

    /*
     * @return true if we're done.
     */
    private boolean sendEnrollResult(int irisId, int groupId, int remaining) {
        IIrisServiceReceiver receiver = getReceiver();
        if (receiver == null)
            return true; // client not listening

        vibrateSuccess();
        MetricsLogger.action(getContext(), MetricsEvent.ACTION_FINGERPRINT_ENROLL);
        try {
            receiver.onEnrollResult(getHalDeviceId(), irisId, groupId, remaining);
            return remaining == 0;
        } catch (RemoteException e) {
            Slog.w(TAG, "Failed to notify EnrollResult:", e);
            return true;
        }
    }

    @Override
    public int start() {
        IBiometricsIris daemon = getIrisDaemon();
        if (daemon == null) {
            Slog.w(TAG, "enroll: no iris HAL!");
            return ERROR_ESRCH;
        }
        final int timeout = (int) (ENROLLMENT_TIMEOUT_MS / MS_PER_SEC);
        try {
            final int result = daemon.enroll(mCryptoToken, getGroupId(), timeout);
            if (result != 0) {
                Slog.w(TAG, "startEnroll failed, result=" + result);
                MetricsLogger.histogram(getContext(), "irisd_enroll_start_error", result);
                onError(IrisManager.IRIS_ERROR_HW_UNAVAILABLE, 0 /* vendorCode */);
                return result;
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "startEnroll failed", e);
        }
        return 0; // success
    }

    @Override
    public int stop(boolean initiatedByClient) {
        if (mAlreadyCancelled) {
            Slog.w(TAG, "stopEnroll: already cancelled!");
            return 0;
        }
        IBiometricsIris daemon = getIrisDaemon();
        if (daemon == null) {
            Slog.w(TAG, "stopEnrollment: no iris HAL!");
            return ERROR_ESRCH;
        }
        try {
            final int result = daemon.cancel();
            if (result != 0) {
                Slog.w(TAG, "startEnrollCancel failed, result = " + result);
                return result;
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "stopEnrollment failed", e);
        }
        if (initiatedByClient) {
            onError(IrisManager.IRIS_ERROR_CANCELED, 0 /* vendorCode */);
        }
        mAlreadyCancelled = true;
        return 0;
    }

    @Override
    public boolean onRemoved(int irisId, int groupId, int remaining) {
        if (DEBUG) Slog.w(TAG, "onRemoved() called for enroll!");
        return true; // Invalid for EnrollClient
    }

    @Override
    public boolean onEnumerationResult(int irisId, int groupId, int remaining) {
        if (DEBUG) Slog.w(TAG, "onEnumerationResult() called for enroll!");
        return true; // Invalid for EnrollClient
    }

    @Override
    public boolean onAuthenticated(int irisId, int groupId) {
        if (DEBUG) Slog.w(TAG, "onAuthenticated() called for enroll!");
        return true; // Invalid for EnrollClient
    }

}
