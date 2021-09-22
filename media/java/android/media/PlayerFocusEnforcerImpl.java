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

package android.media;

import android.annotation.NonNull;
import android.content.Context;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.ArrayUtils;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A class to manage system enforced ducking/muting.
 * It is a reference core implementation that can be reused by any External Audio Focus Policy
 * wishing to take benefit of system enforced ducking/mute rather than relying on Application to
 * perform the ducking.
 *
 * @hide
 */
public final class PlayerFocusEnforcerImpl implements PlayerFocusEnforcer {

    /*package*/  static final String TAG = "PlayerFocusEnforcerImpl";
    /*package*/ static final boolean DEBUG = true/*false*/;
    /*package*/ static final int VOLUME_SHAPER_SYSTEM_DUCK_ID = 1;
    /*package*/ static final int VOLUME_SHAPER_SYSTEM_FADEOUT_ID = 2;

    private static final VolumeShaper.Configuration DUCK_VSHAPE =
            new VolumeShaper.Configuration.Builder()
                .setId(VOLUME_SHAPER_SYSTEM_DUCK_ID)
                .setCurve(new float[] { 0.f, 1.f } /* times */,
                    new float[] { 1.f, 0.2f } /* volumes */)
                .setOptionFlags(VolumeShaper.Configuration.OPTION_FLAG_CLOCK_TIME)
                .setDuration(getFocusRampTimeMs(
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .build()))
                .build();
    private static final VolumeShaper.Configuration DUCK_ID =
            new VolumeShaper.Configuration(VOLUME_SHAPER_SYSTEM_DUCK_ID);
    private static final VolumeShaper.Operation PLAY_CREATE_IF_NEEDED =
            new VolumeShaper.Operation.Builder(VolumeShaper.Operation.PLAY)
                    .createIfNeeded()
                    .build();

    // TODO support VolumeShaper on those players
    private static final int[] UNDUCKABLE_PLAYER_TYPES = {
            AudioPlaybackConfiguration.PLAYER_TYPE_AAUDIO,
            AudioPlaybackConfiguration.PLAYER_TYPE_JAM_SOUNDPOOL,
    };

    // like a PLAY_CREATE_IF_NEEDED operation but with a skip to the end of the ramp
    private static final VolumeShaper.Operation PLAY_SKIP_RAMP =
            new VolumeShaper.Operation.Builder(PLAY_CREATE_IF_NEEDED).setXOffset(1.0f).build();

    private final Object mPlayerLock = new Object();
    @GuardedBy("mPlayerLock")
    private final HashMap<Integer, AudioPlaybackConfiguration> mPlayers =
            new HashMap<Integer, AudioPlaybackConfiguration>();

    private final Context mContext;
    private final AudioEventLogger mEventLogger;

    /**
     * @hide
     */
    public PlayerFocusEnforcerImpl(Context context, AudioEventLogger eventLogger) {
        mContext = context;
        mEventLogger = eventLogger;
        mDuckingManager = new DuckingManager(eventLogger);
        mFadingManager = new FadeOutManager(eventLogger);
    }

    /**
     * Return the volume ramp time expected before playback with the given AudioAttributes would
     * start after gaining audio focus.
     * @param attr attributes of the sound about to start playing
     * @return time in ms
     * @hide
     */
    public static int getFocusRampTimeMs(int focusGain, AudioAttributes attr) {
        switch (attr.getUsage()) {
            case AudioAttributes.USAGE_MEDIA:
            case AudioAttributes.USAGE_GAME:
                return 1000;
            case AudioAttributes.USAGE_ALARM:
            case AudioAttributes.USAGE_NOTIFICATION_RINGTONE:
            case AudioAttributes.USAGE_ASSISTANT:
            case AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY:
            case AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE:
            case AudioAttributes.USAGE_ANNOUNCEMENT:
                return 700;
            case AudioAttributes.USAGE_VOICE_COMMUNICATION:
            case AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING:
            case AudioAttributes.USAGE_NOTIFICATION:
            case AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST:
            case AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT:
            case AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_DELAYED:
            case AudioAttributes.USAGE_NOTIFICATION_EVENT:
            case AudioAttributes.USAGE_ASSISTANCE_SONIFICATION:
            case AudioAttributes.USAGE_VEHICLE_STATUS:
                return 500;
            case AudioAttributes.USAGE_EMERGENCY:
            case AudioAttributes.USAGE_SAFETY:
            case AudioAttributes.USAGE_UNKNOWN:
            default:
                return 0;
        }
    }

