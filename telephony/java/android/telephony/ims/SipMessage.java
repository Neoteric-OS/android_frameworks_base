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

package android.telephony.ims;

import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Represents a partially encoded SIP message. See RFC 3261 for more information on how SIP
 * messages are structured and used.
 * <p>
 * The SIP message is represented in a partially encoded form in order to allow for easier
 * verification and should not be used as a generic SIP message container.
 * @hide
 */
public final class SipMessage implements Parcelable {
    private final String mStartLine;
    private final String mHeaderSection;
    private final byte[] mContent;

    /**
     * Represents a partially encoded SIP message.
     *
     * @param startLine The start line of the message, containing either the request-line or
     *                  status-line.
     * @param headerSection A String containing the full unencoded SIP message header.
     * @param content UTF-8 encoded SIP message body.
     */
    public SipMessage(@NonNull String startLine, @NonNull String headerSection,
            @NonNull byte[] content) {
        mStartLine = startLine;
        mHeaderSection = headerSection;
        mContent = content;
    }

    /**
     * Private constructor used only for unparcelling.
     */
    private SipMessage(Parcel source) {
        mStartLine = source.readString();
        mHeaderSection = source.readString();
        mContent = new byte[source.readInt()];
        source.readByteArray(mContent);
    }
    /**
     * @return The start line of the SIP message, which contains either the request-line or
     * status-line.
     */
    public @NonNull String getStartLine() {
        return mStartLine;
    }

    /**
     * @return The full, unencoded header section of the SIP message.
     */
    public @NonNull String getHeaderSection() {
        return mHeaderSection;
    }

    /**
     * @return only the UTF-8 encoded SIP message body.
     */
    public @NonNull byte[] getContent() {
        return mContent;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mStartLine);
        dest.writeString(mHeaderSection);
        dest.writeInt(mContent.length);
        dest.writeByteArray(mContent);
    }

    public static final Creator<SipMessage> CREATOR = new Creator<SipMessage>() {
        @Override
        public SipMessage createFromParcel(Parcel source) {
            return new SipMessage(source);
        }

        @Override
        public SipMessage[] newArray(int size) {
            return new SipMessage[size];
        }
    };
}
