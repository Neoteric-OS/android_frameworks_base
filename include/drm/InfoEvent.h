/*
 * Copyright 2009, 2010 Sony Corporation
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
 */
class InfoEvent {

public:
	static const int ALREADY_REGISTERED_BY_ANOTHER_ACCOUNT = 0x0000001;
	static const int REMOVE_RIGHTS = 0x0000002;

public:
	/**
	 * Constructor for InfoEvent
	 *
	 * @param[in] uniqueId Unique session identifier
	 * @param[in] infoType Type of information
	 * @param[in] message Message description
	 */
	InfoEvent(
			int uniqueId,
			int infoType,
			const String8& message);

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
