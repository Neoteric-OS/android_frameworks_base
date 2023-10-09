/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.security;

import android.content.Intent;
import android.hardware.security.keymint.IKeyMintDevice;
import android.hardware.security.keymint.KeyMintHardwareInfo;
import android.hardware.security.keymint.KeyCreationResult;
import android.hardware.security.keymint.BeginResult;
import android.hardware.security.keymint.KeyCharacteristics;
import android.hardware.security.keymint.KeyParameter;
import android.hardware.security.keymint.HardwareAuthToken;
import android.hardware.security.keymint.AttestationKey;
import android.hardware.security.secureclock.TimeStampToken;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public class KeyMintTestDevice {
    private static String TAG="KeyMintTestDevice";
    private IKeyMintDevice.Stub binder;

    public void onCreate(Intent intent, IKeyMintDevice kmd) {
        Log.d(TAG, "onBind " + intent);
        make_binder(kmd);
    }

    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void make_binder(IKeyMintDevice keyMintDevice) {
        binder = new IKeyMintDevice.Stub() {
            public KeyMintHardwareInfo getHardwareInfo() throws RemoteException  {
                return keyMintDevice.getHardwareInfo();
            }

            public void addRngEntropy(byte[] data) throws RemoteException {
                keyMintDevice.addRngEntropy(data);
            }

            public void deleteAllKeys() throws RemoteException {
                keyMintDevice.deleteAllKeys();
            }

            public void destroyAttestationIds() throws RemoteException {
                keyMintDevice.destroyAttestationIds();
            }

            public void earlyBootEnded() throws RemoteException {
                keyMintDevice.earlyBootEnded();
            }

            public KeyCreationResult generateKey(KeyParameter[] keyParams, AttestationKey attestationKey) throws RemoteException {
                return keyMintDevice.generateKey(keyParams, attestationKey);
            }

            public KeyCreationResult importKey(KeyParameter[] keyParams, int keyFormat, byte[] keyData, AttestationKey attestationKey) throws RemoteException {
                return keyMintDevice.importKey(keyParams, keyFormat, keyData, attestationKey);
            }

            public KeyCreationResult importWrappedKey(byte[] wrappedKeyData, byte[] wrappingKeyBlob, byte[] maskingKey, KeyParameter[] unwrappingParams, long passwordSid, long biometricSid) throws RemoteException {
                return keyMintDevice.importWrappedKey(wrappedKeyData, wrappingKeyBlob, maskingKey, unwrappingParams, passwordSid, biometricSid);
            }

            public byte[] upgradeKey(byte[] keyBlobToUpgrade, KeyParameter[] upgradeParams) throws RemoteException {
                return keyMintDevice.upgradeKey(keyBlobToUpgrade, upgradeParams);
            }

            public void deleteKey(byte[] keyblob) throws RemoteException {
                keyMintDevice.deleteKey(keyblob);
            }

            public BeginResult begin(int purpose, byte[] keyBlob, KeyParameter[] params, HardwareAuthToken authToken) throws RemoteException {
                return keyMintDevice.begin(purpose, keyBlob, params, authToken);
            }

            @Override
            public void deviceLocked(boolean b, TimeStampToken timeStampToken) throws RemoteException {
                keyMintDevice.deviceLocked(b, timeStampToken);

            }

            public KeyCharacteristics[] getKeyCharacteristics(byte[] keyBlob, byte[] appId, byte[] appData) throws RemoteException {
                return keyMintDevice.getKeyCharacteristics(keyBlob, appId, appData);
            }

            public byte[] getRootOfTrustChallenge() throws RemoteException  {
                return keyMintDevice.getRootOfTrustChallenge();
            }

            @Override
            public byte[] getRootOfTrust(byte[] bytes) throws RemoteException {
                return keyMintDevice.getRootOfTrust(bytes);
            }

            @Override
            public void sendRootOfTrust(byte[] bytes) throws RemoteException {
                keyMintDevice.sendRootOfTrust(bytes);
            }

            @Override
            public int getInterfaceVersion() throws RemoteException {
                return keyMintDevice.getInterfaceVersion();
            }

            @Override
            public String getInterfaceHash() throws RemoteException {
                return keyMintDevice.getInterfaceHash();
            }


            public byte[] convertStorageKeyToEphemeral(byte[] storageKeyBlob) throws RemoteException {
                return keyMintDevice.convertStorageKeyToEphemeral(storageKeyBlob);
            }

        };
    }
}