    private static final int FLAGS_FOR_SILENCE_OVERRIDE =
            AudioAttributes.FLAG_BYPASS_INTERRUPTION_POLICY
            | AudioAttributes.FLAG_BYPASS_MUTE;

    /**
     * For AudioService dump
     * @param pw
     * @hide
     */
    public void dump(PrintWriter pw) {
        synchronized (mPlayerLock) {
            final List<Integer> piidIntList = new ArrayList<Integer>(mPlayers.keySet());
            Collections.sort(piidIntList);
            for (Integer piidInt : piidIntList) {
                final AudioPlaybackConfiguration apc = mPlayers.get(piidInt);
                if (apc != null) {
                    apc.dump(pw);
                }
            }
            // ducked players
            pw.println("\n  ducked players piids:");
            mDuckingManager.dump(pw);
            // faded out players
            pw.println("\n  faded out players piids:");
            mFadingManager.dump(pw);
            // players muted due to the device ringing or being in a call
            pw.print("\n  muted player piids:");
            for (int piid : mMutedPlayers) {
                pw.print(" " + piid);
            }
            pw.println();
        }
    }

    //=================================================================
    // PlayerFocusEnforcer implementation
    private final ArrayList<Integer> mMutedPlayers = new ArrayList<Integer>();

    private final DuckingManager mDuckingManager;

    @Override
    public boolean duckPlayers(
            @NonNull AudioFocusInfo winner, @NonNull AudioFocusInfo loser, boolean forceDuck) {
        if (DEBUG) {
            Log.v(TAG, String.format("duckPlayers: uids winner=%d loser=%d",
                    winner.getClientUid(), loser.getClientUid()));
        }
        synchronized (mPlayerLock) {
            if (mPlayers.isEmpty()) {
                return true;
            }
            // check if this UID needs to be ducked (return false if not), and gather list of
            // eligible players to duck
            final Iterator<AudioPlaybackConfiguration> apcIterator = mPlayers.values().iterator();
            final ArrayList<AudioPlaybackConfiguration> apcsToDuck =
                    new ArrayList<AudioPlaybackConfiguration>();
            while (apcIterator.hasNext()) {
                final AudioPlaybackConfiguration apc = apcIterator.next();
                if (/*winner.getClientUid() != apc.getClientUid()
                        && */loser.getClientUid() == apc.getClientUid()
                        && apc.getPlayerState()
                                == AudioPlaybackConfiguration.PLAYER_STATE_STARTED) {
                    if (!forceDuck && (apc.getAudioAttributes().getContentType()
                            == AudioAttributes.CONTENT_TYPE_SPEECH)) {
                        // the player is speaking, ducking will make the speech unintelligible
                        // so let the app handle it instead
                        Log.v(TAG, "not ducking player " + apc.getPlayerInterfaceId()
                                + " uid:" + apc.getClientUid() + " pid:" + apc.getClientPid()
                                + " - SPEECH");
                        return false;
                    } else if (ArrayUtils.contains(UNDUCKABLE_PLAYER_TYPES, apc.getPlayerType())) {
                        Log.v(TAG, "not ducking player " + apc.getPlayerInterfaceId()
                                + " uid:" + apc.getClientUid() + " pid:" + apc.getClientPid()
                                + " due to type:"
                                + AudioPlaybackConfiguration.toLogFriendlyPlayerType(
                                        apc.getPlayerType()));
                        return false;
                    }
                    apcsToDuck.add(apc);
                }
            }
            // add the players eligible for ducking to the list, and duck them
            // (if apcsToDuck is empty, this will at least mark this uid as ducked, so when
            //  players of the same uid start, they will be ducked by DuckingManager.checkDuck())
            mDuckingManager.duckUid(loser.getClientUid(), apcsToDuck);
        }
        return true;
    }

    @Override
    public void restoreVShapedPlayers(@NonNull AudioFocusInfo winner) {
        if (DEBUG) {
            Log.v(TAG, "unduckPlayers: uids winner=" + winner.getClientUid());
        }
        synchronized (mPlayerLock) {
            mDuckingManager.unduckUid(winner.getClientUid(), mPlayers);
            mFadingManager.unfadeOutUid(winner.getClientUid(), mPlayers);
        }
    }

