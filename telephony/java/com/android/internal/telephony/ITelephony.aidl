/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.internal.telephony;

import android.os.Bundle;
import java.util.List;
import android.telephony.NeighboringCellInfo;
import android.telephony.CellInfo;

/**
 * Interface used to interact with the phone.  Mostly this is used by the
 * TelephonyManager class.  A few places are still using this directly.
 * Please clean them up if possible and use TelephonyManager insteadl.
 *
 * {@hide}
 */
interface ITelephony {

    /**
     * Dial a number. This doesn't place the call. It displays
     * the Dialer screen.
     * @param number the number to be dialed. If null, this
     * would display the Dialer screen with no number pre-filled.
     */
    void dial(String number);

    /**
     * Place a call to the specified number.
     * @param number the number to be called.
     */
    void call(String callingPackage, String number, int simId);

    /**
     * If there is currently a call in progress, show the call screen.
     * The DTMF dialpad may or may not be visible initially, depending on
     * whether it was up when the user last exited the InCallScreen.
     *
     * @return true if the call screen was shown.
     */
    boolean showCallScreen();

    /**
     * Variation of showCallScreen() that also specifies whether the
     * DTMF dialpad should be initially visible when the InCallScreen
     * comes up.
     *
     * @param showDialpad if true, make the dialpad visible initially,
     *                    otherwise hide the dialpad initially.
     * @return true if the call screen was shown.
     *
     * @see showCallScreen
     */
    boolean showCallScreenWithDialpad(boolean showDialpad);

    /**
     * End call if there is a call in progress, otherwise does nothing.
     *
     * @return whether it hung up
     */
    boolean endCall();

    /**
     * Answer the currently-ringing call.
     *
     * If there's already a current active call, that call will be
     * automatically put on hold.  If both lines are currently in use, the
     * current active call will be ended.
     *
     * TODO: provide a flag to let the caller specify what policy to use
     * if both lines are in use.  (The current behavior is hardwired to
     * "answer incoming, end ongoing", which is how the CALL button
     * is specced to behave.)
     *
     * TODO: this should be a oneway call (especially since it's called
     * directly from the key queue thread).
     */
    void answerRingingCall();

    /**
     * Silence the ringer if an incoming call is currently ringing.
     * (If vibrating, stop the vibrator also.)
     *
     * It's safe to call this if the ringer has already been silenced, or
     * even if there's no incoming call.  (If so, this method will do nothing.)
     *
     * TODO: this should be a oneway call too (see above).
     *       (Actually *all* the methods here that return void can
     *       probably be oneway.)
     */
    void silenceRinger();

    /**
     * Check if we are in either an active or holding call
     * @return true if any phone state is OFFHOOK.
     */
    boolean isOffhook();

    /**
     * Check if an incoming phone call is ringing or call waiting.
     * @return true if any phone state is RINGING.
     */
    boolean isRinging();

    /**
     *
     * Check if any phone is idle.
     * @return true if any phone state is IDLE.
     */
    boolean isIdle();

    /**
     *
     * Check if the phone is in either an active or holding call
     * @return true if the phone state is OFFHOOK.
     */
    boolean isPhoneOffhook(int simId);

    /**
     * Check if the phone has an incoming phone call is ringing or call waiting.
     * @return true if the phone state is RINGING.
     */
    boolean isPhoneRinging(int simId);

    /**
     *
     * Check if the phone is idle.
     * @return true if the phone state is IDLE.
     */
    boolean isPhoneIdle(int simId);

    /**
     * Check to see if the radio is on or not.
     * @param simId The indicated sim id.
     * @return returns true if the radio is on.
     */
    boolean isRadioOn(int simId);

    /**
     * Check if the SIM pin lock is enabled.
     * @return true if the SIM pin lock is enabled.
     */
    boolean isSimPinEnabled();

    /**
     * Cancels the missed calls notification.
     */
    void cancelMissedCallsNotification();

    /**
     * Supply a pin to unlock the SIM.  Blocks until a result is determined.
     * @param pin The pin to check.
     *        simId The indicated sim id.
     * @return whether the operation was a success.
     */
    boolean supplyPin(String pin, int simId);

    /**
     * Supply puk to unlock the SIM and set SIM pin to new pin.
     *  Blocks until a result is determined.
     * @param puk The puk to check.
     *        pin The new pin to be set in SIM
     *        simId The indicated sim id.
     * @return whether the operation was a success.
     */
    boolean supplyPuk(String puk, String pin, int simId);

    /**
     * Supply a pin to unlock the SIM.  Blocks until a result is determined.
     * Returns a specific success/error code.
     * @param pin The pin to check.
     *        simId The indicated sim id.
     * @return retValue[0] = Phone.PIN_RESULT_SUCCESS on success. Otherwise error code
     *         retValue[1] = number of attempts remaining if known otherwise -1
     */
    int[] supplyPinReportResult(String pin, int simId);

