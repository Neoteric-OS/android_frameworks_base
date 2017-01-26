/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.telecom;

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;

/**
 * Simple data container encapsulating a request to some entity to
 * create a new {@link Connection}.
 */
public final class ConnectionRequest implements Parcelable {

    public static final class Builder {
        private PhoneAccountHandle mAccountHandle;
        private Uri mAddress;
        private Bundle mExtras;
        private int mVideoState = VideoProfile.STATE_AUDIO_ONLY;
        private String mTelecomCallId;
        private boolean mShouldShowIncomingCallUi = false;
        private ParcelFileDescriptor mRttPipeToInCall;
        private ParcelFileDescriptor mRttPipeFromInCall;

        public Builder() { }

        public Builder setAccountHandle(PhoneAccountHandle accountHandle) {
            this.mAccountHandle = accountHandle;
            return this;
        }

        public Builder setAddress(Uri address) {
            this.mAddress = address;
            return this;
        }

        public Builder setExtras(Bundle extras) {
            this.mExtras = extras;
            return this;
        }

        public Builder setVideoState(int videoState) {
            this.mVideoState = videoState;
            return this;
        }

        public Builder setTelecomCallId(String telecomCallId) {
            this.mTelecomCallId = telecomCallId;
            return this;
        }

        public Builder setShouldShowIncomingCallUi(boolean shouldShowIncomingCallUi) {
            this.mShouldShowIncomingCallUi = shouldShowIncomingCallUi;
            return this;
        }

        /** @hide */
        public Builder setRttPipeFromInCall(ParcelFileDescriptor rttPipeFromInCall) {
            this.mRttPipeFromInCall = rttPipeFromInCall;
            return this;
        }

        /** @hide */
        public Builder setRttPipeToInCall(ParcelFileDescriptor rttPipeToInCall) {
            this.mRttPipeToInCall = rttPipeToInCall;
            return this;
        }

        public ConnectionRequest build() {
            return new ConnectionRequest(
                    mAccountHandle,
                    mAddress,
                    mExtras,
                    mVideoState,
                    mTelecomCallId,
                    mShouldShowIncomingCallUi,
                    mRttPipeFromInCall,
                    mRttPipeToInCall);
        }
    }

    private final PhoneAccountHandle mAccountHandle;
    private final Uri mAddress;
    private final Bundle mExtras;
    private final int mVideoState;
    private final String mTelecomCallId;
    private final boolean mShouldShowIncomingCallUi;
    private final ParcelFileDescriptor mRttPipeToInCall;
    private final ParcelFileDescriptor mRttPipeFromInCall;

    /**
     * @param accountHandle The accountHandle which should be used to place the call.
     * @param handle The handle (e.g., phone number) to which the {@link Connection} is to connect.
     * @param extras Application-specific extra data.
     * @deprecated Use {@link ConnectionRequest.Builder} instead.
     */
    public ConnectionRequest(
            PhoneAccountHandle accountHandle,
            Uri handle,
            Bundle extras) {
        this(accountHandle, handle, extras, VideoProfile.STATE_AUDIO_ONLY, null, false, null, null);
    }

    /**
     * @param accountHandle The accountHandle which should be used to place the call.
     * @param handle The handle (e.g., phone number) to which the {@link Connection} is to connect.
     * @param extras Application-specific extra data.
     * @param videoState Determines the video state for the connection.
     * @deprecated Use {@link ConnectionRequest.Builder} instead.
     */
    public ConnectionRequest(
            PhoneAccountHandle accountHandle,
            Uri handle,
            Bundle extras,
            int videoState) {
        this(accountHandle, handle, extras, videoState, null, false, null, null);
    }

    /**
     * @param accountHandle The accountHandle which should be used to place the call.
     * @param handle The handle (e.g., phone number) to which the {@link Connection} is to connect.
     * @param extras Application-specific extra data.
     * @param videoState Determines the video state for the connection.
     * @param telecomCallId The telecom call ID.
     * @param shouldShowIncomingCallUi For a self-managed {@link ConnectionService}, will be
     *                                 {@code true} if the {@link ConnectionService} should show its
     *                                 own incoming call UI for an incoming call.  When
     *                                 {@code false}, Telecom shows the incoming call UI.
     * @deprecated Use {@link ConnectionRequest.Builder} instead.
     * @hide
     */
    public ConnectionRequest(
            PhoneAccountHandle accountHandle,
            Uri handle,
            Bundle extras,
            int videoState,
            String telecomCallId,
            boolean shouldShowIncomingCallUi) {
        this(accountHandle, handle, extras, videoState, telecomCallId,
                shouldShowIncomingCallUi, null, null);
    }

