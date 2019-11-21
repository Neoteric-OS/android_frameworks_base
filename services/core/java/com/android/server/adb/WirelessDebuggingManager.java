/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.adb;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.debug.AdbManager;
import android.debug.PairDevice;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Slog;

import com.android.framework.protobuf.InvalidProtocolBufferException;
import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.internal.notification.SystemNotificationChannels;
import com.android.server.FgThread;
import com.android.server.adb.AdbKeyStoreProto.Key;
import com.android.server.adb.AdbKeyStoreProto.KeyStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides communication to the Android Debug Daemon to allow, deny, query,
 * or configure the adb wireless debugging.
 */

public class WirelessDebuggingManager {
    private static final String TAG = "WirelessDebuggingManager";
    //private static final boolean DEBUG = false;
    private static final boolean DEBUG = true;

    private static final String ADBDWIFI_SOCKET = "adbdwifi";
    private static final String ADBDWIFI_KEYS = "/data/misc/adb/adb_wifi_keys";
    private static final int BUFFER_SIZE = 4096;
    private static final String WIRELESS_DEBUG_PERSISTENT_CONFIG_PROPERTY =
            "persist.sys.wireless.debug";
    private final Context mContext;
    private final Handler mHandler;
    private WirelessDebuggingThread mThread;
    private PairingThread mPairingThread = null;
    private boolean mAdbWirelessEnabled = false;
    private int mState;
    private String mDeviceName;
    Map<String, PairDevice> mPairedDevices = new HashMap<>();
    Map<String, PairDevice> mPairingDevices = new HashMap<>();
    Set<String> mConnectedDevices = new HashSet<>();
    private static final int PAIRING_CODE_LENGTH = 6;
    private byte[] mOurKey;
    private byte[] mTheirKey;

    private static native boolean native_keystore_init();
    private static native boolean native_keystore_remove_guid(String guid);

    public WirelessDebuggingManager(Context context) {
        mHandler = new WirelessDebuggingHandler(FgThread.get().getLooper());
        mContext = context;
    }

    class PairingThread extends Thread {
        private String mGuid;
        private String mName;
        private String mPairingCode;

        private native boolean native_pairing_start(String password);
        private native void native_pairing_cancel();

        PairingThread(String pairingCode) {
            super(TAG);
            mPairingCode = pairingCode;
        }

        @Override
        public void run() {
            boolean paired = native_pairing_start(mPairingCode);
            if (DEBUG) {
                if (mGuid != null && mName != null) {
                    Slog.i(TAG, "Pairing succeeded guid=" + mGuid + " name=" + mName);
                } else {
                    Slog.i(TAG, "Pairing failed");
                }
            }
            Bundle bundle = new Bundle();
            bundle.putString("guid", paired ? mGuid : null);
            bundle.putString("name", paired ? mName : null);
            Message message = Message.obtain(mHandler,
                                             WirelessDebuggingHandler.MSG_RESPONSE_PAIRING_RESULT,
                                             bundle);
            mHandler.sendMessage(message);
        }

        public void cancelPairing() {
            native_pairing_cancel();
        }
    }

    class WirelessDebuggingThread extends Thread {
        private boolean mStopped;
        private LocalSocket mSocket;
        private OutputStream mOutputStream;
        private InputStream mInputStream;

        WirelessDebuggingThread() {
            super(TAG);
        }

        @Override
        public void run() {
            while (true) {
                synchronized (this) {
                    if (mStopped) {
                        if (DEBUG) Slog.d(TAG, "Exiting thread");
                        return;
                    }
                    try {
                        openSocketLocked();
                    } catch (Exception e) {
                        /* Don't loop too fast if adbd dies, before init restarts it */
                        SystemClock.sleep(1000);
                        continue;
                    }
                }
                try {
                    listenToSocket();
                } catch (Exception e) {
                    /* Don't loop too fast if adbd dies, before init restarts it */
                    SystemClock.sleep(1000);
                }
            }
        }

