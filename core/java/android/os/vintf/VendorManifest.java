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

package android.os.vintf;

import java.util.List;
import libcore.util.NativeAllocationRegistry;

/** @hide */
public final class VendorManifest {
    private static final String TAG = "VendorManifest";

    public static final Version MANIFEST_VERSION = new Version(
            getManifestVersionMinor(), getManifestVersionMajor());

    private static final NativeAllocationRegistry sNativeRegistry;

    private static VendorManifest sInstance = null;


    private VendorManifest() {
        native_setup();

        sNativeRegistry.registerNativeAllocation(
                this,
                mNativeContext);
    }

    /**
     * Get global instance. Is NOT thread safe. If error return null.
     */
    public static VendorManifest getInstance() {
        if (sInstance == null) {
            VendorManifest vm = new VendorManifest();
            if (vm.associateGlobalNativeInstance()) {
                sInstance = vm;
            }
        }
        return sInstance;
    }

    public Transport getTransport(String name) {
        return Transport.fromValue(getTransportNative(name));
    }

    public native List<Version> getSupportedVersions(String name);

    // Returns address of the "freeFunction".
    private static native final long native_init();
    private native final void native_setup();

    // return manifest.version, aka VendorManifest::kVersion.
    private static native long getManifestVersionMajor();
    private static native long getManifestVersionMinor();

    // associate with the native VendorManifest::Get instance.
    private native boolean associateGlobalNativeInstance();

    private native long getTransportNative(String name);

    static {
        long freeFunction = native_init();

        sNativeRegistry = new NativeAllocationRegistry(
                VendorManifest.class.getClassLoader(),
                freeFunction,
                128 /* size */);
    }

    private long mNativeContext;
}
