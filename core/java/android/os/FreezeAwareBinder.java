/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.os;

import android.annotation.CallbackExecutor;
import android.annotation.NonNull;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * {@code FreezeAwareBinder} manages communication with a binder, handling
 * frozen state changes and pausing calls as needed.
 *
 * {@code FreezeAwareBinder} can be configured to drop calls made when the target process is
 * frozen, or to replay the calls immediately when the process becomes unfrozen. Further
 * customization is possible through the {@link Reducer} interface, which allows an implementation
 * to define its own way of coalescing calls.
 *
 * A {@code Reducer} is also responsible for delivering calls by invoking method(s) on the
 * {@link IInterface} object.
 *
 * <p><b>Example Usage:</b></p>
 * This first example shows how to FreezeAwareBinder to hold off calls while the target process is
 * frozen and dispatch them immediately when the process becomes unfrozen. This is commonly used
 * when the recipient wants to receive all calls without losing any history, e.g. the recipient
 * maintains a running count of events that occurred.
 *
 * Queued calls are invoked in the order they were originally broadcasted.
 *
 * <pre>
 * // Assume IMyInterface is your AIDL interface and MyData is the data type.
 * IMyInterface service = IMyInterface.Stub.asInterface(binder);
 *
 * FreezeAwareBinder&lt;IMyInterface, MyData&gt; freezeAwareBinder =
 *         new FreezeAwareBinder&lt;&gt;(service, FreezeAwareBinder.createReplayReducer(
 *                 (data) -&gt; service.myMethod(data)), someExecutor);
 *
 * MyData data1 = new MyData("data1");
 * MyData data2 = new MyData("data2");
 *
 * try {
 *     freezeAwareBinder.call(data1); // Sent immediately if unfrozen, queued otherwise.
 *     freezeAwareBinder.call(data2); // Sent immediately if unfrozen, queued otherwise.
 * } catch (RemoteException e) {
 *     // Handle RemoteException
 * }
 * </pre>
 *
 * Alternatively you can use a Runnable as the data type:
 *
 * <pre>
 * IMyInterface service = IMyInterface.Stub.asInterface(binder);
 *
 * FreezeAwareBinder&lt;IMyInterface, Runnable&gt; freezeAwareBinder =
 *         new FreezeAwareBinder&lt;&gt;(service, FreezeAwareBinder.createReplayReducer(
 *             (r) -&gt; r.run()), someExecutor);
 *
 * try {
 *     // Sent immediately if unfrozen, queued otherwise.
 *     freezeAwareBinder.call(() -&gt; service.myMethod1("data1"));
 *     freezeAwareBinder.call(() -&gt; service.myMethod2("data2"));
 * } catch (RemoteException e) {
 *     // Handle RemoteException
 * }
 * </pre>
 *
 * <p><b>Example with DropReducer:</b></p>
 *
 * This could be useful in the case where the recipient wishes to react to calls only when they
 * occur while the recipient is not frozen. For example, certain network events are only
 * worth responding to if the response can be immediate.
 *
 * <pre>
 * FreezeAwareBinder&lt;IMyInterface, MyData&gt; freezeAwareBinder =
 *         new FreezeAwareBinder&lt;&gt;(service, FreezeAwareBinder.createDropReducer(
 *             (data) -&gt; service.myMethod(data)), someExecutor);
 *
 * try {
 *     freezeAwareBinder.call(data1); // sent if unfrozen, dropped otherwise.
 *     freezeAwareBinder.call(data2); // sent if unfrozen, dropped otherwise.
 * } catch (RemoteException e) {
 *     // Handle RemoteException
 * }
 * </pre>
 *
 * <p><b>Example Usage (Custom Reducer):</b></p>
 *
 * Below is an example of fully customized coalescing implementation. You could de-dup, merge, diff
 * the data anyway you like.
 *
 * This can be used in scenarios where the caller sends state updates to the target. When the
 * target becomes unfrozen, the sender could merge all updates into a single one and dispatch it in
 * one call.
 *
 * <pre>
 * FreezeAwareBinder&lt;IMyInterface, MyData&gt; freezeAwareBinder =
 *         new FreezeAwareBinder&lt;&gt;(service, new FreezeAwareBinder.Reducer&lt;MyData&gt;() {
 *             private final MyData mData;
 *
 *             &#64;Override
 *             public void add(&#64;NonNull MyData data,
 *                     &#64;IBinder.FrozenStateChangeCallback.State int frozenState) {
 *                 if (frozenState == IBinder.FrozenStateChangeCallback.STATE_UNFROZEN) {
 *                     try {
 *                         service.myMethod(data);
 *                     } catch (RemoteException e) {
 *                         // Handle RemoteException
 *                     }
 *                 } else {
 *                     mData = coalesce(mData, data);
 *                 }
 *             }
 *
 *             private MyData coalescing(MyData existingData, MyData newData) {
 *                 // Define your own coalescing logic here.
 *             }
 *
 *             &#64;Override
 *             public void flush() {
 *                 if (mData != null) {
 *                     try {
 *                         service.myMethod(mData);
 *                     } catch (RemoteException e) {
 *                         // Handle RemoteException
 *                     }
 *                     mData = null;
 *                 }
 *             }
 *         }, someExecutor);
 *
 * //... (call freezeAwareBinder.call() as before)
 *
 * </pre>
 *
 * @param <E> The interface type of the recipient.
 * @param <D> The type of data describing a call.
 *
 * @hide
 */
