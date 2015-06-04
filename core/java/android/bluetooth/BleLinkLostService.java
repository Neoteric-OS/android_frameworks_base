/*
 * Copyright (C) 2015 Tieto Corporation
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
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.IBinder;
import android.util.Log;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

/*
 * this class can help to read/write LLS character
 */

public class BleLinkLostService {
	private static final String TAG = "BleLinkLostService";

	public static int No_Alert = 0, Mid_Alert = 1, High_Alert = 2;
	public static final UUID LINK_LOSS_SERVICE_UUID = UUID
			.fromString("00001803-0000-1000-8000-00805f9b34fb");
	public static final UUID ALERT_LEVEL_CHARAC = UUID
			.fromString("00002A06-0000-1000-8000-00805f9b34fb");

	public static int parseCharac(BluetoothGattCharacteristic charac) {
		int alertLevel;
		alertLevel = charac.getIntValue(
				BluetoothGattCharacteristic.FORMAT_UINT8, 0);
		if (alertLevel > High_Alert) {
			return -1;
		}

		return alertLevel;
	}

	/**
	 * set remote device character
	 * 
	 * {@link BluetoothGattCallback#onCharacteristicWrite will be invoked}
	 * 
	 * @param gatt
	 *            Bluetooth GATT.
	 * @param alertLevel
	 *            the character set to remote device.
	 */
	public void characteristicWrite(BluetoothGatt gatt, int alertLevel) {
		if (null == gatt) {
			return;
		}
		BluetoothGattService llcService = gatt
				.getService(LINK_LOSS_SERVICE_UUID);
		if (null == llcService) {
			return;
		}
		BluetoothGattCharacteristic charac = llcService
				.getCharacteristic(ALERT_LEVEL_CHARAC);
		if (null == charac) {
			return;
		}
		UUID charUid = charac.getUuid();
		if (charUid.equals(ALERT_LEVEL_CHARAC)) {
			byte[] value = new byte[1];
			value[0] = (byte) alertLevel;
			charac.setValue(value);
			if (!gatt.writeCharacteristic(charac)) {
				return;
			}
		}
	}

	/**
	 * read remote device character
	 * 
	 * @param gatt
	 *            Bluetooth GATT.
	 * @return alertLevel from remote device, if failed -1 will be returned
	 */
	public int characteristicRead(BluetoothGatt gatt) {
		if (null == gatt) {
			return -1;
		}
		BluetoothGattService llcService = gatt
				.getService(LINK_LOSS_SERVICE_UUID);
		if (null == llcService) {
			return -1;
		}
		BluetoothGattCharacteristic charac = llcService
				.getCharacteristic(ALERT_LEVEL_CHARAC);
		if (null == charac) {
			return -1;
		}
		int alertLevel = -1;
		alertLevel = parseCharac(charac);
		return alertLevel;
	}
}