    @Override
    public void mutePlayersForCall(int[] usagesToMute) {
        if (DEBUG) {
            String log = new String("mutePlayersForCall: usages=");
            for (int usage : usagesToMute) {
                log += " " + usage;
            }
            Log.v(TAG, log);
        }
        synchronized (mPlayerLock) {
            final Set<Integer> piidSet = mPlayers.keySet();
            final Iterator<Integer> piidIterator = piidSet.iterator();
            // find which players to mute
            while (piidIterator.hasNext()) {
                final Integer piid = piidIterator.next();
                final AudioPlaybackConfiguration apc = mPlayers.get(piid);
                if (apc == null) {
                    continue;
                }
                final int playerUsage = apc.getAudioAttributes().getUsage();
                boolean mute = false;
                for (int usageToMute : usagesToMute) {
                    if (playerUsage == usageToMute) {
                        mute = true;
                        break;
                    }
                }
                if (mute) {
                    try {
                        mEventLogger.log((new AudioEventLogger.StringEvent("call: muting piid:"
                                + piid + " uid:" + apc.getClientUid())).printLog(TAG));
                        apc.getPlayerProxy().setVolume(0.0f);
                        mMutedPlayers.add(new Integer(piid));
                    } catch (Exception e) {
                        Log.e(TAG, "call: error muting player " + piid, e);
                    }
                }
            }
        }
    }

    @Override
    public void unmutePlayersForCall() {
        if (DEBUG) {
            Log.v(TAG, "unmutePlayersForCall()");
        }
        synchronized (mPlayerLock) {
            if (mMutedPlayers.isEmpty()) {
                return;
            }
            for (int piid : mMutedPlayers) {
                final AudioPlaybackConfiguration apc = mPlayers.get(piid);
                if (apc != null) {
                    try {
                        mEventLogger.log(new AudioEventLogger.StringEvent("call: unmuting piid:"
                                + piid).printLog(TAG));
                        apc.getPlayerProxy().setVolume(1.0f);
                    } catch (Exception e) {
                        Log.e(TAG, "call: error unmuting player " + piid + " uid:"
                                + apc.getClientUid(), e);
                    }
                }
            }
            mMutedPlayers.clear();
        }
    }

    private final FadeOutManager mFadingManager;

    /**
     *
     * @param winner the new non-transient focus owner
     * @param loser the previous focus owner
     * @return true if there are players being faded out
     */
    @Override
    public boolean fadeOutPlayers(@NonNull AudioFocusInfo winner, @NonNull AudioFocusInfo loser) {
        if (DEBUG) {
            Log.v(TAG, "fadeOutPlayers: winner=" + winner.getPackageName()
                    +  " loser=" + loser.getPackageName());
        }
        boolean loserHasActivePlayers = false;

        // find which players to fade out
        synchronized (mPlayerLock) {
            if (mPlayers.isEmpty()) {
                if (DEBUG) { Log.v(TAG, "no players to fade out"); }
                return false;
            }
            if (!FadeOutManager.canCauseFadeOut(winner, loser)) {
                return false;
            }
            // check if this UID needs to be faded out (return false if not), and gather list of
            // eligible players to fade out
            final Iterator<AudioPlaybackConfiguration> apcIterator = mPlayers.values().iterator();
            final ArrayList<AudioPlaybackConfiguration> apcsToFadeOut =
                    new ArrayList<AudioPlaybackConfiguration>();
            while (apcIterator.hasNext()) {
                final AudioPlaybackConfiguration apc = apcIterator.next();
                if (!winner.hasSameUid(apc.getClientUid())
                        && loser.hasSameUid(apc.getClientUid())
                        && apc.getPlayerState()
                        == AudioPlaybackConfiguration.PLAYER_STATE_STARTED) {
                    if (!FadeOutManager.canBeFadedOut(apc)) {
                        // the player is not eligible to be faded out, bail
                        Log.v(TAG, "not fading out player " + apc.getPlayerInterfaceId()
                                + " uid:" + apc.getClientUid() + " pid:" + apc.getClientPid()
                                + " type:"
                                + AudioPlaybackConfiguration.toLogFriendlyPlayerType(
                                        apc.getPlayerType())
                                + " attr:" + apc.getAudioAttributes());
                        return false;
                    }
                    loserHasActivePlayers = true;
                    apcsToFadeOut.add(apc);
                }
            }
            if (loserHasActivePlayers) {
                mFadingManager.fadeOutUid(loser.getClientUid(), apcsToFadeOut);
            }
        }

        return loserHasActivePlayers;
    }