public class FreezeAwareBinder<E extends IInterface, D> {
    private final Executor mExecutor;
    private final IBinder mBinder;
    private final E mRecipientInterface;
    private final Reducer<D> mReducer;
    private final StateChangeListener mListener;

    private int mCurrentState = IBinder.FrozenStateChangeCallback.STATE_UNFROZEN;
    private boolean mInitialStateReceived;
    private Queue<D> mQueuedData;
    private boolean mBinderDied;

    /**
     * {@link Reducer} provides a way to customize how calls are handled while the target process is
     * frozen. It is also responsible for dispatching the calls by invoking method(s) on an {@link
     * IInterface}.
     *
     * Invocations to {@link Reducer} methods are synchronized. Implementations do not have to be
     * thread-safe.
     *
     * @param <D> The type of data being reduced.
     */
    public interface Reducer<D> {

        /**
         * Adds data to the reducer.  This method is called when new data is
         * available to be sent to the binder.
         *
         * @param data The data to be added.
         * @param frozenState The current frozen state of the binder.
         */
        void add(@NonNull D data, @IBinder.FrozenStateChangeCallback.State int frozenState);

        /**
         * Flushes any pending data. This method is called when the binder's
         * frozen state changes to unfrozen.
         */
        void flush();

        /**
         * Called when the binder has died.
         *
         * @param who The binder that died.
         */
        default void onBinderDied(@NonNull IBinder who) {}
    }

    /**
     * Creates a {@link Reducer} that drops calls when the target process hosing a binder is frozen.
     *
     * @param <D> The type of data being reduced.
     * @param output Reduced data is sent to this output object.
     */
    public static <D> Reducer<D> createDropReducer(@NonNull Consumer<D> output) {
        return new Reducer<D>() {
            @Override
            public void add(@NonNull D data,
                    @IBinder.FrozenStateChangeCallback.State int frozenState) {
                if (frozenState == IBinder.FrozenStateChangeCallback.STATE_UNFROZEN) {
                    output.accept(data);
                    return;
                }
            }
            @Override
            public void flush() {}
        };
    }

    /**
     * Creates a {@link Reducer} that replays queued calls when the binder becomes unfrozen.
     *
     * @param <D> The type of data being reduced.
     * @param output Reduced data is sent to this output object.
     */
    public static <D> Reducer<D> createReplayReducer(@NonNull Consumer<D> output) {
        final int defaultMaxQueueSize = 10;
        return createReplayReducer(output, defaultMaxQueueSize);
    }

