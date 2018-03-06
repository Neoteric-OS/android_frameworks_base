/**
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

package android.hardware.iris;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemService;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Binder;
import android.os.CancellationSignal;
import android.os.CancellationSignal.OnCancelListener;
import android.os.Handler;
import android.os.IBinder;
import android.os.IRemoteCallback;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.security.keystore.AndroidKeyStoreProvider;
import android.util.Log;
import android.util.Slog;

import java.security.Signature;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.Mac;

import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.Manifest.permission.MANAGE_IRIS;
import static android.Manifest.permission.USE_IRIS;

/**
 * A class that coordinates access to the iris hardware.
 */
@SystemService(Context.IRIS_SERVICE)
public class IrisManager {
    private static final String TAG = "IrisManager";
    private static final boolean DEBUG = true;
    private static final int MSG_ENROLL_RESULT = 100;
    private static final int MSG_ACQUIRED = 101;
    private static final int MSG_AUTHENTICATION_SUCCEEDED = 102;
    private static final int MSG_AUTHENTICATION_FAILED = 103;
    private static final int MSG_ERROR = 104;
    private static final int MSG_REMOVED = 105;
    private static final int MSG_ENUMERATED = 106;

    //
    // Error messages from iris hardware during initilization, enrollment, authentication or
    // removal. Must agree with the list in iris.h
    //

    /**
     * The hardware is unavailable. Try again later.
     */
    public static final int IRIS_ERROR_HW_UNAVAILABLE = 1;

    /**
     * Error state returned when the sensor was unable to process the current image.
     */
    public static final int IRIS_ERROR_UNABLE_TO_PROCESS = 2;

    /**
     * Error state returned when the current request has been running too long. This is intended to
     * prevent programs from waiting for the iris sensor indefinitely. The timeout is
     * platform and sensor-specific, but is generally on the order of 30 seconds.
     */
    public static final int IRIS_ERROR_TIMEOUT = 3;

    /**
     * Error state returned for operations like enrollment; the operation cannot be completed
     * because there's not enough storage remaining to complete the operation.
     */
    public static final int IRIS_ERROR_NO_SPACE = 4;

    /**
     * The operation was canceled because the iris sensor is unavailable. For example,
     * this may happen when the user is switched, the device is locked or another pending operation
     * prevents or disables it.
     */
    public static final int IRIS_ERROR_CANCELED = 5;

    /**
     * The {@link IrisManager#remove} call failed. Typically this will happen when the
     * provided iris id was incorrect.
     *
     * @hide
     */
    public static final int IRIS_ERROR_UNABLE_TO_REMOVE = 6;

   /**
     * The operation was canceled because the API is locked out due to too many attempts.
     * This occurs after 5 failed attempts, and lasts for 30 seconds.
     */
    public static final int IRIS_ERROR_LOCKOUT = 7;

    /**
     * Hardware vendors may extend this list if there are conditions that do not fall under one of
     * the above categories. Vendors are responsible for providing error strings for these errors.
     * These messages are typically reserved for internal operations such as enrollment, but may be
     * used to express vendor errors not covered by the ones in iris.h. Applications are
     * expected to show the error message string if they happen, but are advised not to rely on the
     * message id since they will be device and vendor-specific
     */
    public static final int IRIS_ERROR_VENDOR = 8;

    /**
     * The operation was canceled because IRIS_ERROR_LOCKOUT occurred too many times.
     * Iris authentication is disabled until the user unlocks with strong authentication
     * (PIN/Pattern/Password)
     */
    public static final int IRIS_ERROR_LOCKOUT_PERMANENT = 9;

    /**
     * The user canceled the operation. Upon receiving this, applications should use alternate
     * authentication (e.g. a password). The application should also provide the means to return
     * to iris authentication, such as a "use iris" button.
     */
    public static final int IRIS_ERROR_USER_CANCELED = 10;

    /**
     * @hide
     */
    public static final int IRIS_ERROR_VENDOR_BASE = 1000;

    //
    // Image acquisition messages. Must agree with those in iris.h
    //

    /**
     * The image acquired was good.
     */
    public static final int IRIS_ACQUIRED_GOOD = 0;

    /**
     * The iris image was not good enough to process due to a detected condition (i.e. no iris) or
     * a possibly (See {@link #IRIS_ACQUIRED_TOO_BRIGHT or @link #IRIS_ACQUIRED_TOO_DARK}).
     */
    public static final int IRIS_ACQUIRED_INSUFFICIENT = 1;

    /**
     * The iris image was too bright due to too much ambient light.
     * For example, it's reasonable return this after multiple
     * {@link #IRIS_ACQUIRED_INSUFFICIENT}
     * The user is expected to take action to re try in better lighting conditions
     * when this is returned.
     */
    public static final int IRIS_ACQUIRED_TOO_BRIGHT = 2;

