// /*
//  * Copyright (C) 2023 The Android Open Source Project
//  *
//  * Licensed under the Apache License, Version 2.0 (the "License");
//  * you may not use this file except in compliance with the License.
//  * You may obtain a copy of the License at
//  *
//  *      http://www.apache.org/licenses/LICENSE-2.0
//  *
//  * Unless required by applicable law or agreed to in writing, software
//  * distributed under the License is distributed on an "AS IS" BASIS,
//  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  * See the License for the specific language governing permissions and
//  * limitations under the License.
//  */

// package com.android.server.security;

// import android.hardware.security.keymint.AttestationKey;
// import android.hardware.security.keymint.BeginResult;
// import android.hardware.security.keymint.HardwareAuthToken;
// import android.hardware.security.keymint.IKeyMintDevice;
// import android.hardware.security.keymint.KeyCharacteristics;
// import android.hardware.security.keymint.KeyCreationResult;
// import android.hardware.security.keymint.KeyMintHardwareInfo;
// import android.hardware.security.keymint.KeyParameter;
// import android.hardware.security.secureclock.TimeStampToken;
// import android.os.Binder;
// import android.os.RemoteException;
// import android.os.ServiceManager;
// import android.os.ServiceSpecificException;
// import android.os.StrictMode;
// import android.security.CheckedRemoteRequest;
// import android.security.KeyStore2;
// import android.security.KeyStoreException;
// import android.system.keystore2.ResponseCode;
// import android.util.Log;

// public class AndroidKeyMintTestDevice {
//     private static final String TAG = "AndroidKeyMintDevice";
//     private final IKeyMintDevice mKeyMintDevice;

//     public AndroidKeyMintTestDevice(IKeyMintDevice keyMintDevice) {
//         Binder.allowBlocking(keyMintDevice.asBinder());
//         this.mKeyMintDevice = keyMintDevice;
//     }

//     public IKeyMintDevice getKeyMintDevice() {
//         return mKeyMintDevice;
//     }

//     //    private static IKeymintDevice getService() {
//     //        return IKeymintDevice.Stub.asInterface(
//     //                ServiceManager.checkService("android.hardware.security.keymint"));
//     //    }

//     public void register(IKeyMintDevice keyMintDevice) {
//         AndroidKeyMintTestDevice keymintTestDevice = new AndoirdKeyMintTestDevice(keyMintDevice);
//         ServiceManager.addService("android_keymint_test_device", keymintTestDevice);
//     }

//     /** Informs Keystore 2.0 that an off body event was detected. */
//     private <R> R handleExceptions(CheckedRemoteRequest<R> request) throws KeyStoreException {
//         try {
//             return request.execute();
//         } catch (ServiceSpecificException e) {
//             throw KeyStore2.getKeyStoreException(e.errorCode, e.getMessage());
//         } catch (RemoteException e) {
//             // Log exception and report invalid operation handle.
//             // This should prompt the caller drop the reference to this operation and retry.
//             Log.e(TAG, "Could not connect to Keystore.", e);
//             throw new KeyStoreException(ResponseCode.SYSTEM_ERROR, "", e.getMessage());
//         }
//     }

//     /** Informs Keystore 2.0 that an off body event was detected. */
//     public KeyMintHardwareInfo getHardwareInfo() throws KeyStoreException {
//         StrictMode.noteDiskWrite();

//         return handleExceptions(() -> mKeyMintDevice.getHardwareInfo());
//     }

//     /** Informs Keystore 2.0 that an off body event was detected. */
//     public void addRngEntropy(byte[] data) throws KeyStoreException {
//         StrictMode.noteDiskWrite();

//         handleExceptions(
//                 () -> {
//                     mKeyMintDevice.addRngEntropy(data);
//                     return 0;
//                 });
//     }

//     /** Informs Keystore 2.0 that an off body event was detected. */
//     public KeyCreationResult generateKey(KeyParameter[] keyParams, AttestationKey attestationKey)
//             throws KeyStoreException {
//         StrictMode.noteDiskWrite();

//         return handleExceptions(() -> mKeyMintDevice.generateKey(keyParams, attestationKey));
//     }

//     /** Informs Keystore 2.0 that an off body event was detected. */
//     public KeyCreationResult importKey(
//             KeyParameter[] keyParams, int keyFormat, byte[] keyData, AttestationKey attestationKey)
//             throws KeyStoreException {
//         StrictMode.noteDiskWrite();

//         return handleExceptions(
//                 () -> mKeyMintDevice.importKey(keyParams, keyFormat, keyData, attestationKey));
//     }

//     /** Informs Keystore 2.0 that an off body event was detected. */
//     public KeyCreationResult importWrappedKey(
//             byte[] wrappedKeyData,
//             byte[] wrappingKeyBlob,
//             byte[] maskingKey,
//             KeyParameter[] unwrappingParams,
//             int passwordDid,
//             int biometricSid)
//             throws KeyStoreException {
//         StrictMode.noteDiskWrite();

