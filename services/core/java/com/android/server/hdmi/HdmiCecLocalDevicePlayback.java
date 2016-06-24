/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.server.hdmi;

import android.content.ContentResolver;
import android.content.Intent;
import android.hardware.hdmi.HdmiControlManager;
import android.hardware.hdmi.HdmiDeviceInfo;
import android.hardware.hdmi.IHdmiControlCallback;
import android.net.Uri;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.provider.Settings.Global;
import android.provider.Settings.System;
import android.text.TextUtils;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.app.LocalePicker;
import com.android.internal.app.LocalePicker.LocaleInfo;
import com.android.internal.util.IndentingPrintWriter;
import com.android.server.hdmi.HdmiAnnotations.ServiceThreadOnly;
import com.android.server.hdmi.DeviceDiscoveryAction.DeviceDiscoveryCallback;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Represent a logical device of type Playback residing in Android system.
 */
final class HdmiCecLocalDevicePlayback extends HdmiCecLocalDevice {
    private static final String TAG = "HdmiCecLocalDevicePlayback";

    private static final int VOLUME_TYPE_CEC = 0;

    private static final String ACTION_CEC_STATS = "com.nvidia.hdmi.cecstats";
    private static final String CEC_STATS_EXTRA = "cecinfo";

    private static final boolean WAKE_ON_HOTPLUG =
            SystemProperties.getBoolean(Constants.PROPERTY_WAKE_ON_HOTPLUG, true);

    private static final boolean SET_MENU_LANGUAGE =
            SystemProperties.getBoolean(Constants.PROPERTY_SET_MENU_LANGUAGE, false);

    private boolean mIsActiveSource = false;

    // Used to keep the device awake while it is the active source. For devices that
    // cannot wake up via CEC commands, this address the inconvenience of having to
    // turn them on. True by default, and can be disabled (i.e. device can go to sleep
    // in active device status) by explicitly setting the system property
    // persist.sys.hdmi.keep_awake to false.
    // Lazily initialized - should call getWakeLock() to get the instance.
    private ActiveWakeLock mWakeLock;

    // If true, turn off TV upon standby. False by default.
    private boolean mAutoTvOff;

    // Copy of mDeviceInfos to guarantee thread-safety.
    //@GuardedBy("mLock")
    private List<HdmiDeviceInfo> mSafeAllDeviceInfos = Collections.emptyList();

    // Map-like container of all cec devices including local ones.
    // device id is used as key of container.
    // This is not thread-safe. For external purpose use mSafeDeviceInfos.
    private final SparseArray<HdmiDeviceInfo> mDeviceInfos = new SparseArray<>();

    HdmiCecLocalDevicePlayback(HdmiControlService service) {
        super(service, HdmiDeviceInfo.DEVICE_PLAYBACK);

        mAutoTvOff = mService.readBooleanSetting(Global.HDMI_CONTROL_AUTO_DEVICE_OFF_ENABLED, false);

        // The option is false by default. Update settings db as well to have the right
        // initial setting on UI.
        mService.writeBooleanSetting(Global.HDMI_CONTROL_AUTO_DEVICE_OFF_ENABLED, mAutoTvOff);
    }

    @Override
    @ServiceThreadOnly
    protected void onAddressAllocated(int logicalAddress, int reason) {
        assertRunOnServiceThread();
        mService.sendCecCommand(HdmiCecMessageBuilder.buildReportPhysicalAddressCommand(
                mAddress, mService.getPhysicalAddress(), mDeviceType));
        mService.sendCecCommand(HdmiCecMessageBuilder.buildDeviceVendorIdCommand(
                mAddress, mService.getVendorId()));
        if (!hasAction(DeviceDiscoveryAction.class)) {
            launchDeviceDiscovery();
        }
        startQueuedActions();
    }

