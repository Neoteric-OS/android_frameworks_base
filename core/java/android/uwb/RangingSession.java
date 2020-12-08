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
import android.os.PersistableBundle;

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
 * {@link RangingSession.Callback#onOpenSuccess(RangingSession)}. If opening a
 * session fails, the failure is reported through {@link RangingSession.Callback#onClosed(int)} with
 * the failure reason.
 *
 * @hide
 */
public final class RangingSession implements AutoCloseable {
    private enum State {
        /**
         * The state of the {@link RangingSession} until
         * {@link RangingSession.Callback#onOpenSuccess(RangingSession)} is invoked
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
         * Invoked when {@link UwbManager#openRangingSession(PersistableBundle, Executor, Callback)}
         * is successful
         *
         * @param session the newly opened {@link RangingSession}
         */
        void onOpenSuccess(RangingSession session);

        /**
         * Invoked when {@link RangingSession#start()} is successful
         * @param sessionInfo session specific parameters from the lower layers
         */
        void onStarted(PersistableBundle sessionInfo);

        // TODO: define these reasons
        @interface StartFailureReason {}

        /**
         * Invoked when {@link RangingSession#start()} fails
         *
         * @param reason the failure reason
         * @param params protocol specific parameters
         */
        void onStartFailed(@StartFailureReason int reason, PersistableBundle params);

        /**
         * Invoked when a request to reconfigure the session succeeds
         *
         * @param params the updated ranging configuration
         */
        void onReconfigured(@NonNull PersistableBundle params);

        // TODO: define these reasons
        @interface ReconfigureFailureReason {};

        /**
         * Invoked when a request to reconfigure the session fails
         *
         * @param reason reason the session failed to be reconfigured
         * @param params protocol specific failure reasons
         */
        void onReconfigureFailed(@ReconfigureFailureReason int reason,
                @NonNull PersistableBundle params);

        /**
         * Invoked when a request to suspend the session succeeds
         */
        void onSuspended();

        // TODO: define these reasons
        @interface SuspendFailureReason {};

        /**
         * Invoked when a request to suspend the session fails
         *
         * @param reason reason the session failed to be suspended
         * @param params protocol specific failure reasons
         */
        void onSuspendFailed(@SuspendFailureReason int reason,
                @NonNull PersistableBundle params);

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
         * Invoked when session is either closed spontaneously, or per user request via
         * {@link RangingSession#close()} or {@link AutoCloseable#close()}, or when session failed
         * to open.
         *
         * @param reason reason for the session closure
         */
        void onClosed(@CloseReason int reason);

        /**
         * Called once per ranging interval even when a ranging measurement fails
         *
         * @param rangingReport ranging report for this interval's measurements
         */
        void onReportReceived(RangingReport rangingReport);
    }

    /**
     * Begins ranging for the session.
     *
     * <p>May only be invoked when the session is idle.
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

        throw new UnsupportedOperationException();
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
    public void reconfigure(PersistableBundle params) {
        if (mState != State.ACTIVE || mState != State.IDLE) {
            throw new IllegalStateException();
        }

        throw new UnsupportedOperationException();
    }

    /**
     * Suspends ranging without closing the session
     *
     * <p>Suspending a {@link RangingSession} is useful when the lower layers should not discard
     * the parameters of the session, or when a session needs to be able to be resumed quickly.
     *
     * <p>On successfully suspending the session {@link Callback#onSuspended()} is invoked.
     *
     * <p>On failure to suspend the session,
     * {@link RangingSession.Callback#onSuspendFailed(int, PersistableBundle)} is invoked.
     */
    public void suspend() {
        if (mState != State.ACTIVE) {
            throw new IllegalStateException();
        }

        throw new UnsupportedOperationException();
    }

    /**
     * Close the ranging session
     * <p>If this session is currently open, it will close and stop the session.
     * <p>If the session is in the process of being opened, it will attempt to stop the session from
     * being opened.
     * <p>If the session is already closed, the registered {@link Callback#onClosed(int)} callback
     * will still be invoked.
     *
     * <p>{@link Callback#onClosed(int)} will be invoked using the same callback
     * object given to {@link UwbManager#openRangingSession(PersistableBundle, Executor, Callback)}
     * when the {@link RangingSession} was opened. The callback will be invoked after each call to
     * {@link #close()}, even if the {@link RangingSession} is already closed.
     */
    @Override
    public void close() {
        throw new UnsupportedOperationException();
    }
}