    private ConnectionRequest(
            PhoneAccountHandle accountHandle,
            Uri handle,
            Bundle extras,
            int videoState,
            String telecomCallId,
            boolean shouldShowIncomingCallUi,
            ParcelFileDescriptor rttPipeFromInCall,
            ParcelFileDescriptor rttPipeToInCall) {
        mAccountHandle = accountHandle;
        mAddress = handle;
        mExtras = extras;
        mVideoState = videoState;
        mTelecomCallId = telecomCallId;
        mShouldShowIncomingCallUi = shouldShowIncomingCallUi;
        mRttPipeFromInCall = rttPipeFromInCall;
        mRttPipeToInCall = rttPipeToInCall;
    }

    private ConnectionRequest(Parcel in) {
        mAccountHandle = in.readParcelable(getClass().getClassLoader());
        mAddress = in.readParcelable(getClass().getClassLoader());
        mExtras = in.readParcelable(getClass().getClassLoader());
        mVideoState = in.readInt();
        mTelecomCallId = in.readString();
        mShouldShowIncomingCallUi = in.readInt() == 1;
        mRttPipeFromInCall = in.readParcelable(getClass().getClassLoader());
        mRttPipeToInCall = in.readParcelable(getClass().getClassLoader());
    }

    /**
     * The account which should be used to place the call.
     */
    public PhoneAccountHandle getAccountHandle() { return mAccountHandle; }

    /**
     * The handle (e.g., phone number) to which the {@link Connection} is to connect.
     */
    public Uri getAddress() { return mAddress; }

    /**
     * Application-specific extra data. Used for passing back information from an incoming
     * call {@code Intent}, and for any proprietary extensions arranged between a client
     * and servant {@code ConnectionService} which agree on a vocabulary for such data.
     */
    public Bundle getExtras() { return mExtras; }

    /**
     * Describes the video states supported by the client requesting the connection.
     * Valid values: {@link VideoProfile#STATE_AUDIO_ONLY},
     * {@link VideoProfile#STATE_BIDIRECTIONAL},
     * {@link VideoProfile#STATE_TX_ENABLED},
     * {@link VideoProfile#STATE_RX_ENABLED}.
     *
     * @return The video state for the connection.
     */
    public int getVideoState() {
        return mVideoState;
    }

    /**
     * Returns the internal Telecom ID associated with the connection request.
     *
     * @return The Telecom ID.
     * @hide
     */
    public String getTelecomCallId() {
        return mTelecomCallId;
    }

    /**
     * For a self-managed {@link ConnectionService}, indicates for an incoming call whether the
     * {@link ConnectionService} should show its own incoming call UI for an incoming call.
     *
     * @return {@code true} if the {@link ConnectionService} should show its own incoming call UI.
     * When {@code false}, Telecom shows the incoming call UI for the call.
     * @hide
     */
    public boolean shouldShowIncomingCallUi() {
        return mShouldShowIncomingCallUi;
    }

    /**
     * Gets the ParcelFileDescriptor that is used to send RTT text from the connection service
     * to the in-call UI
     * @return The ParcelFileDescriptor that should be used for communication
     * @hide
     */
    public ParcelFileDescriptor getRttPipeToInCall() {
        return mRttPipeToInCall;
    }

    /**
     * Gets the {@link ParcelFileDescriptor} that is used to send RTT text from the in-call UI to
     * the connection service.
     * @return The {@link ParcelFileDescriptor} that should be used for communication
     * @hide
     */
    public ParcelFileDescriptor getRttPipeFromInCall() {
        return mRttPipeFromInCall;
    }

    /**
     * Convenience method for determining whether the ConnectionRequest is requesting an RTT session
     * @return True if RTT is requested, false otherwise.
     * @hide
     */
    public boolean isRequestingRtt() {
        return mRttPipeFromInCall != null && mRttPipeToInCall != null;
    }

    @Override
    public String toString() {
        return String.format("ConnectionRequest %s %s",
                mAddress == null
                        ? Uri.EMPTY
                        : Connection.toLogSafePhoneNumber(mAddress.toString()),
                mExtras == null ? "" : mExtras);
    }

    public static final Creator<ConnectionRequest> CREATOR = new Creator<ConnectionRequest> () {
        @Override
        public ConnectionRequest createFromParcel(Parcel source) {
            return new ConnectionRequest(source);
        }

        @Override
        public ConnectionRequest[] newArray(int size) {
            return new ConnectionRequest[size];
        }
    };

    /**
     * {@inheritDoc}
     */
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel destination, int flags) {
        destination.writeParcelable(mAccountHandle, 0);
        destination.writeParcelable(mAddress, 0);
        destination.writeParcelable(mExtras, 0);
        destination.writeInt(mVideoState);
        destination.writeString(mTelecomCallId);
        destination.writeInt(mShouldShowIncomingCallUi ? 1 : 0);
        destination.writeParcelable(mRttPipeFromInCall, 0);
        destination.writeParcelable(mRttPipeToInCall, 0);
    }
}
