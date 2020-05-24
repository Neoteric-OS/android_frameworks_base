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

package android.os;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.annotation.SystemService;
import android.content.Context;
import android.os.IGestureLauncher;
import android.os.RemoteException;

/**
 * Allow accessing gesture-based features from
 * {@link com.android.server.GestureLauncherService} to handle launching camera
 * on sensor motion or key events.
 *
 * @hide
 */
@SystemService(Context.GESTURE_LAUNCHER_SERVICE)
public class GestureLauncherManager {
    private final IGestureLauncher mService;

    /** @hide */
    public GestureLauncherManager(IGestureLauncher service) {
        mService = checkNotNull(service, "missing IGestureLauncher");
    }

    public boolean isCameraButtonLaunchSettingEnabled() {
        try {
            return mService.isCameraButtonLaunchSettingEnabled();
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    /**
     * @param source launch source, see {@link android.app.StatusBarManager}
     *        for possible CAMERA_LAUNCH_SOURCE_* values.
     *
     * @return true if camera was launched, false otherwise.
     */
    public boolean handleCameraGesture(int source) {
        try {
            return mService.handleCameraGesture(source);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }
}