    /**
     * The iris image was too dark due to illumination light obscured.
     * For example, it's reasonable return this after multiple
     * {@link #IRIS_ACQUIRED_INSUFFICIENT}
     * The user is expected to take action to uncover illumination light source
     * when this is returned.
     */
    public static final int IRIS_ACQUIRED_TOO_DARK = 3;

    /**
     * The iris was not in field of view. User might be close to the camera and should be
     * informed on what needs to happen to resolve this problem, e.g. "move further."
     */
    public static final int IRIS_ACQUIRED_TOO_CLOSE = 4;

    /**
     * The iris image was not enough. User might be far from the camera and should be
     * informed on what needs to happen to resolve this problem, e.g. "move closer."
     */
    public static final int IRIS_ACQUIRED_TOO_FAR = 5;

    /**
     * The iris image was not available. User might have been closing the eyes and should be
     * informed on what needs to happen to resolve this problem, e.g. "open eyes."
     */
    public static final int IRIS_ACQUIRED_EYES_CLOSED = 6;

    /**
     * The iris image was not enough. User might have been closing the eyes and should be
     * informed on what needs to happen to resolve this problem, e.g. "open eyes wider."
     */
    public static final int IRIS_ACQUIRED_EYES_PARTIALLY_OBSCURED = 7;

    /**
     * The image acquired was having one iris.
     */
    public static final int IRIS_ACQUIRED_DETECTED_ONE_EYE = 8;

    /**
     * The image acquired was having two iris.
     */
    public static final int IRIS_ACQUIRED_DETECTED_TWO_EYE = 9;

    /**
     * The image acquired was having too many irises(i.e. someone else in the view, reflections, etc.).
     */
    public static final int IRIS_ACQUIRED_DETECTED_TOO_MANY_EYES = 10;

    /**
     * Hardware vendors may extend this list if there are conditions that do not fall under one of
     * the above categories. Vendors are responsible for providing error strings for these errors.
     * @hide
     */
    public static final int IRIS_ACQUIRED_VENDOR = 11;
    /**
     * @hide
     */
    public static final int IRIS_ACQUIRED_VENDOR_BASE = 1000;

    private IIrisService mService;
    private Context mContext;
    private IBinder mToken = new Binder();
    private AuthenticationCallback mAuthenticationCallback;
    private EnrollmentCallback mEnrollmentCallback;
    private RemovalCallback mRemovalCallback;
    private EnumerateCallback mEnumerateCallback;
    private CryptoObject mCryptoObject;
    private Iris mRemovalIris;
    private Handler mHandler;

    private class OnEnrollCancelListener implements OnCancelListener {
        @Override
        public void onCancel() {
            cancelEnrollment();
        }
    }

    private class OnAuthenticationCancelListener implements OnCancelListener {
        private CryptoObject mCrypto;

        public OnAuthenticationCancelListener(CryptoObject crypto) {
            mCrypto = crypto;
        }

        @Override
        public void onCancel() {
            cancelAuthentication(mCrypto);
        }
    }

    /**
     * A wrapper class for the crypto objects supported by IrisManager. Currently the
     * framework supports {@link Signature}, {@link Cipher} and {@link Mac} objects.
     */
    public static final class CryptoObject {

        public CryptoObject(@NonNull Signature signature) {
            mCrypto = signature;
        }

        public CryptoObject(@NonNull Cipher cipher) {
            mCrypto = cipher;
        }

        public CryptoObject(@NonNull Mac mac) {
            mCrypto = mac;
        }

        /**
         * Get {@link Signature} object.
         * @return {@link Signature} object or null if this doesn't contain one.
         */
        public Signature getSignature() {
            return mCrypto instanceof Signature ? (Signature) mCrypto : null;
        }

        /**
         * Get {@link Cipher} object.
         * @return {@link Cipher} object or null if this doesn't contain one.
         */
        public Cipher getCipher() {
            return mCrypto instanceof Cipher ? (Cipher) mCrypto : null;
        }

        /**
         * Get {@link Mac} object.
         * @return {@link Mac} object or null if this doesn't contain one.
         */
        public Mac getMac() {
            return mCrypto instanceof Mac ? (Mac) mCrypto : null;
        }

        /**
         * @hide
         * @return the opId associated with this object or 0 if none
         */
        public long getOpId() {
            return mCrypto != null ?
                    AndroidKeyStoreProvider.getKeyStoreOperationHandle(mCrypto) : 0;
        }

        private final Object mCrypto;
    };

    /**
     * Container for callback data from {@link IrisManager#authenticate(CryptoObject,
     *     CancellationSignal, int, AuthenticationCallback, Handler)}.
     */
    public static class AuthenticationResult {
        private Iris mIris;
        private CryptoObject mCryptoObject;
        private int mUserId;

