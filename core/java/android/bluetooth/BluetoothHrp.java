/*
 * Copyright (C) 2014 Tieto Corporation
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

package android.bluetooth;

import android.content.Context;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothHrpCallback;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

/**
 * This class provides the public APIs to control the Bluetooth HRP profile.
 * 
 *<p>BluetoothHrp is a wrap object based on {@link BluetoothGatt}.
 */
public final class BluetoothHrp implements BluetoothProfile {
    private static final String TAG = "BluetoothHrp";
    private static final boolean DBG = true;
    private static final boolean VDBG = true;

    private static final UUID HRP_SERVICE = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb");
    private static final UUID DEVICE_INFORMATION_SERVICE = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb");
    private static final UUID HEART_RATE_MEASUREMENT_CHARAC = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb");
    private static final UUID BODY_SENSOR_LOCATION_CHARAC = UUID.fromString("00002A38-0000-1000-8000-00805f9b34fb");
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final UUID HEART_RATE_CONTROL_POINT_CHARAC = UUID.fromString("00002A39-0000-1000-8000-00805f9b34fb");
    private static final UUID SYSTEM_ID_CHARAC = UUID.fromString("00002A23-0000-1000-8000-00805f9b34fb");
    private static final UUID MODEL_NUMBER_CHARAC = UUID.fromString("00002A24-0000-1000-8000-00805f9b34fb");
    private static final UUID SERIAL_NUMBER_CHARAC = UUID.fromString("00002A25-0000-1000-8000-00805f9b34fb");
    private static final UUID HARDWARE_REVISION_CHARAC = UUID.fromString("00002A27-0000-1000-8000-00805f9b34fb");
    private static final UUID SOFTWARE_REVISION_CHARAC = UUID.fromString("00002A28-0000-1000-8000-00805f9b34fb");
    private static final UUID MANUFACTURER_NAME_CHARAC = UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb");
    private static final UUID FIRMWARE_REVISION_CHARAC = UUID.fromString("00002A26-0000-1000-8000-00805f9b34fb");
    private static final UUID ICDL_CHARAC = UUID.fromString("00002A2A-0000-1000-8000-00805f9b34fb");
    private static final UUID PNP_ID_CHARAC = UUID.fromString("00002A50-0000-1000-8000-00805f9b34fb");
    private static final int RESET_ENERGY_EXPENDED = 1;
    private static final int HEART_RATE_FORMAT_BITMASK = 0x01;   
    private static final int SENSOR_CONTACT_STATUS_BITMASK = 0X01 << 1;
    private static final int SENSOR_CONTACT_SUPPORTED_BITMASK = 0X01 << 2;
    private static final int EE_PRESENT_BITMASK = 0X01 << 3;
    private static final int RRI_PRESENT_BITMASK = 0X01 << 4;
    private static final int PNP_ID_SIZE = 7;

    private BluetoothDevice mBluetoothDevice;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothHrpCallback mHrpCallback;
    private int mBodySensorLocation;
    private boolean mIsBodySensorLocationCached;
    private Context mContext;
    private static final String[] sLocations = {"other", "chest", "wrist", "finger", "hand",
            "earlobe", "foot"};

