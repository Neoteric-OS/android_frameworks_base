/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.media.AudioAttributes;
import android.media.AudioFocusInfo;
import android.media.AudioManager;
import android.media.FadeOutManager;
import android.media.IAudioFocusDispatcher;
import android.os.IBinder;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.server.audio.MediaFocusControl.AudioFocusDeathHandler;

import java.io.PrintWriter;

/**
 * @hide
 * Class to handle all the information about a user of audio focus. The lifecycle of each
 * instance is managed by android.media.MediaFocusControl, from its addition to the audio focus
 * stack, or the map of focus owners for an external focus policy, to its release.
 */
public class FocusRequester {

    // on purpose not using this classe's name, as it will only be used from MediaFocusControl
    private static final String TAG = "MediaFocusControl";
    private static final boolean DEBUG = false;

    private AudioFocusDeathHandler mDeathHandler; // may be null
    private IAudioFocusDispatcher mFocusDispatcher; // may be null
    private final IBinder mSourceRef; // may be null
    private final MediaFocusControl mFocusController; // never null

    /**
     * The AudioFocusInformation associated with this gain request that caused the addition of this
     * object in the focus stack..
     * It is qualified by:
     *      - client ID / packageName / SDK Target / calling UID (or client UID) / Audio Attributes
     *      - audio focus gain request
     *      - flags associated with the gain request that qualify the type of grant (e.g. accepting
     *              delay vs grant must be immediate)
     *      - loss received by mFocusDispatcher,
     *              is AudioManager.AUDIOFOCUS_NONE if it never lost focus.
     */
    private final @NonNull AudioFocusInfo mAudioFocusInfo;

    /**
     * whether this focus owner listener was notified when it lost focus
     */
    private boolean mFocusLossWasNotified;
    /**
     * whether this focus owner has already lost focus, but is being faded out until focus loss
     * dispatch occurs. It's in "limbo" mode: has lost focus but not released yet until notified
     */
    boolean mFocusLossFadeLimbo;

    /**
     * Class constructor
     * @param aa
     * @param focusRequest
     * @param grantFlags
     * @param afl
     * @param source
     * @param id
     * @param hdlr
     * @param pn
     * @param uid
     * @param ctlr cannot be null
     */
    FocusRequester(@NonNull AudioAttributes aa, int focusRequest, int grantFlags,
            IAudioFocusDispatcher afl, IBinder source, @NonNull String id,
            AudioFocusDeathHandler hdlr, @NonNull String pn, int uid,
            @NonNull MediaFocusControl ctlr, int sdk) {
        mFocusDispatcher = afl;
        mSourceRef = source;
        mDeathHandler = hdlr;
        mFocusLossWasNotified = true;
        mFocusLossFadeLimbo = false;
        mFocusController = ctlr;
        mAudioFocusInfo = new AudioFocusInfo(
                aa, uid, id, pn, focusRequest, AudioManager.AUDIOFOCUS_NONE, grantFlags, sdk);
    }

    FocusRequester(@NonNull AudioFocusInfo afi, IAudioFocusDispatcher afl,
             IBinder source, AudioFocusDeathHandler hdlr, @NonNull MediaFocusControl ctlr) {
        mFocusLossWasNotified = true;
        mFocusLossFadeLimbo = false;

        mFocusDispatcher = afl;
        mSourceRef = source;
        mDeathHandler = hdlr;
        mFocusController = ctlr;
        mAudioFocusInfo = afi;
    }

    AudioFocusInfo getAudioFocusInfo() {
         return mAudioFocusInfo;
    }

    boolean hasSameClient(String otherClient) {
        return getClientId().compareTo(otherClient) == 0;
    }

    boolean isLockedFocusOwner() {
        return ((getGrantFlags() & AudioManager.AUDIOFOCUS_FLAG_LOCK) != 0);
    }

    /**
     * @return true if the focus requester is scheduled to receive a focus loss
     */
    boolean isInFocusLossLimbo() {
        return mFocusLossFadeLimbo;
    }

    boolean hasSameBinder(IBinder ib) {
        return (mSourceRef != null) && mSourceRef.equals(ib);
    }

    boolean hasSameDispatcher(IAudioFocusDispatcher fd) {
        return (mFocusDispatcher != null) && mFocusDispatcher.equals(fd);
    }