        /**
         * Authentication result
         *
         * @param crypto the crypto object
         * @param iris the recognized iris data, if allowed.
         * @hide
         */
        public AuthenticationResult(CryptoObject crypto, Iris iris, int userId) {
            mCryptoObject = crypto;
            mIris = iris;
            mUserId = userId;
        }

        /**
         * Obtain the crypto object associated with this transaction
         * @return crypto object provided to {@link IrisManager#authenticate(CryptoObject,
         *     CancellationSignal, int, AuthenticationCallback, Handler)}.
         */
        public CryptoObject getCryptoObject() { return mCryptoObject; }

        /**
         * Obtain the Iris associated with this operation. Applications are strongly
         * discouraged from associating specific iris with specific applications or operations.
         *
         * @hide
         */
        public Iris getIris() { return mIris; }

        /**
         * Obtain the userId for which this iris was authenticated.
         * @hide
         */
        public int getUserId() { return mUserId; }
    };

    /**
     * Callback structure provided to {@link IrisManager#authenticate(CryptoObject,
     * CancellationSignal, int, AuthenticationCallback, Handler)}. Users of {@link
     * IrisManager#authenticate(CryptoObject, CancellationSignal,
     * int, AuthenticationCallback, Handler) } must provide an implementation of this for listening to
     * iris events.
     */
    public static abstract class AuthenticationCallback {
        /**
         * Called when an unrecoverable error has been encountered and the operation is complete.
         * No further callbacks will be made on this object.
         * @param errorCode An integer identifying the error message
         * @param errString A human-readable error string that can be shown in UI
         */
        public void onAuthenticationError(int errorCode, CharSequence errString) { }

        /**
         * Called when a recoverable error has been encountered during authentication. The help
         * string is provided to give the user guidance for what went wrong, such as
         * "Sensor dirty, please clean it."
         * @param helpCode An integer identifying the error message
         * @param helpString A human-readable string that can be shown in UI
         */
        public void onAuthenticationHelp(int helpCode, CharSequence helpString) { }

        /**
         * Called when a iris is recognized.
         * @param result An object containing authentication-related data
         */
        public void onAuthenticationSucceeded(AuthenticationResult result) { }

        /**
         * Called when a iris is valid but not recognized.
         */
        public void onAuthenticationFailed() { }

        /**
         * Called when a iris image has been acquired, but wasn't processed yet.
         *
         * @param acquireInfo one of IRIS_ACQUIRED_* constants
         * @hide
         */
        public void onAuthenticationAcquired(int acquireInfo) {}
    };

    /**
     * Callback structure provided to {@link IrisManager#enroll(long, EnrollmentCallback,
     * CancellationSignal, int). Users of {@link #IrisManager()}
     * must provide an implementation of this to {@link IrisManager#enroll(long,
     * CancellationSignal, int, EnrollmentCallback) for listening to iris events.
     *
     * @hide
     */
    public static abstract class EnrollmentCallback {
        /**
         * Called when an unrecoverable error has been encountered and the operation is complete.
         * No further callbacks will be made on this object.
         * @param errMsgId An integer identifying the error message
         * @param errString A human-readable error string that can be shown in UI
         */
        public void onEnrollmentError(int errMsgId, CharSequence errString) { }

        /**
         * Called when a recoverable error has been encountered during enrollment. The help
         * string is provided to give the user guidance for what went wrong, such as
         * "Sensor dirty, please clean it" or what they need to do next, such as
         * "Touch sensor again."
         * @param helpMsgId An integer identifying the error message
         * @param helpString A human-readable string that can be shown in UI
         */
        public void onEnrollmentHelp(int helpMsgId, CharSequence helpString) { }

        /**
         * Called as each enrollment step progresses. Enrollment is considered complete when
         * remaining reaches 0. This function will not be called if enrollment fails. See
         * {@link EnrollmentCallback#onEnrollmentError(int, CharSequence)}
         * @param remaining The number of remaining steps
         */
        public void onEnrollmentProgress(int remaining) { }
    };

    /**
     * Callback structure provided to {@link #remove}. Users of {@link IrisManager} may
     * optionally provide an implementation of this to
     * {@link #remove(Iris, int, RemovalCallback)} for listening to iris template
     * removal events.
     *
     * @hide
     */
    public static abstract class RemovalCallback {
        /**
         * Called when the given iris can't be removed.
         * @param iris The iris that the call attempted to remove
         * @param errMsgId An associated error message id
         * @param errString An error message indicating why the iris id can't be removed
         */
        public void onRemovalError(Iris iris, int errMsgId, CharSequence errString) { }

