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

package android.telephony;

import android.annotation.NonNull;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Keeps track of the connection to a Binder node, refreshes the cache if the node dies, and lets
 * interested parties register listeners on the node to be notified when the node has died via the
 * {@link BinderDeadRunnable}.
 * @param <T> The IInterface representing the Binder type that this manager will be managing the
 *           cache of.
 * @hide
 */
public class BinderCacheManager<T extends IInterface> {

    /**
     * Factory class for creating new IInterfaces in the case that {@link #getBinder()} is
     * called and there is no active binder available.
     * @param <T> The IInterface that should be cached and returned to the caller when
     * {@link #getBinder()} is called until the Binder node dies.
     */
    public interface BinderInterfaceFactory<T> {
        /**
         * @return A new instance of the Binder node, which will be cached until it dies.
         */
        T create();
    }

    /**
     * The Runnable called when the tracked Binder node dies. Associates the Runnable with an
     * IInterface, which is the Binder interface passed to the remote process that will be affected
     * by the remote process's death.
     * @param <T> The Binder type that this Runnable will be tracking. When the cached remote binder
     *           dies, this runnable will be called with the local callback IInterface instance so
     *           that it can take action.
     */
    public abstract static class BinderDeadRunnable<T extends IInterface> implements Runnable {

        private final T mBinder;

        public BinderDeadRunnable(T binder) {
            mBinder = binder;
        }

        @Override
        public void run() {
            run(mBinder);
        }

        /**
         * @return The IInterface passed into this Runnable during construction.
         */
        public final IInterface getBinder() {
            return mBinder;
        }

        /**
         * Called when this Runnable is run and also passes in the tracked Binder callback that may
         * be notified of the death.
         * @param binder The local Binder interface that was passed into this Runnable during
         *               construction.
         */
        public abstract void run(T binder);
    }

    /**
     * Tracks the cached Binder node as well as the listeners that were associated with that
     * Binder node during its lifetime. If the Binder node dies, the listeners will be called and
     * then this tracker will be unlinked and cleaned up.
     */
    private static class BinderDeathTracker<T extends IInterface>
            implements IBinder.DeathRecipient {

        public final T mConnection;
        public final ArrayList<BinderDeadRunnable<?>> mListeners = new ArrayList<>();

        /**
         * Create a tracker to cache the Binder node and add the ability to listen for the cached
         * interface's death.
         */
        BinderDeathTracker(@NonNull T connection) {
            mConnection = connection;
            try {
                mConnection.asBinder().linkToDeath(this, 0 /*flags*/);
            } catch (RemoteException e) {
                // isAlive will return false.
            }
        }

        public boolean addListener(BinderDeadRunnable<?> r) {
            synchronized (mListeners) {
                if (!isAlive()) return false;
                return mListeners.add(r);
            }
        }

        public void removeListener(IInterface binderKey) {
            synchronized (mListeners) {
                mListeners.removeAll(mListeners.stream()
                        .filter(r -> (r.getBinder() == binderKey)).collect(Collectors.toList()));
            }
        }

        @Override
        public void binderDied() {
            ArrayList<BinderDeadRunnable<?>> listeners;
            synchronized (mListeners) {
                listeners = new ArrayList<>(mListeners);
                mListeners.clear();
                try {
                    mConnection.asBinder().unlinkToDeath(this, 0 /*flags*/);
                } catch (NoSuchElementException e) {
                    // No need to worry about this, this means the death recipient was never linked.
                }
            }
            listeners.forEach(Runnable::run);
        }

        /**
         * @return The cached Binder.
         */
        public T getConnection() {
            return mConnection;
        }

        /**
         * @return true if the cached Binder is alive at the time of calling, false otherwise.
         */
        public boolean isAlive() {
            return mConnection.asBinder().isBinderAlive();
        }
    }

    private final BinderInterfaceFactory<T> mBinderInterfaceFactory;
    private final AtomicReference<BinderDeathTracker<T>> mCachedConnection;

    /**
     * Create a new instance, which manages a cached IInterface and creates new ones using the
     * provided factory when the cached IInterface dies.
     * @param factory The factory used to create new Instances of the cached IInterface when it
     *                dies.
     */
    public BinderCacheManager(BinderInterfaceFactory<T> factory) {
        mBinderInterfaceFactory = factory;
        mCachedConnection = new AtomicReference<>();
    }

    /**
     * Get the binder node connection and add a Runnable to be run if this Binder dies. Once this
     * Runnable is run, the Runnable itself is discarded and must be added again.
     * <p>
     * Note: There should be no assumptions here as to which Thread this Runnable is called on.
     * @return T if the runnable was added or {@code null} if the connection is not alive right now
     * and the associated runnable was never added.
     */
    public T listenOnBinder(BinderDeadRunnable<?> deadRunnable) {
        if (deadRunnable == null) return null;
        BinderDeathTracker<T> tracker = getTracker();
        if (tracker == null) return null;

        boolean addSucceeded = tracker.addListener(deadRunnable);
        return addSucceeded ? tracker.getConnection() : null;
    }

    /**
     * @return The cached Binder node. May return null if the requested Binder node is not currently
     * available.
     */
    public T getBinder() {
        BinderDeathTracker<T> tracker = getTracker();
        return (tracker != null) ? tracker.getConnection() : null;
    }

    /**
     * Removes a previously registered runnable associated with the cached Binder node using the
     * IInterface it was registered with in {@link BinderDeadRunnable(IInterface)} if the runnable
     * still exists.
     */
    public void removeRunnable(IInterface runnableKey) {
        if (runnableKey == null) return;
        BinderDeathTracker<T> tracker = getTracker();
        if (tracker == null) return;
        tracker.removeListener(runnableKey);
    }

    /**
     * @return The BinderDeathTracker container, which contains the cached IInterface instance or
     * null if it is not available right now.
     */
    private BinderDeathTracker<T> getTracker() {
        return mCachedConnection.updateAndGet((oldVal) -> {
            BinderDeathTracker<T> tracker = oldVal;
            // Update cache if no longer alive. BinderDied will eventually be called on the tracker,
            // which will call listeners & clean up.
            if (tracker == null || !tracker.isAlive()) {
                T binder = mBinderInterfaceFactory.create();
                tracker = (binder != null) ? new BinderDeathTracker<>(binder) : null;

            }
            return (tracker != null && tracker.isAlive()) ? tracker : null;
        });
    }

}
