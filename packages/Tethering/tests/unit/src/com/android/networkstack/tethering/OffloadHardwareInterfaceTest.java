/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.connectivity.tethering;

import static android.system.OsConstants.SOCK_STREAM;
import static android.system.OsConstants.AF_UNIX;

import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.net.util.SharedLog;
import android.os.NativeHandle;
import android.os.Handler;
import android.os.test.TestLooper;
import android.system.ErrnoException;
import android.system.Os;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import java.io.FileDescriptor;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class OffloadHardwareInterfaceTest {
    @Mock private ApplicationInfo mApplicationInfo;
    @Mock private Context mContext;
    @Mock private NativeHandle h1;
    @Mock private OutputStream outStream;
    @Mock private SharedLog mLog;

    private FileDescriptor mWriteSocket, mReadSocket;

    private final TestLooper mTestLooper = new TestLooper();

    // Random values to test Netlink message.
    private static final int TEST_SIZE = 16;
    private static final short TEST_TYPE = 184;
    private static final short TEST_FLAGS = 263;

    @Before public void setUp() {
        MockitoAnnotations.initMocks(this);

        // Connect sockets to each other in order to check Netlink message is correct.
        mWriteSocket = new FileDescriptor();
        mReadSocket = new FileDescriptor();
        try {
            Os.socketpair(AF_UNIX, SOCK_STREAM, 0, mWriteSocket, mReadSocket);
        } catch (ErrnoException e) {
            fail();
            return;
        }

        when(h1.getFileDescriptor()).thenReturn(mWriteSocket);
    }

    @Test
    public void testNetlinkMessage() throws Exception {
        final OffloadHardwareInterface mHardware = new OffloadHardwareInterface(new Handler(
                                                                mTestLooper.getLooper()), mLog);

        mHardware.sendNetlinkMessage(h1, TEST_SIZE, TEST_TYPE, TEST_FLAGS);

        ByteBuffer buffer = ByteBuffer.allocate(TEST_SIZE);
        int read = Os.read(mReadSocket, buffer);

        buffer.flip();
        assertEquals(TEST_SIZE, buffer.getInt());
        assertEquals(TEST_TYPE, buffer.getShort());
        assertEquals(TEST_FLAGS, buffer.getShort());
        assertEquals(1 /* seq */, buffer.getInt());
        assertEquals(0 /* pid */, buffer.getInt());
    }
}