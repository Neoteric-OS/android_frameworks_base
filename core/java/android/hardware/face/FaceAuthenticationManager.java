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

/**
 * A class that coordinates access to the face authentication hardware.
 */
public class FaceAuthenticationManager {
    //
    // Error messages from face authentication hardware during initialization, enrollment,
    // authentication or removal. Must agree with the list in HAL h file
    //

    /**
     * The hardware is unavailable. Try again later.
     */
    public static final int FACE_ERROR_HW_UNAVAILABLE = 1;

    /**
     * Error state returned when the sensor was unable to process the current image.
     */
    public static final int FACE_ERROR_UNABLE_TO_PROCESS = 2;

    /**
     * Error state returned when the current request has been running too long. This is intended to
     * prevent programs from waiting for the face authentication sensor indefinitely. The timeout is
     * platform and sensor-specific, but is generally on the order of 30 seconds.
     */
    public static final int FACE_ERROR_TIMEOUT = 3;

    /**
     * Error state returned for operations like enrollment; the operation cannot be completed
     * because there's not enough storage remaining to complete the operation.
     */
    public static final int FACE_ERROR_NO_SPACE = 4;

    /**
     * The operation was canceled because the face authentication sensor is unavailable. For example,
     * this may happen when the user is switched, the device is locked or another pending operation
     * prevents or disables it.
     */
    public static final int FACE_ERROR_CANCELED = 5;

    /**
     * The {@link FaceAuthenticationManager#remove} call failed. Typically this will happen when the
     * provided face id was incorrect.
     *
     * @hide
     */
    public static final int FACE_ERROR_UNABLE_TO_REMOVE = 6;

    /**
     * The operation was canceled because the API is locked out due to too many attempts.
     * This occurs after 5 failed attempts, and lasts for 30 seconds.
     */
    public static final int FACE_ERROR_LOCKOUT = 7;

    /**
     * Hardware vendors may extend this list if there are conditions that do not fall under one of
     * the above categories. Vendors are responsible for providing error strings for these errors.
     * These messages are typically reserved for internal operations such as enrollment, but may be
     * used to express vendor errors not covered by the ones in HAL h file. Applications are
     * expected to show the error message string if they happen, but are advised not to rely on the
     * message id since they will be device and vendor-specific
     */
    public static final int FACE_ERROR_VENDOR = 8;

    /**
     * The operation was canceled because FACE_ERROR_LOCKOUT occurred too many times.
     * Face authentication is disabled until the user unlocks with strong authentication
     * (PIN/Pattern/Password)
     */
    public static final int FACE_ERROR_LOCKOUT_PERMANENT = 9;

    /**
     * The user canceled the operation. Upon receiving this, applications should use alternate
     * authentication (e.g. a password). The application should also provide the means to return
     * to face authentication, such as a "use face authentication" button.
     */
    public static final int FACE_ERROR_USER_CANCELED = 10;

    /**
     * The user does not have a face enrolled.
     */
     public static final int FACE_ERROR_NOT_ENROLLED = 11;

    /**
     * The device does not have a face sensor. This message will propagate if the calling app
     * ignores the result from PackageManager.hasFeature(FEATURE_FACE_AUTHENTICATION) and calls
     * this API anyway. Apps should always check for the feature before calling this API.
     */
    public static final int FACE_ERROR_HW_NOT_PRESENT = 12;

    /**
     * @hide
     */
    public static final int FACE_ERROR_VENDOR_BASE = 1000;

    //
    // Image acquisition messages. Must agree with those in HAL h file
    //

    /**
     * The image acquired was good.
     */
    public static final int FACE_ACQUIRED_GOOD = 0;

    /**
    * The face image was not good enough to process due to a detected condition.
    * (See {@link #FACE_ACQUIRED_TOO_BRIGHT or @link #FACE_ACQUIRED_TOO_DARK}).
    */
    public static final int FACE_ACQUIRED_INSUFFICIENT = 1;

    /**
    * The face image was too bright due to too much ambient light.
    * For example, it's reasonable return this after multiple
    * {@link #FACE_ACQUIRED_INSUFFICIENT}
    * The user is expected to take action to retry in better lighting conditions
    * when this is returned.
    */
    public static final int FACE_ACQUIRED_TOO_BRIGHT = 2;

    /**
    * The face image was too dark due to illumination light obscured.
    * For example, it's reasonable return this after multiple
    * {@link #FACE_ACQUIRED_INSUFFICIENT}
    * The user is expected to take action to retry in better lighting conditions
    * when this is returned.
    */
    public static final int FACE_ACQUIRED_TOO_DARK = 3;

    /**
    * The detected face is too close to the sensor, and the image can't be processed.
    * The user should be informed to move further from the sensor
    * when this is returned.
    */
    public static final int FACE_ACQUIRED_TOO_CLOSE = 4;

