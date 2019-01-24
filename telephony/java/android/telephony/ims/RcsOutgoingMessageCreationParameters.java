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

package android.telephony.ims;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * @hide
 */
public class RcsOutgoingMessageCreationParameters extends RcsMessageCreationParameters implements
        Parcelable {
    /**
     * A builder to instantiate and persist an {@link RcsOutgoingMessage}
     */
    public static class Builder extends RcsMessageCreationParameters.Builder {
        /**
         * Creates configuration parameters for a new message.
         */
        public RcsOutgoingMessageCreationParameters build() {
            return new RcsOutgoingMessageCreationParameters(this);
        }
    }

    private RcsOutgoingMessageCreationParameters(Builder builder) {
        super(builder);
    }

    protected RcsOutgoingMessageCreationParameters(Parcel in) {
        super(in);
    }

    public static final Creator<RcsOutgoingMessageCreationParameters> CREATOR =
            new Creator<RcsOutgoingMessageCreationParameters>() {
                @Override
                public RcsOutgoingMessageCreationParameters createFromParcel(Parcel in) {
                    return new RcsOutgoingMessageCreationParameters(in);
                }

                @Override
                public RcsOutgoingMessageCreationParameters[] newArray(int size) {
                    return new RcsOutgoingMessageCreationParameters[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
    }
}
