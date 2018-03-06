/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.iris;

import android.content.Context;
import android.hardware.iris.Iris;
import android.hardware.iris.IIrisServiceReceiver;
import android.os.IBinder;
import android.util.Slog;
import java.util.ArrayList;
import java.util.List;

/**
 * An internal class to help clean up unknown irises in the hardware and software
 */
public abstract class InternalEnumerateClient extends EnumerateClient {

    private List<Iris> mEnrolledList;
    private List<Iris> mEnumeratedList = new ArrayList<>(); // list of iris to delete

    public InternalEnumerateClient(Context context, long halDeviceId, IBinder token,
            IIrisServiceReceiver receiver, int groupId, int userId,
            boolean restricted, String owner, List<Iris> enrolledList) {

        super(context, halDeviceId, token, receiver, userId, groupId, restricted, owner);
        mEnrolledList = enrolledList;
    }

    private void handleEnumeratedIris(int irisId, int groupId, int remaining) {

        boolean matched = false;
        for (int i=0; i<mEnrolledList.size(); i++) {
            if (mEnrolledList.get(i).getIrisId() == irisId) {
                mEnrolledList.remove(i);
                matched = true;
                Slog.e(TAG, "Matched iris id=" + irisId);
                break;
            }
        }

        // irisId 0 means no irises are in hardware
        if (!matched && irisId != 0) {
            Iris iris = new Iris("", groupId, irisId, getHalDeviceId());
            mEnumeratedList.add(iris);
        }
    }

    private void doIrisCleanup() {

        if (mEnrolledList == null) {
            return;
        }

        for (Iris f : mEnrolledList) {
            Slog.e(TAG, "Internal Enumerate: Removing dangling enrolled iris: "
                    + f.getName() + " " + f.getIrisId() + " " + f.getGroupId()
                    + " " + f.getDeviceId());

            IrisUtils.getInstance().removeIrisIdForUser(getContext(),
                    f.getIrisId(), getTargetUserId());
        }
        mEnrolledList.clear();
    }

    public List<Iris> getEnumeratedList() {
        return mEnumeratedList;
    }

    @Override
    public boolean onEnumerationResult(int irisId, int groupId, int remaining) {

        handleEnumeratedIris(irisId, groupId, remaining);
        if (remaining == 0) {
            doIrisCleanup();
        }

        return remaining == 0;
    }

}
