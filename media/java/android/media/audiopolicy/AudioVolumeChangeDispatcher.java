/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.media.audiopolicy;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.media.AudioManager.VolumeGroupCallback;
import android.media.IAudioService;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * @hide
 * Internal dispatcher class for volume change handling.
 */
public class AudioVolumeChangeDispatcher  {
    private static final String TAG = "AudioVolumeChangeDispatcher";

    /**
     * List of listeners for preferred device for strategy and their associated Executor.
     * List is lazy-initialized on first registration
     */
    @GuardedBy("AudioVolumeChangeDispatcher.class")
    private @Nullable ArrayList<AudioVolumeListenerInfo> mAudioVolumeListeners;

    private static class AudioVolumeListenerInfo {
        final @NonNull VolumeGroupCallback mListener;
        final @NonNull Executor mExecutor;

        AudioVolumeListenerInfo(VolumeGroupCallback listener, Executor exe) {
            mListener = listener;
            mExecutor = exe;
        }
    }

    @GuardedBy("AudioVolumeChangeDispatcher.class")
    private AudioVolumeChangeDispatcherStub mAudioVolumeChangeDispatcherStub = null;

    private final class AudioVolumeChangeDispatcherStub  extends IAudioVolumeChangeDispatcher.Stub {
        @Override
        public void onAudioVolumeGroupChanged(int group, int flags) {
            Log.v(TAG, "onAudioVolumeGroupChanged group=" + group);

            // make a shallow copy of listeners so callback is not executed under lock
            final ArrayList<AudioVolumeListenerInfo> audioVolumeListeners;
            synchronized (AudioVolumeChangeDispatcher.this) {
                if (mAudioVolumeListeners == null || mAudioVolumeListeners.isEmpty()) {
                    return;
                }
                audioVolumeListeners =
                        (ArrayList<AudioVolumeListenerInfo>) mAudioVolumeListeners.clone();
            }
            final long ident = Binder.clearCallingIdentity();
            try {
                for (AudioVolumeListenerInfo info : audioVolumeListeners) {
                    info.mExecutor.execute(() ->
                            info.mListener.onAudioVolumeGroupChanged(group, flags));
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    };

    /**
     * @hide
    */
    @GuardedBy("AudioVolumeChangeDispatcher.class")
    public synchronized void addAudioVolumeListener(@NonNull Executor executor,
            @NonNull VolumeGroupCallback listener) {

        Objects.requireNonNull(executor);
        Objects.requireNonNull(listener);

        if (hasAudioVolumeListener(listener)) {
            throw new IllegalArgumentException(
                    "attempt to call addAudioVolumeListener() "
                            + "on a previously registered listener");
        }
        // lazy initialization of the list of strategy-preferred device listener
        if (mAudioVolumeListeners == null) {
            mAudioVolumeListeners = new ArrayList<>();
        }
        final int oldCbCount = mAudioVolumeListeners.size();
        mAudioVolumeListeners.add(new AudioVolumeListenerInfo(listener, executor));
        if (oldCbCount == 0 && !mAudioVolumeListeners.isEmpty()) {
            // register binder for callbacks
            if (mAudioVolumeChangeDispatcherStub == null) {
                mAudioVolumeChangeDispatcherStub = new AudioVolumeChangeDispatcherStub();
            }
            try {
                getService().registerAudioVolumeCallback(mAudioVolumeChangeDispatcherStub);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
    }

    /**
     * @hide
     * Unregister an audio gain change listener.
     * @param callback the {@link VolumeGroupCallback} to unregister
     */
     @GuardedBy("AudioVolumeChangeDispatcher.class")
     public synchronized void removeAudioVolumeListener(@NonNull VolumeGroupCallback listener) {
         Objects.requireNonNull(listener);
         if (!removeAudioVolumeListenerInternal(listener)) {
             throw new IllegalArgumentException(
                     "attempt to call removeAudioVolumeListener() "
                             + "on an unregistered listener");
         }
         if (mAudioVolumeListeners.isEmpty()) {
             try {
                 getService().unregisterAudioVolumeCallback(mAudioVolumeChangeDispatcherStub);
             } catch (RemoteException e) {
                 throw e.rethrowFromSystemServer();
             } finally {
                 mAudioVolumeChangeDispatcherStub = null;
                 mAudioVolumeListeners = null;
             }
         }
     }

     @GuardedBy("AudioVolumeChangeDispatcher.class")
     private synchronized @Nullable AudioVolumeListenerInfo getAudioVolumeListenerInfo(
             VolumeGroupCallback listener) {
         if (mAudioVolumeListeners == null) {
             return null;
         }
         for (AudioVolumeListenerInfo info : mAudioVolumeListeners) {
             if (info.mListener == listener) {
                 return info;
             }
         }
         return null;
     }

     @GuardedBy("AudioVolumeChangeDispatcher.class")
     private synchronized boolean hasAudioVolumeListener(VolumeGroupCallback listener) {
         return getAudioVolumeListenerInfo(listener) != null;
     }

     /**
      * @return true if the listener was removed from the list
      */
     @GuardedBy("AudioVolumeChangeDispatcher.class")
     private synchronized boolean removeAudioVolumeListenerInternal(VolumeGroupCallback listener) {
         final AudioVolumeListenerInfo infoToRemove = getAudioVolumeListenerInfo(listener);
         if (infoToRemove != null) {
             mAudioVolumeListeners.remove(infoToRemove);
             return true;
         }
         return false;
     }

     @GuardedBy("AudioVolumeChangeDispatcher.class")
     private static IAudioService sService;

     private static IAudioService getService()
     {
         if (sService != null) {
             return sService;
         }
         IBinder b = ServiceManager.getService(Context.AUDIO_SERVICE);
         sService = IAudioService.Stub.asInterface(b);
         return sService;
     }
}
