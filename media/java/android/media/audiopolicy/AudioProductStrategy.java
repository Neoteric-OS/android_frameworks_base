/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.media.audiopolicy;

import static android.media.audiopolicy.AudioVolumeGroup.DEFAULT_VOLUME_GROUP;

import static java.util.stream.Collectors.toList;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.annotation.TestApi;
import android.media.AudioAttributes;
import android.media.AudioSystem;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.PrintWriterPrinter;

import com.android.internal.annotations.GuardedBy;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @hide
 * A class to encapsulate a collection of attributes associated to a given product strategy
 * (and for legacy reason, keep the association with the stream type).
 */
@SystemApi
public final class AudioProductStrategy implements Parcelable {
    /**
     * group value to use when introspection API fails.
     * @hide
     */
    public static final int DEFAULT_GROUP = -1;
    /**
     * default zone id is the primary. Legacy API without zone id uses this default
     * @hide
     */
    @FlaggedApi(Flags.FLAG_MULTI_ZONE_AUDIO)
    @SystemApi
    public static final int DEFAULT_ZONE_ID = 0;

    private static final int MATCH_ON_ZONE_ID_SCORE = 1 << 4;
    private static final int MATCH_ON_TAGS_SCORE = 1 << 3;
    private static final int MATCH_ON_FLAGS_SCORE = 1 << 2;
    private static final int MATCH_ON_USAGE_SCORE = 1 << 1;
    private static final int MATCH_ON_CONTENT_TYPE_SCORE = 1 << 0;
    private static final int MATCH_ON_DEFAULT_SCORE = 0;
    private static final int NO_MATCH = -1;
    private static final int MATCH_ATTRIBUTES_EQUALS = MATCH_ON_TAGS_SCORE | MATCH_ON_FLAGS_SCORE
            | MATCH_ON_CONTENT_TYPE_SCORE |MATCH_ON_USAGE_SCORE;
    private static final int MATCH_EQUALS = MATCH_ON_ZONE_ID_SCORE | MATCH_ATTRIBUTES_EQUALS;

    private static final String TAG = "AudioProductStrategy";

    private final AudioAttributesGroup[] mAudioAttributesGroups;
    private final String mName;
    /**
     * Unique identifier of a product strategy.
     * This Id can be assimilated to Car Audio Usage and even more generally to usage.
     * For legacy platforms, the product strategy id is the routing_strategy, which was hidden to
     * upper layer but was transpiring in the {@link AudioAttributes#getUsage()}.
     */
    private int mId;
    /**
     * @hide
     * @return the product strategy zone ID, default is {@code DEFAULT_ZONE_ID}.
     */
    @FlaggedApi(Flags.FLAG_MULTI_ZONE_AUDIO)
    @SystemApi
    private int mZoneId = DEFAULT_ZONE_ID;

    private static final Object sLock = new Object();

    @GuardedBy("sLock")
    private static List<AudioProductStrategy> sAudioProductStrategies;

    /**
     * @hide
     * @return the list of AudioProductStrategy discovered from platform configuration file.
     */
    @NonNull
    public static List<AudioProductStrategy> getAudioProductStrategies() {
        if (sAudioProductStrategies == null) {
            synchronized (sLock) {
                if (sAudioProductStrategies == null) {
                    sAudioProductStrategies = initializeAudioProductStrategies();
                }
            }
        }
        return sAudioProductStrategies;
    }

    /**
     * Select the best {@link AudioProductStrategy} object for the given {@link AudioAttributes}.
     * @param attributes to consider
     * @param fallbackOnDefault if set, allows to fallback on the default strategy (e.g. the
     * strategy associated to {@code DEFAULT_ATTRIBUTES}).
     * @return the highest matching score {@link AudioProductStrategy} if found, default if fallback
     * on default is set, {@code null} otherwise.
     * @hide
     */
    @Nullable
    public static AudioProductStrategy getAudioProductStrategyForAudioAttributes(
            @NonNull AudioAttributes attributes, boolean fallbackOnDefault) {
        return getAudioProductStrategyForAudioAttributes(attributes, DEFAULT_ZONE_ID,
                fallbackOnDefault);
    }