        /**
         * Called when a given iris is successfully removed.
         * @param iris The iris template that was removed.
         * @param remaining The number of irises yet to be removed in this operation. If
         *         {@link #remove} is called on one iris, this should be 0. If
         *         {@link #remove} is called on a group, this should be the number of remaining
         *         irises in the group, and 0 after the last iris is removed.
         */
        public void onRemovalSucceeded(Iris iris, int remaining) { }
    };

    /**
     * Callback structure provided to {@link IrisManager#enumerate(int). Users of
     * {@link #IrisManager()} may optionally provide an implementation of this to
     * {@link IrisManager#enumerate(int, int, EnumerateCallback)} for listening to
     * iris template removal events.
     *
     * @hide
     */
    public static abstract class EnumerateCallback {
        /**
         * Called when the given iris can't be removed.
         * @param errMsgId An associated error message id
         * @param errString An error message indicating why the iris id can't be removed
         */
        public void onEnumerateError(int errMsgId, CharSequence errString) { }

        /**
         * Called when a given iris is successfully removed.
         * @param iris the iris template that was removed.
         */
        public void onEnumerate(Iris iris) { }
    };

    /**
     * @hide
     */
    public static abstract class LockoutResetCallback {

        /**
         * Called when lockout period expired and clients are allowed to listen for iris
         * again.
         */
        public void onLockoutReset() { }
    };

    /**
     * Request authentication of a crypto object. This call warms up the iris hardware
     * and starts scanning for a iris. It terminates when
     * {@link AuthenticationCallback#onAuthenticationError(int, CharSequence)} or
     * {@link AuthenticationCallback#onAuthenticationSucceeded(AuthenticationResult)} is called, at
     * which point the object is no longer valid. The operation can be canceled by using the
     * provided cancel object.
     *
     * @param crypto object associated with the call or null if none required.
     * @param cancel an object that can be used to cancel authentication
     * @param flags optional flags; should be 0
     * @param callback an object to receive authentication events
     * @param handler an optional handler to handle callback events
     *
     * @throws IllegalArgumentException if the crypto operation is not supported or is not backed
     *         by <a href="{@docRoot}training/articles/keystore.html">Android Keystore
     *         facility</a>.
     * @throws IllegalStateException if the crypto primitive is not initialized.
     */
    @RequiresPermission(USE_IRIS)
    public void authenticate(@Nullable CryptoObject crypto, @Nullable CancellationSignal cancel,
            int flags, @NonNull AuthenticationCallback callback, @Nullable Handler handler) {
        authenticate(crypto, cancel, flags, callback, handler, UserHandle.myUserId());
    }

    /**
     * Use the provided handler thread for events.
     * @param handler
     */
    private void useHandler(Handler handler) {
        if (handler != null) {
            mHandler = new MyHandler(handler.getLooper());
        } else if (mHandler.getLooper() != mContext.getMainLooper()){
            mHandler = new MyHandler(mContext.getMainLooper());
        }
    }

    /**
     * Per-user version
     * @hide
     */
    @RequiresPermission(USE_IRIS)
    public void authenticate(@Nullable CryptoObject crypto, @Nullable CancellationSignal cancel,
            int flags, @NonNull AuthenticationCallback callback, Handler handler, int userId) {
        if (callback == null) {
            throw new IllegalArgumentException("Must supply an authentication callback");
        }

        if (cancel != null) {
            if (cancel.isCanceled()) {
                Log.w(TAG, "authentication already canceled");
                return;
            } else {
                cancel.setOnCancelListener(new OnAuthenticationCancelListener(crypto));
            }
        }

        if (mService != null) try {
            useHandler(handler);
            mAuthenticationCallback = callback;
            mCryptoObject = crypto;
            long sessionId = crypto != null ? crypto.getOpId() : 0;
            mService.authenticate(mToken, sessionId, userId, mServiceReceiver, flags,
                    mContext.getOpPackageName());
        } catch (RemoteException e) {
            Log.w(TAG, "Remote exception while authenticating: ", e);
            if (callback != null) {
                // Though this may not be a hardware issue, it will cause apps to give up or try
                // again later.
                callback.onAuthenticationError(IRIS_ERROR_HW_UNAVAILABLE,
                        getErrorString(IRIS_ERROR_HW_UNAVAILABLE, 0 /* vendorCode */));
            }
        }
    }

