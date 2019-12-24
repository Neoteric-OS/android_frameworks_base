/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.annotation.SystemApi;
import android.net.NetworkPolicyManager.NetworkPolicyListenerProxy;

/**
 * Base class for Network policy listener.
 * @hide
 */
// This is used when registering/unregistering network policy listener,
// and the naming is consistent from NetworkPolicyManager to NetworkPolicyManagerService
// for many releases. Thus, if renamed as Callback, it would be inconsistent.
@SuppressLint("ListenerInterface")
@SystemApi
public class NetworkPolicyListener {

    private NetworkPolicyListenerProxy mListener;

    /**
     * Notify that the uid has been updated new rules.
     *
     * @param uid A uid which has been updated rules for.
     * @param uidRules The new rules.
     */
    public void onUidRulesChanged(int uid, int uidRules) {}

    /**
     * Notify that the metered interface is chenaged.
     *
     * @param meteredIfaces A metered interface.
     */
    public void onMeteredIfacesChanged(@NonNull String[] meteredIfaces) {}

    /**
     * Notify that the restrict background policy is changed.
     *
     * @param restrictBackground True if restrict backgrond data, otherwise false.
     */
    public void onRestrictBackgroundChanged(boolean restrictBackground){}

    /**
     * Notify that the uid has been updated new policies.
     *
     * @param uid A uid which has been updated new policies for.
     * @param uidPolicies The new policies.
     */
    public void onUidPoliciesChanged(int uid, int uidPolicies) {}

    /**
     * Notify that the subscriptions is override.
     *
     * @param subId The subscriber this override applies to.
     * @param overrideMask The override mask.
     * @param overrideValue The override vale.
     */
    public void onSubscriptionOverride(int subId, int overrideMask, int overrideValue) {}

    /** @hide */
    public void setListener(NetworkPolicyListenerProxy listener) {
        mListener = listener;
    }

    /** @hide */
    public NetworkPolicyListenerProxy getListener() {
        return mListener;
    }
}
