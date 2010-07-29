/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.drm;

/**
 * This is an entity class which would be passed to caller in
 * {@link DrmManagerClient.OnInfoListener#onInfo(DrmManagerClient, InfoEvent)}
 *
 */
public class InfoEvent extends Event {
    /**
     * Type of informations would be notified by {@link InfoEvent}
     */
    public static class Type {
        /**
         * ALREADY_REGISTERED_BY_ANOTHER_ACCOUNT, when registration has been already done
         * by another account ID.
         */
        public static final int ALREADY_REGISTERED_BY_ANOTHER_ACCOUNT = 0x0000001;
        public static final int REMOVE_RIGHTS = 0x0000002;
        /**
         * RIGHTS_INSTALLED, when the rights are downloaded and installed ok.
         */
        public static final int RIGHTS_INSTALLED = 0x0000003;
        /**
         * RIGHTS_NOT_INSTALLED, when something whent wrong installing the rights.
         */
        public static final int RIGHTS_NOT_INSTALLED = 0x0000004;
        /**
         * RIGHTS_RENEWAL_NOT_ALLOWED, when the server rejects renewal of rights.
         */
        public static final int RIGHTS_RENEWAL_NOT_ALLOWED = 0x0000005;
        /**
         * NOT_SUPPORTED, when answer from server can not be handled by the native agent.
         */
        public static final int NOT_SUPPORTED = 0x0000006;
        /**
         * WAIT_FOR_RIGHTS, rights object is on it's way to phone,
         * wait before calling checkRights again.
         */
        public static final int WAIT_FOR_RIGHTS = 0x0000007;
        /**
         * OUT_OF_MEMORY, when memory allocation fail during renewal.
         * Can in the future perhaps be used to trigger garbage collector.
         */
        public static final int OUT_OF_MEMORY = 0x0000008;
        /**
         * NO_INTERNET_CONNECTION, when the Internet connection is missing and no attempt
         * can be made to renew rights.
         */
        public static final int NO_INTERNET_CONNECTION = 0x0000009;
    }

    /**
     * constructor to create InfoEvent object with given parameters
     *
     * @param uniqueId Unique session identifier
     * @param type Type of information
     * @param message Message description
     */
    public InfoEvent(int uniqueId, int type, String message) {
        super(uniqueId, type, message);
    }
}

