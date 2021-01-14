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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

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

    /**
     * Construct a VcnControlPlaneConfig object.
     *
     * @hide
     */
    @SystemApi(client = Client.MODULE_LIBRARIES)
    public VcnControlPlaneConfig(@ConfigType int configType) {
        mConfigType = configType;
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
