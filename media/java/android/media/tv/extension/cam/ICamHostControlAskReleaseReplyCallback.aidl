/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.media.tv.extension.cam;

/**
 * @hide
 */
oneway interface ICamHostControlAskReleaseReplyCallback {
    /**
     * Notify when the CICAM responds to a host's request to release control of a resource.
     *
     * @param sessionToken The unique token that was provided in the initial release request,
     *                     used to correlate the reply with the original request.
     * @param replyStatus The status of the reply from the CAM. 0 to indicate the CAM has
     *                    accepted the release request and the host can proceed.
     *                    And 1 to Indicate the CAM has refused the release request.
     *                    The host should not proceed with any action that would disturb the current tuning.
     */
    void onAskReleaseReply(String sessionToken, int replyStatus);
}
