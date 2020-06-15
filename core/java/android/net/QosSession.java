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

/**
 * Provides identifying information of a qos session.  Sent to an application through
 * {@link QosCallback}.
 *
 * @hide
 */
@SystemApi
public final class QosSession implements Parcelable {

    public static final int TYPE_EPS_BEARER = 1;

    private final long mSessionId;

    private final int mSessionType;

    /**
     * Gets the session id
     *
     * @return the id of the session
     */
    public long getSessionId() {
        return mSessionId;
    }

    /**
     * Gets the session type
     *
     * @return the type of session
     */
    public int getSessionType() {
        return mSessionType;
    }

    /**
     * ..ctor for session
     *
     * @param sessionId the unique session id
     * @param sessionType the type of session
     */
    public QosSession(final int sessionId, @QosSessionType final int sessionType) {
        //Ensures the session id is unique across types of sessions
        mSessionId = (long) sessionType << 32 | sessionId;
        mSessionType = sessionType;
    }

    @NonNull
    @Override
    public String toString() {
        return "QosSession{"
                + "mSessionId=" + mSessionId
                + ", mSessionType=" + mSessionType
                + '}';
    }

    private QosSession() {
        mSessionId = 0;
        mSessionType = 0;
    }

    /**
     * Annotations for types of qos sessions.
     */
    @IntDef(value = {
            TYPE_EPS_BEARER,
    })
    @interface QosSessionType {}

    QosSession(final Parcel in) {
        mSessionId = in.readLong();
        mSessionType = in.readInt();
    }

    @NonNull
    public static final Creator<QosSession> CREATOR = new Creator<QosSession>() {
        @NonNull
        @Override
        public QosSession createFromParcel(@NonNull Parcel in) {
            return new QosSession(in);
        }

        @NonNull
        @Override
        public QosSession[] newArray(int size) {
            return new QosSession[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeLong(mSessionId);
        dest.writeInt(mSessionType);
    }
}
