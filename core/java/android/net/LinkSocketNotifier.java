/*
 * Copyright (c) 2010, Code Aurora Forum. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at

 * http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package android.net;

import java.util.Map;

/** {@hide}
 * The LinkSocketNotifier interface uses callback functions to notify
 * the application that created the LinkSocket of any changes.
 * <p>
 * An application that uses an LinkSocket needs to implement this
 * interface.
 *
 * @see LinkSocket
 */
public interface LinkSocketNotifier {

    /** {@hide}
     * This callback function will be called if an application has set
     * the {@code BETTER_LINK_NOTIFICATION} flag to {@code "true"} and
     * a better link becomes available.
     * <p>
     * If the duplicate socket is connected the original socket will
     * be marked as no longer in use.
     *
     * @param original
     *            the original LinkSocket that no longer meets the
     *            application requirements
     * @param duplicate
     *            the new LinkSocket that better meets the application
     *            requirements; or {@code null} if auto duplicate is
     *            disabled.
     * @return {@code true} if application intends to use this link;
     *         otherwise {@code false}
     */
    public boolean onBetterLinkAvail(LinkSocket original, LinkSocket duplicate);

    /** {@hide}
     * This callback function will be called when an LinkSocket no
     * longer has an active link.
     *
     * @param socket
     *            the LinkSocket that lost its link
     */
    public void onLinkLost(LinkSocket socket);

    /** {@hide}
     * This callback function will be called when an application
     * called RequestNewLink on a link socket but the LinkSocket is
     * unable to find a new link.
     *
     * @param socket
     *            the LinkSocket for which a new link was not found
     */
    public void onNewLinkUnavailable(LinkSocket socket);

    /** {@hide}
     * This callback function will be called when any of the
     * capabilities of the LinkSocket (e.g. estimated bandwidth) have
     * changed.
     *
     * @param socket
     *            the LinkSocket for which capabilities have changes
     * @param changedCapabilities
     *            the set of capabilities that the application is
     *            interested in that have changed and their new
     *            values.
     */
    public void onCapabilityChanged(LinkSocket socket, Map<Integer, String> changedCapabilities);
}
