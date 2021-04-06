/*
 * Copyright 2020 The Android Open Source Project
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

package android.media;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Bundle;
import android.os.IPqRepository;
import android.os.IPqRepositoryChangeListener;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** @hide */
public final class PqApplier implements AutoCloseable {
    private static final String TAG = "PqApplier";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private MediaCodec mMc;
    private String mPackageName;
    private String mSession;
    private static IPqRepository sPqRepository;
    private PqMediaCodecCallback mPqNonAwareMediaCodecCallback;
    private PqMediaCodecCallback mPqAwareMediaCodecCallback;
    private static final Map<String, List> sPqNonAwareCallBacks = new HashMap<>();
    private static final Map<String, List> sPqAwareCallBacks = new HashMap<>();

    private class PqMediaCodecCallback {
        private MediaCodec mc;
        private String mSession;

        private PqMediaCodecCallback(MediaCodec mc, String session) {
            this.mc = mc;
            this.mSession = session;
        }

        public void onChanged(String packageName, String session) {
            String pqParams = null;
            if (session != null && !mSession.equals(session)) {
                Log.d(TAG, "not me onChanged, skip" + mSession + " " + session);
                return;
            }
            pqParams = getPqParamsFromPqRepo(packageName, session);
            Log.d(TAG, "[PqMediaCodecCallback] [onChanged] packageName:" + packageName + ", mc:" + mc);
            if (VERBOSE) {
                Log.v(TAG, "pqParams: " + pqParams);
            }
            Bundle params = new Bundle();
            params.putObject("vendor.mtk-pq.pqsetting", pqParams);
            if (pqParams != null) {
                mc.setParameters(params);
            }
        }
    }