    /**
     * Request iris enrollment. This call warms up the iris hardware
     * and starts scanning for irises. Progress will be indicated by callbacks to the
     * {@link EnrollmentCallback} object. It terminates when
     * {@link EnrollmentCallback#onEnrollmentError(int, CharSequence)} or
     * {@link EnrollmentCallback#onEnrollmentProgress(int) is called with remaining == 0, at
     * which point the object is no longer valid. The operation can be canceled by using the
     * provided cancel object.
     * @param token a unique token provided by a recent creation or verification of device
     * credentials (e.g. pin, pattern or password).
     * @param cancel an object that can be used to cancel enrollment
     * @param flags optional flags
     * @param userId the user to whom this iris will belong to
     * @param callback an object to receive enrollment events
     * @hide
     */
    @RequiresPermission(MANAGE_IRIS)
    public void enroll(byte [] token, CancellationSignal cancel, int flags,
            int userId, EnrollmentCallback callback) {
        if (userId == UserHandle.USER_CURRENT) {
            userId = getCurrentUserId();
        }
        if (callback == null) {
            throw new IllegalArgumentException("Must supply an enrollment callback");
        }

        if (cancel != null) {
            if (cancel.isCanceled()) {
                Log.w(TAG, "enrollment already canceled");
                return;
            } else {
                cancel.setOnCancelListener(new OnEnrollCancelListener());
            }
        }

        if (mService != null) try {
            mEnrollmentCallback = callback;
            mService.enroll(mToken, token, userId, mServiceReceiver, flags,
                    mContext.getOpPackageName());
        } catch (RemoteException e) {
            Log.w(TAG, "Remote exception in enroll: ", e);
            if (callback != null) {
                // Though this may not be a hardware issue, it will cause apps to give up or try
                // again later.
                callback.onEnrollmentError(IRIS_ERROR_HW_UNAVAILABLE,
                        getErrorString(IRIS_ERROR_HW_UNAVAILABLE, 0 /* vendorCode */));
            }
        }
    }