    // Similar to device discovery thread for tv
    @ServiceThreadOnly
    private void launchDeviceDiscovery() {
        assertRunOnServiceThread();
        clearDeviceInfoList();
        DeviceDiscoveryAction action = new DeviceDiscoveryAction(this,
            new DeviceDiscoveryCallback() {
                @Override
                public void onDeviceDiscoveryDone(List<HdmiDeviceInfo> deviceInfos) {
                    ArrayList<HdmiDeviceInfo> allDeviceInfos = new ArrayList<>(deviceInfos);
                    allDeviceInfos.add(getDeviceInfo());
                    // Sort the list by physical address
                    Collections.sort(allDeviceInfos, new Comparator<HdmiDeviceInfo>() {
                        @Override
                        public int compare(HdmiDeviceInfo info1, HdmiDeviceInfo info2) {
                            return info1.getPhysicalAddress() - info2.getPhysicalAddress();
                        }
                    });
                    // Send the data as a string and broadcast intent
                    sendCecInfo(allDeviceInfos);

                   for (HdmiDeviceInfo info : deviceInfos) {
                       addCecDevice(info);
                   }

                   // Since we removed all devices when it's start and
                   // device discovery action does not poll local devices,
                   // we should put device info of local device manually here
                   for (HdmiCecLocalDevice device : mService.getAllLocalDevices()) {
                       addCecDevice(device.getDeviceInfo());
                   }

                   // If there is AVR, initiate System Audio Auto initiation action,
                   // which turns on and off system audio according to last system
                   // audio setting.
                   HdmiDeviceInfo avr = getAvrDeviceInfo();
                   if (avr != null) {
                       onNewAvrAdded(avr);
                   }
                }
            });
        addAndStartAction(action);
    }

    // Helper function to send cec stats to hdmimonitor
    @ServiceThreadOnly
    final void sendCecInfo(ArrayList<HdmiDeviceInfo> deviceInfos) {
        assertRunOnServiceThread();
        String result = "";
        ArrayList<String> holder = new ArrayList<String>();
        for (HdmiDeviceInfo info : deviceInfos) {
            holder.add(info.toStatsString());
        }
        result = TextUtils.join(";", holder);
        Intent cecIntent = new Intent(ACTION_CEC_STATS);
        cecIntent.putExtra(CEC_STATS_EXTRA, result);
        mService.getContext().sendBroadcast(cecIntent);
    }

    @Override
    @ServiceThreadOnly
    protected int getPreferredAddress() {
        assertRunOnServiceThread();
        return SystemProperties.getInt(Constants.PROPERTY_PREFERRED_ADDRESS_PLAYBACK,
                Constants.ADDR_UNREGISTERED);
    }

    @Override
    @ServiceThreadOnly
    protected void setPreferredAddress(int addr) {
        assertRunOnServiceThread();
        SystemProperties.set(Constants.PROPERTY_PREFERRED_ADDRESS_PLAYBACK,
                String.valueOf(addr));
    }

    @ServiceThreadOnly
    void oneTouchPlay(IHdmiControlCallback callback) {
        assertRunOnServiceThread();
        List<OneTouchPlayAction> actions = getActions(OneTouchPlayAction.class);
        if (!actions.isEmpty()) {
            Slog.i(TAG, "oneTouchPlay already in progress");
            actions.get(0).addCallback(callback);
            return;
        }
        OneTouchPlayAction action = OneTouchPlayAction.create(this, Constants.ADDR_TV,
                callback);
        if (action == null) {
            Slog.w(TAG, "Cannot initiate oneTouchPlay");
            invokeCallback(callback, HdmiControlManager.RESULT_EXCEPTION);
            return;
        }
        addAndStartAction(action);
    }

    @ServiceThreadOnly
    void queryDisplayStatus(IHdmiControlCallback callback) {
        assertRunOnServiceThread();
        List<DevicePowerStatusAction> actions = getActions(DevicePowerStatusAction.class);
        if (!actions.isEmpty()) {
            Slog.i(TAG, "queryDisplayStatus already in progress");
            actions.get(0).addCallback(callback);
            return;
        }
        DevicePowerStatusAction action = DevicePowerStatusAction.create(this, Constants.ADDR_TV,
                callback);
        if (action == null) {
            Slog.w(TAG, "Cannot initiate queryDisplayStatus");
            invokeCallback(callback, HdmiControlManager.RESULT_EXCEPTION);
            return;
        }
        addAndStartAction(action);
    }

