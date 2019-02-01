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

import static android.provider.Telephony.RcsColumns.RcsUnifiedThreadColumns.THREAD_TYPE_1_TO_1;

import android.os.Parcel;
import android.os.Parcelable;

import com.android.ims.RcsTypeIdPair;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @hide
 */
public final class RcsThreadQueryResultDescriptor implements Parcelable {
    private final RcsQueryContinuationToken mContinuationToken;
    private final List<RcsTypeIdPair> mRcsThreadIds;

    public RcsThreadQueryResultDescriptor(
            RcsQueryContinuationToken continuationToken,
            List<RcsTypeIdPair> rcsThreadIds) {
        mContinuationToken = continuationToken;
        mRcsThreadIds = rcsThreadIds;
    }

    RcsThreadQueryResult createRcsThreadQueryResult(RcsControllerCall rcsControllerCall) {
        List<RcsThread> threads = mRcsThreadIds.stream()
                .map(typeIdPair -> typeIdPair.getType() == THREAD_TYPE_1_TO_1
                                ? new Rcs1To1Thread(rcsControllerCall, typeIdPair.getId())
                                : new RcsGroupThread(rcsControllerCall, typeIdPair.getId()))
                .collect(Collectors.toList());

        return new RcsThreadQueryResult(mContinuationToken, threads);
    }

    private RcsThreadQueryResultDescriptor(Parcel in) {
        mContinuationToken = in.readParcelable(RcsQueryContinuationToken.class.getClassLoader());
        mRcsThreadIds = new ArrayList<>();
        in.readList(mRcsThreadIds, RcsTypeIdPair.class.getClassLoader());
    }

    public static final Parcelable.Creator<RcsThreadQueryResultDescriptor> CREATOR =
            new Parcelable.Creator<RcsThreadQueryResultDescriptor>() {
                @Override
                public RcsThreadQueryResultDescriptor createFromParcel(Parcel in) {
                    return new RcsThreadQueryResultDescriptor(in);
                }

                @Override
                public RcsThreadQueryResultDescriptor[] newArray(int size) {
                    return new RcsThreadQueryResultDescriptor[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mContinuationToken, flags);
        dest.writeList(mRcsThreadIds);
    }
}
