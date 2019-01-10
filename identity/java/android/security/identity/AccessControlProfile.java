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
 * 
 */
public class AccessControlProfile {
    private String accessControlProfileName = null;
    private X509Certificate readerCertificate = null;
    private boolean userAuthenticationRequired = false;
    private int userAuthenticationTimeout = -1;

    public class Builder {
        private AccessControlProfile profile;

        public Builder(String accessControlProfileName) {
            profile = new AccessControlProfile();
            profile.accessControlProfileName = accessControlProfileName;
        }

        public Builder setUserAuthenticationRequired(boolean userAuthenticationRequired) {
            profile.userAuthenticationRequired = userAuthenticationRequired;
            return this;
        }

        public Builder setUserAuthenticationTimeout(int userAuthenticationTimeout) {
            profile.userAuthenticationTimeout = userAuthenticationTimeout;
            return this;
        }

        public Builder setReaderCertificate(X509Certificate readerCertificate) {
            profile.readerCertificate = readerCertificate;
            return this;
        }

        public AccessControlProfile build() {
            return profile;
        }
    }

    public String getAccessControlProfileName() {
        return accessControlProfileName;
    }

    public int getUserAuthenticationTimeout() {
        return userAuthenticationTimeout;
    }

    public boolean isUserAuthenticationRequired() {
        return userAuthenticationRequired;
    }

    public X509Certificate getReaderCertificate() {
        return readerCertificate;
    }
}