    /**
     * The detected face is too small, as the user might be too far from the sensor.
     * The user should be informed to move closer to the sensor
     * when this is returned.
     */
     public static final int FACE_ACQUIRED_TOO_FAR = 5;

    /**
     * Only the upper part of the face was detected. The sensor field of view is too high.
     * The user should be informed to move up with respect to the sensor
     * when this is returned.
     */
    public static final int FACE_ACQUIRED_FACE_TOO_HIGH = 6;

    /**
     * Only the lower part of the face was detected. The sensor field of view is too low.
     * The user should be informed to move down with respect to the sensor
     * when this is returned.
     */
    public static final int FACE_ACQUIRED_FACE_TOO_LOW = 7;

    /**
     * Only the right part of the face was detected. The sensor field of view is too far right.
     * The user should be informed to move to the right with respect to the sensor
     * when this is returned.
     */
    public static final int FACE_ACQUIRED_FACE_TOO_RIGHT = 8;

    /**
     * Only the left part of the face was detected. The sensor field of view is too far left.
     * The user should be informed to move to the left with respect to the sensor
     * when this is returned.
     */
    public static final int FACE_ACQUIRED_FACE_TOO_LEFT = 9;

    /**
     * The detected face pose is too rotated. Face parts which are required for recognition
     * are hidden. The user should be informed to turn the face front to the sensor
     * when this is returned.
     */
    public static final int FACE_ACQUIRED_TOO_ROTATED = 10;

    /**
     * No face was detected in front of the sensor.
     * The user should be informed to point the sensor to a face
     * when this is returned.
     */
    public static final int FACE_ACQUIRED_NOT_DETECTED = 11;

    /**
     * Hardware vendors may extend this list if there are conditions that do not fall under one of
     * the above categories. Vendors are responsible for providing error strings for these errors.
     * @hide
     */
    public static final int FACE_ACQUIRED_VENDOR = 7;

    /**
     * @hide
     */
    public static final int FACE_ACQUIRED_VENDOR_BASE = 1000;

    /**
     * A wrapper class for the crypto objects supported by FaceAuthenticationManager.
     */
    public static final class CryptoObject {

        public Signature getSignature() { }

        public Cipher getCipher() { }

        public Mac getMac() { }
    };

    /**
     * Container for callback data from {@link FaceAuthenticationManager#authenticate(CryptoObject,
     *     CancellationSignal, int, AuthenticationCallback, Handler)}.
     */
    public static class AuthenticationResult {

        /**
         * Authentication result
         *
         * @param crypto the crypto object
         * @param face the recognized face data, if allowed.
         * @hide
         */
        public AuthenticationResult(CryptoObject crypto, Face face, int userId) { }

        /**
         * Obtain the crypto object associated with this transaction
         * @return crypto object provided to {@link FaceAuthenticationManager#authenticate(CryptoObject,
         *     CancellationSignal, int, AuthenticationCallback, Handler)}.
         */
        public CryptoObject getCryptoObject() { }

        /**
         * Obtain the Face associated with this operation. Applications are strongly
         * discouraged from associating specific faces with specific applications or operations.
         *
         * @hide
         */
        public Face getFace() { }

        /**
         * Obtain the userId for which this face was authenticated.
         * @hide
         */
        public int getUserId() { }
    };

    /**
     * Callback structure provided to {@link FaceAuthenticationManager#authenticate(CryptoObject,
     * CancellationSignal, int, AuthenticationCallback, Handler)}. Users of {@link
     * FaceAuthenticationManager#authenticate(CryptoObject, CancellationSignal,
     * int, AuthenticationCallback, Handler) } must provide an implementation of this for listening
     * to face events.
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
         * Called when a face is recognized.
         * @param result An object containing authentication-related data
         */
        public void onAuthenticationSucceeded(AuthenticationResult result) { }

        /**
         * Called when a face is detected but not recognized.
         */
        public void onAuthenticationFailed() { }

