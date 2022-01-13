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
 * Applications which wish to have the system listen for incoming Bluetooth socket connections on a
 * given Bluetooth service record should implement this {@link Service}. This {@link Service} will
 * receive incoming connections from the framework managed RFCOMM listener.
 *
 * @hide
 */
@SystemApi
public abstract class BluetoothRfcommHandoffService extends Service {

    @Override
    @Nullable
    public IBinder onBind(@NonNull Intent intent) {
        return new BluetoothRfcommHandoffBinder();
    }

    /**
     * Handles incoming bluetooth socket connections on the service record registered by the app.
     */
    protected abstract void onSocketReceived(
            @NonNull ParcelFileDescriptor fd, @NonNull String hostAddress);

    /**
     * Invoked when the RFCOMM listener is unexpected closed and the system was unable to restart
     * it.
     */
    protected abstract void onListenerError();

    private final class BluetoothRfcommHandoffBinder extends IBluetoothRfcommHandoff.Stub {
        @Override
        public void sendSocketFileDescriptor(ParcelFileDescriptor fd, String hostAddress) {
            onSocketReceived(fd, hostAddress);
        }

        @Override
        public void indicateListenerError() {
            onListenerError();
        }
    }
}
