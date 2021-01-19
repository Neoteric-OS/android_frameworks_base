/*
 * Copyright (C) 2020 The Android Open Source Project
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
package android.net.vcn;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.annotation.SystemApi.Client;
import android.os.PersistableBundle;

import dalvik.system.PathClassLoader;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * This class represents a control plane configuration for a Virtual Carrier Network connection.
 *
 * <p>A {@link VcnControlPlaneConfig} object is required to build an {@link
 * VcnGatewayConnectionConfig}
 *
 * @see android.net.ipsec.ike.vcn.VcnIkeControlPlaneConfig
 */
public abstract class VcnControlPlaneConfig {
    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({CONFIG_TYPE_IKE})
    public @interface ConfigType {}

    /** @hide */
    @SystemApi(client = Client.MODULE_LIBRARIES)
    public static final int CONFIG_TYPE_IKE = 1;

    private static final String CONFIG_TYPE_KEY = "mConfigType";
    private final int mConfigType;

    private static final String IKE_LIB_PATH =
            "/apex/com.android.ipsec/javalib/android.net.ipsec.ike.jar";

    /**
     * Constructs a VcnControlPlaneConfig object.
     *
     * @param configType the control plane configuration type
     * @hide
     */
    @SystemApi(client = Client.MODULE_LIBRARIES)
    public VcnControlPlaneConfig(@ConfigType int configType) {
        mConfigType = configType;
    }

    /**
     * Constructs a VcnControlPlaneConfig object by deserializing a PersistableBundle.
     *
     * @param in the {@link PersistableBundle} containing an {@link VcnControlPlaneConfig} object
     * @hide
     */
    public static VcnControlPlaneConfig fromPersistableBundle(@NonNull PersistableBundle in) {
        int configType = in.getInt(CONFIG_TYPE_KEY);
        switch (configType) {
            case CONFIG_TYPE_IKE:
                try {
                    // VCN needs to dynamically load VcnControlPlaneIkeConfig because
                    // VcnControlPlaneIkeConfig, as part of in IPsec mainline module, is not
                    // loaded in boot class path as VCN. This dynamic loading and reflection will
                    // work on all Android devices because IPsec module is a mandatory module since
                    // Android R.
                    PathClassLoader classLoader =
                            new PathClassLoader(
                                    IKE_LIB_PATH, VcnControlPlaneConfig.class.getClassLoader());
                    Class<?> vcnIkeClass =
                            Class.forName(
                                    "android.net.ipsec.ike.vcn.VcnControlPlaneIkeConfig",
                                    true,
                                    classLoader);
                    Constructor constructor = vcnIkeClass.getConstructor(PersistableBundle.class);

                    return (VcnControlPlaneConfig) constructor.newInstance(in);
                } catch (ClassNotFoundException
                        | NoSuchMethodException
                        | SecurityException
                        | IllegalAccessException
                        | InstantiationException
                        | InvocationTargetException
                        | ExceptionInInitializerError e) {
                    throw new IllegalStateException(
                            "Failed to load or construct VcnControlPlaneIkeConfig", e);
                }
            default:
                throw new IllegalStateException("Unrecognized configType: " + configType);
        }
    }

    /**
     * Converts this VcnControlPlaneConfig to a PersistableBundle.
     *
     * @hide
     */
    @SystemApi(client = Client.MODULE_LIBRARIES)
    @NonNull
    public PersistableBundle toPersistableBundle() {
        final PersistableBundle result = new PersistableBundle();
        result.putInt(CONFIG_TYPE_KEY, mConfigType);
        return result;
    }
}