    /**
     *
     * @param groupId
     * @return
     * @hide
     */
    @FlaggedApi(Flags.FLAG_MULTI_ZONE_AUDIO)
    @SystemApi
    public static int getZoneIdForAudioVolumeGroupId(int groupId) {
        for (AudioProductStrategy strategy : getAudioProductStrategies()) {
            for (AudioAttributesGroup aag : strategy.mAudioAttributesGroups) {
                if (aag.mVolumeGroupId == groupId) {
                    return strategy.getZoneId();
                }
            }
        }
        return DEFAULT_ZONE_ID;
    }

    /**
     * Select the best {@link AudioProductStrategy} object for the given {@link AudioAttributes}.
     * @param attributes to consider
     * @param fallbackOnDefault if set, allows to fallback on the default strategy (e.g. the
     * strategy associated to {@code DEFAULT_ATTRIBUTES}).
     * @return the highest matching score {@link AudioProductStrategy} if found, default if fallback
     * on default is set, {@code null} otherwise.
     * @hide
     */
    @FlaggedApi(Flags.FLAG_MULTI_ZONE_AUDIO)
    @SystemApi
    @Nullable
    public static AudioProductStrategy getAudioProductStrategyForAudioAttributes(
            @NonNull AudioAttributes attributes, int zoneId, boolean fallbackOnDefault) {
        AudioAttributesGroup aag
                = getAudioAttributesGroupForAttributes(attributes, zoneId, fallbackOnDefault);
        return aag != null ? getAudioProductStrategyWithId(aag.getStrategyId()) :  null;
    }

    /**
     * @hide
     * Return the AudioProductStrategy object for the given strategy ID.
     * @param id the ID of the strategy to find
     * @return an AudioProductStrategy on which getId() would return id, null if no such strategy
     *     exists.
     */
    public static @Nullable AudioProductStrategy getAudioProductStrategyWithId(int id) {
        for (AudioProductStrategy strategy : getAudioProductStrategies()) {
            if (strategy.getId() == id) {
                return strategy;
            }
        }
        return null;
    }

    /**
     * @hide
     * Create an invalid AudioProductStrategy instance for testing
     * @param id the ID for the invalid strategy, always use a different one than in use
     *        Unused: do not let caller to set it as some ids are allocated to internal strategies.
     * @return an invalid instance that cannot successfully be used for volume groups or routing
     */
    @SystemApi
    public static @NonNull AudioProductStrategy createInvalidAudioProductStrategy(int id) {
        ArrayList<AudioProductStrategy> apsList = new ArrayList<>();
        native_list_audio_product_strategies(apsList);
        return new AudioProductStrategy("invalid strategy", apsList.size() + 1,
                /* zoneId= */ 0, new AudioAttributesGroup[0]);
    }

    /**
     * @hide
     * @param streamType to match against AudioProductStrategy
     * @return the AudioAttributes for the first strategy found with the associated stream type
     *          If no match is found, returns AudioAttributes with unknown content_type and usage
     */
    @NonNull
    public static AudioAttributes getAudioAttributesForStrategyWithLegacyStreamType(
            int streamType) {
        for (AudioProductStrategy productStrategy : getAudioProductStrategies()) {
            AudioAttributes aa = productStrategy.getAudioAttributesForLegacyStreamType(streamType);
            if (aa != null) {
                return aa;
            }
        }
        return DEFAULT_ATTRIBUTES;
    }

