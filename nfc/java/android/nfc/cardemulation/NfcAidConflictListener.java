/*
 * Copyright 2024 The Android Open Source Project
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

package android.nfc;

/** @hide */
public class NfcAidConflictListener extends INfcAidConflictListener.Stub {

    private final INfcAdapter mAdapter;

    private final Map<AidConflictListener, Executor> mListenerMap = new HashMap<>();

    private boolean mIsRegistered = false;

    public NfcAidConflictListener(@NonNull INfcAdapter adapter) {
        mAdapter = adapter;
    }

    public void register(@NonNull Executor executor, @NonNull AidConflictListener listener) {
        synchronized (this) {
            if (mListenerMap.containsKey(listener)) {
                return;
            }

            mListenerMap.put(listener, executor);

            if (!mIsRegistered) {
                try {
                    mAdapter.registerAidConflictListener(this);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to register");
                }
                mIsRegistered = true;
            }
        }
    }

    public void unregister(@NonNull AidConflictListener listener) {
        synchronized (this) {
            if (!mListenerMap.containsKey(listener)) {
                return;
            }

            mListenerMap.remove(listener);

            if (mListenerMap.isEmpty()) {
                try {
                    mAdapter.unregisterAidConflictListener(this);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to unregister");
                }
                mIsRegistered = false;
            }
        }
    }
}
