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

package android.drm;

/**
 * This is an entity class which would be passed to caller in 
 * {@link DrmManagerClient.OnInfoListener#onInfo(DrmManagerClient, InfoEvent)}
 */
public class InfoEvent extends Event {

	/**
	 * Type of informations would be notified by {@link InfoEvent}
	 */
	public static class Type {
		public static final int ALREADY_REGISTERED_BY_ANOTHER_ACCOUNT = 0x0000001;
		public static final int REMOVE_RIGHTS = 0x0000002;
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