    protected int findKeyReceiverAddress() {
        HdmiDeviceInfo avr = getAvrDeviceInfo();
        if (avr != null) {
            return Constants.ADDR_AUDIO_SYSTEM;
        } else {
            return Constants.ADDR_TV;
        }
    }

    @ServiceThreadOnly
    private void invokeCallback(IHdmiControlCallback callback, int result) {
        assertRunOnServiceThread();
        try {
            callback.onComplete(result);
        } catch (RemoteException e) {
            Slog.e(TAG, "Invoking callback failed:" + e);
        }
    }

    @Override
    @ServiceThreadOnly
    void onHotplug(int portId, boolean connected) {
        assertRunOnServiceThread();
        mCecMessageCache.flushAll();
        // We'll not clear mIsActiveSource on the hotplug event to pass CETC 11.2.2-2 ~ 3.
        if (WAKE_ON_HOTPLUG && connected && mService.isPowerStandbyOrTransient()) {
            mService.wakeUp();
        }
        if (!connected) {
            getWakeLock().release();
        }
    }

    @Override
    @ServiceThreadOnly
    protected void onStandby(boolean initiatedByCec, int standbyAction) {
        assertRunOnServiceThread();
        if (!mService.isControlEnabled() || initiatedByCec || !mAutoTvOff) {
            return;
        }
        switch (standbyAction) {
            case HdmiControlService.STANDBY_SCREEN_OFF:
                mService.sendCecCommand(
                        HdmiCecMessageBuilder.buildStandby(mAddress, Constants.ADDR_TV));
                break;
            case HdmiControlService.STANDBY_SHUTDOWN:
                // ACTION_SHUTDOWN is taken as a signal to power off all the devices.
                mService.sendCecCommand(
                        HdmiCecMessageBuilder.buildStandby(mAddress, Constants.ADDR_BROADCAST));
                break;
        }
    }

    @Override
    @ServiceThreadOnly
    void setAutoDeviceOff(boolean enabled) {
        assertRunOnServiceThread();
        mAutoTvOff = enabled;
    }

    @ServiceThreadOnly
    void setActiveSource(boolean on) {
        assertRunOnServiceThread();
        mIsActiveSource = on;
        if (on) {
            getWakeLock().acquire();
        } else {
            getWakeLock().release();
        }
    }

    @ServiceThreadOnly
    private ActiveWakeLock getWakeLock() {
        assertRunOnServiceThread();
        if (mWakeLock == null) {
            if (SystemProperties.getBoolean(Constants.PROPERTY_KEEP_AWAKE, true)) {
                mWakeLock = new SystemWakeLock();
            } else {
                // Create a dummy lock object that doesn't do anything about wake lock,
                // hence allows the device to go to sleep even if it's the active source.
                mWakeLock = new ActiveWakeLock() {
                    @Override
                    public void acquire() { }
                    @Override
                    public void release() { }
                    @Override
                    public boolean isHeld() { return false; }
                };
                HdmiLogger.debug("No wakelock is used to keep the display on.");
            }
        }
        return mWakeLock;
    }

    @Override
    protected boolean canGoToStandby() {
        return !getWakeLock().isHeld();
    }

    @Override
    @ServiceThreadOnly
    protected boolean handleActiveSource(HdmiCecMessage message) {
        assertRunOnServiceThread();
        int physicalAddress = HdmiUtils.twoBytesToInt(message.getParams());
        mayResetActiveSource(physicalAddress);
        return true;  // Broadcast message.
    }

    private void mayResetActiveSource(int physicalAddress) {
        if (physicalAddress != mService.getPhysicalAddress()) {
            setActiveSource(false);
        }
    }

    @ServiceThreadOnly
    protected boolean handleUserControlPressed(HdmiCecMessage message) {
        assertRunOnServiceThread();
        wakeUpIfActiveSource();
        return super.handleUserControlPressed(message);
    }

    @Override
    @ServiceThreadOnly
    protected boolean handleSetStreamPath(HdmiCecMessage message) {
        assertRunOnServiceThread();
        int physicalAddress = HdmiUtils.twoBytesToInt(message.getParams());
        maySetActiveSource(physicalAddress);
        maySendActiveSource(message.getSource());
        wakeUpIfActiveSource();
        return true;  // Broadcast message.
    }