    PqApplier(MediaCodec mc, String packageName) {
        mPackageName = packageName;
        mMc = mc;
        mSession = null;

        initCallBacks(sPqNonAwareCallBacks, null);

        try {
            getPqRepository().setOnChangeListener(sPqListener, mPackageName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private void initCallBacks(Map<String, List> callbacks, String session) {
        synchronized (callbacks) {
            List<PqMediaCodecCallback> list =
                    (List<PqMediaCodecCallback>) callbacks.get(mPackageName);
            if (list == null) {
                Log.d(TAG, "Pq app add call back list " + mPackageName);
                list = new ArrayList<PqMediaCodecCallback>();
            }

            if (null == session) {
                mPqNonAwareMediaCodecCallback = new PqMediaCodecCallback(mMc, session);
                list.add(mPqNonAwareMediaCodecCallback);
                Log.d(TAG, "add non aware call back list " + mPackageName);
            } else {
                mPqAwareMediaCodecCallback = new PqMediaCodecCallback(mMc, session);
                list.add(mPqAwareMediaCodecCallback);
                Log.d(TAG, "add aware call back list " + mPackageName);
            }
            callbacks.put(mPackageName, list);
            Log.d(TAG, "after create PqMediaCodecCallback, list size = " + list.size());
        }
    }

    private static IPqRepositoryChangeListener sPqListener =
            new IPqRepositoryChangeListener.Stub() {
        @Override
        public void onChanged(String packageName, String session) {
            List<PqMediaCodecCallback> list = null;
            if (session == null) {
                synchronized (sPqNonAwareCallBacks) {
                    list = sPqNonAwareCallBacks.get(packageName);
                }
                if (list == null) {
                    Log.d(TAG, "[sPqListner] [onChanged]" + packageName + ", session= null, "
                            + "list null");
                    return;
                }
                Log.d(TAG, "[sPqListner] [onChanged]" + packageName + ", session= null, "
                        + "list size:" + list.size());
            } else {
                synchronized (sPqAwareCallBacks) {
                    list = sPqAwareCallBacks.get(packageName);
                }
                if (list == null) {
                    Log.d(TAG, "[sPqListner] [onChanged]" + packageName + ", " + session
                            + "list null");
                    return;
                }
                Log.d(TAG, "[sPqListner] [onChanged]" + packageName + ", " + session
                        + "list size:" + list.size());
            }
            for (PqMediaCodecCallback mcc : list) {
                mcc.onChanged(packageName, session);
            }
        }
    };

    // get PQ Repository interface
    private IPqRepository getPqRepository() {
        if (sPqRepository != null) {
            return sPqRepository;
        }
        sPqRepository = IPqRepository.Stub.asInterface(
                ServiceManager.getService("PqRepositoryService"));
        if (sPqRepository == null) {
            Log.e(TAG, "getPqRepository() PqRepositoryService == null");
        }
        return sPqRepository;
    }

    private String getPqParamsFromPqRepo(@NonNull String packageName, String session) {
        String pqParams = null;
        try {
            pqParams = getPqRepository().getPqParams(packageName, session);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return pqParams;
    }

    private void setPqParamsToPqRepo(
            @NonNull String packageName, String session, @NonNull String pqParams) {
        Log.d(TAG, "[setPqParamsToPqRepo] packageName:" + packageName + ", session:" + session);
        if (VERBOSE) {
            Log.v(TAG, "pqParams:" + pqParams);
        }

        try {
            getPqRepository().setPqParams(packageName, session, pqParams);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    // setPQParams() for package (Currently no uasge)
    public void setPqParamsToPqRepo(@NonNull String pqParams) {
        setPqParamsToPqRepo(mPackageName, null, pqParams);
    }

    // setPQParams() for per-stream (PQ Aware App)
    public void setPqParamsToPqRepoWithSession(@NonNull String pqParams) {
        try {
            if (mSession == null) {
                mSession = getPqRepository().startSession(mPackageName);
                initCallBacks(sPqAwareCallBacks, mSession);
                getPqRepository().setOnChangeListenerWithSession(
                        sPqListener, mPackageName, mSession);
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        Log.d(TAG, "setPqParamsToPqRepo " + mPackageName + " " + mSession);
        if (VERBOSE) {
            Log.v(TAG, "paParams: " + pqParams);
        }
        setPqParamsToPqRepo(mPackageName, mSession, pqParams);
    }

    public void setPqParamsToHal() {
        // Set PQ Parameters into PQ HAL
        String pqParams = getPqParamsFromPqRepo(mPackageName, mSession);
        if (pqParams != null) {
            Log.d(TAG, "setPqParamsToHal " + mPackageName + " " + mSession);
            if (VERBOSE) {
                Log.v(TAG, "pqParams:" + pqParams);
            }
            Bundle params = new Bundle();
            params.putObject("vendor.mtk-pq.pqsetting", pqParams);
            mMc.setParameters(params);
        }
    }

    private void closeCallBasks(Map<String, List> callbacks, String session) {
        synchronized (callbacks) {
            List<PqMediaCodecCallback> list;
            list = (List<PqMediaCodecCallback>)
                    callbacks.get(mPackageName);
            if (list != null) {
                if (null == session) {
                    list.remove(mPqNonAwareMediaCodecCallback);
                    Log.d(TAG, "[close] non aware callback has removed, list.size()=" + list.size());
                } else {
                    list.remove(mPqAwareMediaCodecCallback);
                    Log.d(TAG, "[close] aware callback has removed, list.size()=" + list.size());
                }
                if (list.size() == 0) {
                    try {
                        getPqRepository().stopSession(mPackageName, session);
                    } catch (RemoteException e) {
                        throw e.rethrowFromSystemServer();
                    } finally {
                        callbacks.remove(mPackageName);
                    }
                }
            }
        }

    }
    @Override
    public void close() {
        closeCallBasks(sPqNonAwareCallBacks, null);
        if(null != mSession)
            closeCallBasks(sPqAwareCallBacks, mSession);

        mMc = null;
        mSession = null;
        mPackageName = null;
    }
}
