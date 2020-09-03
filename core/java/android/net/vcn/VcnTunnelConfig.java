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

import android.annotation.NonNull;

/**
 * This class represents a configuration for a single Virtual Carrier Network tunnel.
 *
 * <p>Each tunnel represents a single exposed Network for a given mobility anchor.
 *
 * @hide
 */
public final class VcnTunnelConfig {
    private VcnTunnelConfig() {
        validate();
    }

    // TODO: Implement getters, validators, etc

    /**
     * Validates this configuration
     *
     * @hide
     */
    private void validate() {
        // TODO: implement validation logic
    }

    // Parcelable methods

    /** This class is used to incrementally build {@link VcnTunnelConfig} objects */
    public static class Builder {
        // TODO: Implement this builder

        /**
         * Builds and validates the VcnTunnelConfig
         *
         * @return an immutable VcnTunnelConfig instance
         */
        @NonNull
        public VcnTunnelConfig build() {
            return new VcnTunnelConfig();
        }
    }
}
