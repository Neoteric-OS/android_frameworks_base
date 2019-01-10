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

package android.security.identity;

import java.security.cert.X509Certificate;

/**
 * TODO(swillden): Write this.
 */
public class AccessControlProfile {
    private String mAccessControlProfileName = null;
    private X509Certificate mReaderCertificate = null;
    private boolean mUserAuthenticationRequired = false;
    private int mUserAuthenticationTimeout = -1;

    /**
     * TODO(swillden): Write this.
     */
    public class Builder {
        private AccessControlProfile mProfile;

        /**
         * TODO(swillden): Write this.
         */
        public Builder(String accessControlProfileName) {
            mProfile = new AccessControlProfile();
            mProfile.mAccessControlProfileName = accessControlProfileName;
        }

        /**
         * TODO(swillden): Write this.
         */
        public Builder setUserAuthenticationRequired(boolean userAuthenticationRequired) {
            mProfile.mUserAuthenticationRequired = userAuthenticationRequired;
            return this;
        }

        /**
         * TODO(swillden): Write this.
         */
        public Builder setUserAuthenticationTimeout(int userAuthenticationTimeout) {
            mProfile.mUserAuthenticationTimeout = userAuthenticationTimeout;
            return this;
        }

        /**
         * TODO(swillden): Write this.
         */
        public Builder setReaderCertificate(X509Certificate readerCertificate) {
            mProfile.mReaderCertificate = readerCertificate;
            return this;
        }

        /**
         * TODO(swillden): Write this.
         */
        public AccessControlProfile build() {
            return mProfile;
        }
    }

    /**
     * TODO(swillden): Write this.
     */
    public String getAccessControlProfileName() {
        return mAccessControlProfileName;
    }

    /**
     * TODO(swillden): Write this.
     */
    public int getUserAuthenticationTimeout() {
        return mUserAuthenticationTimeout;
    }

    /**
     * TODO(swillden): Write this.
     */
    public boolean isUserAuthenticationRequired() {
        return mUserAuthenticationRequired;
    }

    /**
     * TODO(swillden): Write this.
     */
    public X509Certificate getReaderCertificate() {
        return mReaderCertificate;
    }
}
