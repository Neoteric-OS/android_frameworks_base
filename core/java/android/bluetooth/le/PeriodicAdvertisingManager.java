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

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.IBluetoothManager;

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
   * The {@code scanResult} used must contain a valid advertisingSid.
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
    // TODO(jpawlowski): implement
  }

  /**
   * Cancel pending attempt to create synchronization. Use it only if you
   * haven't received the {@link PeriodicAdvertisingCallback#onSyncEstablished}
   * callback.
   * After that use {@link PeriodicAdvertisingManager#terminateSync}
   *
   * @param callback Callback used to deliver all operations status.
   */
  public void cancelCreateSync(PeriodicAdvertisingCallback callback) {
    // TODO(jpawlowski): implement
  }

  /**
   * Terminate the periodic advertising synchronization
   *
   * @param syncHandle handle used to identify this synchronization, obtained in
   * {@link PeriodicAdvertisingCallback#onSyncEstablished} callback
   */
  public void terminateSync(int syncHandle) {
    // TODO(jpawlowski): implement
  }
}
