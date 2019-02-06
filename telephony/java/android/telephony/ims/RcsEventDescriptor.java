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
package android.telephony.ims;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * The base class for events that can happen on {@link RcsParticipant}s and {@link RcsThread}s.
 * @hide - TODO(109759350) make this public
 */
public abstract class RcsEventDescriptor implements Parcelable {
    protected long mTimestamp;

    RcsEventDescriptor(long timestamp) {
        mTimestamp = timestamp;
    }

    protected abstract RcsEvent createRcsEvent();

    RcsEventDescriptor(Parcel in) {
        mTimestamp = in.readLong();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mTimestamp);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
