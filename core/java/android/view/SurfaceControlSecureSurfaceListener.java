/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.view;

import android.os.IBinder;
import android.util.ArrayMap;

import libcore.util.NativeAllocationRegistry;

import java.util.Objects;

/**
 * Allows for the monitoring of secure surfaces per display.
 *
 * @hide
 */
public abstract class SurfaceControlSecureSurfaceListener {
    private static final NativeAllocationRegistry sRegistry =
            NativeAllocationRegistry.createMalloced(
                    SurfaceControlSecureSurfaceListener.class.getClassLoader(), nGetDestructor());

    /**
     * Callback when the secure surface state on the given display has changed.
     *
     * @param displayToken The display this callback is about
     * @param hasSecureSurface Whether a secure surface is currently shown on the display
     * @hide
     */
    public abstract void onSecureSurfaceChanged(IBinder displayToken, boolean hasSecureSurface);

    /**
     * Registers this as a secure surface listener on the provided display.
     *
     * @param displayToken The token of the display to monitor
     */
    public void register(IBinder displayToken) {
        Objects.requireNonNull(displayToken);
        synchronized (this) {
            if (mRegisteredListeners.containsKey(displayToken)) {
                return;
            }
            long nativePtr = nRegister(displayToken);
            Runnable destructor = sRegistry.registerNativeAllocation(this, nativePtr);
            mRegisteredListeners.put(displayToken, destructor);
        }
    }

    /**
     * Unregisters this as a secure surface listener on the provided display.
     *
     * @param displayToken The token of the display
     */
    public void unregister(IBinder displayToken) {
        Objects.requireNonNull(displayToken);
        final Runnable destructor;
        synchronized (this) {
            destructor = mRegisteredListeners.remove(displayToken);
        }
        if (destructor != null) {
            destructor.run();
        }
    }

    /**
     * Unregisters this on all previously registered displays.
     */
    public void unregisterAll() {
        final ArrayMap<IBinder, Runnable> toDestroy;
        synchronized (this) {
            toDestroy = mRegisteredListeners;
            mRegisteredListeners = new ArrayMap<>();
        }
        for (Runnable destructor : toDestroy.values()) {
            destructor.run();
        }
    }

    private ArrayMap<IBinder, Runnable> mRegisteredListeners = new ArrayMap<>();

    private static native long nGetDestructor();
    private native long nRegister(IBinder displayToken);
}
