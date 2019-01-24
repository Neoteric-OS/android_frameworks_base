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
package com.android.tests.ims;

import static android.telephony.ims.RcsThreadQueryParameters.ONLY_GROUP_THREADS;
import static android.telephony.ims.RcsThreadQueryParameters.TIMESTAMP;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.support.test.runner.AndroidJUnit4;
import android.telephony.ims.RcsParticipant;
import android.telephony.ims.RcsThreadQueryParameters;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RcsThreadQueryParametersTest {

    @Test
    public void testCanUnparcel() {
        RcsParticipant rcsParticipant = new RcsParticipant(1);
        RcsThreadQueryParameters rcsThreadQueryParameters = RcsThreadQueryParameters.builder()
                .setThreadType(ONLY_GROUP_THREADS)
                .withParticipant(rcsParticipant)
                .limitResultsTo(50)
                .sortBy(TIMESTAMP)
                .sortAscending(true)
                .build();

        Parcel parcel = Parcel.obtain();
        rcsThreadQueryParameters.writeToParcel(parcel, rcsThreadQueryParameters.describeContents());

        parcel.setDataPosition(0);
        rcsThreadQueryParameters = RcsThreadQueryParameters.CREATOR.createFromParcel(parcel);

        assertThat(rcsThreadQueryParameters.getThreadType()).isEqualTo(ONLY_GROUP_THREADS);
        assertThat(rcsThreadQueryParameters.getRcsParticipantsIds())
                .contains(rcsParticipant.getId());
        assertThat(rcsThreadQueryParameters.getLimit()).isEqualTo(50);
        assertThat(rcsThreadQueryParameters.getSortingProperty()).isEqualTo(TIMESTAMP);
        assertThat(rcsThreadQueryParameters.isAscending()).isTrue();
    }
}
