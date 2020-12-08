/*
 * Copyright 2020 The Android Open Source Project
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

package android.uwb;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.os.Binder;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.Executor;

/**
 * This class provides a way to control an active UWB ranging session.
 * <p>It also defines the required {@link RangingSession.Callback} that must be implemented
 * in order to be notified of UWB ranging results and status events related to the
 * {@link RangingSession}.
 *
 * <p>To get an instance of {@link RangingSession}, first use
 * {@link UwbManager#openRangingSession(PersistableBundle, Executor, Callback)} to request to open a
 * session. Once the session is opened, a {@link RangingSession} object is provided through
 * {@link RangingSession.Callback#onOpened(RangingSession)}. If opening a session fails, the failure
 * is reported through {@link RangingSession.Callback#onOpenFailed(int, PersistableBundle)} with the
 * failure reason.
 *
 * @hide
 */
public final class RangingSession implements AutoCloseable {
    private static final String TAG = "Uwb.RangingSession";
    private final SessionHandle mSessionHandle;
    private final IUwbAdapter mAdapter;
    private final Executor mExecutor;
    private final Callback mCallback;

    private enum State {
        /**
         * The state of the {@link RangingSession} until
         * {@link RangingSession.Callback#onOpened(RangingSession)} is invoked
         */
        INIT,

        /**
         * The {@link RangingSession} is initialized and ready to begin ranging
         */
        IDLE,

        /**
         * The {@link RangingSession} is actively ranging
         */
        ACTIVE,

        /**
         * The {@link RangingSession} is closed and may not be used for ranging.
         */
        CLOSED
    }

    private State mState = State.INIT;

    /**
     * Interface for receiving {@link RangingSession} events
     */
    public interface Callback {
        /**
         * @hide
         */
        @Retention(RetentionPolicy.SOURCE)
        @IntDef(value = {
                FAILURE_REASON_UNKNOWN,
                FAILURE_REASON_BAD_PARAMETERS,
                FAILURE_REASON_MAX_SESSIONS_REACHED,
                FAILURE_REASON_SYSTEM_POLICY,
                FAILURE_REASON_PROTOCOL_SPECIFIC})
        @interface FailureReason {};

        /**
         * Failed due to an unknown reason
         */
        int FAILURE_REASON_UNKNOWN = 0;

        /**
         * Failed due invalid parameters
         */
        int FAILURE_REASON_BAD_PARAMETERS = 1;

        /**
         * Failed due to too many sessions being open
         */
        int FAILURE_REASON_MAX_SESSIONS_REACHED = 2;

        /**
         * Failed due to the current system policy preventing the action from occurring
         */
        int FAILURE_REASON_SYSTEM_POLICY = 3;

        /**
         * Failed due to a protocol specific reason
         */
        int FAILURE_REASON_PROTOCOL_SPECIFIC = 4;

        /**
         * Invoked when {@link UwbManager#openRangingSession(PersistableBundle, Executor, Callback)}
         * is successful
         *
         * @param session the newly opened {@link RangingSession}
         */
        void onOpened(@NonNull RangingSession session);

        /**
         * Invoked if {@link UwbManager#openRangingSession(PersistableBundle, Executor, Callback)}}
         * fails
         *
         * @param reason the failure reason
         * @param params protocol specific parameters
         */
        void onOpenFailed(@FailureReason int reason, @NonNull PersistableBundle params);

        /**
         * Invoked when {@link RangingSession#start()} is successful
         * @param sessionInfo session specific parameters from the lower layers
         */
        void onStarted(@NonNull PersistableBundle sessionInfo);

        /**
         * Invoked when {@link RangingSession#start()} fails
         *
         * @param reason the failure reason
         * @param params protocol specific parameters
         */
        void onStartFailed(@FailureReason int reason, @NonNull PersistableBundle params);

        /**
         * Invoked when a request to reconfigure the session succeeds
         *
         * @param params the updated ranging configuration
         */
        void onReconfigured(@NonNull PersistableBundle params);

        /**
         * Invoked when a request to reconfigure the session fails
         *
         * @param reason reason the session failed to be reconfigured
         * @param params protocol specific failure reasons
         */
        void onReconfigureFailed(@FailureReason int reason, @NonNull PersistableBundle params);

        /**
         * Invoked when a request to stop the session succeeds
         */
        void onStopped();

        /**
         * Invoked when a request to stop the session fails
         *
         * @param reason reason the session failed to be stopped
         * @param params protocol specific failure reasons
         */
        void onStopFailed(@FailureReason int reason, @NonNull PersistableBundle params);

        /**
         * @hide
         */
        @Retention(RetentionPolicy.SOURCE)
        @IntDef(value = {
                CLOSE_REASON_UNKNOWN,
                CLOSE_REASON_LOCAL_CLOSE_API,
                CLOSE_REASON_LOCAL_BAD_PARAMETERS,
                CLOSE_REASON_LOCAL_GENERIC_ERROR,
                CLOSE_REASON_LOCAL_MAX_SESSIONS_REACHED,
                CLOSE_REASON_LOCAL_SYSTEM_POLICY,
                CLOSE_REASON_REMOTE_GENERIC_ERROR,
                CLOSE_REASON_REMOTE_REQUEST})
        @interface CloseReason {}