    /**
     * @hide
     * @param audioAttributes to identify {@link AudioProductStrategy} with
     * @return legacy stream type associated with matched {@link AudioProductStrategy}. If no
     *              strategy found or found {@link AudioProductStrategy} does not have associated
     *              legacy stream (i.e. associated with {@link AudioSystem#STREAM_DEFAULT}) defaults
     *              to {@link AudioSystem#STREAM_MUSIC}
     */
    public static int getLegacyStreamTypeForStrategyWithAudioAttributes(
            @NonNull AudioAttributes audioAttributes) {
        Objects.requireNonNull(audioAttributes, "AudioAttributes must not be null");
        AudioAttributesGroup aag = getAudioAttributesGroupForAttributes(audioAttributes,
                DEFAULT_ZONE_ID, /* fallbackOnDefault= */ false);
        if (aag != null) {
            int streamType = aag.getStreamType();
            if (streamType == AudioSystem.STREAM_DEFAULT) {
                Log.w(TAG, "Attributes " + audioAttributes + " supported by strategy "
                        + aag.getStrategyId() + " have no associated stream type, "
                        + "therefore falling back to STREAM_MUSIC");
                return AudioSystem.STREAM_MUSIC;
            }
            if (streamType < AudioSystem.getNumStreamTypes()) {
                return streamType;
            }
        }
        return AudioSystem.STREAM_MUSIC;
    }

    /**
     * @hide
     * @param attributes the {@link AudioAttributes} that best identify VolumeGroupId
     * @param fallbackOnDefault if set, allows to fallback on the default group (e.g. the group
     *                          associated to {@link AudioManager#STREAM_MUSIC}).
     * @return volume group id associated with the given {@link AudioAttributes} if found,
     *     default volume group id if fallbackOnDefault is set
     * <p>By convention, the product strategy with default attributes will be associated to the
     * default volume group (e.g. associated to {@link AudioManager#STREAM_MUSIC})
     * or {@code DEFAULT_VOLUME_GROUP} if not found.
     */
    public static int getVolumeGroupIdForAudioAttributes(
            @NonNull AudioAttributes attributes, boolean fallbackOnDefault) {
        return getVolumeGroupIdForAudioAttributes(attributes, DEFAULT_ZONE_ID, fallbackOnDefault);
    }

    /**
     * @hide
     * @param attributes the {@link AudioAttributes} to identify VolumeGroupId with
     * @param fallbackOnDefault if set, allows to fallback on the default group (e.g. the group
     *                          associated to {@link AudioManager#STREAM_MUSIC}).
     * @return volume group id associated with the given {@link AudioAttributes} if found,
     *     default volume group id if fallbackOnDefault is set
     * <p>By convention, the product strategy with default attributes will be associated to the
     * default volume group (e.g. associated to {@link AudioManager#STREAM_MUSIC})
     * or {@code DEFAULT_VOLUME_GROUP} if not found.
     */
    @FlaggedApi(Flags.FLAG_MULTI_ZONE_AUDIO)
    @SystemApi
    public static int getVolumeGroupIdForAudioAttributes(
            @NonNull AudioAttributes attributes, int zoneId, boolean fallbackOnDefault) {
        AudioAttributesGroup aag = getAudioAttributesGroupForAttributes(attributes, zoneId,
                fallbackOnDefault);
        return aag != null ? aag.getVolumeGroupId() : DEFAULT_VOLUME_GROUP;
    }

    @Nullable
    private static AudioAttributesGroup getAudioAttributesGroupForAttributes(
            @NonNull AudioAttributes attributes, int zoneId, boolean fallbackOnDefault) {
        Objects.requireNonNull(attributes, "attributes must not be null");
        int matchScore = NO_MATCH;
        AudioAttributesGroup bestAudioAttributesGroupOrDefault = null;
        for (AudioProductStrategy productStrategy : getAudioProductStrategies()) {
            Pair<Integer, AudioAttributesGroup> scoredAag =
                    productStrategy.getScoredAttributeGroupForAttribute(attributes, zoneId);
            int score = scoredAag.first;
            if (score == MATCH_EQUALS) {
                return scoredAag.second;
            }
            if (score > matchScore) {
                matchScore = score;
                bestAudioAttributesGroupOrDefault = scoredAag.second;
            }
        }
        return (matchScore != MATCH_ON_DEFAULT_SCORE && matchScore != MATCH_ON_ZONE_ID_SCORE
                || fallbackOnDefault) ?
            bestAudioAttributesGroupOrDefault : null;
    }

