/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.bluetooth;

import android.annotation.IntDef;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This class represents a Broadcast source channel originating from an LE Audio Broadcast Source.
 *
 * The Broadcast Audio Source Endpoint (BASE) structure is a hierarchical tri-level structure that
 * is transmitted by an LE Audio Broadcast Source. The first level of the structure represents a
 * Broadcast Isochronous Group (BIG). The second level of the structure represents one or more
 * Broadcast Isochronous Streams (BIS) contained within a BIG. The third level of the structure
 * represents a particular BIS. For example, a BIG might contain two BISs within a single subgroup.
 * One BIS might represent the left audio channel and the other BIS might represent the right audio
 * channel.
 *
 * mIndex : index of the BIS within the subgroup
 * mDescription: the type of broadcast data being transmitted
 * mStatus: synchronization status of the broadcast channel
 * mSubgroupId: The ID of the subgroup within which this BIS (channel) resides
 * mMetadata: Metadata of the subgroup this BIS (channel) belongs to
 *
 * @hide
 */
public final class BluetoothLeBroadcastSourceChannel implements Parcelable {
    private static final String TAG = "BluetoothLeBroadcastSourceChannel";

    /**
     * Constants used to represent the synchronization state of a broadcast channel
     *
     * @hide
     */
    @IntDef(prefix = {"LE_AUDIO_BROADCAST_CHANNEL_STATUS_"}, value = {
      LE_AUDIO_BROADCAST_CHANNEL_STATUS_SYNC,
      LE_AUDIO_BROADCAST_CHANNEL_STATUS_NO_SYNC
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface LeAudioBroadcastChannelStatus {}

    /**
     * Indicates that LE Audio Broadcast Channel is in synchronized state
     *
     * @hide
     */
    public static final int LE_AUDIO_BROADCAST_CHANNEL_STATUS_SYNC = 10;

    /**
     * Indicates that LE Audio Broadcast Channel is not in synchronized state
     *
     * @hide
     */
    public static final int LE_AUDIO_BROADCAST_CHANNEL_STATUS_NO_SYNC = 11;

    private int mIndex;
    private String mDescription;
    private @LeAudioBroadcastChannelStatus int mStatus;
    private int mSubGroupId;
    private byte[] mMetadata;

    public BluetoothLeBroadcastSourceChannel(int index, String description, boolean status,
            int subGroupId, byte[] metadata) {
        mIndex = index;
        mDescription = description;
        mStatus = status;
        mSubGroupId = subGroupId;

        if (metadata != null && metadata.length != 0) {
            mMetadata = new byte[metadata.length];
            System.arraycopy(metadata, 0, mMetadata, 0, metadata.length);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof BluetoothLeBroadcastSourceChannel) {
            BluetoothLeBroadcastSourceChannel other = (BluetoothLeBroadcastSourceChannel) o;
            return (other.mIndex == mIndex
                    && other.mDescription == mDescription
                    && other.mStatus == mStatus);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mIndex, mDescription, mStatus);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return mDescription;
    }

    /**
     * Get the index of the channel within the subgroup
     *
     * @return index of the channel within its subgroup
     *
     * @hide
     */
    public int getIndex() {
        return mIndex;
    }

    /**
     * Get the description for this channel
     *
     * @return description of the channel
     *
     * @hide
     */
    public String getDescription() {
        return mDescription;
    }

    /**
     * Get the status of the channel
     *
     * @hide
     */
    @LeAudioBroadcastChannelStatus
    public int getStatus() {
        return mStatus;
    }

    /**
     * Get the metadata associated with this channel
     *
     * @return byte array representing the metadata for this channel
     *
     * @hide
     */
    public byte[] getMetadata() {
        return mMetadata;
    }

    /**
     * Get the ID of the subgroup this channel (BIS) belongs to
     *
     * @hide
     */
    public int getSubGroupId() {
        return mSubGroupId;
    }

    /**
     * Set the status of the channel
     *
     * @hide
     */
    @LeAudioBroadcastChannelStatus
    public void setStatus(int status) {
        mStatus = status;
    }

    public static final @android.annotation.NonNull
            Parcelable.Creator<BluetoothLeBroadcastSourceChannel> CREATOR =
            new Parcelable.Creator<BluetoothLeBroadcastSourceChannel>() {
                public BluetoothLeBroadcastSourceChannel createFromParcel(Parcel in) {
                    final int index = in.readInt();
                    final String desc = in.readString();
                    final boolean status = in.readBoolean();
                    final int subGroupId = in.readInt();

                    final int metadataLength = in.readInt();
                    byte[] metadata = null;
                    if (metadataLength > 0) {
                        metadata = new byte[metadataLength];
                        in.readByteArray(metadata);
                    }

                    BluetoothLeBroadcastSourceChannel srcChannel =
                            new BluetoothLeBroadcastSourceChannel(index, desc, status,
                                    subGroupId, metadata);
                    log("createFromParcel:" + srcChannel);
                    return srcChannel;
                }

                public BluetoothLeBroadcastSourceChannel[] newArray(int size) {
                    return new BluetoothLeBroadcastSourceChannel[size];
                }
            };



    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mIndex);
        out.writeString(mDescription);
        out.writeBoolean(mStatus);
        out.writeInt(mSubGroupId);
        if (mMetadata != null) {
            out.writeInt(mMetadata.length);
            out.writeByteArray(mMetadata);
        } else {
            out.writeInt(0);
        }
        log("writeToParcel:" + toString());
    }

    private static void log(String msg) {
        if (DBG) Log.d(TAG, msg);
    }
};

