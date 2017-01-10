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

package android.bluetooth.le;

import android.bluetooth.IBluetoothGatt;
import android.bluetooth.le.IExtendedAdvertiserCallback;
import android.os.RemoteException;
import android.util.Log;

/**
 * This class provides a way to control single Bluetooth LE advertising
 * instance.
 * <p>
 * To get an instance of {@link AdvertisingSet}, call the
 * {@link BluetoothLeAdvertiser#newAdvertisingSet} method.
 * <p>
 * <b>Note:</b> Most of the methods here require {@link
 * android.Manifest.permission#BLUETOOTH_ADMIN}
 * permission.
 *
 * @see AdvertiseData
 */
public final class AdvertisingSet {
  private static final String TAG = "AdvertisingSet";

  IBluetoothGatt gatt;
  AdvertisingSetCallbackWrapper callbackWrapper;
  int advertiserId;

  /* package */ AdvertisingSet(IBluetoothGatt gatt,
                               AdvertisingSetParameters parameters,
                               AdvertiseData advertiseData,
                               AdvertiseData scanResponse,
                               PeriodicAdvertisingParameters periodicParameters,
                               AdvertiseData periodicData,
                               AdvertisingSetCallback callback) {
    this.gatt = gatt;
  }

  /* package */ void setAdvertiserId(int advertiserId) {
    this.advertiserId = advertiserId;
  }

  /**
   * Start Advertising. This method returns immediately, the operation status is
   * delivered
   * through {@code callback.onAdvertisingStarted()}.
   * <p>
   * Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN}
   *
   */
  public void startAdvertising() {
  }

  /**
   * Stop Advertising. This method returns immediately, the operation status is
   * delivered
   * through {@code callback.onAdvertisingStopped()}. Advertising can be
   * re-started with
   * {@link BluetoothLeAdvertiser#startAdvertising}. If this AdvertisingSet is
   * no longer
   * needed, delete it by calling the {@link
   * BluetoothLeAdvertiser#deleteAdvertisingSet}
   * If periodic advertising is started for this set, it will not be stopped.
   * <p>
   * Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN} permission.
   *
   */
  public void stopAdvertising() {
  }

  /**
   * Set/update data being Advertised. Make sure that data doesn't exceed the
   * size limit
   * for specified AdvertisingSetParameters. This method returns immediately,
   * the
   * operation status is delivered through {@code
   * callback.onAdvertisingDataSet()}.
   * <p>
   * Advertising data must be empty if non-legacy scannable advertising is used.
   */
  public void setAdvertisingData(AdvertiseData data) {
  }

  /**
   * Set/update scan response data. Make sure that data doesn't exceed the size
   * limit
   * for specified AdvertisingSetParameters. This method returns immediately,
   * the
   * operation status is delivered through {@code
   * callback.onScanResponseDataSet()}.
   */
  public void setScanResponseData(AdvertiseData data) {
  }

  /**
   * Update advertising parameters associated with this AdvertisingSet. Must be
   * called
   * when advertising is not active. This method returns immediately, the
   * operation
   * status is delivered through {@code
   * callback.onAdvertisingParametersUpdated}.
   */
  public void setAdvertisingParameters(AdvertisingSetParameters parameters) {
  }

  /**
   * Update periodic advertising parameters associated with this set. Must be
   * called
   * when periodic advertising is not enabled. This method returns immediately,
   * the operation
   * status is delivered through {@code
   * callback.onPeriodicAdvertisingParametersUpdated()}.
   */
  public void
  setPeriodicAdvertisingParameters(PeriodicAdvertisingParameters parameters) {
  }

  /**
   * Used to set periodic advertising data, must be called after
   * setPeriodicAdvertisingParameters, or after advertising was started with
   * periodic advertising
   * data set. This method returns immediately, the operation status is
   * delivered through
   * {@code callback.onPeriodicAdvertisingDataSet()}.
   */
  public void setPeriodicAdvertisingData(AdvertiseData data) {
  }

  /**
   * Used to enable/disable periodic advertising. This method returns
   * immediately,
   * the operation status is delivered through {@code
   * callback.onPeriodicAdvertisingEnable()}.
   *
   */
  public void periodicAdvertisingEnable(boolean enable) {
  }

  private class AdvertisingSetCallbackWrapper
      extends IExtendedAdvertiserCallback.Stub {
    AdvertisingSetCallback callback;
    AdvertisingSetCallbackWrapper(AdvertisingSetCallback callback) {
      this.callback = callback;
    }

    @Override
    public void onAdvertiserRegistered(int status, int advertiserId) {
      AdvertisingSet.this.setAdvertiserId(advertiserId);
      callback.onNewAdvertisingSet(AdvertisingSet.this, status);
    }
  }
}