    /**
     * Supply puk to unlock the SIM and set SIM pin to new pin.
     * Blocks until a result is determined.
     * Returns a specific success/error code
     * @param puk The puk to check
     *        pin The pin to check.
     *        simId The indicated sim id.
     * @return retValue[0] = Phone.PIN_RESULT_SUCCESS on success. Otherwise error code
     *         retValue[1] = number of attempts remaining if known otherwise -1
     */
    int[] supplyPukReportResult(String puk, String pin, int simId);

    /**
     * Handles PIN MMI commands (PIN/PIN2/PUK/PUK2), which are initiated
     * without SEND (so <code>dial</code> is not appropriate).
     *
     * @param dialString the MMI command to be executed.
     *        simId The indicated sim id.
     * @return true if MMI command is executed.
     */
    boolean handlePinMmi(String dialString, int simId);

    /**
     * Toggles the radio on or off.
     * @param simId The indicated sim id.
     */
    void toggleRadioOnOff(int simId);

    /**
     * Set the radio to on or off
     * @param simId The indicated sim id.
     */
    boolean setRadio(boolean turnOn, int simId);

    /**
     * Set the radio to on or off unconditionally
     * @param simId The indicated sim id.
     */
    boolean setRadioPower(boolean turnOn, int simId);

    /**
     * Request to update location information in service state
     * @param simId The indicated sim id.
     */
    void updateServiceLocation(int simId);

    /**
     * Enable location update notifications.
     * @param simId The indicated sim id.
     */
    void enableLocationUpdates(int simId);

    /**
     * Disable location update notifications.
     * @param simId The indicated sim id.
     */
    void disableLocationUpdates(int simId);

    /**
     * Enable a specific APN type.
     */
    int enableApnType(String type, int simId);

    /**
     * Disable a specific APN type.
     */
    int disableApnType(String type, int simId);

    /**
     * Allow mobile data connections.
     */
    boolean enableDataConnectivity(int simId);

    /**
     * Disallow mobile data connections.
     */
    boolean disableDataConnectivity(int simId);

    /**
     * Report whether data connectivity is possible.
     */
    boolean isDataConnectivityPossible(int simId);

    /**
     * Returns Cell Location
     * @param simId The indicated sim id.
     */
    Bundle getCellLocation(int simId);

    /**
     * Returns the neighboring cell information of the device.
     * @param simId The indicated sim id.
     */
    List<NeighboringCellInfo> getNeighboringCellInfo(String callingPkg, int simId);

     int getCallState();
     int getDataActivity();
     int getDataState();

    /**
     * Returns the current active phone type as integer.
     * Returns TelephonyManager.PHONE_TYPE_CDMA if RILConstants.CDMA_PHONE
     * and TelephonyManager.PHONE_TYPE_GSM if RILConstants.GSM_PHONE
     *
     * @param simId The indicated sim id.
     * @return the active phone type.
     */
    int getActivePhoneType(int simId);

    /**
     * Returns the CDMA ERI icon index to display
     */
    int getCdmaEriIconIndex();

    /**
     * Returns the CDMA ERI icon mode,
     * 0 - ON
     * 1 - FLASHING
     */
    int getCdmaEriIconMode();

    /**
     * Returns the CDMA ERI text,
     */
    String getCdmaEriText();

    /**
     * Returns true if OTA service provisioning needs to run.
     * Only relevant on some technologies, others will always
     * return false.
     */
    boolean needsOtaServiceProvisioning(int simId);

    /**
      * Returns the unread count of voicemails
      */
    int getVoiceMessageCount(int simId);

    /**
      * Returns the network type for data transmission
      * @param simId The indicated sim id.
      */
    int getNetworkType(int simId);

    /**
      * Returns the network type for data transmission
      * @param simId The indicated sim id.
      */
    int getDataNetworkType(int simId);

    /**
      * Returns the network type for voice
      * @param simId The indicated sim id.
      */
    int getVoiceNetworkType(int simId);

    /**
     * Return true if an ICC card is present
     */
    boolean hasIccCard(int simId);

    /**
     * Return if the current radio is LTE on CDMA. This
     * is a tri-state return value as for a period of time
     * the mode may be unknown.
     *
     * @return {@link Phone#LTE_ON_CDMA_UNKNOWN}, {@link Phone#LTE_ON_CDMA_FALSE}
     * or {@link PHone#LTE_ON_CDMA_TRUE}
     */
    int getLteOnCdmaMode(int simId);

    /**
     * Returns the all observed cell information of the device.
     * @param simId The indicated sim id.
     */
    List<CellInfo> getAllCellInfo(int simId);

    /**
     * Sets minimum time in milli-seconds between onCellInfoChanged
     * @param simId The indicated sim id.
     */
    void setCellInfoListRate(int rateInMillis, int simId);

    /**
     *
     * Sets if policy data enabled
     */
    void setPolicyDataEnable(boolean enabled, int simId); 
}

