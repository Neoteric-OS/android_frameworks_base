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

package com.android.server.audio;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.media.audio.common.AudioVolumeGroupChangeEvent;
import android.media.audiopolicy.IAudioVolumeChangeDispatcher;
import android.media.INativeAudioVolumeGroupCallback;
import android.os.IBinder;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Spy;

import java.util.ArrayList;
import java.util.List;

@MediumTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class AudioVolumeChangeHandlerTest {
    private static final String TAG = "AudioVolumeChangeHandlerTest";
    private static final long DEFAULT_TIMEOUT_MS = 1000;

    private AudioSystemAdapter mSpyAudioSystem;

    ArgumentCaptor<INativeAudioVolumeGroupCallback> mINativeAudioVolumeGroupCallbackCaptor =
            ArgumentCaptor.forClass(INativeAudioVolumeGroupCallback.class);

    AudioVolumeChangeHandler mAudioVolumeChangedHandler;

    private IAudioVolumeChangeDispatcher.Stub mMockDispatcher =
            mock(IAudioVolumeChangeDispatcher.Stub.class);

    @Before
    public void setUp() {
        mSpyAudioSystem = spy(new NoOpAudioSystemAdapter());
        when(mMockDispatcher.asBinder()).thenReturn(mock(IBinder.class));

        mAudioVolumeChangedHandler = new AudioVolumeChangeHandler(mSpyAudioSystem);
        mAudioVolumeChangedHandler.init();

        verify(mSpyAudioSystem, timeout(DEFAULT_TIMEOUT_MS))
                .registerAudioVolumeGroupCallback(mINativeAudioVolumeGroupCallbackCaptor.capture());
    }

    @Test
    public void testRegisterInvalidCallback() {
        assertThrows(NullPointerException.class, () -> {
            IAudioVolumeChangeDispatcher.Stub nullCb = null;
            mAudioVolumeChangedHandler.registerListener(nullCb);
        });
    }

    @Test
    public void testUnregisterInvalidCallback() {
        //final AudioVolumeChangeCallbackHelper cb = new AudioVolumeChangeCallbackHelper();
        mAudioVolumeChangedHandler.registerListener(mMockDispatcher);

        assertThrows(NullPointerException.class, () -> {
            IAudioVolumeChangeDispatcher.Stub nullCb = null;
            mAudioVolumeChangedHandler.unregisterListener(nullCb);
        });
        mAudioVolumeChangedHandler.unregisterListener(mMockDispatcher);
    }

    @Test
    public void testRegisterUnregisterCallback() {
       // final AudioVolumeChangeCallbackHelper validCb = new AudioVolumeChangeCallbackHelper();

        // Should not assert, otherwise test will fail
        mAudioVolumeChangedHandler.registerListener(mMockDispatcher);

        // Should not assert, otherwise test will fail
        mAudioVolumeChangedHandler.unregisterListener(mMockDispatcher);
    }

    @Test
    public void testCallbackReceived() {
        mAudioVolumeChangedHandler.registerListener(mMockDispatcher);

        try {
            AudioVolumeGroupChangeEvent volEvent = new AudioVolumeGroupChangeEvent();
            volEvent.groupId = 666;
            volEvent.flags = AudioVolumeGroupChangeEvent.VOLUME_FLAG_FROM_KEY;

            mINativeAudioVolumeGroupCallbackCaptor.getValue().onAudioVolumeGroupChanged(volEvent);

            verify(mMockDispatcher,  timeout(DEFAULT_TIMEOUT_MS)).onAudioVolumeGroupChanged(
                    eq(volEvent.groupId), eq(volEvent.flags));
        }
        catch (RemoteException e) {
        }
        finally {
            mAudioVolumeChangedHandler.unregisterListener(mMockDispatcher);
        }
    }

    @Test
    public void testMultipleCallbackReceived() {
        final int callbackCount = 10;
        final List<IAudioVolumeChangeDispatcher.Stub> validCbs =
                new ArrayList<IAudioVolumeChangeDispatcher.Stub>();
        for (int i = 0; i < callbackCount; i++) {
            IAudioVolumeChangeDispatcher.Stub cb = mock(IAudioVolumeChangeDispatcher.Stub.class);
            when(cb.asBinder()).thenReturn(mock(IBinder.class));
            validCbs.add(cb);
        }
        for (final IAudioVolumeChangeDispatcher.Stub cb : validCbs) {
            mAudioVolumeChangedHandler.registerListener(cb);
        }
        AudioVolumeGroupChangeEvent volEvent = new AudioVolumeGroupChangeEvent();
        volEvent.groupId = 666;
        volEvent.flags = AudioVolumeGroupChangeEvent.VOLUME_FLAG_FROM_KEY;

        try  {
            mINativeAudioVolumeGroupCallbackCaptor.getValue().onAudioVolumeGroupChanged(volEvent);

            for (final IAudioVolumeChangeDispatcher.Stub cb : validCbs) {
                verify(cb,  timeout(DEFAULT_TIMEOUT_MS)).onAudioVolumeGroupChanged(
                        eq(volEvent.groupId), eq(volEvent.flags));
            }
        }
        catch (RemoteException e) {
        }
        finally {
            for (final IAudioVolumeChangeDispatcher.Stub cb : validCbs) {
                mAudioVolumeChangedHandler.unregisterListener(cb);
            }
        }
    }
}