    @NonNull String getPackageName() {
        return mAudioFocusInfo.getPackageName();
    }

    boolean hasSamePackage(@NonNull String pack) {
        return mAudioFocusInfo.getPackageName().compareTo(pack) == 0;
    }

    boolean hasSameUid(int uid) {
        return mAudioFocusInfo.getClientUid() == uid;
    }

    int getClientUid() {
        return mAudioFocusInfo.getClientUid();
    }

    String getClientId() {
        return mAudioFocusInfo.getClientId();
    }

    int getGainRequest() {
        return mAudioFocusInfo.getGainRequest();
    }

    int getGrantFlags() {
        return mAudioFocusInfo.getFlags();
    }

    @NonNull AudioAttributes getAudioAttributes() {
        return mAudioFocusInfo.getAttributes();
    }

    int getSdkTarget() {
        return mAudioFocusInfo.getSdkTarget();
    }

    private String focusGainToString() {
        return AudioFocusInfo.focusChangeToString(getGainRequest());
    }

    private String focusLossToString() {
        return AudioFocusInfo.focusChangeToString(mAudioFocusInfo.getLossReceived());
    }

    void dump(PrintWriter pw) {
        pw.println("  source:" + mSourceRef + " -- " + mAudioFocusInfo
                + " -- notified: " + mFocusLossWasNotified
                + " -- limbo" + mFocusLossFadeLimbo);
    }

    /**
     * Clear all references, except for instances in "loss limbo" due to the current fade out
     * for which there will be an attempt to be clear after the loss has been notified
     */
    void maybeRelease() {
        if (!mFocusLossFadeLimbo) {
            release();
        }
    }

    void release() {
        final IBinder srcRef = mSourceRef;
        final AudioFocusDeathHandler deathHdlr = mDeathHandler;
        try {
            if (srcRef != null && deathHdlr != null) {
                srcRef.unlinkToDeath(deathHdlr, 0);
            }
        } catch (java.util.NoSuchElementException e) { }
        mDeathHandler = null;
        mFocusDispatcher = null;
    }

    @Override
    protected void finalize() throws Throwable {
        release();
        super.finalize();
    }

    /**
     * For a given audio focus gain request, return the audio focus loss type that will result
     * from it, taking into account any previous focus loss.
     * @param gainRequest
     * @return the audio focus loss type that matches the gain request
     */
    private int focusLossForGainRequest(int gainRequest) {
        switch(gainRequest) {
            case AudioManager.AUDIOFOCUS_GAIN:
                switch(mAudioFocusInfo.getLossReceived()) {
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    case AudioManager.AUDIOFOCUS_LOSS:
                    case AudioManager.AUDIOFOCUS_NONE:
                        return AudioManager.AUDIOFOCUS_LOSS;
                }
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                switch(mAudioFocusInfo.getLossReceived()) {
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    case AudioManager.AUDIOFOCUS_NONE:
                        return AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
                    case AudioManager.AUDIOFOCUS_LOSS:
                        return AudioManager.AUDIOFOCUS_LOSS;
                }
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                switch(mAudioFocusInfo.getLossReceived()) {
                    case AudioManager.AUDIOFOCUS_NONE:
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        return AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        return AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
                    case AudioManager.AUDIOFOCUS_LOSS:
                        return AudioManager.AUDIOFOCUS_LOSS;
                }
            default:
                Log.e(TAG, "focusLossForGainRequest() for invalid focus request "+ gainRequest);
                        return AudioManager.AUDIOFOCUS_NONE;
        }
    }

    /**
     * Handle the loss of focus resulting from a given focus gain.
     * @param focusGain the focus gain from which the loss of focus is resulting
     * @param frWinner the new focus owner
     * @return true if the focus loss is definitive, false otherwise.
     */
    @GuardedBy("MediaFocusControl.mAudioFocusLock")
    boolean handleFocusLossFromGain(int focusGain, final FocusRequester frWinner, boolean forceDuck)
    {
        final int focusLoss = focusLossForGainRequest(focusGain);
        handleFocusLoss(focusLoss, frWinner, forceDuck);
        return (focusLoss == AudioManager.AUDIOFOCUS_LOSS);
    }

