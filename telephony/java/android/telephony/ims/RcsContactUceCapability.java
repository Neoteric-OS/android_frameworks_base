/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.telephony.ims;

import android.annotation.IntDef;
import android.annotation.SystemApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @hide
 */
@SystemApi
public class RcsContactUceCapability {

    /** Supports 1-to-1 chat */
    public static final int CAPABILITY_CHAT_STANDALONE = (1 << 0);
    /** Supports group chat */
    public static final int CAPABILITY_CHAT_SESSION = (1 << 1);
    /** Supports full store and forward group chat information. */
    public static final int CAPABILITY_CHAT_SESSION_STORE_FORWARD = (1 << 2);
    /** Supports file transfer via MSRP without Store and Forward */
    public static final int CAPABILITY_FILE_TRANSFER = (1 << 3);
    /** Supports File Transfer Thumbnail */
    public static final int CAPABILITY_FILE_TRANSFER_THUMBNAIL = (1 << 4);
    /** Supports File Transfer with Store and Forward */
    public static final int CAPABILITY_FILE_TRANSFER_STORE_FORWARD = (1 << 5);
    /** Supports File Transfer via HTTP */
    public static final int CAPABILITY_FILE_TRANSFER_HTTP = (1 << 6);
    /** Supports file transfer via SMS */
    public static final int CAPABILITY_FILE_TRANSFER_SMS = (1 << 7);
    /** Supports image sharing */
    public static final int CAPABILITY_IMAGE_SHARE = (1 << 8);
    /** Supports video sharing during a circuit-switch call (IR.74)*/
    public static final int CAPABILITY_VIDEO_SHARE_DURING_CS_CALL = (1 << 9);
    /** Supports video share outside of voice call (IR.84) */
    public static final int CAPABILITY_VIDEO_SHARE = (1 << 10);
    /** Supports social presence information */
    public static final int CAPABILITY_SOCIAL_PRESENCE = (1 << 11);
    /** Supports capability discovery via presence */
    public static final int CAPABILITY_DISCOVERY_VIA_PRESENCE = (1 << 12);
    /** Support IP Voice calling over LTE or IWLAN (IR.92/IR.51) */
    public static final int CAPABILITY_IP_VOICE_CALL = (1 << 13);
    /** Supports IP video calling (IR.94) */
    public static final int CAPABILITY_IP_VIDEO_CALL = (1 << 14);
    /** Supports Geolocation PUSH during 1-to-1 or multiparty chat */
    public static final int CAPABILITY_GEOLOCATION_PUSH = (1 << 15);
    /** Supports Geolocation PUSH via SMS for fallback.  */
    public static final int CAPABILITY_GEOLOCATION_PUSH_SMS = (1 << 16);
    /** Supports Geolocation pull. */
    public static final int CAPABILITY_GEOLOCATION_PULL = (1 << 17);
    /** Supports Geolocation pull using file transfer support. */
    public static final int CAPABILITY_GEOLOCATION_PULL_FILE_TRANSFER = (1 << 18);
    /** Supports RCS voice calling */
    public static final int CAPABILITY_RCS_VOICE_CALL = (1 << 19);
    /** Supports RCS video calling */
    public static final int CAPABILITY_RCS_VIDEO_CALL = (1 << 20);
    /** Supports RCS video calling, where video media can not be dropped */
    public static final int CAPABILITY_RCS_VIDEO_ONLY_CALL = (1 << 21);

    /** @hide*/
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "CAPABILITY_", flag = true, value = {
            CAPABILITY_CHAT_STANDALONE,
            CAPABILITY_CHAT_SESSION,
            CAPABILITY_CHAT_SESSION_STORE_FORWARD,
            CAPABILITY_FILE_TRANSFER,
            CAPABILITY_FILE_TRANSFER_THUMBNAIL,
            CAPABILITY_FILE_TRANSFER_STORE_FORWARD,
            CAPABILITY_FILE_TRANSFER_HTTP,
            CAPABILITY_FILE_TRANSFER_SMS,
            CAPABILITY_IMAGE_SHARE,
            CAPABILITY_VIDEO_SHARE_DURING_CS_CALL,
            CAPABILITY_VIDEO_SHARE,
            CAPABILITY_SOCIAL_PRESENCE,
            CAPABILITY_DISCOVERY_VIA_PRESENCE,
            CAPABILITY_IP_VOICE_CALL,
            CAPABILITY_IP_VIDEO_CALL,
            CAPABILITY_GEOLOCATION_PUSH,
            CAPABILITY_GEOLOCATION_PUSH_SMS,
            CAPABILITY_GEOLOCATION_PULL,
            CAPABILITY_GEOLOCATION_PULL_FILE_TRANSFER,
            CAPABILITY_RCS_VOICE_CALL,
            CAPABILITY_RCS_VIDEO_CALL,
            CAPABILITY_RCS_VIDEO_ONLY_CALL
    })
    public @interface CapabilityFlag {}

    /**
     * Builder to help construct {@link RcsContactUceCapability} instances.
     */
    public static class Builder {

        /**
         * Create the Builder, which can be used to set capabilities as well as custom capability
         * extensions.
         * @param contactUri The Contact URI that the capabilities are attached to.
         */
        public Builder(String contactUri) {

        }

        /** Add a UCE capability as well as the associated URI that the framework should use for
         * that service.
         */
        public Builder add(@CapabilityFlag int type, String serviceUri) {
            throw new UnsupportedOperationException();
        }

        /**
         * Add a UCE capability flag.
         */
        public Builder add(@CapabilityFlag int type) {
            throw new UnsupportedOperationException();
        }

        /**
         * Add a carrier specific service tag.
         */
        public Builder add(String extension) {
            throw new UnsupportedOperationException();
        }

        /**
         * @return the finished capabilities.
         */
        public RcsContactUceCapability build() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * @return true if the capability flag is set, false otherwise.
     */
    public boolean isCapable(@CapabilityFlag int type) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return true if the extension service tag is set, false otherwise.
     */
    public boolean isCapable(String extensionTag) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return all extension tags that have been set as capable.
     */
    public String[] getCapableExtensionTags() {
        throw new UnsupportedOperationException();
    }

    /**
     * @return a String containing the URI associated with the service tag or null if not capable.
     */
    public String getServiceUri(@CapabilityFlag int type) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the URI representing the contact associated with the capabilities.
     */
    public String getContactUri() {
        throw new UnsupportedOperationException();
    }
}
