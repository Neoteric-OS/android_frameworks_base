/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package android.bluetooth.le;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.IBluetoothGatt;
import android.bluetooth.IBluetoothManager;
import android.os.RemoteException;
import android.util.Log;

/**
 * This class provides methods to perform periodic advertising related
 * operations. An application can register for periodic advertisements using
 * {@link PeriodicAdvertisingManager#createSync}.
 * <p>
 * Use {@link BluetoothAdapter#getPeriodicAdvertisingManager()} to get an
 * instance of {@link PeriodicAdvertisingManager}.
 * <p>
 * <b>Note:</b> Most of the methods here require
 * {@link android.Manifest.permission#BLUETOOTH_ADMIN} permission.
 */
public final class PeriodicAdvertisingManager {

  private static final String TAG = "PeriodicAdvertisingManager";

  private static final int SKIP_MIN = 0;
  private static final int SKIP_MAX = 499;
  private static final int TIMEOUT_MIN = 10;
  private static final int TIMEOUT_MAX = 16384;

  private final IBluetoothManager mBluetoothManager;
  private BluetoothAdapter mBluetoothAdapter;

  /**
   * Use {@link BluetoothAdapter#getBluetoothLeScanner()} instead.
   *
   * @param bluetoothManager BluetoothManager that conducts overall Bluetooth Management.
   * @hide
   */
  public PeriodicAdvertisingManager(IBluetoothManager bluetoothManager) {
    mBluetoothManager = bluetoothManager;
    mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
  }

  /**
   * Synchronize with periodic advertising pointed to by the {@code scanResult}.
   * The {@code scanResult} used must contain a valid advertisingSid. First
   * call to createSync will use the {@code skip} and {@code timeout} provided.
   * Subsequent calls from other apps, trying to sync with same set will reuse
   * existing sync, thus {@code skip} and {@code timeout} values will not take
   * effect. The values in effect will be returned in
   * {@link PeriodicAdvertisingCallback#onSyncEstablished}
   *
   * @param scanResult Scan result containing advertisingSid.
   * @param skip The number of periodic advertising packets that can be skipped
   * after a successful receive. Must be between 0 and 499.
   * @param timeout Synchronization timeout for the periodic advertising. One
   * unit is 10ms. Must be between 10 (100ms) and 16384 (163.84s).
   * @param callback Callback used to deliver all operations status.
   * @throws IllegalArgumentException if {@code scanResult} is null or {@code
   * skip} is invalid or {@code timeout} is invalid or {@code callback} is null.
   */
  public void createSync(ScanResult scanResult, int skip, int timeout,
                         PeriodicAdvertisingCallback callback) {
    if (callback == null) {
      throw new IllegalArgumentException("callback can't be null");
    }

    if (scanResult == null) {
      throw new IllegalArgumentException("scanResult can't be null");
    }

    if (scanResult.getAdvertisingSid() == ScanResult.SID_NOT_PRESENT) {
      throw new IllegalArgumentException("scanResult must contain a valid sid");
    } 

    if (skip < SKIP_MIN || skip > SKIP_MAX) {
      throw new IllegalArgumentException("timeout must be between " + TIMEOUT_MIN + " and " + TIMEOUT_MAX);
    } 

    if (timeout < TIMEOUT_MIN || timeout > TIMEOUT_MAX) {
      throw new IllegalArgumentException("timeout must be between " + TIMEOUT_MIN + " and " + TIMEOUT_MAX);
    } 

    IBluetoothGatt gatt;
    try {
        gatt = mBluetoothManager.getBluetoothGatt();
    } catch (RemoteException e) {
        Log.e(TAG, "Failed to get Bluetooth gatt - ", e);
        callback.onSyncEstablished(0, scanResult.getDevice(), scanResult.getAdvertisingSid(),
                                   skip, timeout,  PeriodicAdvertisingCallback.ADVERTISE_FAILED_INTERNAL_ERROR);
        return;
    }

    try {
      gatt.createSync(scanResult, skip, timeout, wrap(callback));
    } catch (RemoteException e) {
      Log.e(TAG, "Failed to create sync - ", e);
      return;
    }

  }

  /**
   * Cancel pending attempt to create synchronization. Use it only if you
   * haven't received the {@link PeriodicAdvertisingCallback#onSyncEstablished}
   * callback.
   * After that use {@link PeriodicAdvertisingManager#terminateSync}
   *
   * @param callback Callback used to deliver all operations status.
   * @throws IllegalArgumentException if {@code callback} is null.
   */
  public void cancelCreateSync(PeriodicAdvertisingCallback callback) {
    if (callback == null) {
      throw new IllegalArgumentException("callback can't be null");
    }

    IBluetoothGatt gatt;
    try {
        gatt = mBluetoothManager.getBluetoothGatt();
    } catch (RemoteException e) {
        Log.e(TAG, "Failed to get Bluetooth gatt - ", e);
        return;
    }

    try {
      gatt.cancelCreateSync(wrap(callback));
    } catch (RemoteException e) {
        Log.e(TAG, "Failed to cancel sync creation - ", e);
        return;
    }
  }

  /**
   * Terminate the periodic advertising synchronization
   *
   * @param syncHandle handle used to identify this synchronization, obtained in
   * {@link PeriodicAdvertisingCallback#onSyncEstablished} callback
   */
  public void terminateSync(int syncHandle) {
    IBluetoothGatt gatt;
    try {
        gatt = mBluetoothManager.getBluetoothGatt();
    } catch (RemoteException e) {
        Log.e(TAG, "Failed to get Bluetooth gatt - ", e);
        return;
    }

    try {
      gatt.terminateSync(syncHandle);
    } catch (RemoteException e) {
        Log.e(TAG, "Failed to terminate sync - ", e);
        return;
    }
  }

  private IPeriodicAdvertisingCallback wrap(PeriodicAdvertisingCallback callback) {
    return new IPeriodicAdvertisingCallback.Stub() {
      public void onSyncEstablished(int syncHandle, BluetoothDevice device,
                                    int advertisingSid, int skip, int timeout, int status) {
        callback.onSyncEstablished(syncHandle, device, advertisingSid, skip, timeout, status);
      }

      public void periodicAdvertisingReport(PeriodicAdvertisingReport report) {
        callback.periodicAdvertisingReport(report);
      }

      public void onSyncLost(int syncHandle) {
        callback.onSyncLost(syncHandle);
      }
    };
  }
}