    private static List<AudioProductStrategy> initializeAudioProductStrategies() {
        ArrayList<AudioProductStrategy> apsList = new ArrayList<>();
        int status = native_list_audio_product_strategies(apsList);
        if (status != AudioSystem.SUCCESS) {
            Log.w(TAG, ": initializeAudioProductStrategies failed");
        }
        return apsList.stream().filter(aps -> !aps.isInternalStrategy())
                .collect(Collectors.toList());
    }

    private static native int native_list_audio_product_strategies(
            ArrayList<AudioProductStrategy> strategies);

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AudioProductStrategy thatStrategy = (AudioProductStrategy) o;

        return mId == thatStrategy.mId && mZoneId == thatStrategy.mZoneId
                && Objects.equals(mName, thatStrategy.mName)
                && Arrays.equals(mAudioAttributesGroups, thatStrategy.mAudioAttributesGroups);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mName, Arrays.hashCode(mAudioAttributesGroups));
    }

    /**
     * @param name of the product strategy
     * @param id of the product strategy
     * @param aag {@link AudioAttributesGroup} associated to the given product strategy
     */
    private AudioProductStrategy(@NonNull String name, int id, int zoneId,
            @NonNull AudioAttributesGroup[] aag) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(aag, "AudioAttributesGroups must not be null");
        mName = name;
        mId = id;
        mZoneId = zoneId;
        mAudioAttributesGroups = aag;
        for (AudioAttributesGroup audioAttributesGroup : mAudioAttributesGroups) {
            audioAttributesGroup.setProductStrategyId(mId);
        }
    }

    /**
     * @hide
     * @return the product strategy ID (which is the generalisation of Car Audio Usage / legacy
     *         routing_strategy linked to {@link AudioAttributes#getUsage()}).
     */
    @SystemApi
    public int getId() {
        return mId;
    }

    /**
     * @hide
     * @return the product strategy zone ID, default is {@code DEFAULT_ZONE_ID}.
     */
    @FlaggedApi(Flags.FLAG_MULTI_ZONE_AUDIO)
    @SystemApi
    public int getZoneId() {
        return mZoneId;
    }

    /**
     * @hide
     * @return the product strategy name (which is the generalisation of Car Audio Usage / legacy
     *         routing_strategy linked to {@link AudioAttributes#getUsage()}).
     */
    @SystemApi
    @NonNull public String getName() {
        return mName;
    }

    /**
     * @hide
     * @return first {@link AudioAttributes} associated to this product strategy.
     */
    @SystemApi
    public @NonNull AudioAttributes getAudioAttributes() {
        // We need a choice, so take the first one
        return mAudioAttributesGroups.length == 0 ? DEFAULT_ATTRIBUTES
                : mAudioAttributesGroups[0].getAudioAttributes();
    }

    /**
     * @hide
     * @param streamType legacy stream type used for volume operation only
     * @return the {@link AudioAttributes} relevant for the given streamType.
     *         If none is found, it builds the default attributes.
     */
    @Nullable
    public AudioAttributes getAudioAttributesForLegacyStreamType(int streamType) {
        AudioAttributesGroup aag = getAudioAttributeGroupForLegacyStreamType(streamType);
        return aag != null ? aag.getAudioAttributes() : null;
    }

    /**
     * @hide
     * @param aa the {@link AudioAttributes} to be considered
     * @return the legacy stream type relevant for the given {@link AudioAttributes}.
     *         If none is found, it return DEFAULT stream type.
     */
    @TestApi
    public int getLegacyStreamTypeForAudioAttributes(@NonNull AudioAttributes attributes) {
        Pair<Integer, AudioAttributesGroup> scoredAag =
                getScoredAttributeGroupForAttribute(attributes, mZoneId);
        AudioAttributesGroup aag = scoredAag.second;
        int score = scoredAag.first;
        return (aag != null && score != MATCH_ON_DEFAULT_SCORE && score != MATCH_ON_ZONE_ID_SCORE)
                ? aag.getStreamType() : AudioSystem.STREAM_DEFAULT;
    }

    /**
     * @hide
     * @param aa the {@link AudioAttributes} to be considered
     * @return true if the {@link AudioProductStrategy} supports the given {@link AudioAttributes},
     *         false otherwise.
     */
    @SystemApi
    public boolean supportsAudioAttributes(@NonNull AudioAttributes aa) {
        return supportsAudioAttributes(aa, mZoneId);
    }

    /**
     * @hide
     * @param aa the {@link AudioAttributes} to be considered
     * @param zoneId to be considered
     * @return true if the {@link AudioProductStrategy} supports the given {@link AudioAttributes},
     *         false otherwise.
     */
    @FlaggedApi(Flags.FLAG_MULTI_ZONE_AUDIO)
    @SystemApi
    public boolean supportsAudioAttributes(@NonNull AudioAttributes aa, int zoneId) {
        int score = getAudioAttributesSupportScore(aa, zoneId);
        return score > 0 && score != MATCH_ON_ZONE_ID_SCORE;
    }

    /**
     * Checks if the strategy supports the given {@link AudioAttributes} and gives a
     * compatibility score.
     * @param attributes to evaluate
     * @param zoneId to be considered
     * @return {@code NO_MATCH} if not supporting the given {@link AudioAttributes},
     * positive or zero score otherwise.
     */
    private int getAudioAttributesSupportScore(@NonNull AudioAttributes aa, int zoneId) {
        return getScoredAttributeGroupForAttribute(aa, zoneId).first;
    }

    private Pair<Integer, AudioAttributesGroup> getScoredAttributeGroupForAttribute(
            @NonNull AudioAttributes aa, int zoneId) {
        Objects.requireNonNull(aa, "AudioAttributes must not be null");
        int bestScore = NO_MATCH;
        AudioAttributesGroup bestAttributGroupOrDefault = null;
        for (AudioAttributesGroup aag : mAudioAttributesGroups) {
            int score = aag.getAttributesMatchingScore(aa, mZoneId, zoneId);
            if (score == MATCH_EQUALS) {
                return new Pair<>(MATCH_EQUALS, aag);
            }
            if (score > bestScore) {
                bestAttributGroupOrDefault = aag;
                bestScore = score;
            }
        }
        return new Pair<>(bestScore, bestAttributGroupOrDefault);
    }

    /**
     * @hide
     * @param streamType legacy stream type used for volume operation only
     * @return the volume group id relevant for the given streamType.
     *         If none is found, {@code DEFAULT_VOLUME_GROUP} is returned.
     */
    @TestApi
    public int getVolumeGroupIdForLegacyStreamType(int streamType) {
        AudioAttributesGroup aag = getAudioAttributeGroupForLegacyStreamType(streamType);
        return aag != null ? aag.getVolumeGroupId() : DEFAULT_VOLUME_GROUP;
    }

    /**
     * Selects the {@link AudioVolumeGroup} id associated with highest matching
     * {@link AudioAttributes} score.
     * @param aa the {@link AudioAttributes} to be considered
     * @return the volume group id associated with the highest and non zero matching
     * {@link AudioAttributes} score, {@code DEFAULT_VOLUME_GROUP} otherwise.
     * @hide
     */
    @TestApi
    public int getVolumeGroupIdForAudioAttributes(@NonNull AudioAttributes attributes) {
        return getVolumeGroupIdForAudioAttributes(attributes, mZoneId);
    }

    /**
     * Selects the {@link AudioVolumeGroup} id associated with highest matching
     * {@link AudioAttributes} score.
     * @param aa the {@link AudioAttributes} to be considered
     * @return the volume group id associated with the highest and non zero matching
     * {@link AudioAttributes} score, {@code DEFAULT_VOLUME_GROUP} otherwise.
     * @hide
     */
    @FlaggedApi(Flags.FLAG_MULTI_ZONE_AUDIO)
    @SystemApi
    public int getVolumeGroupIdForAudioAttributes(@NonNull AudioAttributes attributes, int zoneId) {
        Pair<Integer, AudioAttributesGroup> scoredAag
                = getScoredAttributeGroupForAttribute(attributes, zoneId);
        AudioAttributesGroup aag = scoredAag.second;
        int score = scoredAag.first;
        return (aag != null && score != MATCH_ON_DEFAULT_SCORE && score != MATCH_ON_ZONE_ID_SCORE)
                ? aag.getVolumeGroupId() : DEFAULT_VOLUME_GROUP;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeInt(mId);
        dest.writeInt(mZoneId);
        dest.writeInt(mAudioAttributesGroups.length);
        for (AudioAttributesGroup aag : mAudioAttributesGroups) {
            aag.writeToParcel(dest, flags);
        }
    }

    @NonNull
    public static final Parcelable.Creator<AudioProductStrategy> CREATOR =
            new Parcelable.Creator<AudioProductStrategy>() {
                @Override
                public AudioProductStrategy createFromParcel(@NonNull Parcel in) {
                    String name = in.readString();
                    int id = in.readInt();
                    int zoneId = in.readInt();
                    int nbAttributesGroups = in.readInt();
                    AudioAttributesGroup[] aag = new AudioAttributesGroup[nbAttributesGroups];
                    for (int index = 0; index < nbAttributesGroups; index++) {
                        aag[index] = AudioAttributesGroup.CREATOR.createFromParcel(in);
                    }
                    return new AudioProductStrategy(name, id, zoneId, aag);
                }

                @Override
                public @NonNull AudioProductStrategy[] newArray(int size) {
                    return new AudioProductStrategy[size];
                }
            };

    @NonNull
    @Override
    public String toString() {
        return toString("");
    }

    @NonNull
    String toString(@NonNull String indent) {
        StringBuilder s = new StringBuilder();
        s.append("\n").append(indent).append("Name: ").append(mName);
        s.append(" Id: ").append(Integer.toString(mId));
        s.append(" ZoneId: ");
        s.append(Integer.toString(mZoneId));
        for (AudioAttributesGroup aag : mAudioAttributesGroups) {
            s.append(aag.toString(indent + indent));
        }
        return s.toString();
    }

    /**
     * @hide
     * Default attributes, with default source to be aligned with native.
     */
    private static final @NonNull AudioAttributes DEFAULT_ATTRIBUTES =
            new AudioAttributes.Builder().build();

    /**
     * @hide
     */
    public static void setZoneIdForUserId(int zoneId, int userId) {
        native_set_userid_strategies_affinity(zoneId, userId);
    }

    /**
     * @hide
     */
    public static void resetZoneIdForUserId(int userId) {
        native_remove_userid_strategies_affinity(userId);
    }

    private static native int native_set_userid_strategies_affinity(int zoneId, int userId);

    private static native int native_remove_userid_strategies_affinity(int userId);

    /**
     * @hide
     */
    @TestApi
    public static @NonNull AudioAttributes getDefaultAttributes() {
        return DEFAULT_ATTRIBUTES;
    }

    /** Internal strategies to AudioPolicy, no external volume control allowed */
    private static final String sInternalTag = "reserved_internal_strategy";

    /**
     * To avoid duplicating the logic in java and native, we shall make use of
     * native API native_get_product_strategies_from_audio_attributes
     * Keep in sync with native counterpart code in
     * frameworks/av/media/libaudioclient/AudioProductStrategy::attributesMatchesScore
     * @param refAttr {@link AudioAttributes} to be taken as the reference
     * @param attr {@link AudioAttributes} of the requester.
     */
    private static int attributesMatchesScore(@NonNull AudioAttributes refAttr,
            @NonNull AudioAttributes attr, int refZoneId, int zoneId) {
        Objects.requireNonNull(refAttr, "refAttr must not be null");
        Objects.requireNonNull(attr, "attr must not be null");
        if (zoneId != refZoneId && refZoneId != DEFAULT_ZONE_ID) {
            // Default zone shall match for all zoneId requested to ensure a fallback
            return NO_MATCH;
        }
        int score = MATCH_ON_DEFAULT_SCORE;
        if (refZoneId == zoneId) {
            score |= MATCH_ON_ZONE_ID_SCORE;
        }
        if (refAttr.equals(attr)) {
            return score | MATCH_ATTRIBUTES_EQUALS;
        }
        if (refAttr.equals(DEFAULT_ATTRIBUTES)) {
            return score;
        }
        if (refAttr.getSystemUsage() == AudioAttributes.USAGE_UNKNOWN) {
            score |= MATCH_ON_DEFAULT_SCORE;
        } else if (attr.getSystemUsage() == refAttr.getSystemUsage()) {
            score |= MATCH_ON_USAGE_SCORE;
        } else {
            return NO_MATCH;
        }
        if (refAttr.getContentType() == AudioAttributes.CONTENT_TYPE_UNKNOWN) {
            score |= MATCH_ON_DEFAULT_SCORE;
        } else if (attr.getContentType() == refAttr.getContentType()) {
            score |= MATCH_ON_CONTENT_TYPE_SCORE;
        } else {
            return NO_MATCH;
        }
        String refFormattedTags = TextUtils.join(";", refAttr.getTags());
        String cliFormattedTags = TextUtils.join(";", attr.getTags());
        if (refFormattedTags.length() == 0) {
            score |= MATCH_ON_DEFAULT_SCORE;
        } else if (refFormattedTags.equals(cliFormattedTags)) {
            score |= MATCH_ON_TAGS_SCORE;
        } else {
            return NO_MATCH;
        }
        if (refAttr.getAllFlags() == 0) {
            score |= MATCH_ON_DEFAULT_SCORE;
        } else if ((attr.getAllFlags() != 0)
                && ((attr.getAllFlags() & refAttr.getAllFlags()) == refAttr.getAllFlags())) {
            score |= MATCH_ON_FLAGS_SCORE;
        } else {
            return NO_MATCH;
        }
        return score;
    }

    @Nullable
    private AudioAttributesGroup getAudioAttributeGroupForLegacyStreamType(int streamType) {
        for (AudioAttributesGroup aag : mAudioAttributesGroups) {
            if (aag.supportsStreamType(streamType)) {
                return aag;
            }
        }
        return null;
    }

    private boolean isInternalStrategy() {
        return Arrays.stream(mAudioAttributesGroups).filter(aag -> aag.isInternalStrategy())
                .findFirst().isPresent();
    }

    /** private package */ static boolean isInternalAttributesForStrategy(
            @NonNull AudioAttributes aa) {
        final String formattedTags = TextUtils.join(";", aa.getTags());
        return formattedTags.equals(sInternalTag);
    }

    private static final class AudioAttributesGroup implements Parcelable {
        private int mVolumeGroupId;
        private int mLegacyStreamType;
        private final AudioAttributes[] mAudioAttributes;
        private int mProductStrategyId;

        AudioAttributesGroup(int volumeGroupId, int streamType,
                @NonNull AudioAttributes[] audioAttributes) {
            mVolumeGroupId = volumeGroupId;
            mLegacyStreamType = streamType;
            mAudioAttributes = audioAttributes;
        }

        private boolean isInternalStrategy() {
            return Arrays.stream(mAudioAttributes).filter(aa -> isInternalAttributesForStrategy(aa))
                    .findFirst().isPresent();
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            AudioAttributesGroup thatAag = (AudioAttributesGroup) o;

            return mVolumeGroupId == thatAag.mVolumeGroupId
                    && mLegacyStreamType == thatAag.mLegacyStreamType
                    && Arrays.equals(mAudioAttributes, thatAag.mAudioAttributes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mVolumeGroupId, mLegacyStreamType,
                    Arrays.hashCode(mAudioAttributes));
        }

        public int getStreamType() {
            return mLegacyStreamType;
        }

        public int getVolumeGroupId() {
            return mVolumeGroupId;
        }

        public @NonNull AudioAttributes getAudioAttributes() {
            // We need a choice, so take the first one
            return mAudioAttributes.length == 0 ? DEFAULT_ATTRIBUTES : mAudioAttributes[0];
        }

        void setProductStrategyId(int strategyId) {
            mProductStrategyId = strategyId;
        }

        int getStrategyId() {
            return mProductStrategyId;
        }

        /**
         * Checks if the {@link AudioProductStrategy.AudioAttributesGroup} supports the given
         * {@link AudioAttributes} and gives a compatibility score.
         * @param attributes to evaluate
         * @return {@code NO_MATCH} if not supporting the given {@link AudioAttributes},
         * positive or zero score otherwise.
         */
        public int getAttributesMatchingScore(@NonNull AudioAttributes attributes, int refZoneId,
                                           int zoneId) {
            int strategyScore = NO_MATCH;
            for (AudioAttributes refAa : mAudioAttributes) {
                int attributesGroupScore = attributesMatchesScore(refAa, attributes, refZoneId,
                        zoneId);
                if (attributesGroupScore == MATCH_EQUALS) {
                    return attributesGroupScore;
                }
                strategyScore = Math.max(strategyScore, attributesGroupScore);
            }
            return strategyScore;
        }

        public boolean supportsStreamType(int streamType) {
            return mLegacyStreamType == streamType;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeInt(mVolumeGroupId);
            dest.writeInt(mLegacyStreamType);
            dest.writeInt(mAudioAttributes.length);
            for (AudioAttributes attributes : mAudioAttributes) {
                attributes.writeToParcel(dest, flags | AudioAttributes.FLATTEN_TAGS/*flags*/);
            }
        }

        public static final @android.annotation.NonNull Parcelable.Creator<AudioAttributesGroup> CREATOR =
                new Parcelable.Creator<AudioAttributesGroup>() {
                    @Override
                    public AudioAttributesGroup createFromParcel(@NonNull Parcel in) {
                        int volumeGroupId = in.readInt();
                        int streamType = in.readInt();
                        int nbAttributes = in.readInt();
                        AudioAttributes[] aa = new AudioAttributes[nbAttributes];
                        for (int index = 0; index < nbAttributes; index++) {
                            aa[index] = AudioAttributes.CREATOR.createFromParcel(in);
                        }
                        return new AudioAttributesGroup(volumeGroupId, streamType, aa);
                    }

                    @Override
                    public @NonNull AudioAttributesGroup[] newArray(int size) {
                        return new AudioAttributesGroup[size];
                    }
                };


        @Override
        public @NonNull String toString() {
            return toString("");
        }

        String toString(String indent) {
            StringBuilder s = new StringBuilder();
            s.append("\n" + indent + "Legacy Stream Type: ");
            s.append(Integer.toString(mLegacyStreamType));
            s.append(" Volume Group Id: ");
            s.append(Integer.toString(mVolumeGroupId));

            for (AudioAttributes attribute : mAudioAttributes) {
                s.append("\n" + indent + "-");
                s.append(attribute.toString());
            }
            return s.toString();
        }
    }

    private static final String INDENT = "  ";

    /**
     * @hide
     */
    public static void dump(@NonNull PrintWriter pw) {
        pw.println("- AUDIO PRODUCT STRATEGIES:");
        getAudioProductStrategies().forEach(aps -> {
            pw.printf("%s%s\n", INDENT, aps.toString(INDENT + INDENT));
        });
        pw.println();
        pw.println("- AUDIO VOLUME GROUPS:");
        AudioVolumeGroup.getAudioVolumeGroups().forEach(avg -> {
            pw.printf("%s%s\n", INDENT, avg.toString(INDENT + INDENT));
        });
        pw.println();
    }
}