        private void openSocketLocked() throws IOException {
            try {
                LocalSocketAddress address = new LocalSocketAddress(ADBDWIFI_SOCKET,
                        LocalSocketAddress.Namespace.RESERVED);
                mInputStream = null;

                if (DEBUG) Slog.d(TAG, "Creating socket");
                mSocket = new LocalSocket(LocalSocket.SOCKET_SEQPACKET);
                mSocket.connect(address);

                mOutputStream = mSocket.getOutputStream();
                mInputStream = mSocket.getInputStream();
            } catch (IOException ioe) {
                Slog.w(TAG, "Failed to connect to " + ADBDWIFI_SOCKET + " socket.");
                Slog.w(TAG, "Error (" + ioe + ")");
                closeSocketLocked();
                throw ioe;
            }
        }

        private void handleAdbdMsg(int count, byte[]  buffer, int op) {
            // Msg is in the following format:
            //  1) Opcode (2 bytes)
            //  2) <content>
            byte[] content = Arrays.copyOfRange(buffer, 2, count);
            Message msg = mHandler.obtainMessage(op);
            msg.obj = content;
            mHandler.sendMessage(msg);
        }

        private class AdbdWifiMsg {
            public final String code;
            public final int msgInt;
            AdbdWifiMsg(String c, int m) {
                code = c;
                msgInt = m;
            }
        }
        // Messages that adbd can send to us.
        private final List<AdbdWifiMsg> mAdbdWifiMsgs = Collections.unmodifiableList(
                Arrays.asList(
                    new AdbdWifiMsg("CN", WirelessDebuggingHandler.MSG_DEVICE_CONNECTED),
                    new AdbdWifiMsg("DC", WirelessDebuggingHandler.MSG_DEVICE_DISCONNECTED)));

        private void listenToSocket() throws IOException {
            try {
                byte[] buffer = new byte[BUFFER_SIZE];
                while (true) {
                    int count = mInputStream.read(buffer);
                    if (count < 2) {
                        Slog.w(TAG, "Read failed with count " + count);
                        break;
                    }
                    if (DEBUG) {
                        Slog.i(TAG, "listenToSocket received " + count + " :"
                                + new String(Arrays.copyOf(buffer, count)));
                    }
                    boolean handled = false;
                    for (AdbdWifiMsg adbdWifiMsg : mAdbdWifiMsgs) {
                        if (adbdWifiMsg.code.charAt(0) == buffer[0]
                                && adbdWifiMsg.code.charAt(1) == buffer[1]) {
                            handleAdbdMsg(count, buffer, adbdWifiMsg.msgInt);
                            handled = true;
                            break;
                        }
                    }
                    if (!handled) {
                        Slog.e(TAG, "Unexpected operator: " + buffer[0]
                                + buffer[1]);
                        break;
                    }
                }
            } finally {
                synchronized (this) {
                    closeSocketLocked();
                }
            }
        }

        private void closeSocketLocked() {
            if (DEBUG) Slog.d(TAG, "Closing socket");
            try {
                if (mOutputStream != null) {
                    mOutputStream.close();
                    mOutputStream = null;
                }
            } catch (IOException e) {
                Slog.e(TAG, "Failed closing output stream: " + e);
            }

            try {
                if (mSocket != null) {
                    mSocket.close();
                    mSocket = null;
                }
            } catch (IOException ex) {
                Slog.e(TAG, "Failed closing socket: " + ex);
            }
        }

        /** Call to stop listening on the socket and exit the thread. */
        void stopListening() {
            synchronized (this) {
                mStopped = true;
                closeSocketLocked();
            }
        }

        void sendMessage(byte[] msg) {
            synchronized (this) {
                if (!mStopped && mOutputStream != null) {
                    try {
                        mOutputStream.write(msg);
                        mOutputStream.flush();
                    } catch (IOException ex) {
                        Slog.e(TAG, "Failed to write response:", ex);
                    }
                }
            }
        }
    }

    class WirelessDebuggingHandler extends Handler {
        private NotificationManager mNotificationManager;
        private boolean mAdbNotificationShown;