    // Samsung model we tested sends <Routing Change> and <Request Active Source>
    // in a row, and then changes the input to the internal source if there is no
    // <Active Source> in response. To handle this, we'll set ActiveSource aggressively.
    @Override
    @ServiceThreadOnly
    protected boolean handleRoutingChange(HdmiCecMessage message) {
        assertRunOnServiceThread();
        int newPath = HdmiUtils.twoBytesToInt(message.getParams(), 2);
        maySetActiveSource(newPath);
        return true;  // Broadcast message.
    }

    @Override
    @ServiceThreadOnly
    protected boolean handleRoutingInformation(HdmiCecMessage message) {
        assertRunOnServiceThread();
        int physicalAddress = HdmiUtils.twoBytesToInt(message.getParams());
        maySetActiveSource(physicalAddress);
        return true;  // Broadcast message.
    }

    private void maySetActiveSource(int physicalAddress) {
        setActiveSource(physicalAddress == mService.getPhysicalAddress());
    }

    private void wakeUpIfActiveSource() {
        if (!mIsActiveSource) {
            return;
        }
        // Wake up the device if the power is in standby mode, or its screen is off -
        // which can happen if the device is holding a partial lock.
        if (mService.isPowerStandbyOrTransient() || !mService.getPowerManager().isScreenOn()) {
            mService.wakeUp();
        }
    }

    private void maySendActiveSource(int dest) {
        if (mIsActiveSource) {
            mService.sendCecCommand(HdmiCecMessageBuilder.buildActiveSource(
                    mAddress, mService.getPhysicalAddress()));
            // Always reports menu-status active to receive RCP.
            mService.sendCecCommand(HdmiCecMessageBuilder.buildReportMenuStatus(
                    mAddress, dest, Constants.MENU_STATE_ACTIVATED));
        }
    }

    @Override
    @ServiceThreadOnly
    protected boolean handleRequestActiveSource(HdmiCecMessage message) {
        assertRunOnServiceThread();
        maySendActiveSource(message.getSource());
        return true;  // Broadcast message.
    }

    @ServiceThreadOnly
    protected boolean handleSetMenuLanguage(HdmiCecMessage message) {
        assertRunOnServiceThread();
        if (!SET_MENU_LANGUAGE) {
            return false;
        }

        try {
            String iso3Language = new String(message.getParams(), 0, 3, "US-ASCII");
            Locale currentLocale = mService.getContext().getResources().getConfiguration().locale;
            if (currentLocale.getISO3Language().equals(iso3Language)) {
                // Do not switch language if the new language is the same as the current one.
                // This helps avoid accidental country variant switching from en_US to en_AU
                // due to the limitation of CEC. See the warning below.
                return true;
            }

            // Don't use Locale.getAvailableLocales() since it returns a locale
            // which is not available on Settings.
            final List<LocaleInfo> localeInfos = LocalePicker.getAllAssetLocales(
                    mService.getContext(), false);
            for (LocaleInfo localeInfo : localeInfos) {
                if (localeInfo.getLocale().getISO3Language().equals(iso3Language)) {
                    // WARNING: CEC adopts ISO/FDIS-2 for language code, while Android requires
                    // additional country variant to pinpoint the locale. This keeps the right
                    // locale from being chosen. 'eng' in the CEC command, for instance,
                    // will always be mapped to en-AU among other variants like en-US, en-GB,
                    // an en-IN, which may not be the expected one.
                    LocalePicker.updateLocale(localeInfo.getLocale());
                    return true;
                }
            }
            Slog.w(TAG, "Can't handle <Set Menu Language> of " + iso3Language);
            return false;
        } catch (UnsupportedEncodingException e) {
            Slog.w(TAG, "Can't handle <Set Menu Language>", e);
            return false;
        }
    }

    // TODO: move out of locked, move matching call in HdmiControlService getDeviceList
    @Override
    protected List<HdmiDeviceInfo> getSafeCecDevicesLocked() {
        return new ArrayList<>(mSafeAllDeviceInfos);
    }

