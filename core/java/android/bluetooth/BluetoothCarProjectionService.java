/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;

/**
 * The system application service intending to handle incoming Bluetooth RFCOMM connections for car
 * projection should implement this class.
 *
 * @hide
 */
@SystemApi
public abstract class BluetoothCarProjectionService extends Service {

    @Override
    @Nullable
    public IBinder onBind(@NonNull Intent intent) {
        return new BluetoothCarProjectionBinder();
    }

    /** Handles incoming bluetooth socket connections intended for car projection. */
    protected abstract void onSocketReceived(
            @NonNull ParcelFileDescriptor fd, @NonNull String hostAddress);

    private final class BluetoothCarProjectionBinder extends IBluetoothCarProjection.Stub {
        @Override
        public void sendSocketFileDescriptor(ParcelFileDescriptor fd, String hostAddress) {
            onSocketReceived(fd, hostAddress);
        }
    }
}
