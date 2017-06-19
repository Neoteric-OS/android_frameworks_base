/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.internal.util.BitUtils.uint16;

import android.hardware.tetheroffload.control.V1_0.IOffloadControl;
import android.hardware.tetheroffload.control.V1_0.ITetheringOffloadCallback;
import android.hardware.tetheroffload.control.V1_0.NatTimeoutUpdate;
import android.os.Handler;
import android.os.RemoteException;
import android.net.netlink.NetlinkSocket;
import android.net.util.SharedLog;
import android.system.ErrnoException;
import android.system.OsConstants;

import java.io.InterruptedIOException;
import java.net.SocketException;
import java.util.ArrayList;


/**
 * Capture tethering dependencies, for injection.
 *
 * @hide
 */
public class OffloadHardwareInterface {
    private static final String TAG = OffloadHardwareInterface.class.getSimpleName();
    private static final String NO_INTERFACE_NAME = "";
    private static final String NO_IPV4_ADDRESS = "";
    private static final String NO_IPV4_GATEWAY = "";

    private static native boolean configOffload();

    private final Handler mHandler;
    private final SharedLog mLog;
    private IOffloadControl mOffloadControl;
    private TetheringOffloadCallback mTetheringOffloadCallback;
    private ControlCallback mControlCallback;

    public static class ControlCallback {
        public void onOffloadEvent(int event) {}

        public void onNatTimeoutUpdate(int proto,
                                       String srcAddr, int srcPort,
                                       String dstAddr, int dstPort) {}
    }

    public OffloadHardwareInterface(Handler h, SharedLog log) {
        mHandler = h;
        mLog = log.forSubComponent(TAG);
    }

    public boolean initOffloadConfig() {
        return configOffload();
    }

    public boolean initOffloadControl(ControlCallback controlCb) {
        mControlCallback = controlCb;

        if (mOffloadControl == null) {
            try {
                mOffloadControl = IOffloadControl.getService();
            } catch (RemoteException e) {
                mLog.e("tethering offload control not supported: " + e);
                return false;
            }
        }

        mTetheringOffloadCallback = new TetheringOffloadCallback(mHandler, mControlCallback);
        final CbResults results = new CbResults();
        try {
            mOffloadControl.initOffload(
                    mTetheringOffloadCallback,
                    (boolean success, String errMsg) -> {
                        results.success = success;
                        results.errMsg = errMsg;
                    });
        } catch (RemoteException e) {
            mLog.e("failed to initOffload: " + e);
            return false;
        }

        if (!results.success) mLog.e("initOffload failed: " + results.errMsg);
        return results.success;
    }

    public void stopOffloadControl() {
        if (mOffloadControl != null) {
            try {
                mOffloadControl.stopOffload(
                        (boolean success, String errMsg) -> {
                            if (!success) mLog.e("stopOffload failed: " + errMsg);
                        });
            } catch (RemoteException e) {
                mLog.e("failed to stopOffload: " + e);
            }
        }
        mOffloadControl = null;
        mTetheringOffloadCallback = null;
        mControlCallback = null;
    }

    public int updateNatTimeout() {
        int errno = -OsConstants.EPROTO;
        try (NetlinkSocket nlSocket = new NetlinkSocket(OsConstants.NETLINK_NETFILTER)) {
/*

            final long IO_TIMEOUT = 300L;
            nlSocket.connectToKernel();
            nlSocket.sendMessage(msg, 0, msg.length, IO_TIMEOUT);
            final ByteBuffer bytes = nlSocket.recvMessage(IO_TIMEOUT);
            // recvMessage() guaranteed to not return null if it did not throw.
            final NetlinkMessage response = NetlinkMessage.parse(bytes);
            if (response != null && response instanceof NetlinkErrorMessage &&
                    (((NetlinkErrorMessage) response).getNlMsgError() != null)) {
                errno = ((NetlinkErrorMessage) response).getNlMsgError().error;
                if (errno != 0) {
                    // TODO: consider ignoring EINVAL (-22), which appears to be
                    // normal when probing a neighbor for which the kernel does
                    // not already have / no longer has a link layer address.
                    Log.e(TAG, "Error " + msgSnippet + ", errmsg=" + response.toString());
                }
            } else {
                String errmsg;
                if (response == null) {
                    bytes.position(0);
                    errmsg = "raw bytes: " + NetlinkConstants.hexify(bytes);
                } else {
                    errmsg = response.toString();
                }
                Log.e(TAG, "Error " + msgSnippet + ", errmsg=" + errmsg);
            }
*/
        } catch (ErrnoException e) {
            mLog.e("Error " + e);
            errno = -e.errno;
/*
        } catch (InterruptedIOException e) {
            mLog.e("Error " + e);
            errno = -OsConstants.ETIMEDOUT;
        } catch (SocketException e) {
            mLog.e("Error " + e);
            errno = -OsConstants.EIO;
*/
        }
        return errno;

    }

    public boolean setUpstreamParameters(
            String iface, String v4addr, String v4gateway, ArrayList<String> v6gws) {
        iface = iface != null ? iface : NO_INTERFACE_NAME;
        v4addr = v4addr != null ? v4addr : NO_IPV4_ADDRESS;
        v4gateway = v4gateway != null ? v4gateway : NO_IPV4_GATEWAY;
        v6gws = v6gws != null ? v6gws : new ArrayList<>();

        final CbResults results = new CbResults();
        try {
            mOffloadControl.setUpstreamParameters(
                    iface, v4addr, v4gateway, v6gws,
                    (boolean success, String errMsg) -> {
                        results.success = success;
                        results.errMsg = errMsg;
                    });
        } catch (RemoteException e) {
            mLog.e("failed to setUpstreamParameters: " + e);
            return false;
        }

        if (!results.success) mLog.e("setUpstreamParameters failed: " + results.errMsg);
        return results.success;
    }

    private static class TetheringOffloadCallback extends ITetheringOffloadCallback.Stub {
        public final Handler handler;
        public final ControlCallback controlCb;

        public TetheringOffloadCallback(Handler h, ControlCallback cb) {
            handler = h;
            controlCb = cb;
        }

        @Override
        public void onEvent(int event) {
            handler.post(() -> { controlCb.onOffloadEvent(event); });
        }

        @Override
        public void updateTimeout(NatTimeoutUpdate params) {
            handler.post(() -> {
                    controlCb.onNatTimeoutUpdate(
                        params.proto,
                        params.src.addr, uint16(params.src.port),
                        params.dst.addr, uint16(params.dst.port));
            });
        }
    }

    private static class CbResults {
        boolean success;
        String errMsg;
    }

/*
    private static byte[] newConntrackMessage() {
        final int length = StructNlMsgHdr.STRUCT_SIZE;
        final byte[] bytes = new byte[length];
        final ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.order(ByteOrder.nativeOrder());

        final StructNlMsgHdr nlmsghdr = new StructNlMsgHdr();
        nlmsghdr.nlmsg_len = length;
        nlmsghdr.nlmsg_type = NetlinkConstants.IPCTNL_MSG_CT_NEW;
        nlmsghdr.nlmsg_flags = NLM_F_REQUEST | NLM_F_ACK;
    }
*/
}
