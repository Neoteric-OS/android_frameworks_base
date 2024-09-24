/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.app;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

@FlaggedApi(Flags.FLAG_APP_START_INFO)
public final class TargetProcessInfo implements Parcelable {
    public final int uid;
    public final int pid;
    public final String processName;

    public TargetProcessInfo(int uid, int pid, @NonNull String processName) {
        this.uid = uid;
        this.pid = pid;
        this.processName = processName;
    }

    public static final @NonNull Parcelable.Creator<TargetProcessInfo> CREATOR =
            new Parcelable.Creator<>() {
                @Override
                public TargetProcessInfo createFromParcel(Parcel in) {
                    return new TargetProcessInfo(in);
                }

                @Override
                public TargetProcessInfo[] newArray(int size) {
                    return new TargetProcessInfo[size];
                }
            };

    private TargetProcessInfo(Parcel in) {
        this.uid = in.readInt();
        this.pid = in.readInt();
        this.processName = in.readString();
    }

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        out.writeInt(uid);
        out.writeInt(pid);
        out.writeString(processName);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
