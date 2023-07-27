/*
 * Copyright (c) 2023, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.security.keystore;

import android.hardware.security.keymint.IKeyMintDevice;
import android.hardware.security.keymint.SecurityLevel;

/**
 * @hide
 * This parcelable constitutes and excerpt from the PackageManager's PackageInfo for the purpose of
 * key attestation. It is part of the KeyAttestationApplicationId, which is used by
 * keystore to identify the caller of the keystore API towards a remote party.
 */
interface IKeyMintDeviceTest {
    /**
     * Return an implementation of IKeyMintDevice, that it implemented by Keystore 2.0 itself.
     * The underlying implementation depends on the requested securityLevel:
     * - TRUSTED_ENVIRONMENT or STRONGBOX: implementation is by means of a hardware-backed
     *   Keymaster 4.x instance. In this case, the returned device supports version 1 of
     *   the IKeyMintDevice interface, with some small omissions:
     *     - KeyPurpose::ATTEST_KEY is not supported (b/216437537)
     *     - Specification of the MGF1 digest for RSA-OAEP is not supported (b/216436980)
     *     - Specification of CERTIFICATE_{SUBJECT,SERIAL} is not supported for keys attested
     *       by hardware (b/216468666).
     * - SOFTWARE: implementation is entirely software based.  In this case, the returned device
     *   supports the current version of the IKeyMintDevice interface.
     */
    IKeyMintDevice getKeyMintDevice (SecurityLevel securityLevel);
}