        // === Messages from the UI ==============
        // UI asks adbd to enable adbdwifi
        private static final int MSG_ADBDWIFI_ENABLE = 1;
        // UI asks adbd to disable adbdwifi
        private static final int MSG_ADBDWIFI_DISABLE = 2;
        // UI asks for the list of paired devices
        private static final int MSG_QUERY_PAIRED_DEVICES = 3;
        // UI asks for the list of pairing devices
        private static final int MSG_QUERY_PAIRING_DEVICES = 4;
        // UI asks to cancel pairing
        private static final int MSG_REQ_CANCEL_PAIRING = 5;
        // UI asks to change the device name
        private static final int MSG_QUERY_SET_DEVICE_NAME = 6;
        // Enable discoverability mode
        private static final int MSG_QUERY_DISCOVER_ENABLE = 7;
        // Disable discoverability mode
        private static final int MSG_QUERY_DISCOVER_DISABLE = 8;
        // UI asks to unpair (forget) a device.
        private static final int MSG_REQ_UNPAIR = 9;

        // === Messages from the PairingThread ===========
        // Result of the pairing
        private static final int MSG_RESPONSE_PAIRING_RESULT = 10;

        // === Messages from adbd ================
        // Notifys us a paired device connected.
        private static final int MSG_DEVICE_CONNECTED = 11;
        // Notifys us a paired device disconnected.
        private static final int MSG_DEVICE_DISCONNECTED = 12;

        // === System server messages ==============
        private static final int MSG_BOOT_COMPLETED = 13;

        // === Messages we can send to adbd ===========
        private static final String MSG_DISCONNECT_DEVICE = "DD";
        private static final String MSG_DISABLE_ADBDWIFI = "DA";

        private static final String ADB_NOTIFICATION_CHANNEL_ID_TV = "usbdevicemanager.adb.tv";
        private boolean isTv() {
            return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        }

        WirelessDebuggingHandler(Looper looper) {
            super(looper);
        }

        // Show when at least one device is connected.
        public void showAdbConnectedNotification(boolean show) {
            final int id = SystemMessage.NOTE_ADB_WIFI_ACTIVE;
            final int titleRes = com.android.internal.R.string.adbwifi_active_notification_title;
            if (show == mAdbNotificationShown) {
                return;
            }
            if (!mAdbNotificationShown) {
                Resources r = mContext.getResources();
                CharSequence title = r.getText(titleRes);
                CharSequence message = r.getText(
                        com.android.internal.R.string.adbwifi_active_notification_message);

                Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                PendingIntent pi = PendingIntent.getActivityAsUser(mContext, 0,
                        intent, 0, null, UserHandle.CURRENT);

                Notification notification =
                        new Notification.Builder(mContext, SystemNotificationChannels.DEVELOPER)
                                .setSmallIcon(com.android.internal.R.drawable.stat_sys_adb)
                                .setWhen(0)
                                .setOngoing(true)
                                .setTicker(title)
                                .setDefaults(0)  // please be quiet
                                .setColor(mContext.getColor(
                                        com.android.internal.R.color
                                                .system_notification_accent_color))
                                .setContentTitle(title)
                                .setContentText(message)
                                .setContentIntent(pi)
                                .setVisibility(Notification.VISIBILITY_PUBLIC)
                                .extend(new Notification.TvExtender()
                                        .setChannelId(ADB_NOTIFICATION_CHANNEL_ID_TV))
                                .build();
                mAdbNotificationShown = true;
                mNotificationManager.notifyAsUser(null, id, notification,
                        UserHandle.ALL);
            } else {
                mAdbNotificationShown = false;
                mNotificationManager.cancelAsUser(null, id, UserHandle.ALL);
            }
        }

        private void setupNotifications() {
            mNotificationManager = (NotificationManager)
                    mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            if (mNotificationManager == null) {
                Slog.e(TAG, "Unable to setup notifications for wireless debugging");
                return;
            }

            // Ensure that the notification channels are set up
            if (isTv()) {
                // TV-specific notification channel
                mNotificationManager.createNotificationChannel(
                        new NotificationChannel(ADB_NOTIFICATION_CHANNEL_ID_TV,
                                mContext.getString(
                                        com.android.internal.R.string
                                                .adb_debugging_notification_channel_tv),
                                NotificationManager.IMPORTANCE_HIGH));
            }
        }

