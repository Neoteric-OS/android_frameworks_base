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

package com.android.server.pq;

import java.lang.*;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Set;
import java.io.FileDescriptor;
import java.io.PrintWriter;

import android.content.Context;
import android.os.RemoteException;
import android.os.IPqRepository;
import android.os.IPqRepositoryChangeListener;
import android.os.ServiceManager;
import android.util.Log;
import android.util.Slog;

import android.annotation.NonNull;
import android.annotation.Nullable;
import com.android.server.SystemService;
import com.android.internal.util.DumpUtils;

public class PqRepositoryService extends SystemService {
    private static final String TAG = "PqRepositoryService";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean DEBUG = true;
    private final AtomicInteger mSession = new AtomicInteger();
    private final Context mContext;
    private static final String DEFAULT_PQ = "Default";

    private class MapKey {
        private String mPackageName;
        private String SessionId;
        private MapKey(String packageName, String session) {
            if (packageName == null || session == null) {
                Log.e(TAG, "MapKey constructor with NULL");
            }
            this.mPackageName = packageName;
            this.SessionId = session;
        }

        public void setPackageName(@NonNull String packageName) {
            this.mPackageName = packageName;
        }

        public void setSessionId(@Nullable String session) {
            this.SessionId = session;
        }

        public String getPackageName() {
            return this.mPackageName;
        }

        public String getSessionId() {
            return this.SessionId;
        }
    }

    private class MapValue {
        private String PqParam;
        private IPqRepositoryChangeListener Listener;
        private MapValue(String pqParams, IPqRepositoryChangeListener listener) {
            this.PqParam = pqParams;
            this.Listener = listener;
        }

        public void setPqParam(@NonNull String param) {
            this.PqParam = param;
        }

        public void setListener(@Nullable IPqRepositoryChangeListener listener) {
            if (listener != null) {
                this.Listener = listener;
            }
        }

        public String getPqParam() {
            return this.PqParam;
        }

        public IPqRepositoryChangeListener getListener() {
            return this.Listener;
        }
    }

    public PqRepositoryService(@Nullable Context context) {
        super(context);
        mContext = context;
    }

    private Map<MapKey, MapValue> mMap = new LinkedHashMap<MapKey, MapValue>();

    @Override
    public void onStart() {
        ServiceManager.addService("PqRepositoryService", new mBinderService());
    }

    private final class mBinderService extends IPqRepository.Stub {

        @Override
        public String getPqParams(@NonNull String packageName, @Nullable String session) {
            if (packageName == null) {
                packageName = DEFAULT_PQ;
                session = null;
            }
            String pqParams = null;
            MapKey key = findMapValue(packageName, session);

            if (key != null) {
                pqParams = mMap.get(key).getPqParam();
            } else if (packageName != DEFAULT_PQ) {
                key = findMapValue(DEFAULT_PQ, null);
                if (key != null) {
                    pqParams = mMap.get(key).getPqParam();
                }
            } else {
                Log.e(TAG, "mMap.get(key) fail");
            }

            if (DEBUG) {
                Log.d(TAG,"[getPqParams] packgeName: " + packageName + " session:" +session );
            }
            if (VERBOSE) {
                Log.v(TAG,"pqParams:" + pqParams);
            }
            return pqParams;

        }