    private final BluetoothGattCallback mGattCallback =
        new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (mBluetoothGatt != null) {
                    mBluetoothGatt.discoverServices();
                }
            }
            if (mHrpCallback != null) {
                mHrpCallback.onConnectionStateChanged(status, newState);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (setNotification(true)) {
                    mBodySensorLocation = 255;
                    mIsBodySensorLocationCached = false;
                    return;
                }
            }
            disconnect();
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                      int status) {
            if (!mIsBodySensorLocationCached) {
                UUID descUuid = descriptor.getUuid();
                if (descUuid.equals(CLIENT_CHARACTERISTIC_CONFIG)) {
                    BluetoothGattService hrpService = mBluetoothGatt.getService(HRP_SERVICE);

                    if (hrpService != null) {
                        BluetoothGattCharacteristic bodySensorLocationCharac =
                                hrpService.getCharacteristic(BODY_SENSOR_LOCATION_CHARAC);

                        if (bodySensorLocationCharac != null) {
                            mBluetoothGatt.readCharacteristic(bodySensorLocationCharac);
                        }
                    }
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic, int status) {
            UUID charUuid = characteristic.getUuid();
            if (charUuid.equals(HEART_RATE_CONTROL_POINT_CHARAC) && mHrpCallback != null) {
                mHrpCallback.onEnergyExpendedReset(status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                UUID charUuid = characteristic.getUuid();

                if (charUuid.equals(BODY_SENSOR_LOCATION_CHARAC)) {
                    mBodySensorLocation = characteristic.getIntValue(
                            BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                    mIsBodySensorLocationCached = true;
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            if (characteristic.getUuid().equals(HEART_RATE_MEASUREMENT_CHARAC)) {
                parseHRMCharac(characteristic);
            }
        }

        private void parseHRMCharac(BluetoothGattCharacteristic characteristic) {
            int flag = 0;
            int length = characteristic.getValue().length;
            int format = -1;
            boolean isEEPresent = false;
            boolean isRRIPresent = false;
            int offset = 0;
            int heartRate = -1;
            int energyExpended = -1;
            boolean isSensorContactSupported = false;
            boolean isSensorContactDetected = false;
            ArrayList<Integer> rrInterval = new ArrayList<Integer>();

            flag = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset);
            offset += 1;

            if ((flag & SENSOR_CONTACT_SUPPORTED_BITMASK) != 0) {
                isSensorContactSupported = true;
                if ((flag & SENSOR_CONTACT_STATUS_BITMASK) != 0) {
                    isSensorContactDetected = true;
                } else {
                    isSensorContactDetected = false;
                }
                
            } else {
                isSensorContactSupported = false;
            }

            if ((flag & HEART_RATE_FORMAT_BITMASK) != 0) {
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
            }
            if ((flag & EE_PRESENT_BITMASK) != 0) {
                isEEPresent = true;
            }
            if ((flag & RRI_PRESENT_BITMASK) != 0) {
                isRRIPresent = true;
            }

            heartRate = characteristic.getIntValue(format, offset);
            if (format == BluetoothGattCharacteristic.FORMAT_UINT8) {
                offset += 1;
            }
            if (format == BluetoothGattCharacteristic.FORMAT_UINT16) {
                offset += 2;
            }

            if (isEEPresent == true) {
                energyExpended = characteristic.getIntValue(
                        BluetoothGattCharacteristic.FORMAT_UINT16, offset);
                offset += 2;
            }

            if (isRRIPresent == true) {
                for (int i = offset; i < length; i += 2) {
                    rrInterval.add(characteristic.getIntValue(
                            BluetoothGattCharacteristic.FORMAT_UINT16, i));
                }
            }

            if (mHrpCallback != null) {
                mHrpCallback.onHeartRateChanged(heartRate, energyExpended, rrInterval, 
                        isSensorContactSupported, isSensorContactDetected);
            }
        }
    };

    /**
     * Create a new BluetoothHrp
     *
     * @param callback the handler that will receive asynchronous callbacks.
     */
    public BluetoothHrp(Context context, BluetoothHrpCallback callback) {
        mHrpCallback = callback;
        mContext = context;
    }

    /**
     * Connect to a remote heart rate sensor.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @param device The remote heart rate sensor to connect to.
     * @param autoConnect Whether to directly connect to the remote device (false)
     *                    or to automatically connect as soon as the remote
     *                    device becomes available (true).
     *
     * @return true, if the connection attempt was initiated successfully.
     */
    public boolean connect(BluetoothDevice device, boolean autoConnect) {
        if (device == null) {
            return false;
        }

        if (mBluetoothDevice != null && device.equals(mBluetoothDevice) &&
                mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                return true;
            } else {
                return false;
            }
        }

        mBluetoothGatt = device.connectGatt(mContext, autoConnect, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDevice = device;
        return true;
    }

    /**
     * Disconnect an established connection, or cancel a connection attemp
     * currently in progress.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     */
    public void disconnect() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * Enable or disable notification for Heart Rate Measurement.
     *
     * <p>Once notification is enabled, a
     * {@link BluetoothHrpCallback#onHeartRateChanged} callback will be
     * triggered if the heart rate sensor indicates taht Heart Rate Measurement
     * has changed.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @param enable Set to true to enable notification.
     * @return true, if the notification status was set successfully.
     */
    public boolean setNotification(boolean enable) {
        if (mBluetoothGatt == null) {
            return false;
        }

        BluetoothGattService hrpService = mBluetoothGatt.getService(HRP_SERVICE);
        if (hrpService == null) {
            return false;
        }

        BluetoothGattCharacteristic hrpCharac =
                hrpService.getCharacteristic(HEART_RATE_MEASUREMENT_CHARAC);
        if (hrpCharac == null) {
            return false;
        }

        if (!mBluetoothGatt.setCharacteristicNotification(hrpCharac, enable)) {
            return false;
        }

        BluetoothGattDescriptor cccDescrip = hrpCharac.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
        if (cccDescrip == null)
            return false;

        if (enable) {
            cccDescrip.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        } else {
            cccDescrip.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        }

        return mBluetoothGatt.writeDescriptor(cccDescrip);
    }

    /**
     * Get Body Sensor Location.
     *
     * @return the location string. If location is no supported, string is null.
     */
    public String getBodySensorLocation() {
        return location2Str(mBodySensorLocation);
    }

    private String location2Str(int location) {
        if (location < sLocations.length) {
            return sLocations[location];
        }
        return null;
    }

    /**
     * Reset Energy Expended in the associated remote Heart Rate Sensor.
     * 
     * <p>Once the reset operation has been completed, the
     * {@link BluetoothHrpCallback#onEnergyExpendedReset} callback is invoked
     * reporting the result of the operation.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @return true, if the reset operation was initiated successfully.
     */
    public boolean resetEnergyExpended() {
        if (mBluetoothGatt == null) {
            return false;
        }

        BluetoothGattService hrpService = mBluetoothGatt.getService(HRP_SERVICE);
        if (hrpService == null) {
            return false;
        }

        BluetoothGattCharacteristic hrcpCharac =
                hrpService.getCharacteristic(HEART_RATE_CONTROL_POINT_CHARAC);
        if (hrcpCharac == null) {
            return false;
        }

        byte[] value = new byte[1];
        value[0] = (byte) RESET_ENERGY_EXPENDED;
        hrcpCharac.setValue(value);
        return mBluetoothGatt.writeCharacteristic(hrcpCharac);
    }

    /**
     * Close HRP client. Application should call this method as early as possible
     * after it is done with this client.
     *
     * @return true, if HRP client was closed successfully.
     */
    public boolean close() {
        if (mBluetoothGatt == null) {
            return false;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
        mBluetoothDevice = null;

        return true;
    }

    /**
     * Not supported - please use {@link BluetoothManager#getConnectedDevices(int)}
     * with {@link BluetoothProfile#GATT} as argument
     *
     * @throws UnsupportedOperationException
     */
    @Override
    public List<BluetoothDevice> getConnectedDevices() {
        throw new UnsupportedOperationException
                ("Use BluetoothManager#getConnectedDevices instead.");
    }

    /**
     * Not supported - please use
     * {@link BluetoothManager#getDevicesMatchingConnectionStates(int, int[])}
     * with {@link BluetoothProfile#GATT} as first argument
     *
     * @throws UnsupportedOperationException
     */
    @Override
    public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        throw new UnsupportedOperationException
                ("Use BluetoothManager#getDevicesMatchingConnectionStates instead.");
    }

    /**
     * Not supported - please use {@link BluetoothManager#getConnectedDevices(int)}
     * with {@link BluetoothProfile#GATT} as argument
     *
     * @throws UnsupportedOperationException
     */
    @Override
    public int getConnectionState(BluetoothDevice device) {
        throw new UnsupportedOperationException
                ("Use BluetoothManager#getConnectionState instead.");
    }
}