        /**
         * Indicates that the session was closed or failed to open due to an unknown reason
         */
        int CLOSE_REASON_UNKNOWN = 0;

        /**
         * Indicates that the session was closed or failed to open because
         * {@link AutoCloseable#close()} or {@link RangingSession#close()} was called
         */
        int CLOSE_REASON_LOCAL_CLOSE_API = 1;

        /**
         * Indicates that the session failed to open due to erroneous parameters passed
         * to {@link UwbManager#openRangingSession(PersistableBundle, Executor, Callback)}
         */
        int CLOSE_REASON_LOCAL_BAD_PARAMETERS = 2;

        /**
         * Indicates that the session was closed due to some local error on this device besides the
         * error code already listed
         */
        int CLOSE_REASON_LOCAL_GENERIC_ERROR = 3;

        /**
         * Indicates that the session failed to open because the number of currently open sessions
         * is equal to {@link UwbManager#getMaxSimultaneousSessions()}
         */
        int CLOSE_REASON_LOCAL_MAX_SESSIONS_REACHED = 4;

        /**
         * Indicates that the session was closed or failed to open due to local system policy, such
         * as privacy policy, power management policy, permissions, and more.
         */
        int CLOSE_REASON_LOCAL_SYSTEM_POLICY = 5;

        /**
         * Indicates that the session was closed or failed to open due to an error with the remote
         * device besides error codes already listed.
         */
        int CLOSE_REASON_REMOTE_GENERIC_ERROR = 6;

        /**
         * Indicates that the session was closed or failed to open due to an explicit request from
         * the remote device.
         */
        int CLOSE_REASON_REMOTE_REQUEST = 7;

        /**
         * Indicates that the session was closed for a protocol specific reason. The associated
         * {@link PersistableBundle} should be consulted for additional information.
         */
        int CLOSE_REASON_PROTOCOL_SPECIFIC = 8;

        /**
         * Invoked when session is either closed spontaneously, or per user request via
         * {@link RangingSession#close()} or {@link AutoCloseable#close()}.
         *
         * @param reason reason for the session closure
         * @param parameters protocol specific parameters related to the close reason
         */
        void onClosed(@CloseReason int reason, @NonNull PersistableBundle parameters);

        /**
         * Called once per ranging interval even when a ranging measurement fails
         *
         * @param rangingReport ranging report for this interval's measurements
         */
        void onReportReceived(@NonNull RangingReport rangingReport);
    }

    /**
     * @hide
     */
    public RangingSession(Executor executor, Callback callback, IUwbAdapter adapter,
            SessionHandle sessionHandle) {
        mState = State.INIT;
        mExecutor = executor;
        mCallback = callback;
        mAdapter = adapter;
        mSessionHandle = sessionHandle;
    }

    /**
     * @hide
     */
    public boolean isOpen() {
        return mState == State.IDLE || mState == State.ACTIVE;
    }

