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

// Mediatek Android Patch Begin, {DTV02759244}, {PQ repo, PQ applier}

package android.os;

import android.os.IPqRepositoryChangeListener;

/**
  * Binder interface to set and get PqParams in system server
  * {@hide}
  */
interface IPqRepository {

    String getPqParams(String packageName, String session);

    void setPqParams(String packageName, String session, String pqParams);

    void setOnChangeListenerWithSession(in IPqRepositoryChangeListener listener, String packageName, String session);

    void setOnChangeListener(in IPqRepositoryChangeListener listener, String packageName);

    String startSession(String packageName);

    void stopSession(String packageName, String session);

}

// Mediatek Android Patch End