    /**
     * Called when a device is newly added or a new device is detected or
     * existing device is updated.
     *
     * @param info device info of a new device.
     */
    @ServiceThreadOnly
    final void addCecDevice(HdmiDeviceInfo info) {
        assertRunOnServiceThread();
        HdmiDeviceInfo old = addDeviceInfo(info);
        if (info.getLogicalAddress() == mAddress) {
            // The addition of TV device itself should not be notified.
            return;
        }
        // skip invokeDeviceEventListener(), tv does it, but playback doesn't need it
    }

    /**
     * Called when a device is removed or removal of device is detected.
     *
     * @param address a logical address of a device to be removed
     */
    @ServiceThreadOnly
    final void removeCecDevice(int address) {
        assertRunOnServiceThread();
        HdmiDeviceInfo info = removeDeviceInfo(HdmiDeviceInfo.idForCecDevice(address));

        mCecMessageCache.flushMessagesFrom(address);
        // skip invokeDeviceEventListener(), tv does it, but playback doesn't need it
    }

    /**
     * Add a new {@link HdmiDeviceInfo}. It returns old device info which has the same
     * logical address as new device info's.
     *
     * <p>Declared as package-private. accessed by {@link HdmiControlService} only.
     *
     * @param deviceInfo a new {@link HdmiDeviceInfo} to be added.
     * @return {@code null} if it is new device. Otherwise, returns old {@HdmiDeviceInfo}
     *         that has the same logical address as new one has.
     */
    @ServiceThreadOnly
    private HdmiDeviceInfo addDeviceInfo(HdmiDeviceInfo deviceInfo) {
        assertRunOnServiceThread();
        HdmiDeviceInfo oldDeviceInfo = getCecDeviceInfo(deviceInfo.getLogicalAddress());
        if (oldDeviceInfo != null) {
            removeDeviceInfo(deviceInfo.getId());
        }
        mDeviceInfos.append(deviceInfo.getId(), deviceInfo);
        updateSafeDeviceInfoList();
        return oldDeviceInfo;
    }

    /**
     * Remove a device info corresponding to the given {@code logicalAddress}.
     * It returns removed {@link HdmiDeviceInfo} if exists.
     *
     * <p>Declared as package-private. accessed by {@link HdmiControlService} only.
     *
     * @param id id of device to be removed
     * @return removed {@link HdmiDeviceInfo} it exists. Otherwise, returns {@code null}
     */
    @ServiceThreadOnly
    private HdmiDeviceInfo removeDeviceInfo(int id) {
        assertRunOnServiceThread();
        HdmiDeviceInfo deviceInfo = mDeviceInfos.get(id);
        if (deviceInfo != null) {
            mDeviceInfos.remove(id);
        }
        updateSafeDeviceInfoList();
        return deviceInfo;
    }

    @ServiceThreadOnly
    private void updateSafeDeviceInfoList() {
        assertRunOnServiceThread();
        List<HdmiDeviceInfo> copiedDevices = HdmiUtils.sparseArrayToList(mDeviceInfos);
        synchronized (mLock) {
            mSafeAllDeviceInfos = copiedDevices;
        }
    }

    /**
     * Whether a device of the specified physical address and logical address exists
     * in a device info list. However, both are minimal condition and it could
     * be different device from the original one.
     *
     * @param logicalAddress logical address of a device to be searched
     * @param physicalAddress physical address of a device to be searched
     * @return true if exist; otherwise false
     */
    @ServiceThreadOnly
    boolean isInDeviceList(int logicalAddress, int physicalAddress) {
        assertRunOnServiceThread();
        HdmiDeviceInfo device = getCecDeviceInfo(logicalAddress);
        if (device == null) {
            return false;
        }
        return device.getPhysicalAddress() == physicalAddress;
    }

    /**
     * Return a {@link HdmiDeviceInfo} corresponding to the given {@code logicalAddress}.
     *
     * This is not thread-safe. For thread safety, call {@link #getSafeCecDeviceInfo(int)}.
     *
     * @param logicalAddress logical address of the device to be retrieved
     * @return {@link HdmiDeviceInfo} matched with the given {@code logicalAddress}.
     *         Returns null if no logical address matched
     */
    @ServiceThreadOnly
    HdmiDeviceInfo getCecDeviceInfo(int logicalAddress) {
        assertRunOnServiceThread();
        return mDeviceInfos.get(HdmiDeviceInfo.idForCecDevice(logicalAddress));
    }