    @Override
    public void forgetUid(int uid) {
        final HashMap<Integer, AudioPlaybackConfiguration> players;
        synchronized (mPlayerLock) {
            players = (HashMap<Integer, AudioPlaybackConfiguration>) mPlayers.clone();
        }
        mFadingManager.unfadeOutUid(uid, players);
    }

    /**
     * @hide
     */
    public void addPlayer(int piid, AudioPlaybackConfiguration apc) {
        synchronized (mPlayerLock) {
            mPlayers.put(piid, apc);
        }
    }

    /**
     * @hide
     */
    public void removePlayer(int piid) {
        synchronized (mPlayerLock) {
            final AudioPlaybackConfiguration apc = mPlayers.get(new Integer(piid));
            mPlayers.remove(new Integer(piid));
            mDuckingManager.removeReleased(apc);
            mFadingManager.removeReleased(apc);
        }
    }

    /**
     * @hide
     */
    public void clearPlayers() {
        synchronized (mPlayerLock) {
            mPlayers.clear();
        }
    }

    /**
     * @hide
     */
    public ArrayList<AudioPlaybackConfiguration> getActivePlaybackConfigurations() {
        synchronized (mPlayerLock) {
            return new ArrayList<AudioPlaybackConfiguration>(mPlayers.values());
        }
    }

    /**
     * @hide
     */
    public AudioPlaybackConfiguration getPlaybackConfigurationForPlayerIid(int piid) {
        synchronized (mPlayerLock) {
            return mPlayers.get(new Integer(piid));
        }
    }

    /**
     * @hide
     */
    public void checkDuckForPlayer(AudioPlaybackConfiguration apc) {
        synchronized (mPlayerLock) {
            mDuckingManager.checkDuck(apc);
            mFadingManager.checkFade(apc);
        }
    }

    //=================================================================
    // Class to handle ducking related operations for a given UID
    private static final class DuckingManager {
        private final HashMap<Integer, DuckedApp> mDuckers = new HashMap<Integer, DuckedApp>();
        private final AudioEventLogger mEventLogger;

        DuckingManager(AudioEventLogger eventLogger) {
            mEventLogger = eventLogger;
        }

        synchronized void duckUid(int uid, ArrayList<AudioPlaybackConfiguration> apcsToDuck) {
            if (DEBUG) {
                Log.v(TAG, "DuckingManager: duckUid() uid:" + uid);
            }
            if (!mDuckers.containsKey(uid)) {
                mDuckers.put(uid, new DuckedApp(uid, mEventLogger));
            }
            final DuckedApp da = mDuckers.get(uid);
            for (AudioPlaybackConfiguration apc : apcsToDuck) {
                da.addDuck(apc, false /*skipRamp*/);
            }
        }

        synchronized void unduckUid(int uid, HashMap<Integer, AudioPlaybackConfiguration> players) {
            if (DEBUG) {
                Log.v(TAG, "DuckingManager: unduckUid() uid:" + uid);
            }
            final DuckedApp da = mDuckers.remove(uid);
            if (da == null) {
                return;
            }
            da.removeUnduckAll(players);
        }

        // pre-condition: apc.getPlayerState() == AudioPlaybackConfiguration.PLAYER_STATE_STARTED
        synchronized void checkDuck(@NonNull AudioPlaybackConfiguration apc) {
            if (DEBUG) {
                Log.v(TAG, "DuckingManager: checkDuck() player piid:"
                        + apc.getPlayerInterfaceId() + " uid:" + apc.getClientUid());
            }
            final DuckedApp da = mDuckers.get(apc.getClientUid());
            if (da == null) {
                return;
            }
            da.addDuck(apc, true /*skipRamp*/);
        }

        synchronized void dump(PrintWriter pw) {
            for (DuckedApp da : mDuckers.values()) {
                da.dump(pw);
            }
        }

