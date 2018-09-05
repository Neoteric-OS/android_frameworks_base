package com.android.settingslib.bluetooth;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothA2dpSink;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class A2dpSinkProfileTest {

    @Mock
    private LocalBluetoothAdapter mAdapter;
    @Mock
    private CachedBluetoothDeviceManager mDeviceManager;
    @Mock
    private LocalBluetoothProfileManager mProfileManager;
    @Mock
    private BluetoothA2dpSink mService;
    @Mock
    private CachedBluetoothDevice mCachedBluetoothDevice;
    @Mock
    private BluetoothDevice mBluetoothDevice;
    private BluetoothProfile.ServiceListener mServiceListener;
    private A2dpSinkProfile mProfile;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Context context = spy(RuntimeEnvironment.application);

        doAnswer((invocation) -> {
            mServiceListener = (BluetoothProfile.ServiceListener) invocation.getArguments()[1];
            return null;
        }).when(mAdapter).getProfileProxy(any(Context.class), any(),
                eq(BluetoothProfile.A2DP_SINK));

        mProfile = new A2dpSinkProfile(context, mAdapter, mDeviceManager, mProfileManager);
        mServiceListener.onServiceConnected(BluetoothProfile.A2DP_SINK, mService);
    }

    @Test
    public void connect_shouldConnectBluetoothA2dpSink() {
        mProfile.connect(mBluetoothDevice);
        verify(mService).connect(mBluetoothDevice);
    }

    @Test
    public void disconnect_shouldDisconnectBluetoothA2dpSink() {
        mProfile.disconnect(mBluetoothDevice);
        verify(mService).disconnect(mBluetoothDevice);
    }

    @Test
    public void getConnectionStatus_shouldReturnConnectionState() {
        when(mService.getConnectionState(mBluetoothDevice)).
                thenReturn(BluetoothProfile.STATE_CONNECTED);
        assertThat(mProfile.getConnectionStatus(mBluetoothDevice)).
                isEqualTo(BluetoothProfile.STATE_CONNECTED);
    }
}