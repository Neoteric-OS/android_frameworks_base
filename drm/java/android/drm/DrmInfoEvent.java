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
 * {@link DrmManagerClient.OnInfoListener#onInfo(DrmManagerClient, DrmInfoEvent)}
 *
 */
public class DrmInfoEvent extends DrmEvent {
    /**
     * TYPE_ALREADY_REGISTERED_BY_ANOTHER_ACCOUNT, when registration has been already done
     * by another account ID.
     */
    public static final int TYPE_ALREADY_REGISTERED_BY_ANOTHER_ACCOUNT = 1;
    /**
     * TYPE_REMOVE_RIGHTS, when the rights needs to be removed completely.
     */
    public static final int TYPE_REMOVE_RIGHTS = 2;
    /**
     * TYPE_RIGHTS_INSTALLED, when the rights are downloaded and installed ok.
     */
    public static final int TYPE_RIGHTS_INSTALLED = 3;
    /**
     * TYPE_RIGHTS_NOT_INSTALLED, when something went wrong installing the rights.
     */
    public static final int TYPE_RIGHTS_NOT_INSTALLED = 4;
    /**
     * TYPE_RIGHTS_RENEWAL_NOT_ALLOWED, when the server rejects renewal of rights.
     */
    public static final int TYPE_RIGHTS_RENEWAL_NOT_ALLOWED = 5;
    /**
     * TYPE_NOT_SUPPORTED, when answer from server can not be handled by the native agent.
     */
    public static final int TYPE_NOT_SUPPORTED = 6;
    /**
     * TYPE_WAIT_FOR_RIGHTS, rights object is on it's way to phone,
     * wait before calling checkRights again.
     */
    public static final int TYPE_WAIT_FOR_RIGHTS = 7;
    /**
     * TYPE_OUT_OF_MEMORY, when memory allocation fail during renewal.
     * Can in the future perhaps be used to trigger garbage collector.
     */
    public static final int TYPE_OUT_OF_MEMORY = 8;
    /**
     * TYPE_NO_INTERNET_CONNECTION, when the Internet connection is missing and no attempt
     * can be made to renew rights.
     */
    public static final int TYPE_NO_INTERNET_CONNECTION = 9;
    /**
     * TYPE_INITIALIZE_FAILED, when failed to load and initialize the available plugins.
     */
    public static final int TYPE_INITIALIZE_FAILED = 10;
    /**
     * TYPE_FINALIZE_FAILED, when failed to unload and finalize the loaded plugins.
     */
    public static final int TYPE_FINALIZE_FAILED = 11;
    /**
     * TYPE_REMOVE_ALL_RIGHTS_FAILED, when failed to remove all the rights objects
     * associated with all DRM schemes.
     */
    public static final int TYPE_REMOVE_ALL_RIGHTS_FAILED = 12;
    /**
     * TYPE_REGISTRATION_FAILED, when failed to register with the service.
     */
    public static final int TYPE_REGISTRATION_FAILED = 13;
    /**
     * TYPE_UNREGISTRATION_FAILED, when failed to unregister with the service.
     */
    public static final int TYPE_UNREGISTRATION_FAILED = 14;
    /**
     * TYPE_RIGHTS_ACQUISITION_FAILED, when failed to acquire the rights information required.
     */
    public static final int TYPE_RIGHTS_ACQUISITION_FAILED = 15;
    /**
     * TYPE_DRM_INFO_ACQUISITION_FAILED, when failed to get the required information to
     * communicate with the service.
     */
    public static final int TYPE_DRM_INFO_ACQUISITION_FAILED = 16;

    /**
     * constructor to create DrmInfoEvent object with given parameters
     *
     * @param uniqueId Unique session identifier
     * @param type Type of information
     * @param message Message description
     */
    public DrmInfoEvent(int uniqueId, int type, String message) {
        super(uniqueId, type, message);
    }
}

