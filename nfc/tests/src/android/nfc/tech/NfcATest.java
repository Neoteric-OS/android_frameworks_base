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

package android.nfc.tech;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.nfc.Tag;
import android.os.RemoteException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class NfcATest {
    @Mock
    private Tag mMockTag;
    private NfcA mNfcA;


    @Before
    public void setUp() throws RemoteException {
        MockitoAnnotations.initMocks(this);

        mNfcA = new NfcA(mMockTag);
    }

    @Test
    public void testGetNfcAWhenTagTechNotNfcA() {
        when(mMockTag.hasTech(TagTechnology.NFC_A)).thenReturn(false);
        assertNotNull(NfcA.get(mMockTag));
        verify(mMockTag).getTechExtras(TagTechnology.NFC_A);
    }

    @Test
    public void testGetNfcAWhenTagTechNfcA() {
        when(mMockTag.hasTech(TagTechnology.NFC_A)).thenReturn(true);
        assertNull(NfcA.get(mMockTag));
        verify(mMockTag).getTechExtras(TagTechnology.NFC_A);
    }

    @Test
    public void testGetAtga() {
        assertNull(mNfcA.getAtqa());
    }
}