        /**
         * Called when a face image has been acquired, but wasn't processed yet.
         *
         * @param acquireInfo one of FACE_ACQUIRED_* constants
         * @hide
         */
        public void onAuthenticationAcquired(int acquireInfo) { }
    };

    /**
     * Callback structure provided to {@link FaceAuthenticationManager#enroll(long, EnrollmentCallback,
     * CancellationSignal, int). Users of {@link #FaceAuthenticationManager()}
     * must provide an implementation of this to {@link FaceAuthenticationManager#enroll(long,
     * CancellationSignal, int, EnrollmentCallback) for listening to face enrollment events.
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
         * "Rotate face up / down."
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
     * Callback structure provided to {@link #remove}. Users of {@link FaceAuthenticationManager} may
     * optionally provide an implementation of this to
     * {@link #remove(Face, int, RemovalCallback)} for listening to face template
     * removal events.
     *
     * @hide
     */
    public static abstract class RemovalCallback {

        /**
         * Called when the given face can't be removed.
         * @param fp The face that the call attempted to remove
         * @param errMsgId An associated error message id
         * @param errString An error message indicating why the face id can't be removed
         */
        public void onRemovalError(Face face, int errMsgId, CharSequence errString) { }

        /**
         * Called when a given face is successfully removed.
         * @param face The face template that was removed.
         */
        public void onRemovalSucceeded(Face face) { }
    };

    /**
     * @hide
     */
    public static abstract class LockoutResetCallback {

        /**
         * Called when lockout period expired and clients are allowed to listen for face authentication
         * again.
         */
        public void onLockoutReset() { }
    };

    /**
     * Request authentication of a crypto object. This call operates the face recognition sensor
     * and starts capturing images. It terminates when
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
     @RequiresPermission(USE_FACE_AUTHENTICATION)
     public void authenticate(@Nullable CryptoObject crypto, @Nullable CancellationSignal cancel,
             int flags, @NonNull AuthenticationCallback callback, @Nullable Handler handler) { }

    /**
     * Per-user version
     * @hide
     */
    @RequiresPermission(USE_FACE_AUTHENTICATION)
    public void authenticate(@Nullable CryptoObject crypto, @Nullable CancellationSignal cancel,
            int flags, @NonNull AuthenticationCallback callback, Handler handler, int userId) { }

    /**
     * Request face authentication enrollment. This call operates the face recognition sensor
     * and starts capturing images. Progress will be indicated by callbacks to the
     * {@link EnrollmentCallback} object. It terminates when
     * {@link EnrollmentCallback#onEnrollmentError(int, CharSequence)} or
     * {@link EnrollmentCallback#onEnrollmentProgress(int) is called with remaining == 0, at
     * which point the object is no longer valid. The operation can be canceled by using the
     * provided cancel object.
     * @param token a unique token provided by a recent creation or verification of device
     * credentials (e.g. pin, pattern or password).
     * @param cancel an object that can be used to cancel enrollment
     * @param flags optional flags
     * @param userId the user to whom this face will belong to
     * @param callback an object to receive enrollment events
     * @hide
     */
    @RequiresPermission(MANAGE_FACE_AUTHENTICATION)
    public void enroll(byte[] token, CancellationSignal cancel, int flags,
            int userId, EnrollmentCallback callback) { }

    /**
     * Requests a pre-enrollment auth token to tie enrollment to the confirmation of
     * existing device credentials (e.g. pin/pattern/password).
     * @hide
     */
    @RequiresPermission(MANAGE_FACE_AUTHENTICATION)
    public long preEnroll() { }

    /**
     * Finishes enrollment and cancels the current auth token.
     * @hide
     */
    @RequiresPermission(MANAGE_FACE_AUTHENTICATION)
    public int postEnroll() { }

    /**
     * Sets the active user. This is meant to be used to select the current profile for enrollment
     * to allow separate enrolled faces for a work profile
     * @param userId
     * @hide
     */
    @RequiresPermission(MANAGE_FACE_AUTHENTICATION)
    public void setActiveUser(int userId) {
        if (mService != null) try {
            mService.setActiveUser(userId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Remove given face template from face hardware and/or protected storage.
     * @param face the face item to remove
     * @param userId the user who this face belongs to
     * @param callback an optional callback to verify that face templates have been
     * successfully removed. May be null if no callback is required.
     *
     * @hide
     */
    @RequiresPermission(MANAGE_FACE_AUTHENTICATION)
    public void remove(Face face, int userId, RemovalCallback callback) { }

    /**
     * Obtain the enrolled face template.
     * @return the current face item
     *
     * @hide
     */
    @RequiresPermission(USE_FACE_AUTHENTICATION)
    public Face getEnrolledFace(int userId) { }

    /**
     * Obtain the enrolled face template.
     * @return the current face item
     *
     * @hide
     */
    @RequiresPermission(USE_FACE_AUTHENTICATION)
    public Face getEnrolledFace() { }

    /**
     * Determine if there is a face enrolled.
     *
     * @return true if a face is enrolled, false otherwise
     */
    @RequiresPermission(USE_FACE_AUTHENTICATION)
    public boolean hasEnrolledFace() { }

    /**
     * @hide
     */
    @RequiresPermission(allOf = {
            USE_FACE_AUTHENTICATION,
            INTERACT_ACROSS_USERS})
    public boolean hasEnrolledFace(int userId) { }

   /**
     * Determine if face authentication sensor hardware is present and functional.
     *
     * @return true if hardware is present and functional, false otherwise.
     */
    @RequiresPermission(USE_FACE_AUTHENTICATION)
    public boolean isHardwareDetected() { }

    /**
     * Retrieves the authenticator token for binding keys to the lifecycle
     * of the calling user's face. Used only by internal clients.
     *
     * @hide
     */
    public long getAuthenticatorId() { }
};
