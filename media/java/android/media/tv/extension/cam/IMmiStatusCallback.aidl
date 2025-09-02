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

import android.os.Bundle;

/**
 * @hide
 */
oneway interface IMmiStatusCallback {
    /**
     * Called when the CAM sends a request to display an enquiry screen, which typically
     * prompts the user for text input.
     *
     * @param enqComponent A Bundle containing the components needed to display the enquiry:
     *                     KEY_MMI_ENQ_INPUTTEXT: The text of the enquiry.
     *                     KEY_MMI_ENQ_IS_HIDE: Whether to show or hide the user's answer on screen
     *                     (e.g., hiding for passwords). true to hide, false to show.
     *                     KEY_MMI_ENQ_MAX_TEXT_LENGTH: The maximum length of the user's answer.
     */
    void onMmiEnquire(in Bundle enqComponent);
    /**
     * Called when the CAM sends a request to display a menu or a list of choices.
     *
     * @param listMenuComponent A Bundle containing the components needed to display the menu/list:
     *                          KEY_MMI_MENULIST_TITLE: The title for the menu or list screen.
     *                          KEY_MMI_MENULIST_SUBTITLE: The subtitle for the menu or list screen.
     *                          KEY_MMI_MENULIST_FOOTERNOTE: The footnote text.
     *                          KEY_MMI_MENULIST_ITEMLIST}: The list of choices for the user.
     */
    void onMmiListMenu(in Bundle listMenuComponent);
    /**
     * Called when the CAM sends a request to close the current MMI screen.
     */
    void onMmiClose();
}
