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

package android.app.compat;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.compat.IPlatformCompat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles caching of calls to {@link com.android.internal.compat.IPlatformCompat}
 * @hide
 */
public final class ChangeIdStateCache {
    private static final String TAG = "ChangeIdStateCache";

    private static final long NONCE_UNSET = 0;

    private static final int INITIAL_CACHE_CAPACITY = 2;
    private static final float CACHE_LOAD_FACTOR = 0.75f;
    private static final boolean CACHE_ACCESS_ORDER = true; // LRU access order
    private static final int MAX_CACHED_ENTRIES = 20;
    private static final String CACHE_KEY = "cache_key.platform_compat.is_change_enabled";

    private static boolean sDisabled = false;

    private static final int QUERY_BY_PACKAGE_NAME = 0;
    private static final int QUERY_BY_UID = 0;

    @IntDef({QUERY_BY_PACKAGE_NAME, QUERY_BY_UID})
    @Retention(RetentionPolicy.SOURCE)
    @interface QueryType {}

    /**
     * A key type for caching calls to {@link com.android.internal.compat.IPlatformCompat}
     *
     * <p>For {@link com.android.internal.compat.IPlatformCompat#isChangeEnabledByPackageName}
     * and {@link com.android.internal.compat.IPlatformCompat#isChangeEnabledByUid}
     */
    static final class ChangeIdStateQuery {
        public @QueryType int type;
        public long changeId;
        public String packageName;
        public int uid;
        public int userId;

        private ChangeIdStateQuery(@QueryType int type, long changeId, String packageName,
                                   int uid, int userId) {
            this.type = type;
            this.changeId = changeId;
            this.packageName = packageName;
            this.uid = uid;
            this.userId = userId;
        }

        static ChangeIdStateQuery byPackageName(long changeId, @NonNull String packageName,
                                                int userId) {
            return new ChangeIdStateQuery(QUERY_BY_PACKAGE_NAME, changeId, packageName, 0, userId);
        }

        static ChangeIdStateQuery byUid(long changeId, int uid) {
            return new ChangeIdStateQuery(QUERY_BY_UID, changeId, null, uid, 0);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if ((other == null) || !(other instanceof ChangeIdStateQuery)) {
                return false;
            }
            final ChangeIdStateQuery that = (ChangeIdStateQuery) other;
            return this.type == that.type
                && this.changeId == that.changeId
                && Objects.equals(this.packageName, that.packageName)
                && this.uid == that.uid
                && this.userId == that.userId;
        }
        @Override
        public int hashCode() {
            return Objects.hash(changeId, packageName, uid, userId);
        }
    }

    /**
     * Handle to the {@code CACHE_KEY} property.
     */
    private volatile SystemProperties.Handle mPropertyHandle;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final LinkedHashMap<ChangeIdStateQuery, Boolean> mCache;

    /**
     * The last value of the {@code mPropertyHandle} that we observed.
     */
    @GuardedBy("mLock")
    private long mLastSeenNonce = NONCE_UNSET;

    ChangeIdStateCache() {
        mCache = new LinkedHashMap<>(
            INITIAL_CACHE_CAPACITY,
            CACHE_LOAD_FACTOR,
            CACHE_ACCESS_ORDER) {
                @Override
                protected boolean removeEldestEntry(Map.Entry eldest) {
                    return size() > MAX_CACHED_ENTRIES;
                }
            };
    }

    // Inner class avoids initialization in processes that don't do any invalidation
    private static final class NoPreloadHolder {
        private static final AtomicLong sNextNonce = new AtomicLong((new Random()).nextLong());
        public static long next() {
            return sNextNonce.getAndIncrement();
        }
    }

    /**
     * Invalidates the cache.
     */
    public static void invalidate() {
        if (sDisabled) {
            return;
        }
        final long nonce = SystemProperties.getLong(CACHE_KEY, NONCE_UNSET);
        long newValue;
        do {
            newValue = NoPreloadHolder.next();
        } while (newValue == NONCE_UNSET);
        SystemProperties.set(CACHE_KEY, Long.toString(newValue));
    }

    /**
     * Disable caching for unit tests (only system server can invalidate.)
     */
    public static void disable() {
        sDisabled = true;
    }

    private long getCurrentNonce() {
        SystemProperties.Handle handle = mPropertyHandle;
        if (handle == null) {
            handle = SystemProperties.find(CACHE_KEY);
            if (handle == null) {
                return NONCE_UNSET;
            }
            mPropertyHandle = handle;
        }
        return handle.getLong(NONCE_UNSET);
    }

    private Boolean recompute(ChangeIdStateQuery query) {
        IPlatformCompat platformCompat = IPlatformCompat.Stub.asInterface(
                ServiceManager.getService(Context.PLATFORM_COMPAT_SERVICE));
        try {
            if (query.type == QUERY_BY_PACKAGE_NAME) {
                return platformCompat.isChangeEnabledByPackageName(query.changeId,
                                                                   query.packageName,
                                                                   query.userId);
            } else if (query.type == QUERY_BY_UID) {
                return platformCompat.isChangeEnabledByUid(query.changeId, query.uid);
            } else {
                throw new IllegalArgumentException("Invalid query type: " + query.type);
            }
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }
        throw new IllegalStateException("Could not recompute value!");
    }

    /**
     * Get a value from the cache or recompute it.
     * @hide
     */
    Boolean query(ChangeIdStateQuery query) {
        for (;;) {
            long currentNonce = getCurrentNonce();
            if (sDisabled || (currentNonce == NONCE_UNSET)) {
                return recompute(query);
            }
            synchronized (mLock) {
                if (currentNonce == mLastSeenNonce) {
                    return mCache.get(query);
                }
                mCache.clear();
                mLastSeenNonce = currentNonce;
            }
            final Boolean result = recompute(query);
            currentNonce = getCurrentNonce();
            // If someone else invalidated the cache while we did the recomputation, don't
            // update the cache with a potentially stale result.
            if (mLastSeenNonce == currentNonce) {
                synchronized (mLock) {
                    mCache.put(query, result);
                    return result;
                }
            }
        }
    }
}