        public void handleMessage(Message msg) {
            if (DEBUG) Slog.i(TAG, "adb wireless handle msg " + msg.what);
            switch (msg.what) {
                case MSG_BOOT_COMPLETED:
                    setupNotifications();
                    break;
                case MSG_ADBDWIFI_ENABLE:
                    if (mAdbWirelessEnabled) {
                        break;
                    }

                    mAdbWirelessEnabled = true;
                    if (!native_keystore_init()) {
                        Slog.e(TAG, "Unable to initialize adbwifi keystore");
                        // TODO: Should we re-disable the UI?
                    }
                    // Send the paired devices list to the UI
                    refreshPairedDevicesList(mPairedDevices);
                    sendPairedDevicesToUI(mPairedDevices);
                    SystemProperties.set(WIRELESS_DEBUG_PERSISTENT_CONFIG_PROPERTY,
                            Boolean.toString(mAdbWirelessEnabled));

                    if (DEBUG) Slog.i(TAG, "adb start wireless adb");
                    mThread = new WirelessDebuggingThread();
                    mThread.start();

                    break;
                case MSG_ADBDWIFI_DISABLE:
                    if (!mAdbWirelessEnabled) {
                        break;
                    }

                    mAdbWirelessEnabled = false;
                    SystemProperties.set(WIRELESS_DEBUG_PERSISTENT_CONFIG_PROPERTY,
                            Boolean.toString(mAdbWirelessEnabled));

                    if (mThread != null) {
                        mThread.sendMessage(MSG_DISABLE_ADBDWIFI.getBytes());
                        mThread.stopListening();
                        mThread = null;
                    }
                    mPairedDevices.clear();
                    mPairingDevices.clear();
                    mConnectedDevices.clear();
                    showAdbConnectedNotification(false);
                    break;
                case MSG_QUERY_PAIRED_DEVICES:
                    sendPairedDevicesToUI(mPairedDevices);
                    break;
                case MSG_QUERY_PAIRING_DEVICES:
                    // TODO: dead code ATM. We don't support picking a device from a list to pair
                    // with.
                    sendPairingDevicesToUI(mPairingDevices);
                    break;
                case MSG_REQ_UNPAIR: {
                    // Tell adbd to disconnect the device if connected.
                    String guid = (String) msg.obj;
                    native_keystore_remove_guid(guid);
                    // Send the updated paired devices list to the UI.
                    refreshPairedDevicesList(mPairedDevices);
                    sendPairedDevicesToUI(mPairedDevices);
                    String cmdStr = MSG_DISCONNECT_DEVICE + guid;
                    if (mThread != null) {
                        mThread.sendMessage(cmdStr.getBytes());
                    }
                    break;
                }
                case MSG_RESPONSE_PAIRING_RESULT: {
                    Bundle bundle = (Bundle) msg.obj;
                    String guid = bundle.getString("guid");
                    String name = bundle.getString("name");
                    onPairingResult(guid, name);
                    // Send the updated paired devices list to the UI.
                    refreshPairedDevicesList(mPairedDevices);
                    sendPairedDevicesToUI(mPairedDevices);
                    break;
                }
                case MSG_QUERY_SET_DEVICE_NAME:
                    mDeviceName = new String((byte[]) msg.obj);
                    if (mThread != null) {
                        String cmdStr = "SN" + mDeviceName;
                        mThread.sendMessage(cmdStr.getBytes());
                    }
                    break;
                case MSG_QUERY_DISCOVER_ENABLE: {
                    String pairingCode = createPairingCode(PAIRING_CODE_LENGTH);
                    updateUIPairCode(pairingCode);
                    mPairingThread = new PairingThread(pairingCode);
                    mPairingThread.start();
                    break;
                }
                case MSG_REQ_CANCEL_PAIRING:
                case MSG_QUERY_DISCOVER_DISABLE:
                    if (mPairingThread != null) {
                        mPairingThread.cancelPairing();
                        try {
                            mPairingThread.join();
                        } catch (InterruptedException e) {
                            Slog.w(TAG, "Error while waiting for pairing thread to quit.");
                            e.printStackTrace();
                        }
                    }
                    break;
                case MSG_DEVICE_CONNECTED: {
                    String guid = new String((byte[]) msg.obj);
                    if (mConnectedDevices.add(guid)) {
                        refreshPairedDevicesList(mPairedDevices);
                        sendPairedDevicesToUI(mPairedDevices);
                        showAdbConnectedNotification(true);
                    }
                    break;
                }
                case MSG_DEVICE_DISCONNECTED: {
                    String guid = new String((byte[]) msg.obj);
                    if (mConnectedDevices.remove(guid)) {
                        refreshPairedDevicesList(mPairedDevices);
                        sendPairedDevicesToUI(mPairedDevices);
                        if (mConnectedDevices.isEmpty()) {
                            showAdbConnectedNotification(false);
                        }
                    }
                    break;
                }
            }
        }

