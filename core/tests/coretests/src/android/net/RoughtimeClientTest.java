/*
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

package android.net;

import android.net.RoughtimeClient;
import android.util.Base64;
import android.util.Log;
import libcore.util.HexEncoding;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.security.MessageDigest;
import java.util.Arrays;
import junit.framework.TestCase;


public class RoughtimeClientTest extends TestCase {
    private static final String TAG = "RoughtimeClientTest";

    private static final long TEST_TIME = 8675309;
    private static final int TEST_RADIUS = 42;

    private static byte b(int i) { return (byte)i; }

    private static final byte[] privateKey = {
        b(0x06), b(0x79), b(0x1d), b(0xf3), b(0x53), b(0x77), b(0xae), b(0x15),
        b(0x8c), b(0x23), b(0x17), b(0x6c), b(0x59), b(0xdc), b(0x30), b(0x19),
        b(0x9c), b(0x7d), b(0x4b), b(0x40), b(0xe3), b(0x34), b(0x79), b(0x70),
        b(0xd8), b(0x65), b(0xdc), b(0xda), b(0x8f), b(0x13), b(0x05), b(0x5a),
        b(0xce), b(0xe0), b(0x65), b(0x35), b(0x09), b(0x59), b(0x28), b(0x3b),
        b(0xa2), b(0x81), b(0x2d), b(0x56), b(0xd1), b(0xed), b(0x34), b(0xb8),
        b(0xd2), b(0xfc), b(0xb7), b(0xe2), b(0xc2), b(0xeb), b(0xf3), b(0xc1),
        b(0x84), b(0x26), b(0x1f), b(0xf5), b(0xd7), b(0x11), b(0x5d), b(0xd3),
    };

    private static final byte[] publicKey = {
        b(0xce), b(0xe0), b(0x65), b(0x35), b(0x09), b(0x59), b(0x28), b(0x3b),
        b(0xa2), b(0x81), b(0x2d), b(0x56), b(0xd1), b(0xed), b(0x34), b(0xb8),
        b(0xd2), b(0xfc), b(0xb7), b(0xe2), b(0xc2), b(0xeb), b(0xf3), b(0xc1),
        b(0x84), b(0x26), b(0x1f), b(0xf5), b(0xd7), b(0x11), b(0x5d), b(0xd3),
    };

    private static final byte[] privateLongTermKey = {
        b(0x4f), b(0x43), b(0x88), b(0x73), b(0xfc), b(0xcf), b(0x86), b(0x88),
        b(0x52), b(0x53), b(0xb5), b(0xaa), b(0xb1), b(0x64), b(0x36), b(0x7b),
        b(0xda), b(0xc8), b(0x72), b(0x91), b(0xda), b(0xa3), b(0x70), b(0xdb),
        b(0x64), b(0xfd), b(0x05), b(0x8f), b(0xa8), b(0x08), b(0x19), b(0x41),
        b(0x20), b(0x8d), b(0x90), b(0xb7), b(0xfe), b(0xed), b(0xd4), b(0xa7),
        b(0x59), b(0xd5), b(0x8f), b(0x29), b(0x0d), b(0x50), b(0xfb), b(0xdf),
        b(0x22), b(0x94), b(0x5f), b(0x2b), b(0x6b), b(0x36), b(0xe4), b(0xdb),
        b(0x9c), b(0x0c), b(0x92), b(0xc2), b(0x0b), b(0x9b), b(0x69), b(0x68),
    };

    private static final byte[] publicLongTermKey = {
        b(0x20), b(0x8d), b(0x90), b(0xb7), b(0xfe), b(0xed), b(0xd4), b(0xa7),
        b(0x59), b(0xd5), b(0x8f), b(0x29), b(0x0d), b(0x50), b(0xfb), b(0xdf),
        b(0x22), b(0x94), b(0x5f), b(0x2b), b(0x6b), b(0x36), b(0xe4), b(0xdb),
        b(0x9c), b(0x0c), b(0x92), b(0xc2), b(0x0b), b(0x9b), b(0x69), b(0x68),
    };

    private final RoughtimeTestServer mServer = new RoughtimeTestServer();
    private final RoughtimeClient mClient = new RoughtimeClient();

    public void testBasicWorkingRoughtimeClientQuery() throws Exception {
        mServer.shouldRespond(true);
        assertTrue(mClient.requestTime(mServer.getAddress(), mServer.getPort(),
                    publicLongTermKey, 500));
        assertEquals(1, mServer.numRequestsReceived());
        assertEquals(1, mServer.numRepliesSent());
    }

    public void testDnsResolutionFailure() throws Exception {
        mServer.shouldRespond(true);
        assertFalse(mClient.requestTime("roughtime.server.doesnotexist.example",
                    publicLongTermKey, 5000));
    }

    public void testTimeoutFailure() throws Exception {
        mServer.shouldRespond(false);
        assertFalse(mClient.requestTime(mServer.getAddress(), mServer.getPort(),
                    publicLongTermKey, 500));
        assertEquals(1, mServer.numRequestsReceived());
        assertEquals(0, mServer.numRepliesSent());
    }

    private static MessageDigest md = null;

    private static byte[] signedResponse(byte[] nonce) {
        RoughtimeClient.Message signed = new RoughtimeClient.Message();

        try {
            if (md == null) {
                md = MessageDigest.getInstance("SHA-512");
            }
        } catch(Exception e) {
            return null;
        }

        md.update(new byte[]{0});
        byte[] hash = md.digest(nonce);
        signed.put(RoughtimeClient.Tag.ROOT, hash);
        signed.putLong(RoughtimeClient.Tag.MIDP, TEST_TIME);
        signed.putInt(RoughtimeClient.Tag.RADI, TEST_RADIUS);

        return signed.serialize();
    }

    private static byte[] delegation(long minTime, long maxTime,
            byte[] pubKey) {
        RoughtimeClient.Message delegation = new RoughtimeClient.Message();

        delegation.putLong(RoughtimeClient.Tag.MINT, minTime);
        delegation.putLong(RoughtimeClient.Tag.MAXT, maxTime);
        delegation.put(RoughtimeClient.Tag.PUBK, pubKey);

        return delegation.serialize();
    }

    private static byte[] cert(long minTime, long maxTime, byte[] pubKey) {
        RoughtimeClient.Message cert = new RoughtimeClient.Message();

        byte[] data = delegation(minTime, maxTime, pubKey);
        byte[] signature = new byte[64];

        if (!RoughtimeClient.sign(signature, privateLongTermKey, data)) {
            return null;
        }

        cert.put(RoughtimeClient.Tag.DELE, data);
        cert.put(RoughtimeClient.Tag.SIG, signature);

        return cert.serialize();
    }

    private static byte[] response(byte[] nonce) {
        RoughtimeClient.Message msg = new RoughtimeClient.Message();

        byte[] srep = signedResponse(nonce);
        byte[] srepSignature = new byte[64];

        if (!RoughtimeClient.sign(srepSignature, privateKey, srep)) {
            return null;
        }

        byte[] certificate = cert(TEST_TIME - TEST_RADIUS,
                TEST_TIME + TEST_RADIUS, publicKey);

        msg.put(RoughtimeClient.Tag.SREP, srep);
        msg.put(RoughtimeClient.Tag.SIG, srepSignature);
        msg.putInt(RoughtimeClient.Tag.INDX, 0);
        msg.put(RoughtimeClient.Tag.PATH, new byte[0]);
        msg.put(RoughtimeClient.Tag.CERT, certificate);

        return msg.serialize();
    }

    private static class RoughtimeTestServer {
        private final Object mLock = new Object();
        private final DatagramSocket mSocket;
        private final InetAddress mAddress;
        private final int mPort;
        private int mRcvd;
        private int mSent;
        private Thread mListeningThread;
        private boolean mShouldRespond = true;

        public RoughtimeTestServer() {
            mSocket = makeSocket();
            mAddress = mSocket.getLocalAddress();
            mPort = mSocket.getLocalPort();
            Log.d(TAG, "testing server listening on (" + mAddress + ", " + mPort + ")");

            mListeningThread = new Thread() {
                public void run() {
                    while (true) {
                        byte[] buffer = new byte[2048];
                        DatagramPacket request = new DatagramPacket(buffer, buffer.length);
                        try {
                            mSocket.receive(request);
                        } catch (IOException e) {
                            Log.e(TAG, "datagram receive error: " + e);
                            break;
                        }
                        synchronized (mLock) {
                            mRcvd++;

                            if (! mShouldRespond) {
                                continue;
                            }

                            RoughtimeClient.Message msg =
                                RoughtimeClient.Message.deserialize(
                                    Arrays.copyOf(buffer, request.getLength()));

                            byte[] nonce = msg.get(RoughtimeClient.Tag.NONC);
                            if (nonce.length != 64) {
                                Log.e(TAG, "Nonce is wrong length.");
                            }

                            try {
                                request.setData(response(nonce));
                                mSocket.send(request);
                            } catch (IOException e) {
                                Log.e(TAG, "datagram send error: " + e);
                                break;
                            }
                            mSent++;
                        }
                    }
                    mSocket.close();
                }
            };
            mListeningThread.start();
        }

        private DatagramSocket makeSocket() {
            DatagramSocket socket;
            try {
                socket = new DatagramSocket(0, InetAddress.getLoopbackAddress());
            } catch (SocketException e) {
                Log.e(TAG, "Failed to create test server socket: " + e);
                return null;
            }
            return socket;
        }

        public void shouldRespond(boolean value) { mShouldRespond = value; }

        public InetAddress getAddress() { return mAddress; }
        public int getPort() { return mPort; }
        public int numRequestsReceived() { synchronized (mLock) { return mRcvd; } }
        public int numRepliesSent() { synchronized (mLock) { return mSent; } }
    }
}
