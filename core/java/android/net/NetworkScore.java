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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Object representing the quality of a network as perceived by the user.
 *
 * A NetworkScore object represents the characteristics of a network that affects how good the
 * network is considered for a particular use.
 *
 * @hide
 */
@SystemApi
public final class NetworkScore implements Parcelable {
    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            DONT_FORCE_KEEPUP,
            FORCE_KEEPUP_FOR_HANDOVER
    })
    public @interface ForceKeepupReason {
    }

    public static final int DONT_FORCE_KEEPUP = 0;
    public static final int FORCE_KEEPUP_FOR_HANDOVER = 1;

    // This network should never be preferred to a wifi that has ever been validated
    // NOTE : temporarily this policy is managed by ConnectivityService, because of legacy. The
    // legacy design has this bit global to the system and tacked on WiFi which means it will affect
    // networks from carriers who don't want it and non-carrier networks, which is bad for users.
    // The S design has this on mobile networks only, so this can be fixed eventually ; as CS
    // doesn't know what carriers need this bit, the initial S implementation will continue to
    // affect other carriers but will at least leave non-mobile networks alone. Eventually Telephony
    // should set this on networks from carriers that require it.
    public static final int POLICY_BAD_WIFI_AVOIDANCE = 1;
    // This network is part of the default subscription.
    public static final int POLICY_DEFAULT_SUBSCRIPTION = 2;
    // This network is exiting : it will likely disconnect in a few seconds
    public static final int POLICY_EXITING = 3;
    // CS-managed policies
    // This network is explicitly selected by the user. CS-managed because the source of truth
    // is in NetworkAgentConfig.
    public static final int POLICY_EXPLICITLY_SELECTED = 63;
    // This network is a VPN. CS-managed because the source of truth is in NetworkCapabilities.
    public static final int POLICY_IS_VPN = 62;
    // This network is a VPN in lockdown mode. CS-managed because the source of truth is in
    // Settings.
    public static final int POLICY_IS_VPN_LOCKDOWN = 61;
    // This network is validated. CS-managed because the source of truth is in NetworkCapabilities.
    public static final int POLICY_IS_VALIDATED = 60;
    // This network is unmetered. CS-managed because the source of truth is in NetworkCapabilities.
    public static final int POLICY_IS_UNMETERED = 60;

    // TODO : remove this, it's not necessary with an API to listen to all requests
    @NonNull
    public static final NetworkScore INVINCIBLE_SCORE = new NetworkScore(1000,
            DONT_FORCE_KEEPUP,
            POLICY_DEFAULT_SUBSCRIPTION | POLICY_IS_VALIDATED);

    // This will be removed soon. Do *NOT* depend on it for any new code that is not part of
    // a migration.
    public final int legacyInt;
    private final int forceKeepupReason;
    private final int mPolicy;

    /** @hide */
    public NetworkScore(final int legacyInt, @ForceKeepupReason final int forceKeepupReason,
            final int policy) {
        this.legacyInt = legacyInt;
        this.forceKeepupReason = forceKeepupReason;
        this.mPolicy = policy;
    }

    /** @hide */
    public NetworkScore withCSManagedCapabilities(final boolean isExplicitlySelected,
            final boolean isVpn, final boolean isVpnLockdown,
            final boolean isValidated, final boolean isUnmetered) {
        return new NetworkScore(legacyInt, forceKeepupReason,
                mPolicy
                | (isExplicitlySelected ? POLICY_EXPLICITLY_SELECTED : 0)
                | (isVpn ? POLICY_IS_VPN : 0)
                | (isVpnLockdown ? POLICY_IS_VPN_LOCKDOWN : 0)
                | (isValidated ? POLICY_IS_VALIDATED : 0)
                | (isUnmetered ? POLICY_IS_UNMETERED : 0));
    }

    private boolean hasPolicy(final int policy) {
        return (mPolicy & policy) != 0;
    }

    // Policies from transport
    public boolean hasBadWifiAvoidance() {
        return hasPolicy(POLICY_BAD_WIFI_AVOIDANCE);
    }

    public boolean isDefaultSubscription() {
        return hasPolicy(POLICY_DEFAULT_SUBSCRIPTION);
    }

    public boolean isExiting() {
        return hasPolicy(POLICY_EXITING);
    }

    // CS-managed policies
    public boolean isVpn() {
        return hasPolicy(POLICY_IS_VPN);
    }

    public boolean isVpnLockdown() {
        return hasPolicy(POLICY_IS_VPN_LOCKDOWN);
    }

    public boolean isValidated() {
        return hasPolicy(POLICY_IS_VALIDATED);
    }

    public boolean isUnmetered() {
        return hasPolicy(POLICY_IS_UNMETERED);
    }

    public boolean isExplicitlySelected() {
        return hasPolicy(POLICY_EXPLICITLY_SELECTED);
    }

    /** @hide */
    public static NetworkScore validatedScore(final NetworkScore score) {
        return new NetworkScore(score.legacyInt, score.forceKeepupReason,
                score.mPolicy | POLICY_IS_VALIDATED);
    }

    private NetworkScore(@NonNull final Parcel in) {
        legacyInt = in.readInt();
        forceKeepupReason = in.readInt();
        mPolicy = in.readInt();
    }

    public int getForceKeepupReason() {
        return forceKeepupReason;
    }

    public int getLegacyInt() {
        return legacyInt;
    }

    @Override
    public String toString() {
        return "Score(" + legacyInt + ")";
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest, final int flags) {
        dest.writeInt(legacyInt);
        dest.writeInt(forceKeepupReason);
        dest.writeInt(mPolicy);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @NonNull
    public static final Creator<NetworkScore> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public NetworkScore createFromParcel(@NonNull final Parcel in) {
            return new NetworkScore(in);
        }

        @Override
        @NonNull
        public NetworkScore[] newArray(int size) {
            return new NetworkScore[size];
        }
    };

    public static final class Builder {
        private static final int INVALID_LEGACY_INT = Integer.MIN_VALUE;
        private int mLegacyInt = INVALID_LEGACY_INT;
        private int mForceKeepupReason = DONT_FORCE_KEEPUP;
        private int mPolicy = 0;

        @NonNull
        public NetworkScore build() {
            return new NetworkScore(mLegacyInt, mForceKeepupReason, mPolicy);
        }

        @NonNull
        public Builder setForceKeepupReason(@ForceKeepupReason final int reason) {
            mForceKeepupReason = reason;
            return this;
        }

        @NonNull
        public Builder setLegacyInt(final int score) {
            mLegacyInt = score;
            return this;
        }

        /**
         * Set for a network that should never be preferred to a wifi that has ever been validated
         *
         * @return this builder
         */
        @NonNull
        public Builder setHasBadWifiAvoidance(final boolean val) {
            if (val) {
                mPolicy |= POLICY_BAD_WIFI_AVOIDANCE;
            } else {
                mPolicy &= ~POLICY_BAD_WIFI_AVOIDANCE;
            }
            return this;
        }

        /**
         * Set for a network that is part of the default subscription.
         *
         * @return this builder
         */
        @NonNull
        public Builder setDefaultSubscription(final boolean val) {
            if (val) {
                mPolicy |= POLICY_DEFAULT_SUBSCRIPTION;
            } else {
                mPolicy &= ~POLICY_DEFAULT_SUBSCRIPTION;
            }
            return this;
        }

        /**
         * Set for a network that will likely disconnect in a few seconds
         *
         * @return this builder
         */
        @NonNull
        public Builder setExiting(final boolean val) {
            if (val) {
                mPolicy |= POLICY_EXITING;
            } else {
                mPolicy &= ~POLICY_EXITING;
            }
            return this;
        }

        /**
         *
         */
        @NonNull
        public Builder setExplicitlySelected(final boolean val) {
            if (val) {
                mPolicy |= POLICY_EXPLICITLY_SELECTED;
            } else {
                mPolicy &= ~POLICY_EXPLICITLY_SELECTED;
            }
            return this;
        }

        // CS-managed policies

        /**
         * Set for a VPN network.
         *
         * @return this builder
         * @hide
         */
        @NonNull
        public Builder setVpn(final boolean val) {
            if (val) {
                mPolicy |= POLICY_IS_VPN;
            } else {
                mPolicy &= ~POLICY_IS_VPN;
            }
            return this;
        }

        /**
         * Set for a VPN in lockdown mode.
         *
         * @return this builder
         * @hide
         */
        @NonNull
        public Builder setVpnLockdown(final boolean val) {
            if (val) {
                mPolicy |= POLICY_IS_VPN_LOCKDOWN;
            } else {
                mPolicy &= ~POLICY_IS_VPN_LOCKDOWN;
            }
            return this;
        }

        /**
         * Set for a validated network.
         *
         * @return this builder
         * @hide
         */
        @NonNull
        public Builder setValidated(final boolean val) {
            if (val) {
                mPolicy |= POLICY_IS_VALIDATED;
            } else {
                mPolicy &= ~POLICY_IS_VALIDATED;
            }
            return this;
        }

        /**
         * Set for an unmetered network.
         *
         * @return this builder
         * @hide
         */
        @NonNull
        public Builder setUnmetered(final boolean val) {
            if (val) {
                mPolicy |= POLICY_IS_UNMETERED;
            } else {
                mPolicy &= ~POLICY_IS_UNMETERED;
            }
            return this;
        }
    }
}