        private static final String ALPHA_NUMERICS = "0123456789";
        // Generates a random string of size |size|.
        private String createPairingCode(int size) {
            String res = "";
            SecureRandom rand = new SecureRandom();
            byte[] data = new byte[size];
            rand.nextBytes(data);
            for (byte a : data) {
                int idx = Byte.toUnsignedInt(a) % ALPHA_NUMERICS.length();
                res += ALPHA_NUMERICS.charAt(idx);
            }

            return res;
        }

        private void onPairingResult(String guid, String name) {
            if (guid == null || name == null) {
                Intent intent = new Intent(AdbManager.WIRELESS_DEBUG_PAIRING_RESULT_ACTION);
                intent.putExtra(AdbManager.WIRELESS_STATUS_EXTRA, AdbManager.WIRELESS_STATUS_FAIL);
                mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
            } else {
                Intent intent = new Intent(AdbManager.WIRELESS_DEBUG_PAIRING_RESULT_ACTION);
                intent.putExtra(AdbManager.WIRELESS_STATUS_EXTRA,
                        AdbManager.WIRELESS_STATUS_SUCCESS);
                PairDevice device = new PairDevice(guid, name, false);
                intent.putExtra(AdbManager.WIRELESS_PAIR_DEVICE_EXTRA, device);
                mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
            }
        }

        private void sendPairedDevicesToUI(Map<String, PairDevice> devices) {
            sendDevicesToUI(devices, true);
        }

        private void sendPairingDevicesToUI(Map<String, PairDevice> devices) {
            sendDevicesToUI(devices, false);
        }

        private void sendDevicesToUI(Map<String, PairDevice> devices, boolean isPairedDevices) {
            Intent intent = new Intent(isPairedDevices
                    ? AdbManager.WIRELESS_DEBUG_PAIRED_DEVICES_ACTION
                    : AdbManager.WIRELESS_DEBUG_PAIRING_DEVICES_ACTION);
            // Map is not serializable, so need to downcast
            intent.putExtra(AdbManager.WIRELESS_DEVICES_EXTRA, (HashMap) devices);
            mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
        }

        private void updateUIPairCode(String code) {
            if (DEBUG) Slog.i(TAG, "updateUIPairCode: " + code);

            Intent intent = new Intent(AdbManager.WIRELESS_DEBUG_PAIRING_RESULT_ACTION);
            intent.putExtra(AdbManager.WIRELESS_PAIRING_CODE_EXTRA, code);
            intent.putExtra(AdbManager.WIRELESS_STATUS_EXTRA,
                    AdbManager.WIRELESS_STATUS_PAIRING_CODE);
            mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
        }

