/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.bluetooth;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.List;

/**
 * This class provides the public APIs for the LE Audio Broadcast Assistant profile.
 *
 * <p>BluetoothLeBroadcastAssistant is a proxy object for controlling the Broadcast Assistant
 * service via IPC. Use {@link BluetoothAdapter#getProfileProxy} to get the
 * BluetoothLeBroadcastAssistant proxy object.
 *
 * @hide
 */
public final class BluetoothLeBroadcastAssistant implements BluetoothProfile {
    private static final String TAG = "BluetoothLeBroadcastAssistant";
    private static final boolean DBG = true;

    /**
     * Create a new instance of an LE Audio Broadcast Assistant.
     *
     * @hide
     */
    /*package*/ BluetoothLeBroadcastAssistant(
            @NonNull Context context, @NonNull ServiceListener listener) {}

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            BluetoothProfile.STATE_DISCONNECTED,
            BluetoothProfile.STATE_CONNECTED
    })
    public @interface GetConnectionStateReturnValues {}

    /**
     * Check whether the LE Audio Broadcast Assistant is connected to the specified Broadcast Sink.
     *
     * @param sink BluetoothDevice representing the Scan Delegator
     *
     * @hide
     */
    @Override
    public @GetConnectionStateReturnValues int getConnectionState(BluetoothDevice sink) {
        return BluetoothProfile.STATE_DISCONNECTED;
    }

    /**
     * Get a list of all LE Audio Broadcast Sinks with the specified connection states.
     *
     * @param states array representing the connection states
     * @return a list of devices that match the provided connection states
     * @hide
     */
    @Override
    public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        return Collections.emptyList();
    }

    /**
     * Get a list of all LE Audio Broadcast Sinks connected with the LE Audio Broadcast Assistant.
     *
     * @return list of connected devices
     * @hide
     */
    @Override
    public List<BluetoothDevice> getConnectedDevices() {
        return Collections.emptyList();
    }

    /**
     * Set connection policy of the profile
     *
     * <p> The device should already be paired.
     * Connection policy can be one of {@link #CONNECTION_POLICY_ALLOWED},
     * {@link #CONNECTION_POLICY_FORBIDDEN}, {@link #CONNECTION_POLICY_UNKNOWN}
     *
     * @param device Paired bluetooth device
     * @param connectionPolicy is the connection policy to set to for this profile
     * @return true if connectionPolicy is set, false on error
     * @hide
     */
    public boolean setConnectionPolicy(@NonNull BluetoothDevice device,
            @ConnectionPolicy int connectionPolicy) {
        return false;
    }

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            BluetoothStatusCodes.ERROR_LE_AUDIO_BROADCAST_ASSISTANT_REGISTER_CALLBACK_FAILED,
            BluetoothStatusCodes.SUCCESS
    })
    public @interface GetRegisterCallbackReturnValues {}

    /**
     * Register callbacks that will be invoked during scan offloading.
     *
     * @param sink BluetoothDevice representing the Scan Delegator
     * @param callback callbacks to be invoked
     * @hide
     */
    public @GetRegisterCallbackReturnValues int registerCallback(
            @NonNull BluetoothDevice sink,
            @NonNull BluetoothLeBroadcastAssistantCallback callback) {
        log("registerCallback: " + sink);
        return BluetoothStatusCodes.ERROR_LE_AUDIO_BROADCAST_ASSISTANT_REGISTER_CALLBACK_FAILED;
    }

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            BluetoothStatusCodes.ERROR_LE_AUDIO_BROADCAST_ASSISTANT_UNREGISTER_CALLBACK_FAILED,
            BluetoothStatusCodes.SUCCESS
    })
    public @interface GetUnregisterCallbackReturnValues {}

    /**
     * Unregister callbacks that are invoked during scan offloading.
     *
     * @param sink BluetoothDevice representing the Scan Delegator
     * @param callback callbacks to be unregistered
     * @hide
     */
    public @GetUnregisterCallbackReturnValues int unregisterCallback(
            @NonNull BluetoothDevice sink,
            @NonNull BluetoothLeBroadcastAssistantCallback callback) {
        log("unregisterCallback: " + sink);
        return BluetoothStatusCodes.ERROR_LE_AUDIO_BROADCAST_ASSISTANT_UNREGISTER_CALLBACK_FAILED;
    }

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            BluetoothStatusCodes.ERROR_LE_AUDIO_BROADCAST_ASSISTANT_START_SEARCH_FAILED,
            BluetoothStatusCodes.SUCCESS
    })
    public @interface GetSearchForBroadcastSourcesReturnValues {}

    /**
     * Search for LE Audio Broadcast Sources on behalf of a Scan Delegator.
     *
     * <p>Search results will be delivered to the application using {@link
     * BluetoothLeBroadcastAssistantCallback#onBluetoothLeBroadcastSourceFound}
     *
     * @param sink BluetoothDevice representing the Scan Delegator
     * @hide
     */
    public @GetSearchForBroadcastSourcesReturnValues int searchforBroadcastSources(
            @NonNull BluetoothDevice sink) {
        log("searchforBroadcastSources: " + sink);
        return BluetoothStatusCodes.ERROR_LE_AUDIO_BROADCAST_ASSISTANT_START_SEARCH_FAILED;
    }

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            BluetoothStatusCodes.ERROR_LE_AUDIO_BROADCAST_ASSISTANT_STOP_SEARCH_FAILED,
            BluetoothStatusCodes.SUCCESS
    })
    public @interface GetStopSearchForBroadcastSourcesReturnValues {}

    /**
     * Stops an ongoing search for LE Audio Broadcast Sources.
     *
     * @param sink BluetoothDevice representing the Scan Delegator
     * @hide
     */
    public @GetStopSearchForBroadcastSourcesReturnValues int stopSearchforBroadcastSources(
            @NonNull BluetoothDevice sink) {
        log("stopSearchforBroadcastSources: " + sink);
        return BluetoothStatusCodes.ERROR_LE_AUDIO_BROADCAST_ASSISTANT_STOP_SEARCH_FAILED;
    }

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            BluetoothStatusCodes.ERROR_LE_AUDIO_BROADCAST_ASSISTANT_SELECT_SOURCE_FAILED,
            BluetoothStatusCodes.SUCCESS
    })
    public @interface GetSelectBroadcastSourceReturnValues {}

    /**
     * Selects a Broadcast Source on behalf of a Scan Delegator.
     *
     * <p>This internally synchronizes with the Periodic Advertisements (PAs) from the provided
     * Broadcast Source. Upon synchronization, it will notify the Broadcast Assistant about the
     * channels that are available from the Broadcast Source.
     *
     * <p>The application should select the set of channels it wants to synchronize with and then
     * call {@link #addBroadcastSource} method to ask the Scan Delegator to synchronize with the
     * provided audio channels.
     *
     * <p>Result of selection of Broadcast source will be delivered through {@link
     * BluetoothLeBroadcastAssistantCallback#onBluetoothLeBroadcastSourceSelected}
     *
     * @param source BluetoothDevice representing the Broadcast Source to synchronize with
     * @param scanResult {@link #ScanResult} containing a Broadcast Source this is obtained from
     *     {@link BluetoothLeBroadcastAssistantCallback#onBluetoothLeBroadcastSourceFound}
     * @param isGroupOp set to true If Application wants to perform this operation for the whole
     *     coordinated set members
     * @hide
     */
    public @GetSelectBroadcastSourceReturnValues int selectBroadcastSource(
            @NonNull BluetoothDevice source, @NonNull ScanResult scanResult, boolean isGroupOp) {
        log("selectBroadcastSource: " + source);
        return BluetoothStatusCodes.ERROR_LE_AUDIO_BROADCAST_ASSISTANT_SELECT_SOURCE_FAILED;
    }

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            BluetoothStatusCodes.ERROR_LE_AUDIO_BROADCAST_ASSISTANT_ADD_SOURCE_FAILED,
            BluetoothStatusCodes.SUCCESS
    })
    public @interface GetAddBroadcastSourceReturnValues {}

    /**
     * Asks a Scan Delegator to add the provided Broadcast Source.
     *
     * <p>Internally, this writes the provided Broadcast Source information to the Broadcast Audio
     * Scan Control Point of the Scan Delegator.
     *
     * <p>Upon addition of the Broadcast Source, {@link
     * BluetoothLeBroadcastAssistantCallback#onBluetoothLeBroadcastSourceAdded} will be invoked
     *
     * @param sink {@link #BluetoothDevice} representing the Broadcast Sink to which the Broadcast
     *     Source should be added
     * @param source Broadcast Source to be added to the Scan Delegator
     * @param isGroupOp set to true If Application wants to perform this operation for all
     *     coordinated set members, False otherwise
     * @hide
     */
    public @GetAddBroadcastSourceReturnValues int addBroadcastSource(
            @NonNull BluetoothDevice sink,
            @NonNull BluetoothLeBroadcastSourceInfo source,
            boolean isGroupOp) {
        log("addBroadcastSource: " + source + " on " + sink);
        return BluetoothStatusCodes.ERROR_LE_AUDIO_BROADCAST_ASSISTANT_ADD_SOURCE_FAILED;
    }

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            BluetoothStatusCodes.ERROR_LE_AUDIO_BROADCAST_ASSISTANT_UPDATE_SOURCE_FAILED,
            BluetoothStatusCodes.SUCCESS
    })
    public @interface GetUpdateBroadcastSourceReturnValues {}

    /**
     * Updates Broadcast Source information on a Scan Delegator.
     *
     * <p>After updating the Broadcast Source on the Scan Delegator, the callback {@link
     * BluetoothLeBroadcastAssistantCallback#onBluetoothLeBroadcastSourceUpdated} will be invoked.
     *
     * <p>In case of Group Operation, if there are no matching sources among any coordinated set
     * members, this operation will fail and the callback {@link
     * BluetoothLeBroadcastAssistantCallback#onBluetoothLeBroadcastSourceUpdated} will be invoked.
     *
     * @param sink {@link #BluetoothDevice} representing the Broadcast Sink to which the Broadcast
     *     Source should be updated
     * @param source Broadcast Source to be updated on the Scan Delegator
     * @param isGroupOp set to true if the application wants to perform this operation for all the
     *     coordinated set members, false otherwise
     * @hide
     */
    public @GetUpdateBroadcastSourceReturnValues int updateBroadcastSource(
            @NonNull BluetoothDevice sink,
            @NonNull BluetoothLeBroadcastSourceInfo source,
            boolean isGroupOp) {
        log("updateBroadcastSource: " + source + " on " + sink);
        return BluetoothStatusCodes.ERROR_LE_AUDIO_BROADCAST_ASSISTANT_UPDATE_SOURCE_FAILED;
    }

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            BluetoothStatusCodes.ERROR_LE_AUDIO_BROADCAST_ASSISTANT_REMOVE_SOURCE_FAILED,
            BluetoothStatusCodes.SUCCESS
    })
    public @interface GetRemoveBroadcastSourceReturnValues {}

    /**
     * Removes the Broadcast Source Information from a Scan delegator.
     *
     * <p>Upon removal of Broadcast Source information from the Scan Delegator, the callback {@link
     * BluetoothLeBroadcastAssistantCallback#OnBluetoothLeBroadcastSourceRemoved} will be invoked.
     *
     * @param sink {@link #BluetoothDevice} representing the Broadcast Sink from which a Broadcast
     *     Source should be removed
     * @param sourceId source ID of the Broadcast Source which needs to be removed
     * @param isGroupOp true if an application wants to perform this operation for all the
     *     coordinated set members, false otherwise
     * @hide
     */
    public @GetRemoveBroadcastSourceReturnValues int removeBroadcastSource(
            @NonNull BluetoothDevice sink, int sourceId, boolean isGroupOp) {
        log("removeBroadcastSource: " + sourceId + " from " + sink);
        return BluetoothStatusCodes.ERROR_LE_AUDIO_BROADCAST_ASSISTANT_REMOVE_SOURCE_FAILED;
    }

    /**
     * Get information about all the Broadcast Sources that a Scan Delegator knows about.
     *
     * @param sink {@link #BluetoothDevice} representing the Broadcast Sink from which to get all
     *     Broadcast Sources
     * @return returns the List of Broadcast Source Information {@link #BleBroadcastSourceInfo}
     *     stored in the Scan Delegator
     * @hide
     */
    public @NonNull List<BluetoothLeBroadcastSourceInfo> getAllBroadcastSources(
            @NonNull BluetoothDevice sink) {
        return Collections.emptyList();
    }

    private static void log(@NonNull String msg) {
        if (DBG) {
            Log.d(TAG, msg);
        }
    }
}