    /**
     * Creates a {@link Reducer} that replays queued calls when the binder becomes unfrozen.
     *
     * @param <D> The type of data being reduced.
     * @param output Reduced data is sent to this output object.
     * @param maxQueueSize The max number of data items kept around and replayed.
     */
    public static <D> Reducer<D> createReplayReducer(@NonNull Consumer<D> output,
            int maxQueueSize) {
        return new Reducer<D>() {
            private Queue<D> mQueue;

            @Override
            public void add(@NonNull D data,
                    @IBinder.FrozenStateChangeCallback.State int frozenState) {
                if (frozenState == IBinder.FrozenStateChangeCallback.STATE_UNFROZEN) {
                    output.accept(data);
                } else {
                    if (mQueue == null) {
                        mQueue = new LinkedList<>();
                    }
                    if (mQueue.size() >= maxQueueSize) {
                        mQueue.remove();
                    }
                    mQueue.add(data);
                }
            }

            @Override
            public void flush() {
                while (mQueue != null && !mQueue.isEmpty()) {
                    output.accept(mQueue.remove());
                }
            }
        };
    }

    /**
     * Creates a {@link Reducer} that replays the most recent queued call when the binder becomes
     * unfrozen.
     *
     * @param <D> The type of data being reduced.
     * @param output Reduced data is sent to this output object.
     */
    public static <D> Reducer<D> createReplayMostRecentReducer(@NonNull Consumer<D> output) {
        return createReplayReducer(output, 1);
    }

    private final class StateChangeListener implements IBinder.DeathRecipient,
            IBinder.FrozenStateChangeCallback {

        synchronized void register() throws RemoteException {
            try {
                mBinder.linkToDeath(this, 0);
                try {
                    mBinder.addFrozenStateChangeCallback(mExecutor, this);
                } catch (UnsupportedOperationException e) {
                    mCurrentState = IBinder.FrozenStateChangeCallback.STATE_UNFROZEN;
                }
            } catch (DeadObjectException e) {
                mInitialStateReceived = true;
                mBinderDied = true;
                throw e;
            }
        }

        @Override
        public synchronized void onFrozenStateChanged(@NonNull IBinder who, int state) {
            if (mBinderDied) {
                return;
            }
            mCurrentState = state;
            if (!mInitialStateReceived) {
                mInitialStateReceived = true;
                if (mQueuedData != null) {
                    for (D data : mQueuedData) {
                        mReducer.add(data, state);
                    }
                    mQueuedData = null;
                }
                return;
            }
            if (state == STATE_UNFROZEN) {
                mReducer.flush();
            }
        }

        public synchronized void binderDied() {
            mInitialStateReceived = true;
            mBinderDied = true;
            mReducer.onBinderDied(mBinder);
        }
    }


    /**
     * Creates a {@code FreezeAwareBinder}.
     *
     * @param recipientInterface The interface of the recipient.
     * @param reducer            The reducer to use.
     * @param executor           The executor to use for callbacks.
     * @throws RemoteException   If a remote exception occurs during registration.
     */
    public FreezeAwareBinder(@NonNull E recipientInterface, @NonNull Reducer<D> reducer,
            @NonNull @CallbackExecutor Executor executor) throws RemoteException {
        mExecutor = executor;
        mBinder = recipientInterface.asBinder();
        mRecipientInterface = recipientInterface;
        mReducer = reducer;
        mListener = new StateChangeListener();
        mListener.register();
    }

    /**
     * Sends data to the binder.  If the binder is frozen, the data is held off and the reducer is
     * used to determine how the data is handled.
     *
     * @param data The data to send.
     * @throws RemoteException If a remote exception occurs or the binder has died.
     */
    public void call(@NonNull D data) throws RemoteException {
        synchronized (mListener) {
            if (mBinderDied) {
                throw new DeadObjectException();
            }
            if (!mInitialStateReceived) {
                if (mQueuedData == null) {
                    mQueuedData = new LinkedList();
                }
                mQueuedData.add(data);
                return;
            }
            mReducer.add(data, mCurrentState);
        }
    }
}
