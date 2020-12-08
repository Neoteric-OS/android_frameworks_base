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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.PersistableBundle;
import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;

/**
 * Test of {@link AdapterStateListener}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class RangingManagerTest {

    private static final IUwbAdapter ADAPTER = mock(IUwbAdapter.class);
    private static final Executor EXECUTOR = UwbTestUtils.getExecutor();
    private static final PersistableBundle PARAMS = new PersistableBundle();
    private static final @CloseReason int CLOSE_REASON = CloseReason.UNKNOWN;
    private static final @FailureReason int FAILURE_REASON = FailureReason.UNKNOWN;


    @Test
    public void testOpenSession_OpenRangingInvoked() throws RemoteException {
        RangingManager rangingManager = new RangingManager(ADAPTER);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        rangingManager.openSession(PARAMS, EXECUTOR, callback);
        verify(ADAPTER, times(1)).openRanging(eq(rangingManager), eq(PARAMS));
    }

    @Test
    public void testOpenSession_ErrorIfSameSessionHandleReturned() throws RemoteException {
        RangingManager rangingManager = new RangingManager(ADAPTER);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        SessionHandle handle = new SessionHandle(1);
        when(ADAPTER.openRanging(any(), any())).thenReturn(handle);

        rangingManager.openSession(PARAMS, EXECUTOR, callback);

        // Calling openSession will cause the same session handle to be returned. The onClosed
        // callback should be invoked
        RangingSession.Callback callback2 = mock(RangingSession.Callback.class);
        rangingManager.openSession(PARAMS, EXECUTOR, callback2);
        verify(callback, times(0)).onClosed(anyInt(), any());
        verify(callback2, times(1)).onClosed(anyInt(), any());
    }

    @Test
    public void testOnRangingOpened_ValidSessionHandle() throws RemoteException {
        RangingManager rangingManager = new RangingManager(ADAPTER);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        SessionHandle handle = new SessionHandle(1);
        when(ADAPTER.openRanging(any(), any())).thenReturn(handle);

        rangingManager.openSession(PARAMS, EXECUTOR, callback);
        rangingManager.onRangingOpened(handle);
        verify(callback, times(1)).onOpened(any());
    }

    @Test
    public void testOnRangingOpened_InvalidSessionHandle() throws RemoteException {
        RangingManager rangingManager = new RangingManager(ADAPTER);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);

        rangingManager.onRangingOpened(new SessionHandle(2));
        verify(callback, times(0)).onOpened(any());
    }

    @Test
    public void testOnRangingOpened_MultipleSessionsRegistered() throws RemoteException {
        SessionHandle sessionHandle1 = new SessionHandle(1);
        SessionHandle sessionHandle2 = new SessionHandle(2);
        RangingSession.Callback callback1 = mock(RangingSession.Callback.class);
        RangingSession.Callback callback2 = mock(RangingSession.Callback.class);

        when(ADAPTER.openRanging(any(), any()))
                .thenReturn(sessionHandle1)
                .thenReturn(sessionHandle2);

        RangingManager rangingManager = new RangingManager(ADAPTER);
        rangingManager.openSession(PARAMS, EXECUTOR, callback1);
        rangingManager.openSession(PARAMS, EXECUTOR, callback2);

        rangingManager.onRangingOpened(sessionHandle1);
        verify(callback1, times(1)).onOpened(any());
        verify(callback2, times(0)).onOpened(any());

        rangingManager.onRangingOpened(sessionHandle2);
        verify(callback1, times(1)).onOpened(any());
        verify(callback2, times(1)).onOpened(any());
    }

    @Test
    public void testCorrectCallbackInvoked() throws RemoteException {
        RangingManager rangingManager = new RangingManager(ADAPTER);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        SessionHandle handle = new SessionHandle(1);
        when(ADAPTER.openRanging(any(), any())).thenReturn(handle);

        rangingManager.openSession(PARAMS, EXECUTOR, callback);
        rangingManager.onRangingOpened(handle);
        verify(callback, times(1)).onOpened(any());

        rangingManager.onRangingStarted(handle, PARAMS);
        verify(callback, times(1)).onStarted(eq(PARAMS));

        rangingManager.onRangingStartFailed(handle, FAILURE_REASON, PARAMS);
        verify(callback, times(1)).onStartFailed(eq(FAILURE_REASON), eq(PARAMS));

        RangingReport report = UwbTestUtils.getRangingReports(1);
        rangingManager.onRangingResult(handle, report);
        verify(callback, times(1)).onReportReceived(eq(report));

        rangingManager.onRangingReconfigured(handle, PARAMS);
        verify(callback, times(1)).onReconfigured(eq(PARAMS));

        rangingManager.onRangingReconfigureFailed(handle, FAILURE_REASON, PARAMS);
        verify(callback, times(1)).onReconfigureFailed(eq(FAILURE_REASON), eq(PARAMS));

        rangingManager.onRangingStopped(handle);
        verify(callback, times(1)).onStopped();

        rangingManager.onRangingStopFailed(handle, FAILURE_REASON, PARAMS);
        verify(callback, times(1)).onStopFailed(eq(FAILURE_REASON), eq(PARAMS));

        rangingManager.onRangingClosed(handle, CLOSE_REASON, PARAMS);
        verify(callback, times(1)).onClosed(eq(CLOSE_REASON), eq(PARAMS));
    }

    @Test
    public void testOnRangingClosed_MultipleSessionsRegistered() throws RemoteException {
        // Verify that if multiple sessions are registered, only the session that is
        // requested to close receives the associated callbacks
        SessionHandle sessionHandle1 = new SessionHandle(1);
        SessionHandle sessionHandle2 = new SessionHandle(2);
        RangingSession.Callback callback1 = mock(RangingSession.Callback.class);
        RangingSession.Callback callback2 = mock(RangingSession.Callback.class);

        when(ADAPTER.openRanging(any(), any()))
                .thenReturn(sessionHandle1)
                .thenReturn(sessionHandle2);

        RangingManager rangingManager = new RangingManager(ADAPTER);
        rangingManager.openSession(PARAMS, EXECUTOR, callback1);
        rangingManager.openSession(PARAMS, EXECUTOR, callback2);

        rangingManager.onRangingClosed(sessionHandle1, CLOSE_REASON, PARAMS);
        verify(callback1, times(1)).onClosed(anyInt(), any());
        verify(callback2, times(0)).onClosed(anyInt(), any());

        rangingManager.onRangingClosed(sessionHandle2, CLOSE_REASON, PARAMS);
        verify(callback1, times(1)).onClosed(anyInt(), any());
        verify(callback2, times(1)).onClosed(anyInt(), any());
    }

    @Test
    public void testOnRangingReport_MultipleSessionsRegistered() throws RemoteException {
        SessionHandle sessionHandle1 = new SessionHandle(1);
        SessionHandle sessionHandle2 = new SessionHandle(2);
        RangingSession.Callback callback1 = mock(RangingSession.Callback.class);
        RangingSession.Callback callback2 = mock(RangingSession.Callback.class);

        when(ADAPTER.openRanging(any(), any()))
                .thenReturn(sessionHandle1)
                .thenReturn(sessionHandle2);

        RangingManager rangingManager = new RangingManager(ADAPTER);
        rangingManager.openSession(PARAMS, EXECUTOR, callback1);
        rangingManager.onRangingStarted(sessionHandle1, PARAMS);
        rangingManager.openSession(PARAMS, EXECUTOR, callback2);
        rangingManager.onRangingStarted(sessionHandle2, PARAMS);

        rangingManager.onRangingResult(sessionHandle1, UwbTestUtils.getRangingReports(1));
        verify(callback1, times(1)).onReportReceived(any());
        verify(callback2, times(0)).onReportReceived(any());

        rangingManager.onRangingResult(sessionHandle2, UwbTestUtils.getRangingReports(1));
        verify(callback1, times(1)).onReportReceived(any());
        verify(callback2, times(1)).onReportReceived(any());
    }

    @Test
    public void testOnClose_Reasons() throws RemoteException {
        runOnClose_Reason(CloseReason.LOCAL_API,
                RangingSession.Callback.CLOSE_REASON_LOCAL_CLOSE_API);

        runOnClose_Reason(CloseReason.MAX_SESSIONS_REACHED,
                RangingSession.Callback.CLOSE_REASON_LOCAL_MAX_SESSIONS_REACHED);

        runOnClose_Reason(CloseReason.PROTOCOL_SPECIFIC,
                RangingSession.Callback.CLOSE_REASON_PROTOCOL_SPECIFIC);

        runOnClose_Reason(CloseReason.REMOTE_REQUEST,
                RangingSession.Callback.CLOSE_REASON_REMOTE_REQUEST);

        runOnClose_Reason(CloseReason.SYSTEM_POLICY,
                RangingSession.Callback.CLOSE_REASON_LOCAL_SYSTEM_POLICY);

        runOnClose_Reason(CloseReason.UNKNOWN,
                RangingSession.Callback.CLOSE_REASON_UNKNOWN);
    }

    private void runOnClose_Reason(@CloseReason int reasonIn,
            @RangingSession.Callback.CloseReason int reasonOut) throws RemoteException {
        RangingManager rangingManager = new RangingManager(ADAPTER);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        SessionHandle handle = new SessionHandle(1);
        when(ADAPTER.openRanging(any(), any())).thenReturn(handle);
        rangingManager.openSession(PARAMS, EXECUTOR, callback);

        rangingManager.onRangingClosed(handle, reasonIn, PARAMS);
        verify(callback, times(1)).onClosed(eq(reasonOut), eq(PARAMS));
    }

    @Test
    public void testFailureReasons() throws RemoteException {
        runFailureReason(FailureReason.BAD_PARAMETERS,
                RangingSession.Callback.FAILURE_REASON_BAD_PARAMETERS);

        runFailureReason(FailureReason.MAX_SESSIONS_REACHED,
                RangingSession.Callback.FAILURE_REASON_MAX_SESSIONS_REACHED);

        runFailureReason(FailureReason.PROTOCOL_SPECIFIC,
                RangingSession.Callback.FAILURE_REASON_PROTOCOL_SPECIFIC);

        runFailureReason(FailureReason.SYSTEM_POLICY,
                RangingSession.Callback.FAILURE_REASON_SYSTEM_POLICY);

        runFailureReason(FailureReason.UNKNOWN,
                RangingSession.Callback.FAILURE_REASON_UNKNOWN);
    }

    private void runFailureReason(@FailureReason int reasonIn,
            @RangingSession.Callback.FailureReason int reasonOut) throws RemoteException {
        RangingManager rangingManager = new RangingManager(ADAPTER);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        SessionHandle handle = new SessionHandle(1);
        when(ADAPTER.openRanging(any(), any())).thenReturn(handle);
        rangingManager.openSession(PARAMS, EXECUTOR, callback);

        rangingManager.onRangingOpenFailed(handle, reasonIn, PARAMS);
        verify(callback, times(1)).onOpenFailed(eq(reasonOut), eq(PARAMS));

        // Open a new session
        rangingManager.openSession(PARAMS, EXECUTOR, callback);
        rangingManager.onRangingOpened(handle);

        rangingManager.onRangingStartFailed(handle, reasonIn, PARAMS);
        verify(callback, times(1)).onStartFailed(eq(reasonOut), eq(PARAMS));

        rangingManager.onRangingReconfigureFailed(handle, reasonIn, PARAMS);
        verify(callback, times(1)).onReconfigureFailed(eq(reasonOut), eq(PARAMS));

        rangingManager.onRangingStopFailed(handle, reasonIn, PARAMS);
        verify(callback, times(1)).onStopFailed(eq(reasonOut), eq(PARAMS));
    }
}
