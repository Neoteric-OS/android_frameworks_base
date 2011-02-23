/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.server;

/** {@hide} */
public interface SystemService {

    /**
     * Sent out when the SystemServer is initialized
     */
    public static final int SYSTEM_READY = 1;

    /**
     * Dispatched from SystemServer to notify services
     * on updates from the system.
     *
     * @param event type of event from system.
     */
    public void onSystemEvent(int event);

    /**
     * Used to retrieve the handle
     *
     * @returns non-null handle if service should be registered in ServiceManager
     *          null if service should not be registered in ServiceManager
     */
    public String getHandle();
}