    // Clear all device info.
    @ServiceThreadOnly
    private void clearDeviceInfoList() {
        assertRunOnServiceThread();
        // skip invokeDeviceEventListener(), tv does, but playback doesn't have to
        mDeviceInfos.clear();
        updateSafeDeviceInfoList();
    }

    void startNewDeviceAction(ActiveSource activeSource, int deviceType) {
        for (NewDeviceAction action : getActions(NewDeviceAction.class)) {
            // If there is new device action which has the same logical address and path
            // ignore new request.
            // NewDeviceAction is created whenever it receives <Report Physical Address>.
            // And there is a chance starting NewDeviceAction for the same source.
            // Usually, new device sends <Report Physical Address> when it's plugged
            // in. However, TV can detect a new device from HotPlugDetectionAction,
            // which sends <Give Physical Address> to the source for newly detected
            // device.
            if (action.isActionOf(activeSource)) {
                return;
            }
        }

        addAndStartAction(new NewDeviceAction(this, activeSource.logicalAddress,
                activeSource.physicalAddress, deviceType));
    }

    int getPortId(int physicalAddress) {
        return Constants.INVALID_PORT_ID;
    }

    @Override
    @ServiceThreadOnly
    protected void sendStandby(int deviceId) {
        assertRunOnServiceThread();

        // Playback device can send <Standby> to TV only. Ignore the parameter.
        int targetAddress = Constants.ADDR_TV;
        mService.sendCecCommand(HdmiCecMessageBuilder.buildStandby(mAddress, targetAddress));
    }

    @Override
    @ServiceThreadOnly
    protected void disableDevice(boolean initiatedByCec, PendingActionClearedCallback callback) {
        assertRunOnServiceThread();
        removeAction(DeviceDiscoveryAction.class);
        disableSystemAudioIfExist();

        super.disableDevice(initiatedByCec, callback);

        // Do not send <Inactive Source> command as some TVs switch inputs improperly
        clearDeviceInfoList();
        setActiveSource(false);
        checkIfPendingActionsCleared();
    }

    @ServiceThreadOnly
    private void disableSystemAudioIfExist() {
        assertRunOnServiceThread();
        if (getAvrDeviceInfo() == null) {
            return;
        }

        // Seq #31.
        removeAction(SystemAudioActionFromAvr.class);
        removeAction(SystemAudioActionFromTv.class);
        removeAction(SystemAudioAutoInitiationAction.class);
        removeAction(SystemAudioStatusAction.class);
        removeAction(VolumeControlAction.class);
    }

    @Override
    protected void dump(final IndentingPrintWriter pw) {
        super.dump(pw);
        pw.println("mIsActiveSource: " + mIsActiveSource);
        pw.println("mAutoTvOff:" + mAutoTvOff);
        synchronized (mLock) {
            pw.println("mSafeAllDeviceInfos: " + getSafeCecDevicesLocked());
        }
    }

    // Wrapper interface over PowerManager.WakeLock
    private interface ActiveWakeLock {
        void acquire();
        void release();
        boolean isHeld();
    }

    private class SystemWakeLock implements ActiveWakeLock {
        private final WakeLock mWakeLock;
        public SystemWakeLock() {
            mWakeLock = mService.getPowerManager().newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            mWakeLock.setReferenceCounted(false);
        }

        @Override
        public void acquire() {
            mWakeLock.acquire();
            HdmiLogger.debug("active source: %b. Wake lock acquired", mIsActiveSource);
        }

        @Override
        public void release() {
            mWakeLock.release();
            HdmiLogger.debug("Wake lock released");
        }

        @Override
        public boolean isHeld() {
            return mWakeLock.isHeld();
        }
    }

    // TODO: implement these?
    // disableSystemAudioIfExist? call on disableDevice?

