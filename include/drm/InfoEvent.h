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

#ifndef __INFO_EVENT_H__
#define __INFO_EVENT_H__

namespace android {

class String8;

/**
 * This is an entity class which would be passed to caller in
 * DrmManagerClient::OnInfoListener::onInfo(const InfoEvent&).
 *
 */
class InfoEvent {
public:
    static const int ALREADY_REGISTERED_BY_ANOTHER_ACCOUNT = 0x0000001;
    static const int REMOVE_RIGHTS = 0x0000002;
    //! RIGHTS_INSTALLED, when the rights are downloaded and installed ok.
    static const int RIGHTS_INSTALLED = 0x0000003;
    //! RIGHTS_NOT_INSTALLED, when something whent wrong installing the rights
    static const int RIGHTS_NOT_INSTALLED = 0x0000004;
    //! RIGHTS_RENEWAL_NOT_ALLOWED, when the server rejects renewal of rights
    static const int RIGHTS_RENEWAL_NOT_ALLOWED = 0x0000005;
    //! NOT_SUPPORTED, when answer from server can not be handled by the native agent
    static const int NOT_SUPPORTED = 0x0000006;
    //! WAIT_FOR_RIGHTS, rights object is on it's way to phone,
    //! wait before calling checkRights again
    static const int WAIT_FOR_RIGHTS = 0x0000007;
    //! OUT_OF_MEMORY, when memory allocation fail during renewal.
    //! Can in the future perhaps be used to trigger garbage collector
    static const int OUT_OF_MEMORY = 0x0000008;
    //! NO_INTERNET_CONNECTION, when the Internet connection is missing and no attempt
    //! can be made to renew rights
    static const int NO_INTERNET_CONNECTION = 0x0000009;

public:
    /**
     * Constructor for InfoEvent
     *
     * @param[in] uniqueId Unique session identifier
     * @param[in] infoType Type of information
     * @param[in] message Message description
     */
    InfoEvent(int uniqueId, int infoType, const String8& message);

    /**
     * Destructor for InfoEvent
     */
    virtual ~InfoEvent() {}

public:
    /**
     * Returns the Unique Id associated with this instance
     *
     * @return Unique Id
     */
    int getUniqueId() const;

    /**
     * Returns the Type of information associated with this object
     *
     * @return Type of information
     */
    int getType() const;

    /**
     * Returns the message description associated with this object
     *
     * @return Message description
     */
    const String8& getMessage() const;

private:
    int mUniqueId;
    int mInfoType;
    const String8& mMessage;
};

};

# endif //__INFO_EVENT_H__
