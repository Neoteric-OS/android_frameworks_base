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

package android.net;

import android.net.NetworkRequest;

/** @hide */
oneway interface INeedNetwork {
    /**
     * Tells the registrant that the offer is needed to fulfill this request.
     * @param networkRequest the request to satisfy
     * @param factorySerialNumber the serial number of the factory currently satisfying
     *                            this request, or NetworkProvider.ID_NONE if none.
     */
    void onOfferNeeded(in NetworkRequest networkRequest, int factorySerialNumber);

    /**
     * Tells the registrant that the offer is no longer needed to fulfill this request.
     */
    void onOfferUnneeded(in NetworkRequest networkRequest);
}