    @ServiceThreadOnly
    void changeSystemAudioMode(boolean enabled, IHdmiControlCallback callback) {
        assertRunOnServiceThread();
        if (!mService.isControlEnabled() /* || hasAction(DeviceDiscoveryAction.class) */) { // FIXME
            setSystemAudioMode(false, true);
            invokeCallback(callback, HdmiControlManager.RESULT_INCORRECT_MODE);
        }
        HdmiDeviceInfo avr = getAvrDeviceInfo();
        if (avr == null) {
            setSystemAudioMode(false, true);
            invokeCallback(callback, HdmiControlManager.RESULT_TARGET_NOT_AVAILABLE);
        }
        addAndStartAction(
                new SystemAudioActionFromTv(this, Constants.ADDR_AUDIO_SYSTEM, enabled, callback));
    }

    @ServiceThreadOnly
    void changeVolume(int curVolume, int delta, int maxVolume) {
        assertRunOnServiceThread();
/*
        if (delta == 0 || !isSystemAudioActivated()) {
            return;
        }

        int targetVolume = curVolume + delta;
        int cecVolume = VolumeControlAction.scaleToCecVolume(targetVolume, maxVolume);
        synchronized (mLock) {
            // If new volume is the same as current system audio volume, just ignore it.
            // Note that UNKNOWN_VOLUME is not in range of cec volume scale.
            if (cecVolume == mSystemAudioVolume) {
                // Update tv volume with system volume value.
                mService.setAudioStatus(false,
                        VolumeControlAction.scaleToCustomVolume(mSystemAudioVolume, maxVolume));
                return;
            }
        }

        List<VolumeControlAction> actions = getActions(VolumeControlAction.class);
        if (actions.isEmpty()) {
            // TODO: need to add "if NV_CEC_VOLUME && isSystemAudioActivated, then send to AVR, else send to TV"
            // 2nd arg: getAvrDeviceInfo().getLogicalAddress()
            addAndStartAction(new VolumeControlAction(this,
                    Constants.ADDR_AUDIO_SYSTEM, delta > 0));
        } else {
            actions.get(0).handleVolumeChange(delta > 0);
        }
*/
    }

    @ServiceThreadOnly
    void changeMute(boolean mute) {
/*
        assertRunOnServiceThread();
        HdmiLogger.debug("[A]:Change mute:%b", mute);
        synchronized (mLock) {
            if (mSystemAudioMute == mute) {
                HdmiLogger.debug("No need to change mute.");
                return;
            }
        }
        if (!isSystemAudioActivated()) {
            HdmiLogger.debug("[A]:System audio is not activated.");
            return;
        }

        // Remove existing volume action.
        removeAction(VolumeControlAction.class);
        // TODO: fix getAvrDeviceInfo()
        // 1st arg: getAvrDeviceInfo().getLogicalAddress()
        sendUserControlPressedAndReleased(Constants.ADDR_AUDIO_SYSTEM,
                mute ? HdmiCecKeycode.CEC_KEYCODE_MUTE_FUNCTION :
                        HdmiCecKeycode.CEC_KEYCODE_RESTORE_VOLUME_FUNCTION);
*/
    }

    boolean isSystemAudioActivated() {
        // TODO: SS: changed from HdmiCecLocalDeviceTv
/*
        if (!hasSystemAudioDevice()) {
            return false;
        }
*/
        synchronized (mLock) {
            return mSystemAudioActivated;
        }
    }

    @ServiceThreadOnly
    HdmiDeviceInfo getAvrDeviceInfo() {
        assertRunOnServiceThread();
        return getCecDeviceInfo(Constants.ADDR_AUDIO_SYSTEM);
    }

    @ServiceThreadOnly
    void onNewAvrAdded(HdmiDeviceInfo avr) {
        assertRunOnServiceThread();
        if (getNvHdmiVolumeControl() == VOLUME_TYPE_CEC && getSystemAudioModeSetting() && !isSystemAudioActivated()) {
            addAndStartAction(new SystemAudioAutoInitiationAction(this, avr.getLogicalAddress()));
        }
    }

    @ServiceThreadOnly
    private int getNvHdmiVolumeControl() {
        assertRunOnServiceThread();
        ContentResolver cr = mService.getContext().getContentResolver();
        return System.getInt(cr, System.HDMI_VOLUME_CONTROL, 1);
    }
}
