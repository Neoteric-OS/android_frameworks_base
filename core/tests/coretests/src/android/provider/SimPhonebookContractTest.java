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

package android.provider;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.testng.Assert.assertThrows;

import android.content.ContentValues;
import android.os.Parcel;
import android.provider.SimPhonebookContract.SimPhonebookException;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SimPhonebookContractTest {

    @Test
    public void getContentUri_invalidEfType_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                SimPhonebookContract.SimRecords.getContentUri(1, 100)
        );
        assertThrows(IllegalArgumentException.class, () ->
                SimPhonebookContract.SimRecords.getContentUri(1, -1)
        );
        assertThrows(IllegalArgumentException.class, () ->
                SimPhonebookContract.SimRecords.getContentUri(1,
                        SimPhonebookContract.ElementaryFiles.EF_UNKNOWN)
        );
    }

    @Test
    public void getItemUri_invalidEfType_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                SimPhonebookContract.SimRecords.getItemUri(1, 100, 1)
        );
        assertThrows(IllegalArgumentException.class, () ->
                SimPhonebookContract.SimRecords.getItemUri(1, -1, 1)
        );
        assertThrows(IllegalArgumentException.class, () ->
                SimPhonebookContract.SimRecords.getItemUri(1,
                        SimPhonebookContract.ElementaryFiles.EF_UNKNOWN, 1)
        );
    }

    @Test
    public void getItemUri_invalidRecordIndex_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                SimPhonebookContract.SimRecords.getItemUri(1,
                        SimPhonebookContract.ElementaryFiles.EF_ADN, 0)
        );
        assertThrows(IllegalArgumentException.class, () ->
                SimPhonebookContract.SimRecords.getItemUri(1,
                        SimPhonebookContract.ElementaryFiles.EF_ADN, -1)
        );
    }

    private static SimPhonebookException parcelRoundtrip(SimPhonebookException e) {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeException(e);
            parcel.setDataPosition(0);
            parcel.readException();
        } catch (SimPhonebookException thrown) {
            return thrown;
        } finally {
            parcel.recycle();
        }
        Assert.fail("Expected SimPhonebookException but none was thrown");
        throw new IllegalStateException("unreachable");
    }

    @Test
    public void simPhonebookException_parcel() {
        ContentValues values = new ContentValues();
        values.put("name", "Name");
        values.put("phone_number", "123");

        SimPhonebookException e = parcelRoundtrip(new SimPhonebookException(
                SimPhonebookException.ERROR_INVALID_DATA, "Bad data", values));

        assertThat(SimPhonebookException.getErrorName(e.getErrorCode()))
                .isEqualTo(SimPhonebookException.getErrorName(
                        SimPhonebookException.ERROR_INVALID_DATA));
        assertThat(e.getMessage()).isEqualTo("Bad data");
        assertThat(e.getSanitizedValues()).isEqualTo(new ContentValues(values));

        e = parcelRoundtrip(new SimPhonebookException(SimPhonebookException.ERROR_MISSING_SIM));
        assertThat(SimPhonebookException.getErrorName(e.getErrorCode()))
                .isEqualTo(SimPhonebookException.getErrorName(
                        SimPhonebookException.ERROR_MISSING_SIM));
        assertThat(e.getMessage()).isEqualTo(
                SimPhonebookException.getErrorName(SimPhonebookException.ERROR_MISSING_SIM));
        assertWithMessage("isEmpty()")
                .that(e.getSanitizedValues().isEmpty()).isTrue();
    }
}