//         return handleExceptions(
//                 () ->
//                         mKeyMintDevice.importWrappedKey(
//                                 wrappedKeyData,
//                                 wrappingKeyBlob,
//                                 maskingKey,
//                                 unwrappingParams,
//                                 passwordDid,
//                                 biometricSid));
//     }

//     /** Informs Keystore 2.0 that an off body event was detected. */
//     public byte[] upgradeKey(byte[] keyBlobToUpgrade, KeyParameter[] upgradeParams)
//             throws KeyStoreException {
//         StrictMode.noteDiskWrite();

//         return handleExceptions(() -> mKeyMintDevice.upgradeKey(keyBlobToUpgrade, upgradeParams));
//     }

//     /** Informs Keystore 2.0 that an off body event was detected. */
//     public void deleteKey(byte[] keyBlob) throws KeyStoreException {
//         StrictMode.noteDiskWrite();

//         handleExceptions(
//                 () -> {
//                     mKeyMintDevice.deleteKey(keyBlob);
//                     return 0;
//                 });
//     }

//     /** Informs Keystore 2.0 that an off body event was detected. */
//     public void deleteAllKeys() throws KeyStoreException {
//         StrictMode.noteDiskWrite();

//         handleExceptions(
//                 () -> {
//                     mKeyMintDevice.deleteAllKeys();
//                     return 0;
//                 });
//     }

//     /** Informs Keystore 2.0 that an off body event was detected. */
//     public void destroyAttestationIds() throws KeyStoreException {
//         StrictMode.noteDiskWrite();

//         handleExceptions(
//                 () -> {
//                     mKeyMintDevice.destroyAttestationIds();
//                     return 0;
//                 });
//     }

//     /** Informs Keystore 2.0 that an off body event was detected. */
//     public BeginResult begin(
//             int purpose, byte[] keyBlob, KeyParameter[] keyParams, HardwareAuthToken authToken)
//             throws KeyStoreException {
//         StrictMode.noteDiskWrite();

//         return handleExceptions(() -> mKeyMintDevice.begin(purpose, keyBlob, keyParams, authToken));
//     }

//     /** Informs Keystore 2.0 that an off body event was detected. */
//     public void deviceLocked(Boolean passwordOnly, TimeStampToken timeStampToken)
//             throws KeyStoreException {
//         StrictMode.noteDiskWrite();

//         handleExceptions(
//                 () -> {
//                     mKeyMintDevice.deviceLocked(passwordOnly, timeStampToken);
//                     return 0;
//                 });
//     }

//     /** Informs Keystore 2.0 that an off body event was detected. */
//     public void earlyBootEnded() throws KeyStoreException {
//         StrictMode.noteDiskWrite();

//         handleExceptions(
//                 () -> {
//                     mKeyMintDevice.earlyBootEnded();
//                     return 0;
//                 });
//     }

//     /** Informs Keystore 2.0 that an off body event was detected. */
//     public void convertStorageKeyToEphemeral(byte[] storageKeyBlob) throws KeyStoreException {
//         StrictMode.noteDiskWrite();

//         handleExceptions(
//                 () -> {
//                     mKeyMintDevice.convertStorageKeyToEphemeral(storageKeyBlob);
//                     return 0;
//                 });
//     }

//     /** Informs Keystore 2.0 that an off body event was detected. */
//     public KeyCharacteristics[] getKeyCharacteristics(byte[] keyBlob, byte[] appId, byte[] appData)
//             throws KeyStoreException {
//         StrictMode.noteDiskWrite();

//         return handleExceptions(
//                 () -> mKeyMintDevice.getKeyCharacteristics(keyBlob, appId, appData));
//     }

//     /** Informs Keystore 2.0 that an off body event was detected. */
//     public byte[] getRootOfTrustChallenge() throws KeyStoreException {
//         StrictMode.noteDiskWrite();

//         return handleExceptions(() -> mKeyMintDevice.getRootOfTrustChallenge());
//     }

//     /** Informs Keystore 2.0 that an off body event was detected. */
//     public byte[] getRootOfTrust(byte[] challenge) throws KeyStoreException {
//         StrictMode.noteDiskWrite();

//         return handleExceptions(() -> mKeyMintDevice.getRootOfTrust(challenge));
//     }

//     /** Informs Keystore 2.0 that an off body event was detected. */
//     public void sendRootOfTrust(byte[] rootOfTrust) throws KeyStoreException {
//         StrictMode.noteDiskWrite();

//         handleExceptions(
//                 () -> {
//                     mKeyMintDevice.sendRootOfTrust(rootOfTrust);
//                     return 0;
//                 });
//     }
// }