    /**
     * Requests a pre-enrollment auth token to tie enrollment to the confirmation of
     * existing device credentials (e.g. pin/pattern/password).
     * @hide
     */
    @RequiresPermission(MANAGE_IRIS)
    public long preEnroll() {
        long result = 0;
        if (mService != null) try {
            result = mService.preEnroll(mToken);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return result;
    }

    /**
     * Finishes enrollment and cancels the current auth token.
     * @hide
     */
    @RequiresPermission(MANAGE_IRIS)
    public int postEnroll() {
        int result = 0;
        if (mService != null) try {
            result = mService.postEnroll(mToken);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return result;
    }

    /**
     * Sets the active user. This is meant to be used to select the current profile for enrollment
     * to allow separate enrolled iris for a work profile
     * @param userId
     * @hide
     */
    @RequiresPermission(MANAGE_IRIS)
    public void setActiveUser(int userId) {
        if (mService != null) try {
            mService.setActiveUser(userId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Remove given iris template from iris hardware and/or protected storage.
     * @param iris the iris item to remove
     * @param userId the user who this iris belongs to
     * @param callback an optional callback to verify that iris templates have been
     * successfully removed. May be null of no callback is required.
     *
     * @hide
     */
    @RequiresPermission(MANAGE_IRIS)
    public void remove(Iris iris, int userId, RemovalCallback callback) {
        if (mService != null) try {
            mRemovalCallback = callback;
            mRemovalIris = iris;
            mService.remove(mToken, iris.getIrisId(), iris.getGroupId(), userId, mServiceReceiver);
        } catch (RemoteException e) {
            Log.w(TAG, "Remote exception in remove: ", e);
            if (callback != null) {
                callback.onRemovalError(iris, IRIS_ERROR_HW_UNAVAILABLE,
                        getErrorString(IRIS_ERROR_HW_UNAVAILABLE, 0 /* vendorCode */));
            }
        }
    }

    /**
     * Enumerate all iris templates stored in hardware and/or protected storage.
     * @param userId the user who this iris belongs to
     * @param callback an optional callback to verify that iris templates have been
     * successfully removed. May be null of no callback is required.
     *
     * @hide
     */
    @RequiresPermission(MANAGE_IRIS)
    public void enumerate(int userId, @NonNull EnumerateCallback callback) {
        if (mService != null) try {
            mEnumerateCallback = callback;
            mService.enumerate(mToken, userId, mServiceReceiver);
        } catch (RemoteException e) {
            Log.w(TAG, "Remote exception in enumerate: ", e);
            if (callback != null) {
                callback.onEnumerateError(IRIS_ERROR_HW_UNAVAILABLE,
                        getErrorString(IRIS_ERROR_HW_UNAVAILABLE, 0 /* vendorCode */));
            }
        }
    }

    /**
     * Renames the given iris template
     * @param irisId the iris id
     * @param userId the user who this iris belongs to
     * @param newName the new name
     *
     * @hide
     */
    @RequiresPermission(MANAGE_IRIS)
    public void rename(int irisId, int userId, String newName) {
        // Renames the given irisId
        if (mService != null) {
            try {
                mService.rename(irisId, userId, newName);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        } else {
            Log.w(TAG, "rename(): Service not connected!");
        }
    }

    /**
     * Obtain the list of enrolled iris templates.
     * @return list of current iris items
     *
     * @hide
     */
    @RequiresPermission(USE_IRIS)
    public List<Iris> getEnrolledIrises(int userId) {
        if (mService != null) try {
            return mService.getEnrolledIrises(userId, mContext.getOpPackageName());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return null;
    }

    /**
     * Obtain the list of enrolled iris templates.
     * @return list of current iris items
     *
     * @hide
     */
    @RequiresPermission(USE_IRIS)
    public List<Iris> getEnrolledIrises() {
        return getEnrolledIrises(UserHandle.myUserId());
    }

    /**
     * Determine if there is at least one iris enrolled.
     *
     * @return true if at least one iris is enrolled, false otherwise
     */
    @RequiresPermission(USE_IRIS)
    public boolean hasEnrolledIrises() {
        if (mService != null) try {
            return mService.hasEnrolledIrises(
                    UserHandle.myUserId(), mContext.getOpPackageName());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return false;
    }

    /**
     * @hide
     */
    @RequiresPermission(allOf = {
            USE_IRIS,
            INTERACT_ACROSS_USERS})
    public boolean hasEnrolledIrises(int userId) {
        if (mService != null) try {
            return mService.hasEnrolledIrises(userId, mContext.getOpPackageName());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return false;
    }

    /**
     * Determine if iris hardware is present and functional.
     *
     * @return true if hardware is present and functional, false otherwise.
     */
    @RequiresPermission(USE_IRIS)
    public boolean isHardwareDetected() {
        if (mService != null) {
            try {
                long deviceId = 0; /* TODO: plumb hardware id to FPMS */
                return mService.isHardwareDetected(deviceId, mContext.getOpPackageName());
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        } else {
            Log.w(TAG, "isIrisHardwareDetected(): Service not connected!");
        }
        return false;
    }

    /**
     * Retrieves the authenticator token for binding keys to the lifecycle
     * of the calling user's irises. Used only by internal clients.
     *
     * @hide
     */
    public long getAuthenticatorId() {
        if (mService != null) {
            try {
                return mService.getAuthenticatorId(mContext.getOpPackageName());
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        } else {
            Log.w(TAG, "getAuthenticatorId(): Service not connected!");
        }
        return 0;
    }

    /**
     * Reset the lockout timer when asked to do so by keyguard.
     *
     * @param token an opaque token returned by password confirmation.
     *
     * @hide
     */
    public void resetTimeout(byte[] token) {
        if (mService != null) {
            try {
                mService.resetTimeout(token);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        } else {
            Log.w(TAG, "resetTimeout(): Service not connected!");
        }
    }

    /**
     * @hide
     */
    public void addLockoutResetCallback(final LockoutResetCallback callback) {
        if (mService != null) {
            try {
                final PowerManager powerManager = mContext.getSystemService(PowerManager.class);
                mService.addLockoutResetCallback(
                        new IIrisServiceLockoutResetCallback.Stub() {

                    @Override
                    public void onLockoutReset(long deviceId, IRemoteCallback serverCallback)
                            throws RemoteException {
                        try {
                            final PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                                    PowerManager.PARTIAL_WAKE_LOCK, "lockoutResetCallback");
                            wakeLock.acquire();
                            mHandler.post(() -> {
                                try {
                                    callback.onLockoutReset();
                                } finally {
                                    wakeLock.release();
                                }
                            });
                        } finally {
                            serverCallback.sendResult(null /* data */);
                        }
                    }
                });
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        } else {
            Log.w(TAG, "addLockoutResetCallback(): Service not connected!");
        }
    }

    private class MyHandler extends Handler {
        private MyHandler(Context context) {
            super(context.getMainLooper());
        }

        private MyHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(android.os.Message msg) {
            switch(msg.what) {
                case MSG_ENROLL_RESULT:
                    sendEnrollResult((Iris) msg.obj, msg.arg1 /* remaining */);
                    break;
                case MSG_ACQUIRED:
                    sendAcquiredResult((Long) msg.obj /* deviceId */, msg.arg1 /* acquire info */,
                            msg.arg2 /* vendorCode */);
                    break;
                case MSG_AUTHENTICATION_SUCCEEDED:
                    sendAuthenticatedSucceeded((Iris) msg.obj, msg.arg1 /* userId */);
                    break;
                case MSG_AUTHENTICATION_FAILED:
                    sendAuthenticatedFailed();
                    break;
                case MSG_ERROR:
                    sendErrorResult((Long) msg.obj /* deviceId */, msg.arg1 /* errMsgId */,
                            msg.arg2 /* vendorCode */);
                    break;
                case MSG_REMOVED:
                    sendRemovedResult((Iris) msg.obj, msg.arg1 /* remaining */);
                    break;
                case MSG_ENUMERATED:
                    sendEnumeratedResult((Long) msg.obj /* deviceId */, msg.arg1 /* irisId */,
                            msg.arg2 /* groupId */);
                    break;
            }
        }

        private void sendRemovedResult(Iris iris, int remaining) {
            if (mRemovalCallback == null) {
                return;
            }
            if (iris == null) {
                Log.e(TAG, "Received MSG_REMOVED, but iris is null");
                return;
            }

            int irisId = iris.getIrisId();
            int reqIrisId = mRemovalIris.getIrisId();
            if (reqIrisId != 0 && irisId != 0 && irisId != reqIrisId) {
                Log.w(TAG, "Iris id didn't match: " + irisId + " != " + reqIrisId);
                return;
            }
            int groupId = iris.getGroupId();
            int reqGroupId = mRemovalIris.getGroupId();
            if (groupId != reqGroupId) {
                Log.w(TAG, "Group id didn't match: " + groupId + " != " + reqGroupId);
                return;
            }

            mRemovalCallback.onRemovalSucceeded(iris, remaining);
        }

        private void sendEnumeratedResult(long deviceId, int irisId, int groupId) {
            if (mEnumerateCallback != null) {
                mEnumerateCallback.onEnumerate(new Iris(null, groupId, irisId, deviceId));
            }
        }

        private void sendErrorResult(long deviceId, int errMsgId, int vendorCode) {
            // emulate HAL 2.1 behavior and send real errMsgId
            final int clientErrMsgId = errMsgId == IRIS_ERROR_VENDOR
                    ? (vendorCode + IRIS_ERROR_VENDOR_BASE) : errMsgId;
            if (mEnrollmentCallback != null) {
                mEnrollmentCallback.onEnrollmentError(clientErrMsgId,
                        getErrorString(errMsgId, vendorCode));
            } else if (mAuthenticationCallback != null) {
                mAuthenticationCallback.onAuthenticationError(clientErrMsgId,
                        getErrorString(errMsgId, vendorCode));
            } else if (mRemovalCallback != null) {
                mRemovalCallback.onRemovalError(mRemovalIris, clientErrMsgId,
                        getErrorString(errMsgId, vendorCode));
            } else if (mEnumerateCallback != null) {
                mEnumerateCallback.onEnumerateError(clientErrMsgId,
                        getErrorString(errMsgId, vendorCode));
            }
        }

        private void sendEnrollResult(Iris iris, int remaining) {
            if (mEnrollmentCallback != null) {
                mEnrollmentCallback.onEnrollmentProgress(remaining);
            }
        }

        private void sendAuthenticatedSucceeded(Iris iris, int userId) {
            if (mAuthenticationCallback != null) {
                final AuthenticationResult result =
                        new AuthenticationResult(mCryptoObject, iris, userId);
                mAuthenticationCallback.onAuthenticationSucceeded(result);
            }
        }

        private void sendAuthenticatedFailed() {
            if (mAuthenticationCallback != null) {
                mAuthenticationCallback.onAuthenticationFailed();
            }
        }

        private void sendAcquiredResult(long deviceId, int acquireInfo, int vendorCode) {
            if (mAuthenticationCallback != null) {
                mAuthenticationCallback.onAuthenticationAcquired(acquireInfo);
            }
            final String msg = getAcquiredString(acquireInfo, vendorCode);
            if (msg == null) {
                return;
            }
            // emulate HAL 2.1 behavior and send real acquiredInfo
            final int clientInfo = acquireInfo == IRIS_ACQUIRED_VENDOR
                    ? (vendorCode + IRIS_ACQUIRED_VENDOR_BASE) : acquireInfo;
            if (mEnrollmentCallback != null) {
                mEnrollmentCallback.onEnrollmentHelp(clientInfo, msg);
            } else if (mAuthenticationCallback != null) {
                mAuthenticationCallback.onAuthenticationHelp(clientInfo, msg);
            }
        }
    };

    /**
     * @hide
     */
    public IrisManager(Context context, IIrisService service) {
        mContext = context;
        mService = service;
        if (mService == null) {
            Slog.v(TAG, "IrisManagerService was null");
        }
        mHandler = new MyHandler(context);
    }

    private int getCurrentUserId() {
        try {
            return ActivityManager.getService().getCurrentUser().id;
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private void cancelEnrollment() {
        if (mService != null) try {
            mService.cancelEnrollment(mToken);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private void cancelAuthentication(CryptoObject cryptoObject) {
        if (mService != null) try {
            mService.cancelAuthentication(mToken, mContext.getOpPackageName());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private String getErrorString(int errMsg, int vendorCode) {
        switch (errMsg) {
            case IRIS_ERROR_UNABLE_TO_PROCESS:
                return mContext.getString(
                    com.android.internal.R.string.iris_error_unable_to_process);
            case IRIS_ERROR_HW_UNAVAILABLE:
                return mContext.getString(
                    com.android.internal.R.string.iris_error_hw_not_available);
            case IRIS_ERROR_NO_SPACE:
                return mContext.getString(
                    com.android.internal.R.string.iris_error_no_space);
            case IRIS_ERROR_TIMEOUT:
                return mContext.getString(com.android.internal.R.string.iris_error_timeout);
            case IRIS_ERROR_CANCELED:
                return mContext.getString(com.android.internal.R.string.iris_error_canceled);
            case IRIS_ERROR_LOCKOUT:
                return mContext.getString(com.android.internal.R.string.iris_error_lockout);
            case IRIS_ERROR_LOCKOUT_PERMANENT:
                return mContext.getString(
                        com.android.internal.R.string.iris_error_lockout_permanent);
            case IRIS_ERROR_VENDOR: {
                    String[] msgArray = mContext.getResources().getStringArray(
                            com.android.internal.R.array.iris_error_vendor);
                    if (vendorCode < msgArray.length) {
                        return msgArray[vendorCode];
                    }
                }
        }
        Slog.w(TAG, "Invalid error message: " + errMsg + ", " + vendorCode);
        return null;
    }

    private String getAcquiredString(int acquireInfo, int vendorCode) {
        switch (acquireInfo) {
            case IRIS_ACQUIRED_GOOD:
                return null;
            case IRIS_ACQUIRED_INSUFFICIENT:
                return mContext.getString(
                    com.android.internal.R.string.iris_acquired_insufficient);
            case IRIS_ACQUIRED_TOO_BRIGHT:
                return mContext.getString(
                    com.android.internal.R.string.iris_acquired_too_bright);
            case IRIS_ACQUIRED_TOO_DARK:
                return mContext.getString(
                    com.android.internal.R.string.iris_acquired_too_dark);
            case IRIS_ACQUIRED_TOO_CLOSE:
                return mContext.getString(
                    com.android.internal.R.string.iris_acquired_too_close);
            case IRIS_ACQUIRED_TOO_FAR:
                return mContext.getString(
                    com.android.internal.R.string.iris_acquired_too_far);
            case IRIS_ACQUIRED_EYES_CLOSED:
                return mContext.getString(
                    com.android.internal.R.string.iris_acquired_eyes_closed);
            case IRIS_ACQUIRED_EYES_PARTIALLY_OBSCURED:
                return mContext.getString(
                    com.android.internal.R.string.iris_acquired_eyes_partially_obscured);
            case IRIS_ACQUIRED_DETECTED_ONE_EYE:
                return null;
            case IRIS_ACQUIRED_DETECTED_TWO_EYE:
                return null;
            case IRIS_ACQUIRED_DETECTED_TOO_MANY_EYES:
                return null;
            case IRIS_ACQUIRED_VENDOR: {
                    String[] msgArray = mContext.getResources().getStringArray(
                            com.android.internal.R.array.iris_acquired_vendor);
                    if (vendorCode < msgArray.length) {
                        return msgArray[vendorCode];
                    }
                }
        }
        Slog.w(TAG, "Invalid acquired message: " + acquireInfo + ", " + vendorCode);
        return null;
    }

    private IIrisServiceReceiver mServiceReceiver = new IIrisServiceReceiver.Stub() {

        @Override // binder call
        public void onEnrollResult(long deviceId, int irisId, int groupId, int remaining) {
            mHandler.obtainMessage(MSG_ENROLL_RESULT, remaining, 0,
                    new Iris(null, groupId, irisId, deviceId)).sendToTarget();
        }

        @Override // binder call
        public void onAcquired(long deviceId, int acquireInfo, int vendorCode) {
            mHandler.obtainMessage(MSG_ACQUIRED, acquireInfo, vendorCode, deviceId).sendToTarget();
        }

        @Override // binder call
        public void onAuthenticationSucceeded(long deviceId, Iris iris, int userId) {
            mHandler.obtainMessage(MSG_AUTHENTICATION_SUCCEEDED, userId, 0, iris).sendToTarget();
        }

        @Override // binder call
        public void onAuthenticationFailed(long deviceId) {
            mHandler.obtainMessage(MSG_AUTHENTICATION_FAILED).sendToTarget();
        }

        @Override // binder call
        public void onError(long deviceId, int error, int vendorCode) {
            mHandler.obtainMessage(MSG_ERROR, error, vendorCode, deviceId).sendToTarget();
        }

        @Override // binder call
        public void onRemoved(long deviceId, int irisId, int groupId, int remaining) {
            mHandler.obtainMessage(MSG_REMOVED, remaining, 0,
                    new Iris(null, groupId, irisId, deviceId)).sendToTarget();
        }

        @Override // binder call
        public void onEnumerated(long deviceId, int irisId, int groupId, int remaining) {
            // TODO: propagate remaining
            mHandler.obtainMessage(MSG_ENUMERATED, irisId, groupId, deviceId).sendToTarget();
        }
    };

}