        @Override
        public void setPqParams(@NonNull String packageName, @Nullable String session, @Nullable String pqParams) {
            if (packageName == null) {
                packageName = DEFAULT_PQ;
                session = null;
            }
            IPqRepositoryChangeListener mListener = null;
            MapKey key = findMapValue(packageName, session);
            if (key != null) {
                MapValue value = (MapValue) mMap.get(key);
                if (value != null) {
                    mListener = (IPqRepositoryChangeListener) value.getListener();
                    if (pqParams == null) {
                        pqParams = "null";
                    }
                    value.setPqParam(pqParams);
                    mMap.put(key, value);
                    if (DEBUG) {
                        Log.d(TAG,"[setPqParams] packgeName: " + packageName + " session:" + session);
                    }
                    if (VERBOSE) {
                        Log.v(TAG,"pqParams:" + pqParams);
                    }
                }
            } else {
                Log.e(TAG, "PackageName[" + packageName + "] and Session[" + session + "] has not registered in PqRepositoryService");
                if (VERBOSE) {
                    Log.v(TAG, "pqParams:" + pqParams);
                }
                MapKey new_key = new MapKey(packageName, session);
                MapValue new_value = new MapValue(pqParams, null);
                mMap.put(new_key, new_value);
            }

            // onChanged PqParams
            if(mListener != null) {
                try {
                    mListener.onChanged(packageName, session);
                    if (DEBUG) {
                        Log.d(TAG,"[onChanged] packgeName: " + packageName + " session:" + session + "listener:" + mListener);
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }

        // per-stream callback
        @Override
        public void setOnChangeListenerWithSession(@NonNull IPqRepositoryChangeListener listener,
              @NonNull String packageName, @NonNull String session) {
            if (packageName == null) {
                Log.e(TAG, "[setOnChangeListenerWithSession] packageName is NULL");
                return;
            }
            MapKey key = findMapValue(packageName, session);
            if (key == null) {
                MapKey new_key = new MapKey(packageName, session);
                if (new_key != null) {
                    MapValue value = new MapValue(null, listener);
                    if (value != null) {
                        mMap.put(new_key, value);
                    } else {
                        Log.e(TAG, "Add MapValue Failed");
                    }

                    if (DEBUG) {
                        Log.d(TAG,"[setOnChangeListenerWithSession] packagename :" + packageName + " session:" +
                        session + " listener: " + value.getListener());
                    }

                } else {
                    Log.e(TAG, "Failed to Add MapKey, packageName: " + packageName + " session: " + session);
                }
            } else {
                MapValue value = new MapValue(null, listener);
                if (value != null) {
                    mMap.put(key, value);
                } else {
                    Log.e(TAG, "Add MapValue Failed");
                }

                if (DEBUG) {
                    Log.d(TAG,"[setOnChangeListenerWithSession] packagename :" + packageName + " session:" +
                    session + " has been inserted into PqRepositoryService, update listener: " + value.getListener());
                }
            }
        }

        // global callback (currently not used)
        @Override
        public void setOnChangeListener(@NonNull IPqRepositoryChangeListener listener, @NonNull String packageName) {
            if (packageName == null) {
                Log.e(TAG, "[setOnChangeListener] packageName is NULL");
                return;
            }
            Log.d(TAG, "[setOnChangeListener] packagename :" + packageName + " listener: " + listener);
            MapKey key = findMapValue(packageName, null);
            if (key == null) {
                MapKey new_key = new MapKey(packageName, null);
                if (new_key != null) {
                    MapValue value = new MapValue(null, listener);
                    if (value != null) {
                        mMap.put(new_key, value);
                    } else {
                        Log.e(TAG, "Add MapValue Failed");
                    }

                    if (DEBUG) {
                        Log.d(TAG,"[setOnChangeListener] packagename :" + packageName + " listener: " + value.getListener());
                    }

                } else {
                    Log.e(TAG, "Failed to Add MapKey, packageName: " + packageName);
                }
            } else {
                mMap.get(key).setListener(listener);
                if (DEBUG) {
                    Log.d(TAG,"[setOnChangeListener] packagename :" + packageName + " has been inserted into PqRepositoryService , update listener: " + listener);
                }
            }
        }

        @Override
        public String startSession(@NonNull String packageName) {
            if (packageName == null) {
                Log.e(TAG, "[startSession] packageName is NULL");
                return null;
            }
            String session = generateUniqueId();
            return session;
        }

        @Override
        public void stopSession(String packageName, String session) {
            if (packageName == null) {
                Log.e(TAG, "[stopSession] packageName is NULL");
                return;
            }
            MapKey key = null;
            Iterator<MapKey> it = mMap.keySet().iterator();
            while (it.hasNext()) {
                key = it.next();
                if (key.getPackageName().equals(packageName)) {
                    if (key.getSessionId() != null) {
                        // remove aware session
                        if (key.getSessionId().equals(session)) {
                            it.remove();
                        }
                    } else if (key.getSessionId() == null && session == null) {
                        // remove non-aware session
                        Log.e(TAG, "[startSession] non pq aware app not remove key");
                        // keep PQ data and remove listener only
                        mMap.get(key).setListener(null);
                        //if (mMap.get(key).getListener() != null) {
                        //    it.remove();
                        //}
                    }
                }
            }
        }

        @Override
        protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            if (!DumpUtils.checkDumpPermission(mContext, TAG, pw)) {
                return;
            }

            pw.println("PQ REPOSITORY SRV (dumpsys PqRepositoryService)\n");
            pw.println("PqRepository List:");
            int index = 0;
            MapKey key = null;
            Iterator<MapKey> it = mMap.keySet().iterator();
            while (it.hasNext()) {
                key = it.next();
                pw.println("PQ index : " + index);
                pw.println("  PackageName = " + key.getPackageName());
                pw.println("  SessionId   = " + key.getSessionId());
                if (mMap.get(key) != null) {
                    pw.println("  PqParams    = " + mMap.get(key).getPqParam());
                    pw.println("  Listener    = " + mMap.get(key).getListener());
                } else {
                    pw.println("  MapValue is null " );
                }
                index++;
            }
        }
    }

    private String generateUniqueId() {
        return String.valueOf(mSession.incrementAndGet());
    }

    // Use for find the MapValue by packageName and session
    private MapKey findMapValue(String packageName, String sessionId) {
        MapKey key = null;
        // traverse LinkedHashMap in reverse order
        Set<MapKey> setKeys = mMap.keySet();
        LinkedList<MapKey> listKeys = new LinkedList<MapKey>(setKeys);
        Iterator<MapKey> it = listKeys.descendingIterator();

        while (it.hasNext()) {
            key = it.next();
            if (key.getPackageName().equals(packageName)) {
                if (key.getSessionId() != null) {
                    // aware case
                    if (key.getSessionId().equals(sessionId))
                       return key;
                } else if (key.getSessionId() == null && sessionId != null) {
                    // aware case but not initialized
                    return null;
                } else if (key.getSessionId() == null) {
                    // non-aware case
                    return key;
                }
            }
            key = null;
        }
        return key;
    }

}