        synchronized void removeReleased(@NonNull AudioPlaybackConfiguration apc) {
            final int uid = apc.getClientUid();
            if (DEBUG) {
                Log.v(TAG, "DuckingManager: removedReleased() player piid: "
                        + apc.getPlayerInterfaceId() + " uid:" + uid);
            }
            final DuckedApp da = mDuckers.get(uid);
            if (da == null) {
                return;
            }
            da.removeReleased(apc);
        }

        private static final class DuckedApp {
            private final int mUid;
            private final ArrayList<Integer> mDuckedPlayers = new ArrayList<Integer>();
            private final AudioEventLogger mEventLogger;

            DuckedApp(int uid, AudioEventLogger eventLogger) {
                mUid = uid;
                mEventLogger = eventLogger;
            }

            void dump(PrintWriter pw) {
                pw.print("\t uid:" + mUid + " piids:");
                for (int piid : mDuckedPlayers) {
                    pw.print(" " + piid);
                }
                pw.println("");
            }

            // pre-conditions:
            //  * apc != null
            //  * apc.getPlayerState() == AudioPlaybackConfiguration.PLAYER_STATE_STARTED
            void addDuck(@NonNull AudioPlaybackConfiguration apc, boolean skipRamp) {
                final int piid = new Integer(apc.getPlayerInterfaceId());
                if (mDuckedPlayers.contains(piid)) {
                    if (DEBUG) {
                        Log.v(TAG, "player piid:" + piid + " already ducked");
                    }
                    return;
                }
                try {
                    Log.v(TAG, "player piid:" + piid + " added for Duck");
                    mEventLogger.log((new DuckEvent(apc, skipRamp)).printLog(TAG));
                    apc.getPlayerProxy().applyVolumeShaper(
                            DUCK_VSHAPE,
                            skipRamp ? PLAY_SKIP_RAMP : PLAY_CREATE_IF_NEEDED);
                    mDuckedPlayers.add(piid);
                } catch (Exception e) {
                    Log.e(TAG, "Error ducking player piid:" + piid + " uid:" + mUid, e);
                }
            }

            void removeUnduckAll(HashMap<Integer, AudioPlaybackConfiguration> players) {
                for (int piid : mDuckedPlayers) {
                    final AudioPlaybackConfiguration apc = players.get(piid);
                    if (apc != null) {
                        try {
                            mEventLogger.log((new AudioEventLogger.StringEvent("unducking piid:"
                                    + piid)).printLog(TAG));
                            apc.getPlayerProxy().applyVolumeShaper(
                                    DUCK_ID,
                                    VolumeShaper.Operation.REVERSE);
                        } catch (Exception e) {
                            Log.e(TAG, "Error unducking player piid:" + piid + " uid:" + mUid, e);
                        }
                    } else {
                        // this piid was in the list of ducked players, but wasn't found
                        if (DEBUG) {
                            Log.v(TAG, "Error unducking player piid:" + piid
                                    + ", player not found for uid " + mUid);
                        }
                    }
                }
                mDuckedPlayers.clear();
            }

            void removeReleased(@NonNull AudioPlaybackConfiguration apc) {
                mDuckedPlayers.remove(new Integer(apc.getPlayerInterfaceId()));
            }
        }
    }

    //=================================================================
    // For logging
    /*private package*/ abstract static class VolumeShaperEvent extends AudioEventLogger.Event {
        private final int mPlayerIId;
        private final boolean mSkipRamp;
        private final int mClientUid;
        private final int mClientPid;

        abstract String getVSAction();

        VolumeShaperEvent(@NonNull AudioPlaybackConfiguration apc, boolean skipRamp) {
            mPlayerIId = apc.getPlayerInterfaceId();
            mSkipRamp = skipRamp;
            mClientUid = apc.getClientUid();
            mClientPid = apc.getClientPid();
        }

        @Override
        public String eventToString() {
            return new StringBuilder(getVSAction()).append(" player piid:").append(mPlayerIId)
                    .append(" uid/pid:").append(mClientUid).append("/").append(mClientPid)
                    .append(" skip ramp:").append(mSkipRamp).toString();
        }
    }

    static final class DuckEvent extends VolumeShaperEvent {
        @Override
        String getVSAction() {
            return "ducking";
        }

        DuckEvent(@NonNull AudioPlaybackConfiguration apc, boolean skipRamp) {
            super(apc, skipRamp);
        }
    }
}