    @GuardedBy("MediaFocusControl.mAudioFocusLock")
    void handleFocusGain(int focusGain) {
        try {
            mAudioFocusInfo.setLossReceived(AudioManager.AUDIOFOCUS_NONE);
            mFocusLossFadeLimbo = false;
            mFocusController.notifyExtPolicyFocusGrant_syncAf(mAudioFocusInfo,
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
            final IAudioFocusDispatcher fd = mFocusDispatcher;
            if (fd != null) {
                if (DEBUG) {
                    Log.v(TAG, "dispatching " + AudioFocusInfo.focusChangeToString(focusGain)
                        + " to "
                        + getClientId());
                }
                if (mFocusLossWasNotified) {
                    fd.dispatchAudioFocusChange(focusGain, getClientId());
                }
            }
            mFocusController.restoreVShapedPlayers(this.getAudioFocusInfo());
        } catch (android.os.RemoteException e) {
            Log.e(TAG, "Failure to signal gain of audio focus due to: ", e);
        }
    }

    @GuardedBy("MediaFocusControl.mAudioFocusLock")
    void handleFocusGainFromRequest(int focusRequestResult) {
        if (focusRequestResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            mFocusController.restoreVShapedPlayers(this.getAudioFocusInfo());
        }
    }

    @GuardedBy("MediaFocusControl.mAudioFocusLock")
    void handleFocusLoss(int focusLoss, @Nullable final FocusRequester frWinner, boolean forceDuck)
    {
        try {
            if (focusLoss != mAudioFocusInfo.getLossReceived()) {
                mAudioFocusInfo.setLossReceived(focusLoss);
                mFocusLossWasNotified = false;
                // before dispatching a focus loss, check if the following conditions are met:
                // 1/ the framework is not supposed to notify the focus loser on a DUCK loss
                //    (i.e. it has a focus controller that implements a ducking policy)
                // 2/ it is a DUCK loss
                // 3/ the focus loser isn't flagged as pausing in a DUCK loss
                // if they are, do not notify the focus loser
                if (!mFocusController.mustNotifyFocusOwnerOnDuck()
                        && mAudioFocusInfo.getLossReceived()
                                == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
                        && (getGrantFlags()
                                & AudioManager.AUDIOFOCUS_FLAG_PAUSES_ON_DUCKABLE_LOSS) == 0) {
                    if (DEBUG) {
                        Log.v(TAG, "NOT dispatching " + focusLossToString()
                                + " to " + getClientId() + ", to be handled externally");
                    }
                    mFocusController.notifyExtPolicyFocusLoss_syncAf(
                            mAudioFocusInfo, false /* wasDispatched */);
                    return;
                }

                // check enforcement by the framework
                boolean handled = false;
                if (frWinner != null) {
                    handled = frameworkHandleFocusLoss(focusLoss, frWinner, forceDuck);
                }

                if (handled) {
                    if (DEBUG) {
                        Log.v(TAG, "NOT dispatching " + focusLossToString()
                                + " to " + getClientId() + ", response handled by framework");
                    }
                    mFocusController.notifyExtPolicyFocusLoss_syncAf(
                            mAudioFocusInfo, false /* wasDispatched */);
                    return; // with mFocusLossWasNotified = false
                }

                final IAudioFocusDispatcher fd = mFocusDispatcher;
                if (fd != null) {
                    if (DEBUG) {
                        Log.v(TAG, "dispatching " + focusLossToString() + " to "
                            + getClientId());
                    }
                    mFocusController.notifyExtPolicyFocusLoss_syncAf(
                            mAudioFocusInfo, true /* wasDispatched */);
                    mFocusLossWasNotified = true;
                    fd.dispatchAudioFocusChange(mAudioFocusInfo.getLossReceived(), getClientId());
                }
            }
        } catch (android.os.RemoteException e) {
            Log.e(TAG, "Failure to signal loss of audio focus due to:", e);
        }
    }

    /**
     * Let the framework handle the focus loss if possible
     * @param focusLoss
     * @param frWinner
     * @param forceDuck
     * @return true if the framework handled the focus loss
     */
    @GuardedBy("MediaFocusControl.mAudioFocusLock")
    private boolean frameworkHandleFocusLoss(int focusLoss, @NonNull final FocusRequester frWinner,
                                             boolean forceDuck) {
        if (frWinner.getClientUid() == this.getClientUid()) {
            // the focus change is within the same app, so let the dispatching
            // happen as if the framework was not involved.
            return false;
        }

        if (focusLoss == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            if (!MediaFocusControl.ENFORCE_DUCKING) {
                return false;
            }

            // candidate for enforcement by the framework
            if (!forceDuck && ((getGrantFlags()
                    & AudioManager.AUDIOFOCUS_FLAG_PAUSES_ON_DUCKABLE_LOSS) != 0)) {
                // the focus loser declared it would pause instead of duck, let it
                // handle it (the framework doesn't pause for apps)
                Log.v(TAG, "not ducking uid " + this.getClientUid() + " - flags");
                return false;
            }
            if (!forceDuck && (MediaFocusControl.ENFORCE_DUCKING_FOR_NEW
                    && this.getSdkTarget() <= MediaFocusControl.DUCKING_IN_APP_SDK_LEVEL)) {
                // legacy behavior, apps used to be notified when they should be ducking
                Log.v(TAG, "not ducking uid " + this.getClientUid() + " - old SDK");
                return false;
            }

            return mFocusController.duckPlayers(
                    frWinner.getAudioFocusInfo(), /*loser*/ this.getAudioFocusInfo(), forceDuck);
        }

        if (focusLoss == AudioManager.AUDIOFOCUS_LOSS) {
            if (!MediaFocusControl.ENFORCE_FADEOUT_FOR_FOCUS_LOSS) {
                return false;
            }

            // candidate for fade-out before a receiving a loss
            boolean playersAreFaded =  mFocusController.fadeOutPlayers(
                    frWinner.getAudioFocusInfo(), /* loser */ this.getAudioFocusInfo());
            if (playersAreFaded) {
                // active players are being faded out, delay the dispatch of focus loss
                // mark this instance as being faded so it's not released yet as the focus loss
                // will be dispatched later, it is now in limbo mode
                mFocusLossFadeLimbo = true;
                mFocusController.postDelayedLossAfterFade(this,
                        FadeOutManager.FADE_OUT_DURATION_MS);
                return true;
            }
        }

        return false;
    }

    int dispatchFocusChange(int focusChange) {
        final IAudioFocusDispatcher fd = mFocusDispatcher;
        if (fd == null) {
            if (MediaFocusControl.DEBUG) { Log.e(TAG, "dispatchFocusChange: no focus dispatcher"); }
            return AudioManager.AUDIOFOCUS_REQUEST_FAILED;
        }
        if (focusChange == AudioManager.AUDIOFOCUS_NONE) {
            if (MediaFocusControl.DEBUG) { Log.v(TAG, "dispatchFocusChange: AUDIOFOCUS_NONE"); }
            return AudioManager.AUDIOFOCUS_REQUEST_FAILED;
        } else if ((focusChange == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                || focusChange == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                || focusChange == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                || focusChange == AudioManager.AUDIOFOCUS_GAIN)
                && (getGainRequest() != focusChange)){
            Log.w(TAG, "focus gain was requested with " + getGainRequest()
                    + ", dispatching " + focusChange);
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
                || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                || focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            mAudioFocusInfo.setLossReceived(focusChange);
        }
        try {
            fd.dispatchAudioFocusChange(focusChange, getClientId());
        } catch (android.os.RemoteException e) {
            Log.e(TAG, "dispatchFocusChange: error talking to focus listener " + getClientId(), e);
            return AudioManager.AUDIOFOCUS_REQUEST_FAILED;
        }
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    void dispatchFocusResultFromExtPolicy(int requestResult) {
        final IAudioFocusDispatcher fd = mFocusDispatcher;
        if (fd == null) {
            if (MediaFocusControl.DEBUG) {
                Log.e(TAG, "dispatchFocusResultFromExtPolicy: no focus dispatcher");
            }
            return;
        }
        if (DEBUG) {
            Log.v(TAG, "dispatching result" + requestResult + " to " + getClientId());
        }
        try {
            fd.dispatchFocusResultFromExtPolicy(requestResult, getClientId());
        } catch (android.os.RemoteException e) {
            Log.e(TAG, "dispatchFocusResultFromExtPolicy: error talking to focus listener"
                    + getClientId(), e);
        }
    }
}
