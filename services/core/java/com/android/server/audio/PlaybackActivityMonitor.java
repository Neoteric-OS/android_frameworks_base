/*
 * Copyright (C) 2016 The Android Open Source Project
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
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioEventLogger;
import android.media.AudioFocusInfo;
import android.media.AudioPlaybackConfiguration;
import android.media.AudioSystem;
import android.media.IPlaybackConfigDispatcher;
import android.media.PlayerBase;
import android.media.PlayerFocusEnforcer;
import android.media.PlayerFocusEnforcerImpl;
import android.media.VolumeShaper;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * Class to receive and dispatch updates from AudioSystem about recording configurations.
 */
/* private package */ final class PlaybackActivityMonitor
        implements AudioPlaybackConfiguration.PlayerDeathMonitor, PlayerFocusEnforcer {

    public static final String TAG = "AudioService.PlaybackActivityMonitor";

    /*package*/ static final boolean DEBUG = false;

    private final ArrayList<PlayMonitorClient> mClients = new ArrayList<PlayMonitorClient>();
    // a public client is one that needs an anonymized version of the playback configurations, we
    // keep track of whether there is at least one to know when we need to create the list of
    // playback configurations that do not contain uid/pid/package name information.
    private boolean mHasPublicClients = false;

    private final Object mPlayerLock = new Object();

    private final Context mContext;
    private int mSavedAlarmVolume = -1;
    private final int mMaxAlarmVolume;
    private int mPrivilegedAlarmActiveCount = 0;
    @GuardedBy("mPlayerLock")
    private final @NonNull PlayerFocusEnforcerImpl mPlayerFocusEnforcerImpl;

    PlaybackActivityMonitor(Context context, int maxAlarmVolume) {
        mContext = context;
        mMaxAlarmVolume = maxAlarmVolume;
        PlayMonitorClient.sListenerDeathMonitor = this;
        AudioPlaybackConfiguration.sPlayerDeathMonitor = this;
        mPlayerFocusEnforcerImpl = new PlayerFocusEnforcerImpl(context, sEventLogger);
    }

    //=================================================================
    private final ArrayList<Integer> mBannedUids = new ArrayList<Integer>();

    // see AudioManagerInternal.disableAudioForUid(boolean disable, int uid)
    public void disableAudioForUid(boolean disable, int uid) {
        synchronized(mPlayerLock) {
            final int index = mBannedUids.indexOf(new Integer(uid));
            if (index >= 0) {
                if (!disable) {
                    if (DEBUG) { // hidden behind DEBUG, too noisy otherwise
                        sEventLogger.log(new AudioEventLogger.StringEvent("unbanning uid:" + uid));
                    }
                    mBannedUids.remove(index);
                    // nothing else to do, future playback requests from this uid are ok
                } // no else to handle, uid already present, so disabling again is no-op
            } else {
                if (disable) {
                    final ArrayList<AudioPlaybackConfiguration> apcs =
                            mPlayerFocusEnforcerImpl.getActivePlaybackConfigurations();
                    for (AudioPlaybackConfiguration apc : apcs) {
                        checkBanPlayer(apc, uid);
                    }
                    if (DEBUG) { // hidden behind DEBUG, too noisy otherwise
                        sEventLogger.log(new AudioEventLogger.StringEvent("banning uid:" + uid));
                    }
                    mBannedUids.add(new Integer(uid));
                } // no else to handle, uid already not in list, so enabling again is no-op
            }
        }
    }

    private boolean checkBanPlayer(@NonNull AudioPlaybackConfiguration apc, int uid) {
        final boolean toBan = (apc.getClientUid() == uid);
        if (toBan) {
            final int piid = apc.getPlayerInterfaceId();
            try {
                Log.v(TAG, "banning player " + piid + " uid:" + uid);
                apc.getPlayerProxy().pause();
            } catch (Exception e) {
                Log.e(TAG, "error banning player " + piid + " uid:" + uid, e);
            }
        }
        return toBan;
    }

    //=================================================================
    // Track players and their states
    // methods playerAttributes, playerEvent, releasePlayer are all oneway calls
    //  into AudioService. They trigger synchronous dispatchPlaybackChange() which updates
    //  all listeners as oneway calls.

    public int trackPlayer(PlayerBase.PlayerIdCard pic) {
        final int newPiid = AudioSystem.newAudioPlayerId();
        if (DEBUG) { Log.v(TAG, "trackPlayer() new piid=" + newPiid); }
        final AudioPlaybackConfiguration apc =
                new AudioPlaybackConfiguration(pic, newPiid,
                        Binder.getCallingUid(), Binder.getCallingPid());
        apc.init();
        synchronized (mAllowedCapturePolicies) {
            int uid = apc.getClientUid();
            if (mAllowedCapturePolicies.containsKey(uid)) {
                updateAllowedCapturePolicy(apc, mAllowedCapturePolicies.get(uid));
            }
        }
        sEventLogger.log(new NewPlayerEvent(apc));
        synchronized(mPlayerLock) {
            mPlayerFocusEnforcerImpl.addPlayer(newPiid, apc);
        }
        return newPiid;
    }

    public void playerAttributes(int piid, @NonNull AudioAttributes attr, int binderUid) {
        final boolean change;
        synchronized (mAllowedCapturePolicies) {
            if (mAllowedCapturePolicies.containsKey(binderUid)
                    && attr.getAllowedCapturePolicy() < mAllowedCapturePolicies.get(binderUid)) {
                attr = new AudioAttributes.Builder(attr)
                        .setAllowedCapturePolicy(mAllowedCapturePolicies.get(binderUid)).build();
            }
        }
        synchronized(mPlayerLock) {
            final AudioPlaybackConfiguration apc =
                    mPlayerFocusEnforcerImpl.getPlaybackConfigurationForPlayerIid(piid);
            if (checkConfigurationCaller(piid, apc, binderUid)) {
                sEventLogger.log(new AudioAttrEvent(piid, attr));
                change = apc.handleAudioAttributesEvent(attr);
            } else {
                Log.e(TAG, "Error updating audio attributes");
                change = false;
            }
        }
        if (change) {
            dispatchPlaybackChange(false);
        }
    }

    /**
     * Update player session ID
     * @param piid Player id to update
     * @param sessionId The new audio session ID
     * @param binderUid Calling binder uid
     */
    public void playerSessionId(int piid, int sessionId, int binderUid) {
        final boolean change;
        synchronized (mPlayerLock) {
            final AudioPlaybackConfiguration apc =
                    mPlayerFocusEnforcerImpl.getPlaybackConfigurationForPlayerIid(piid);
            if (checkConfigurationCaller(piid, apc, binderUid)) {
                change = apc.handleSessionIdEvent(sessionId);
            } else {
                Log.e(TAG, "Error updating audio session");
                change = false;
            }
        }
        if (change) {
            dispatchPlaybackChange(false);
        }
    }

    private static final int FLAGS_FOR_SILENCE_OVERRIDE =
            AudioAttributes.FLAG_BYPASS_INTERRUPTION_POLICY |
            AudioAttributes.FLAG_BYPASS_MUTE;

    private void checkVolumeForPrivilegedAlarm(AudioPlaybackConfiguration apc, int event) {
        if (event == AudioPlaybackConfiguration.PLAYER_STATE_STARTED ||
                apc.getPlayerState() == AudioPlaybackConfiguration.PLAYER_STATE_STARTED) {
            if ((apc.getAudioAttributes().getAllFlags() & FLAGS_FOR_SILENCE_OVERRIDE)
                        == FLAGS_FOR_SILENCE_OVERRIDE  &&
                    apc.getAudioAttributes().getUsage() == AudioAttributes.USAGE_ALARM &&
                    mContext.checkPermission(android.Manifest.permission.MODIFY_PHONE_STATE,
                            apc.getClientPid(), apc.getClientUid()) ==
                            PackageManager.PERMISSION_GRANTED) {
                if (event == AudioPlaybackConfiguration.PLAYER_STATE_STARTED &&
                        apc.getPlayerState() != AudioPlaybackConfiguration.PLAYER_STATE_STARTED) {
                    if (mPrivilegedAlarmActiveCount++ == 0) {
                        mSavedAlarmVolume = AudioSystem.getStreamVolumeIndex(
                                AudioSystem.STREAM_ALARM, AudioSystem.DEVICE_OUT_SPEAKER);
                        AudioSystem.setStreamVolumeIndexAS(AudioSystem.STREAM_ALARM,
                                mMaxAlarmVolume, AudioSystem.DEVICE_OUT_SPEAKER);
                    }
                } else if (event != AudioPlaybackConfiguration.PLAYER_STATE_STARTED &&
                        apc.getPlayerState() == AudioPlaybackConfiguration.PLAYER_STATE_STARTED) {
                    if (--mPrivilegedAlarmActiveCount == 0) {
                        if (AudioSystem.getStreamVolumeIndex(
                                AudioSystem.STREAM_ALARM, AudioSystem.DEVICE_OUT_SPEAKER) ==
                                mMaxAlarmVolume) {
                            AudioSystem.setStreamVolumeIndexAS(AudioSystem.STREAM_ALARM,
                                    mSavedAlarmVolume, AudioSystem.DEVICE_OUT_SPEAKER);
                        }
                    }
                }
            }
        }
    }

    /**
     * Update player event
     * @param piid Player id to update
     * @param event The new player event
     * @param deviceId The new player device id
     * @param binderUid Calling binder uid
     */
    public void playerEvent(int piid, int event, int deviceId, int binderUid) {
        if (DEBUG) {
            Log.v(TAG, String.format("playerEvent(piid=%d, deviceId=%d, event=%s)",
                    piid, deviceId, AudioPlaybackConfiguration.playerStateToString(event)));
        }
        final boolean change;
        synchronized(mPlayerLock) {
            final AudioPlaybackConfiguration apc =
                    mPlayerFocusEnforcerImpl.getPlaybackConfigurationForPlayerIid(piid);
            if (apc == null) {
                return;
            }
            sEventLogger.log(new PlayerEvent(piid, event, deviceId));
            if (event == AudioPlaybackConfiguration.PLAYER_STATE_STARTED) {
                for (Integer uidInteger: mBannedUids) {
                    if (checkBanPlayer(apc, uidInteger.intValue())) {
                        // player was banned, do not update its state
                        sEventLogger.log(new AudioEventLogger.StringEvent(
                                "not starting piid:" + piid + " ,is banned"));
                        return;
                    }
                }
            }
            if (apc.getPlayerType() == AudioPlaybackConfiguration.PLAYER_TYPE_JAM_SOUNDPOOL) {
                // FIXME SoundPool not ready for state reporting
                return;
            }
            if (checkConfigurationCaller(piid, apc, binderUid)) {
                //TODO add generation counter to only update to the latest state
                checkVolumeForPrivilegedAlarm(apc, event);
                change = apc.handleStateEvent(event, deviceId);
            } else {
                Log.e(TAG, "Error handling event " + event);
                change = false;
            }
            if (change && event == AudioPlaybackConfiguration.PLAYER_STATE_STARTED) {
                mPlayerFocusEnforcerImpl.checkDuckForPlayer(apc);
            }
        }
        if (change) {
            dispatchPlaybackChange(event == AudioPlaybackConfiguration.PLAYER_STATE_RELEASED);
        }
    }

    public void playerHasOpPlayAudio(int piid, boolean hasOpPlayAudio, int binderUid) {
        // no check on UID yet because this is only for logging at the moment
        sEventLogger.log(new PlayerOpPlayAudioEvent(piid, hasOpPlayAudio, binderUid));
    }

    public void releasePlayer(int piid, int binderUid) {
        if (DEBUG) { Log.v(TAG, "releasePlayer() for piid=" + piid); }
        boolean change = false;
        synchronized(mPlayerLock) {
            final AudioPlaybackConfiguration apc =
                    mPlayerFocusEnforcerImpl.getPlaybackConfigurationForPlayerIid(piid);
            if (checkConfigurationCaller(piid, apc, binderUid)) {
                sEventLogger.log(new AudioEventLogger.StringEvent(
                        "releasing player piid:" + piid));
                mPlayerFocusEnforcerImpl.removePlayer(piid);
                checkVolumeForPrivilegedAlarm(apc, AudioPlaybackConfiguration.PLAYER_STATE_RELEASED);
                change = apc.handleStateEvent(AudioPlaybackConfiguration.PLAYER_STATE_RELEASED,
                        AudioPlaybackConfiguration.PLAYER_DEVICEID_INVALID);
            }
        }
        if (change) {
            dispatchPlaybackChange(true /*iplayerreleased*/);
        }
    }

    /**
     * A map of uid to capture policy.
     */
    private final HashMap<Integer, Integer> mAllowedCapturePolicies =
            new HashMap<Integer, Integer>();

    /**
     * Cache allowed capture policy, which specifies whether the audio played by the app may or may
     * not be captured by other apps or the system.
     *
     * @param uid the uid of requested app
     * @param capturePolicy one of
     *     {@link AudioAttributes#ALLOW_CAPTURE_BY_ALL},
     *     {@link AudioAttributes#ALLOW_CAPTURE_BY_SYSTEM},
     *     {@link AudioAttributes#ALLOW_CAPTURE_BY_NONE}.
     */
    public void setAllowedCapturePolicy(int uid, int capturePolicy) {
        synchronized (mAllowedCapturePolicies) {
            if (capturePolicy == AudioAttributes.ALLOW_CAPTURE_BY_ALL) {
                // When the capture policy is ALLOW_CAPTURE_BY_ALL, it is okay to
                // remove it from cached capture policy as it is the default value.
                mAllowedCapturePolicies.remove(uid);
                return;
            } else {
                mAllowedCapturePolicies.put(uid, capturePolicy);
            }
        }
        synchronized (mPlayerLock) {
            final List<AudioPlaybackConfiguration> players =
                    mPlayerFocusEnforcerImpl.getActivePlaybackConfigurations();
            for (AudioPlaybackConfiguration apc : players) {
                if (apc.getClientUid() == uid) {
                    updateAllowedCapturePolicy(apc, capturePolicy);
                }
            }
        }
    }

    /**
     * Return the capture policy for given uid.
     * @param uid the uid to query its cached capture policy.
     * @return cached capture policy for given uid or AudioAttributes.ALLOW_CAPTURE_BY_ALL
     *         if there is not cached capture policy.
     */
    public int getAllowedCapturePolicy(int uid) {
        return mAllowedCapturePolicies.getOrDefault(uid, AudioAttributes.ALLOW_CAPTURE_BY_ALL);
    }

    /**
     * Return a copy of all cached capture policies.
     */
    public HashMap<Integer, Integer> getAllAllowedCapturePolicies() {
        synchronized (mAllowedCapturePolicies) {
            return (HashMap<Integer, Integer>) mAllowedCapturePolicies.clone();
        }
    }

    private void updateAllowedCapturePolicy(AudioPlaybackConfiguration apc, int capturePolicy) {
        AudioAttributes attr = apc.getAudioAttributes();
        if (attr.getAllowedCapturePolicy() >= capturePolicy) {
            return;
        }
        apc.handleAudioAttributesEvent(
                new AudioAttributes.Builder(apc.getAudioAttributes())
                        .setAllowedCapturePolicy(capturePolicy).build());
    }

    // Implementation of AudioPlaybackConfiguration.PlayerDeathMonitor
    @Override
    public void playerDeath(int piid) {
        releasePlayer(piid, 0);
    }

    /**
     * Returns true if a player belonging to the app with given uid is active.
     *
     * @param uid the app uid
     * @return true if a player is active, false otherwise
     */
    public boolean isPlaybackActiveForUid(int uid) {
        synchronized (mPlayerLock) {
            final List<AudioPlaybackConfiguration> players =
                    mPlayerFocusEnforcerImpl.getActivePlaybackConfigurations();
            for (AudioPlaybackConfiguration apc : players) {
                if (apc.isActive() && apc.getClientUid() == uid) {
                    return true;
                }
            }
        }
        return false;
    }

    protected void dump(PrintWriter pw) {
        // players
        pw.println("\nPlaybackActivityMonitor dump time: "
                + DateFormat.getTimeInstance().format(new Date()));
        synchronized(mPlayerLock) {
            pw.println("\n  playback listeners:");
            synchronized(mClients) {
                for (PlayMonitorClient pmc : mClients) {
                    pw.print(" " + (pmc.mIsPrivileged ? "(S)" : "(P)")
                            + pmc.toString());
                }
            }
            pw.println("\n");
            // all players
            pw.println("\n  players:");
            // ducked and muted players
            mPlayerFocusEnforcerImpl.dump(pw);
            // banned players:
            pw.print("\n  banned uids:");
            for (int uid : mBannedUids) {
                pw.print(" " + uid);
            }
            pw.println("\n");
            // log
            sEventLogger.dump(pw);
        }
        synchronized (mAllowedCapturePolicies) {
            pw.println("\n  allowed capture policies:");
            for (HashMap.Entry<Integer, Integer> entry : mAllowedCapturePolicies.entrySet()) {
                pw.println("  uid: " + entry.getKey() + " policy: " + entry.getValue());
            }
        }
    }

    /**
     * Check that piid and uid are valid for the given valid configuration.
     * @param piid the piid of the player.
     * @param apc the configuration found for this piid.
     * @param binderUid actual uid of client trying to signal a player state/event/attributes.
     * @return true if the call is valid and the change should proceed, false otherwise. Always
     *      returns false when apc is null.
     */
    private static boolean checkConfigurationCaller(int piid,
            final AudioPlaybackConfiguration apc, int binderUid) {
        if (apc == null) {
            return false;
        } else if ((binderUid != 0) && (apc.getClientUid() != binderUid)) {
            Log.e(TAG, "Forbidden operation from uid " + binderUid + " for player " + piid);
            return false;
        }
        return true;
    }

    /**
     * Sends new list after update of playback configurations
     * @param iplayerReleased indicates if the change was due to a player being released
     */
    private void dispatchPlaybackChange(boolean iplayerReleased) {
        synchronized (mClients) {
            // typical use case, nobody is listening, don't do any work
            if (mClients.isEmpty()) {
                return;
            }
        }
        if (DEBUG) { Log.v(TAG, "dispatchPlaybackChange to " + mClients.size() + " clients"); }
        final List<AudioPlaybackConfiguration> configsSystem;
        // list of playback configurations for "public consumption". It is only computed if there
        // are non-system playback activity listeners.
        final List<AudioPlaybackConfiguration> configsPublic;
        synchronized (mPlayerLock) {
            configsSystem = mPlayerFocusEnforcerImpl.getActivePlaybackConfigurations();
            if (configsSystem.isEmpty()) {
                return;
            }
        }
        synchronized (mClients) {
            // was done at beginning of method, but could have changed
            if (mClients.isEmpty()) {
                return;
            }
            configsPublic = mHasPublicClients ? anonymizeForPublicConsumption(configsSystem) : null;
            final Iterator<PlayMonitorClient> clientIterator = mClients.iterator();
            while (clientIterator.hasNext()) {
                final PlayMonitorClient pmc = clientIterator.next();
                try {
                    // do not spam the logs if there are problems communicating with this client
                    if (pmc.mErrorCount < PlayMonitorClient.MAX_ERRORS) {
                        if (pmc.mIsPrivileged) {
                            pmc.mDispatcherCb.dispatchPlaybackConfigChange(configsSystem,
                                    iplayerReleased);
                        } else {
                            // non-system clients don't have the control interface IPlayer, so
                            // they don't need to flush commands when a player was released
                            pmc.mDispatcherCb.dispatchPlaybackConfigChange(configsPublic, false);
                        }
                    }
                } catch (RemoteException e) {
                    pmc.mErrorCount++;
                    Log.e(TAG, "Error (" + pmc.mErrorCount +
                            ") trying to dispatch playback config change to " + pmc, e);
                }
            }
        }
    }

    private ArrayList<AudioPlaybackConfiguration> anonymizeForPublicConsumption(
            List<AudioPlaybackConfiguration> sysConfigs) {
        ArrayList<AudioPlaybackConfiguration> publicConfigs =
                new ArrayList<AudioPlaybackConfiguration>();
        // only add active anonymized configurations,
        for (AudioPlaybackConfiguration config : sysConfigs) {
            if (config.isActive()) {
                publicConfigs.add(AudioPlaybackConfiguration.anonymizedCopy(config));
            }
        }
        return publicConfigs;
    }


    //=================================================================
    // PlayerFocusEnforcer implementation
    @Override
    public boolean duckPlayers(
    @NonNull AudioFocusInfo winner, @NonNull AudioFocusInfo loser, boolean forceDuck) {
        synchronized (mPlayerLock) {
            return mPlayerFocusEnforcerImpl.duckPlayers(winner, loser, forceDuck);
        }
    }

    @Override
    public void restoreVShapedPlayers(@NonNull AudioFocusInfo winner) {
        if (DEBUG) { Log.v(TAG, "unduckPlayers: uids winner=" + winner.getClientUid()); }
        synchronized (mPlayerLock) {
            mPlayerFocusEnforcerImpl.restoreVShapedPlayers(winner);
        }
    }

    @Override
    public void mutePlayersForCall(int[] usagesToMute) {
        synchronized (mPlayerLock) {
            mPlayerFocusEnforcerImpl.mutePlayersForCall(usagesToMute);
        }
    }

    @Override
    public void unmutePlayersForCall() {
        synchronized (mPlayerLock) {
            mPlayerFocusEnforcerImpl.unmutePlayersForCall();
        }
    }

    @Override
    public boolean fadeOutPlayers(@NonNull AudioFocusInfo winner, @NonNull AudioFocusInfo loser) {
        synchronized (mPlayerLock) {
            return mPlayerFocusEnforcerImpl.fadeOutPlayers(winner, loser);
        }
    }

    @Override
    public void forgetUid(int uid) {
        synchronized (mPlayerLock) {
            mPlayerFocusEnforcerImpl.forgetUid(uid);
        }
    }

    //=================================================================
    // Track playback activity listeners

    void registerPlaybackCallback(IPlaybackConfigDispatcher pcdb, boolean isPrivileged) {
        if (pcdb == null) {
            return;
        }
        synchronized(mClients) {
            final PlayMonitorClient pmc = new PlayMonitorClient(pcdb, isPrivileged);
            if (pmc.init()) {
                if (!isPrivileged) {
                    mHasPublicClients = true;
                }
                mClients.add(pmc);
            }
        }
    }

    void unregisterPlaybackCallback(IPlaybackConfigDispatcher pcdb) {
        if (pcdb == null) {
            return;
        }
        synchronized(mClients) {
            final Iterator<PlayMonitorClient> clientIterator = mClients.iterator();
            boolean hasPublicClients = false;
            // iterate over the clients to remove the dispatcher to remove, and reevaluate at
            // the same time if we still have a public client.
            while (clientIterator.hasNext()) {
                PlayMonitorClient pmc = clientIterator.next();
                if (pcdb.asBinder().equals(pmc.mDispatcherCb.asBinder())) {
                    pmc.release();
                    clientIterator.remove();
                } else {
                    if (!pmc.mIsPrivileged) {
                        hasPublicClients = true;
                    }
                }
            }
            mHasPublicClients = hasPublicClients;
        }
    }

    List<AudioPlaybackConfiguration> getActivePlaybackConfigurations(boolean isPrivileged) {
        synchronized (mPlayerLock) {
            final List<AudioPlaybackConfiguration> players =
                    mPlayerFocusEnforcerImpl.getActivePlaybackConfigurations();
            if (isPrivileged) {
                return players;
            } else {
                final List<AudioPlaybackConfiguration> configsPublic;
                configsPublic = anonymizeForPublicConsumption(players);
                return configsPublic;
            }
        }
    }


    /**
     * Inner class to track clients that want to be notified of playback updates
     */
    private static final class PlayMonitorClient implements IBinder.DeathRecipient {

        // can afford to be static because only one PlaybackActivityMonitor ever instantiated
        static PlaybackActivityMonitor sListenerDeathMonitor;

        final IPlaybackConfigDispatcher mDispatcherCb;
        final boolean mIsPrivileged;

        int mErrorCount = 0;
        // number of errors after which we don't update this client anymore to not spam the logs
        static final int MAX_ERRORS = 5;

        PlayMonitorClient(IPlaybackConfigDispatcher pcdb, boolean isPrivileged) {
            mDispatcherCb = pcdb;
            mIsPrivileged = isPrivileged;
        }

        public void binderDied() {
            Log.w(TAG, "client died");
            sListenerDeathMonitor.unregisterPlaybackCallback(mDispatcherCb);
        }

        boolean init() {
            try {
                mDispatcherCb.asBinder().linkToDeath(this, 0);
                return true;
            } catch (RemoteException e) {
                Log.w(TAG, "Could not link to client death", e);
                return false;
            }
        }

        void release() {
            mDispatcherCb.asBinder().unlinkToDeath(this, 0);
        }
    }

    //=================================================================
    // For logging
    private final static class PlayerEvent extends AudioEventLogger.Event {
        // only keeping the player interface ID as it uniquely identifies the player in the event
        final int mPlayerIId;
        final int mState;
        final int mDeviceId;

        PlayerEvent(int piid, int state, int deviceId) {
            mPlayerIId = piid;
            mState = state;
            mDeviceId = deviceId;
        }

        @Override
        public String eventToString() {
            return new StringBuilder("player piid:").append(mPlayerIId).append(" state:")
                    .append(AudioPlaybackConfiguration.toLogFriendlyPlayerState(mState))
                    .append(" DeviceId:").append(mDeviceId).toString();
        }
    }

    private final static class PlayerOpPlayAudioEvent extends AudioEventLogger.Event {
        // only keeping the player interface ID as it uniquely identifies the player in the event
        final int mPlayerIId;
        final boolean mHasOp;
        final int mUid;

        PlayerOpPlayAudioEvent(int piid, boolean hasOp, int uid) {
            mPlayerIId = piid;
            mHasOp = hasOp;
            mUid = uid;
        }

        @Override
        public String eventToString() {
            return new StringBuilder("player piid:").append(mPlayerIId)
                    .append(" has OP_PLAY_AUDIO:").append(mHasOp)
                    .append(" in uid:").append(mUid).toString();
        }
    }

    private final static class NewPlayerEvent extends AudioEventLogger.Event {
        private final int mPlayerIId;
        private final int mPlayerType;
        private final int mClientUid;
        private final int mClientPid;
        private final AudioAttributes mPlayerAttr;
        private final int mSessionId;

        NewPlayerEvent(AudioPlaybackConfiguration apc) {
            mPlayerIId = apc.getPlayerInterfaceId();
            mPlayerType = apc.getPlayerType();
            mClientUid = apc.getClientUid();
            mClientPid = apc.getClientPid();
            mPlayerAttr = apc.getAudioAttributes();
            mSessionId = apc.getSessionId();
        }

        @Override
        public String eventToString() {
            return new String("new player piid:" + mPlayerIId + " uid/pid:" + mClientUid + "/"
                    + mClientPid + " type:"
                    + AudioPlaybackConfiguration.toLogFriendlyPlayerType(mPlayerType)
                    + " attr:" + mPlayerAttr
                    + " session:" + mSessionId);
        }
    }

    private static final class AudioAttrEvent extends AudioEventLogger.Event {
        private final int mPlayerIId;
        private final AudioAttributes mPlayerAttr;

        AudioAttrEvent(int piid, AudioAttributes attr) {
            mPlayerIId = piid;
            mPlayerAttr = attr;
        }

        @Override
        public String eventToString() {
            return new String("player piid:" + mPlayerIId + " new AudioAttributes:" + mPlayerAttr);
        }
    }

    static final AudioEventLogger sEventLogger = new AudioEventLogger(100,
            "playback activity as reported through PlayerBase");
}
