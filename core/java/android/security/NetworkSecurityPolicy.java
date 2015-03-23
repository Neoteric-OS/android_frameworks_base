/**
 * Copyright (c) 2015, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.security;

/**
 * Network security policy.
 *
 * <p>Network stacks/components should honor this policy to make it possible to centrally control
 * the relevant aspects of network security behavior.
 *
 * <p>The policy currently consists of a single flag: whether cleartext network traffic is
 * permitted. See {@link #isCleartextTrafficPermitted()}.
 *
 * @hide
 */
public class NetworkSecurityPolicy {

    private static final NetworkSecurityPolicy INSTANCE = new NetworkSecurityPolicy();

    private volatile boolean mCleartextTrafficPermitted = true;

    private NetworkSecurityPolicy() {}

    /**
     * Gets the policy.
     */
    public static NetworkSecurityPolicy getInstance() {
        return INSTANCE;
    }

    /**
     * Returns whether cleartext network traffic (e.g., HTTP, FTP WebSockets, XMPP, IMAP, SMTP --
     * without TLS or STARTTLS) is permitted for this process.
     *
     * <p>When cleartext network traffic is not permitted, the platform's components (e.g., HTTP
     * and stacks, {@code WebView}, {@code MediaPlayer}) will refuse this process's requests to use
     * cleartext traffic. Third-party libraries are encouraged to honor this setting as well.
     *
     * <p>This flag is honored on a best effort basis because it's impossible to prevent all
     * cleartext traffic from Android applications given the level of access provided to
     * applications. For example, there's no expectation that {@link java.net.Socket} API will honor
     * this flag because it doesn't have enough visibility into whether its traffic is in cleartext.
     * Luckily, most network traffic from apps is handled by higher-level network stacks/components
     * which do have sufficient visibility into whether traffic is in cleartext and can thus honor
     * this aspect of the policy.
     */
    public boolean isCleartextTrafficPermitted() {
        return mCleartextTrafficPermitted;
    }

    /**
     * Sets whether cleartext network traffic is permitted for this process.
     *
     * <p>This method is used by the platform early on in the application's initialization to set
     * the policy.
     *
     * @hide
     */
    public void setCleartextTrafficPermitted(boolean permitted) {
        mCleartextTrafficPermitted = permitted;
    }
}