        private void refreshPairedDevicesList(Map<String, PairDevice> pairedDevices) {
            pairedDevices.clear();

            try {
                // Read the keystore data from file.
                byte[] data = Files.readAllBytes(Paths.get(ADBDWIFI_KEYS));
                if (data == null || data.length == 0) {
                    Slog.i(TAG, ADBDWIFI_KEYS + " doesn't exist or is empty");
                    return;
                }

                // Parse the keystore proto data and put it in pairedDevices
                KeyStore keyStore = KeyStore.parseFrom(data);
                List<Key> keys = keyStore.getKeysList();
                keys.forEach(key -> {
                    pairedDevices.put(key.getGuid(),
                                      new PairDevice(key.getName(),
                                                     key.getGuid(),
                                                     mConnectedDevices.contains(key.getGuid())));
                });
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
                Slog.i(TAG, "Failed to parse " + ADBDWIFI_KEYS);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * check and enable wireless debugging when reboot
     */
    public void bootCompleted() {
        boolean enable = SystemProperties.getBoolean(
                WIRELESS_DEBUG_PERSISTENT_CONFIG_PROPERTY, false);
        mHandler.sendEmptyMessage(WirelessDebuggingHandler.MSG_BOOT_COMPLETED);
        enableAdbWireless(enable);
        if (DEBUG) Slog.i(TAG, "enable adb wireless on boot completion");
    }

    /**
     * When {@code enabled} is {@code true}, this allows wireless debugging and starts the
     * ADB hanler thread. When {@code enabled} is {@code false}, this disallows wireless
     * debugging and shuts down the handler thread.
     */
    public void enableAdbWireless(boolean enable) {
        if (enable) {
            mHandler.sendEmptyMessage(WirelessDebuggingHandler.MSG_ADBDWIFI_ENABLE);
        } else {
            mHandler.sendEmptyMessage(WirelessDebuggingHandler.MSG_ADBDWIFI_DISABLE);
        }
    }

    /**
     * UI queries the paired devices.
     */
    public void queryPairedDevices() {
        mHandler.sendEmptyMessage(WirelessDebuggingHandler.MSG_QUERY_PAIRED_DEVICES);
    }

    /**
     * UI queries the pairing devices.
     */
    public void queryPairingDevices() {
        mHandler.sendEmptyMessage(WirelessDebuggingHandler.MSG_QUERY_PAIRING_DEVICES);
    }

    /**
     * Pair with device
     */
    public void pairDevice(int pairMode, String guid, String code) {
        // TODO: NOT IMPLEMENTED. Are we going to allow to pick a specific device to pair with?
    }

    /**
     * Unpair with device
     */
    public void unpairDevice(String guid) {
        Message message = Message.obtain(mHandler,
                                         WirelessDebuggingHandler.MSG_REQ_UNPAIR,
                                         guid);
        mHandler.sendMessage(message);
    }

    /**
     * Cancel pairing with device
     */
    public void cancelPairing(int pairMode, String guid) {
        Bundle bundle = new Bundle();
        bundle.putInt("pairMode", pairMode);
        bundle.putString("guid", guid);
        Message message = Message.obtain(mHandler,
                                         WirelessDebuggingHandler.MSG_REQ_CANCEL_PAIRING,
                                         bundle);
        mHandler.sendMessage(message);
    }

    /**
     * Status enabled/disabled check
     */
    public boolean isEnabled() {
        return mAdbWirelessEnabled;
    }

    /**
     * Get the device name
     */
    public String getName() {
        return mDeviceName;
    }

    /**
     * Set the device name
     */
    public void setName(String name) {
        Message message = Message.obtain(mHandler,
                                         WirelessDebuggingHandler.MSG_QUERY_SET_DEVICE_NAME,
                                         0, 0, name);
        mHandler.sendMessage(message);
    }

    /**
     * set adbd in discover state
     */
    public void setDiscoverable(int pairMode, boolean enable) {
        if (enable) {
            Message message = Message.obtain(mHandler,
                                             WirelessDebuggingHandler
                                             .MSG_QUERY_DISCOVER_ENABLE,
                                             pairMode, 0);
            mHandler.sendMessage(message);
        } else {
            Message message = Message.obtain(mHandler,
                                             WirelessDebuggingHandler
                                             .MSG_QUERY_DISCOVER_DISABLE,
                                             pairMode, 0);
            mHandler.sendMessage(message);
        }
    }
}
