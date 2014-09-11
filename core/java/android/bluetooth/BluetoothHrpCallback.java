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

import java.util.ArrayList;

/**
 * This abstract class is used to implement {@link BluetoothHrp} callbacks.
 */
public abstract class BluetoothHrpCallback {

    /**
     * Callback indicating when HR collector has connected/disconnected to/from
     * a remote HR sensor.
     *
     * @param status Status of the connect or disconnect operation.
     *               {@link BluetoothGatt#GATT_SUCCESS} if the operation succeeds.
     * @param newState Returns the new connection state. Can be one of
                       {@link BluetoothProfile#STATE_DISCONNECTED} or
                       {@link BluetoothProfile#STATE_CONNECTED}
     */
    public void onConnectionStateChanged(int status, int newState) {
    }

    /**
     * Callback triggered as a result of a remote Heart Rate Measurement
     * notification. energyExpended and rrInterval are optional. If they are
     * not supported, the values are -1.
     *
     * @param heartRate Returns the heart rate value. Unit: beats per minute
     * @param energyExpended Returns the expended energy value. Unit: kilo joule
     * @param rrInterval Returns the RR-interval values. Unit: second
     * @param isSensorContactSupported Returns if the sensor contact feature is
     *        supported by the server or not.
     * @param isSensorContactDetected if isSensorContactSupported is true, returns
     *        if the device detects contact with the skin or not.
     */
    public void onHeartRateChanged(int heartRate, int energyExpended,
                                   ArrayList<Integer> rrInterval, boolean isSensorContactSupported,
                                   boolean isSensorContactDetected) {
    }

    /**
     * Callback indicating the result of Energy Expended reset operation.
     *
     * @param status The result of the reset operation
     *               {@link BluetoothGatt#GATT_SUCCESS} if the operation succeeds.
     */
    public void onEnergyExpendedReset(int status) {
    }
}