     /**
     * Begins ranging for the session.
     *
     * <p>On successfully starting a ranging session,
     * {@link RangingSession.Callback#onStarted(PersistableBundle)} is invoked.
     *
     * <p>On failure to start the session,
     * {@link RangingSession.Callback#onStartFailed(int, PersistableBundle)} is invoked.
     */
    public void start() {
        if (mState != State.IDLE) {
            throw new IllegalStateException();
        }

        try {
            mAdapter.startRanging(mSessionHandle);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Attempts to reconfigure the session with the given parameters
     * <p>This call may be made when the session is open.
     *
     * <p>On successfully reconfiguring the session
     * {@link RangingSession.Callback#onReconfigured(PersistableBundle)} is invoked.
     *
     * <p>On failure to reconfigure the session,
     * {@link RangingSession.Callback#onReconfigureFailed(int, PersistableBundle)} is invoked.
     *
     * @param params the parameters to reconfigure and their new values
     */
    public void reconfigure(@NonNull PersistableBundle params) {
        if (mState != State.ACTIVE && mState != State.IDLE) {
            throw new IllegalStateException();
        }

        try {
            mAdapter.reconfigureRanging(mSessionHandle, params);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Stops actively ranging
     *
     * <p>A session that has been stopped may be resumed by calling {@link RangingSession#start()}
     * without the need to open a new session.
     *
     * <p>Stopping a {@link RangingSession} is useful when the lower layers should not discard
     * the parameters of the session, or when a session needs to be able to be resumed quickly.
     *
     * <p>If the {@link RangingSession} is no longer needed, use {@link RangingSession#close()} to
     * completely close the session and allow lower layers of the stack to perform necessarily
     * cleanup.
     *
     * <p>Stopped sessions may be closed by the system at any time. In such a case,
     * {@link RangingSession.Callback#onClosed(int, PersistableBundle)} is invoked.
     *
     * <p>On failure to stop the session,
     * {@link RangingSession.Callback#onStopFailed(int, PersistableBundle)} is invoked.
     */
    public void stop() {
        if (mState != State.ACTIVE) {
            throw new IllegalStateException();
        }

        try {
            mAdapter.stopRanging(mSessionHandle);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Close the ranging session
     *
     * <p>After calling this function, in order resume ranging, a new {@link RangingSession} must
     * be opened by calling
     * {@link UwbManager#openRangingSession(PersistableBundle, Executor, Callback)}.
     *
     * <p>If this session is currently ranging, it will stop and close the session.
     * <p>If the session is in the process of being opened, it will attempt to stop the session from
     * being opened.
     * <p>If the session is already closed, the registered
     * {@link Callback#onClosed(int, PersistableBundle)} callback will still be invoked.
     *
     * <p>{@link Callback#onClosed(int, PersistableBundle)} will be invoked using the same callback
     * object given to {@link UwbManager#openRangingSession(PersistableBundle, Executor, Callback)}
     * when the {@link RangingSession} was opened. The callback will be invoked after each call to
     * {@link #close()}, even if the {@link RangingSession} is already closed.
     */
    @Override
    public void close() {
        if (mState == State.CLOSED) {
            mExecutor.execute(() -> mCallback.onClosed(
                    Callback.CLOSE_REASON_LOCAL_CLOSE_API, new PersistableBundle()));
            return;
        }

        try {
            mAdapter.closeRanging(mSessionHandle);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * @hide
     */
    public void onRangingOpened() {
        if (mState == State.CLOSED) {
            Log.w(TAG, "onRangingOpened invoked for a closed session");
            return;
        }

        mState = State.IDLE;
        executeCallback(() -> mCallback.onOpened(this));
    }

    /**
     * @hide
     */
    public void onRangingOpenFailed(@Callback.FailureReason int reason,
            @NonNull PersistableBundle params) {
        if (mState == State.CLOSED) {
            Log.w(TAG, "onRangingOpenFailed invoked for a closed session");
            return;
        }

        mState = State.CLOSED;
        executeCallback(() -> mCallback.onOpenFailed(reason, params));
    }

    /**
     * @hide
     */
    public void onRangingStarted(@NonNull PersistableBundle parameters) {
        if (mState == State.CLOSED) {
            Log.w(TAG, "onRangingStarted invoked for a closed session");
            return;
        }

        mState = State.ACTIVE;
        executeCallback(() -> mCallback.onStarted(parameters));
    }

    /**
     * @hide
     */
    public void onRangingStartFailed(@Callback.FailureReason int reason,
            @NonNull PersistableBundle params) {
        if (mState == State.CLOSED) {
            Log.w(TAG, "onRangingStartFailed invoked for a closed session");
            return;
        }

        executeCallback(() -> mCallback.onStartFailed(reason, params));
    }

    /**
     * @hide
     */
    public void onRangingReconfigured(@NonNull PersistableBundle params) {
        if (mState == State.CLOSED) {
            Log.w(TAG, "onRangingReconfigured invoked for a closed session");
            return;
        }

        executeCallback(() -> mCallback.onReconfigured(params));
    }

    /**
     * @hide
     */
    public void onRangingReconfigureFailed(@Callback.FailureReason int reason,
            @NonNull PersistableBundle params) {
        if (mState == State.CLOSED) {
            Log.w(TAG, "onRangingReconfigureFailed invoked for a closed session");
            return;
        }

        executeCallback(() -> mCallback.onReconfigureFailed(reason, params));
    }

    /**
     * @hide
     */
    public void onRangingStopped() {
        if (mState == State.CLOSED) {
            Log.w(TAG, "onRangingStopped invoked for a closed session");
            return;
        }

        mState = State.IDLE;
        executeCallback(() -> mCallback.onStopped());
    }

    /**
     * @hide
     */
    public void onRangingStopFailed(@Callback.FailureReason int reason,
            @NonNull PersistableBundle params) {
        if (mState == State.CLOSED) {
            Log.w(TAG, "onRangingStopFailed invoked for a closed session");
            return;
        }

        executeCallback(() -> mCallback.onStopFailed(reason, params));
    }

    /**
     * @hide
     */
    public void onRangingClosed(@Callback.CloseReason int reason,
            @NonNull PersistableBundle parameters) {
        mState = State.CLOSED;
        executeCallback(() -> mCallback.onClosed(reason, parameters));
    }

    /**
     * @hide
     */
    public void onRangingResult(@NonNull RangingReport report) {
        if (!isOpen()) {
            Log.w(TAG, "onRangingResult invoked for non-open session");
            return;
        }

        executeCallback(() -> mCallback.onReportReceived(report));
    }

    /**
     * @hide
     */
    private void executeCallback(@NonNull Runnable runnable) {
        final long identity = Binder.clearCallingIdentity();
        try {
            mExecutor.execute(runnable);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }
}
