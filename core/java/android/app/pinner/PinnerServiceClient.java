/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.app.pinner;

import static android.app.Flags.FLAG_PINNER_SERVICE_CLIENT_API;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.app.pinner.IPinnerService;
import android.app.pinner.PinnedFileStat;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Slog;

import java.util.ArrayList;
import java.util.List;

/**
 * Expose PinnerService as an interface to apps.
 * @hide
 */
@FlaggedApi(FLAG_PINNER_SERVICE_CLIENT_API)
public class PinnerServiceClient {
    private static String TAG = "PinnerServiceClient";
    private IPinnerService pinnerService;

    /**
     * @hide
     */
    @FlaggedApi(FLAG_PINNER_SERVICE_CLIENT_API)
    public PinnerServiceClient() {
        IBinder binder = ServiceManager.getService("pinner");
        if (binder == null) {
            Slog.w(TAG,
                    "Failed to retrieve PinnerService. A common failure reason is due to a lack of selinux permissions.");
        } else {
            pinnerService = IPinnerService.Stub.asInterface(binder);
            if (pinnerService == null) {
                Slog.w(TAG, "Failed to cast PinnerService.");
            }
        }
    }

    /**
     * Obtain the pinned file stats used for testing infrastructure.
     * @return List of pinned files or an empty list if failed to retrieve them.
     * @throws RuntimeException on failure to retrieve stats.
     * @hide
     */
    @FlaggedApi(FLAG_PINNER_SERVICE_CLIENT_API)
    public @NonNull List<PinnedFileStat> getPinnerStats() {
        if (pinnerService != null) {
            List<PinnedFileStat> stats;
            try {
                stats = pinnerService.getPinnerStats();
            } catch (RemoteException e) {
                throw new RuntimeException("Failed to retrieve stats from PinnerService");
            }
            return stats;
        } else {
            Slog.e(TAG, "no PinnerService, just return default value");
            return new ArrayList<>();
        }
    }

    /**
     * pin the specified App
     * @param key App keyID, defined in PinnerService AppKey{KEY_CAMERA, KEY_HOME, KEY_ASSISTANT}
     * @hide
     */
    @FlaggedApi(FLAG_PINNER_SERVICE_CLIENT_API)
    public void pinApp(int key) {
        if(pinnerService != null) {
            try {
                pinnerService.pinApp(key);
            } catch (RemoteException e) {
                throw new RuntimeException("Failed to retrieve pinApp from PinnerService");
            }
        }
    }

    /**
     * unpin the specified App
     * @param key App keyID, defined in PinnerService AppKey{KEY_CAMERA, KEY_HOME, KEY_ASSISTANT}
     * @hide
     */
    @FlaggedApi(FLAG_PINNER_SERVICE_CLIENT_API)
    public void unpinApp(int key) {
        if(pinnerService != null) {
            try {
                pinnerService.unpinApp(key);
            } catch (RemoteException e) {
                throw new RuntimeException("Failed to retrieve unpinApp from PinnerService");
            }
        }
    }

    /**
     * pin the specified file
     * @param fileName need pin file's url and name
     * @hide
     */
    @FlaggedApi(FLAG_PINNER_SERVICE_CLIENT_API)
    public void pinFile(@NonNull String fileName) {
        if(pinnerService != null) {
            try {
                pinnerService.pinFile(fileName);
            } catch (RemoteException e) {
                throw new RuntimeException("Failed to retrieve pinFile from PinnerService");
            }
        }
    }

    /**
     * unpin the specified file
     * @param fileName choice from getPinnerStats return value
     * @hide
     */
    @FlaggedApi(FLAG_PINNER_SERVICE_CLIENT_API)
    public void unpinFile(@NonNull String fileName) {
        if(pinnerService != null) {
            try {
                pinnerService.unpinFile(fileName);
            } catch (RemoteException e) {
                throw new RuntimeException("Failed to retrieve unpinFile from PinnerService");
            }
        }
    }

    /**
     * pin the specified files defined in config.xml
     * @hide
     */
    @FlaggedApi(FLAG_PINNER_SERVICE_CLIENT_API)
    public void pinFiles() {
        if(pinnerService != null) {
            try {
                pinnerService.pinFiles();
            } catch (RemoteException e) {
                throw new RuntimeException("Failed to retrieve pinFiles from PinnerService");
            }
        }
    }

    /**
     * unpin the specified files defined in config.xml
     * @hide
     */
    @FlaggedApi(FLAG_PINNER_SERVICE_CLIENT_API)
    public void unpinFiles() {
        if(pinnerService != null) {
            try {
                pinnerService.unpinFiles();
            } catch (RemoteException e) {
                throw new RuntimeException("Failed to retrieve unpinFiles from PinnerService");
            }
        }
    }

    /**
     * pin the specified Apps defined in config.xml
     * @hide
     */
    @FlaggedApi(FLAG_PINNER_SERVICE_CLIENT_API)
    public void pinApps() {
        if(pinnerService != null) {
            try {
                pinnerService.pinApps();
            } catch (RemoteException e) {
                throw new RuntimeException("Failed to retrieve pinApps from PinnerService");
            }
        }
    }

    /**
     * unpin the specified Apps defined in config.xml
     * @hide
     */
    @FlaggedApi(FLAG_PINNER_SERVICE_CLIENT_API)
    public void unpinApps() {
        if(pinnerService != null) {
            try {
                pinnerService.unpinApps();
            } catch (RemoteException e) {
                throw new RuntimeException("Failed to retrieve unpinApps from PinnerService");
            }
        }
    }
}
