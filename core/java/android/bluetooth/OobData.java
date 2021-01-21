/**
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

package android.bluetooth;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.lang.IllegalArgumentException;

/**
 * Out Of Band Data for Bluetooth device pairing.
 *
 * <p>This object represents optional data obtained from a remote device through
 * an out-of-band channel (eg. NFC, QR).
 *
 * <p>References:
 * NFCForum-AD-BluetoothSSP_1_1-Final
 * CSS V9
 *
 * <p>There are several BR/EDR Examples
 *
 * <p>Negotiated Handover:
 *   Bluetooth Carrier Configuration Record:
 *    - OOB Data Length
 *    - Device Address
 *    - Class of Device
 *    - Simple Pairing Hash C
 *    - Simple Pairing Randomizer R
 *    - Service Class UUID
 *    - Bluetooth Local Name
 *
 * <p>Static Handover:
 *   Bluetooth Carrier Configuration Record:
 *    - OOB Data Length
 *    - Device Address
 *    - Class of Device
 *    - Service Class UUID
 *    - Bluetooth Local Name
 *
 * <p>Simplified Tag Format for Single BT Carrier:
 *   Bluetooth OOB Data Record:
 *    - OOB Data Length
 *    - Device Address
 *    - Class of Device
 *    - Service Class UUID
 *    - Bluetooth Local Name
 *
 * @hide
 */
@SystemApi
public final class OobData implements Parcelable {

    private static final String TAG = "OobData";
    /** The minimum {@link OobData#mOobDataLength} may be. (AD 3.1.1) (CSS 1.6.2) @hide */
    @SystemApi
    public static final int OOB_LENGTH_OCTETS = 2;
    /** The length for the {@link OobData#mDeviceAddress}. (AD 3.1.2) (CSS 1.6.2) @hide */
    @SystemApi
    public static final int DEVICE_ADDRESS_OCTETS = 6;
    /** The Class of Device is 3 octets. (AD 3.1.3) (CSS 1.6.2) @hide */
    @SystemApi
    public static final int CLASS_OF_DEVICE_OCTETS = 3;
    /** The Confirmation data must be 16 octets. (AD 3.2.2) (CSS 1.6.2) @hide */
    @SystemApi
    public static final int CONFIRMATION_OCTETS = 16;
    /** The Randomizer data must be 16 octets. (AD 3.2.3) (CSS 1.6.2) @hide */
    @SystemApi
    public static final int RANDOMIZER_OCTETS = 16;
    /** The LE Device Address plus Address Type length. (3.3.1) @hide */
    @SystemApi
    public static final int LE_DEVICE_ADDRESS_OCTETS = 7;
    /** The LE Device Role length is 1 octet. (AD 3.3.2) (CSS 1.17) @hide */
    @SystemApi
    public static final int LE_DEVICE_ROLE_OCTETS = 1;
    /** The {@link OobData#mLeTemporaryKey} length. (3.4.1) @hide */
    @SystemApi
    public static final int LE_TK_OCTETS = 16;
    /** The {@link OobData#mLeAppearance} length. (3.4.1) @hide */
    @SystemApi
    public static final int LE_APPEARANCE_OCTETS = 2;
    /** The {@link OobData#mLeFlags} length. (3.4.1) @hide */
    @SystemApi
    public static final int LE_DEVICE_FLAG_OCTETS = 1; // 1 octet to hold the 0-4 value.

    // Le Roles
    /** @hide */
    @SystemApi
    public static final int LE_DEVICE_ROLE_PERIPHERAL_ONLY = 0x00;
    /** @hide */
    @SystemApi
    public static final int LE_DEVICE_ROLE_CENTRAL_ONLY = 0x01;
    /** @hide */
    @SystemApi
    public static final int LE_DEVICE_ROLE_BOTH_PREFER_PERIPHERAL = 0x02;
    /** @hide */
    @SystemApi
    public static final int LE_DEVICE_ROLE_BOTH_PREFER_CENTRAL = 0x03;

    // Le Flags
    /** @hide */
    @SystemApi
    public static final int LE_FLAG_LIMITED_DISCOVERY_MODE = 0x00;
    /** @hide */
    @SystemApi
    public static final int LE_FLAG_GENERAL_DISCOVERY_MODE = 0x01;
    /** @hide */
    @SystemApi
    public static final int LE_FLAG_BREDR_NOT_SUPPORTED = 0x02;
    /** @hide */
    @SystemApi
    public static final int LE_FLAG_SIMULTANEOUS_CONTROLLER = 0x03;
    /** @hide */
    @SystemApi
    public static final int LE_FLAG_SIMULTANEOUS_HOST = 0x04;

    /**
     * Main creation method for creating a Classic version of {@link OobData}.
     *
     * <p>This object will allow the caller to call {@link ClassicBuilder#build()}
     * to build the data object or add any option information to the builder.
     *
     * @param confirmation byte array consisting of {@link OobData#CONFIRMATION_OCTETS} octets
     * of data. Data is derived from controller/host stack and is required for pairing OOB.
     * @param randomizer byte array consisting of {@link OobData#RANDOMIZER_OCTETS} octets
     * of data. Data is derived from controller/host stack and is required for pairing OOB.
     * Also, randomizer may be all 0s or null in which case it becomes all 0s.
     * @param oobDataLength byte array representing the length of data from 8-65535 across 2
     * octets (0xXXXX).
     * @param deviceAddress byte array representing the Bluetooth Address of the device
     * that owns the OOB data. (i.e. the originator) [6 octets]
     *
     * @return a Classic Builder instance with all the given data set or null.
     *
     * @throws IllegalArgumentException if any of the values fail to be set.
     *
     * @hide
     */
    @Nullable
    @SystemApi
    public static ClassicBuilder createClassicBuilder(@NonNull byte[] confirmation,
            @Nullable byte[] randomizer, @NonNull byte[] oobDataLength,
            @NonNull byte[] deviceAddress) {
        return new ClassicBuilder(confirmation, randomizer, oobDataLength, deviceAddress);
    }

    /**
     * Main creation method for creating a LE version of {@link OobData}.
     *
     * <p>This object will allow the caller to call {@link LeBuilder#build()}
     * to build the data object or add any option information to the builder.
     *
     * @param leDeviceAddress the LE device address plus the address type (7 octets); not null.
     * @param leDeviceRole whether the device supports Peripheral, Central,
     * Both including preference; not null. (1 octet)
     * @param confirmation Array consisting of {@link OobData#CONFIRMATION_OCTETS} octets
     * of data. Data is derived from controller/host stack and is
     * required for pairing OOB.
     * @param randomizer the accompanying randomizer key, may be null or all 0s. 
     * if not present, all 0s will be assumed and passed to the controller.
     *
     * <p>Possible LE Device Role Values:
     * 0x00 Only Peripheral supported
     * 0x01 Only Central supported
     * 0x02 Central & Peripheral supported; Peripheral Preferred
     * 0x03 Only peripheral supported; Central Preferred
     * 0x04 - 0xFF Reserved
     *
     * @return a LeBuilder instance with all the given data set or null.
     *
     * @throws IllegalArgumentException if any of the values fail to be set.
     *
     * @hide
     */
    @Nullable
    @SystemApi
    public static LeBuilder createLeBuilder(@NonNull byte[] confirmation,
            @Nullable byte[] randomizer, @Nullable byte[] leDeviceAddress,
            @Nullable int leDeviceRole) {
        return new LeBuilder(confirmation, randomizer, leDeviceAddress, leDeviceRole);
    }

    /**
     * Common functionality for the Builders
     *
     * @hide
     */
    @SystemApi
    public static abstract class AbstractCommon<T> {

        // Used by both Classic and LE
        /**
         * Confirmation HASH C.
         *
         * <p>It is recommended that the Hash C is generated anew for each
         * pairing.
         *
         * <p>It should be noted that on passive NFC this isn't possible as the data is static
         * and immutable.
         *
         * @hide
         */
        protected byte[] mConfirmation = null;

        /**
         * Randomizer HASH R.
         *
         * <p>Optional, but adds more validity to the pairing.
         *
         * <p>If not present a value of 0 is assumed.
         *
         * @hide
         */
        protected byte[] mRandomizer = new byte[] {
            0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
            0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
        };

        /**
         * The Bluetooth Device user-friendly name presented over Bluetooth Technology.
         *
         * <p>This is the name that may be displayed to the device user as part of the UI.
         *
         * @hide
         */
        protected byte[] mDeviceName = null;

        /**
         * @param confirmation Array consisting of {@link OobData#CONFIRMATION_OCTETS} octets
         * of data. Data is derived from controller/host stack and is required for pairing OOB.
         * @param randomizer the accompanying randomizer key, may be null or all 0s. 
         * if not present, all 0s will be assumed and passed to the controller.
         *
         * @hide
         */
        @SystemApi
        private AbstractCommon(@NonNull byte[] confirmation, @Nullable byte[] randomizer) {
            if (confirmation.length != OobData.CONFIRMATION_OCTETS) {
                throw new IllegalArgumentException("confirmation must be " +
                        OobData.CONFIRMATION_OCTETS + " octets in length.");
            }
            this.mConfirmation = confirmation;
            // Default is all 0s
            if (randomizer != null) {
                if (randomizer.length != OobData.RANDOMIZER_OCTETS) {
                    throw new IllegalArgumentException("randomizer must be " +
                            OobData.RANDOMIZER_OCTETS + " octets in length.");
                }
                this.mRandomizer = randomizer;
            }
        }

        /**
         *
         * @return {@link OobData#Builder}
         *
         * @hide
         */
        @NonNull
        @SystemApi
        public T setRandomizer(@NonNull byte[] randomizer) {
            return (T)this;
        }

        /**
         * Sets the Bluetooth Device name to be used for UI purposes.
         *
         * <p>Optional attribute.
         *
         * @param deviceName byte array representing the name, may be 0 in length, not null.
         *
         * @return {@link OobData#ClassicBuilder}
         *
         * @hide
         */
        @NonNull
        @SystemApi
        public T setDeviceName(@NonNull byte[] deviceName) {
            this.mDeviceName = deviceName;
            return (T)this;
        }

        /**
         * To be overridden buy the subclasses.
         *
         * @return data built from set values.
         *
         * @hide
         */
        @Nullable
        @SystemApi
        public abstract OobData build();
    }

    /**
     * Builds an {@link OobData} object and validates that the required combination
     * of values are present to create the desired OobData type.
     *
     * @hide
     */
    @SystemApi
    public static final class LeBuilder extends AbstractCommon<LeBuilder> {
        // LE Only
        /* Required */

        /**
         * Identify the LE Device.
         *
         * <p>Consists of 7 octets. The least significant octets contain the 48 bit address that is
         * used for Bluetooth pairing over the LE transport and will identify thepeer device to
         * establish a connection with.
         *
         * <p> Address is encoded in Little Endian order.
         *
         * <p>e.g. 00:01:02:03:04:05 would be x05x04x03x02x01x00
         *
         * @hide
         */
        private byte[] mLeDeviceAddress = null;

        /**
         * During an LE connection establishment, one must be in the Peripheral mode and the other
         * in the Central role.
         *
         * <p>Possible Values:
         * {@link LE_DEVICE_ROLE_PERIPHERAL_ONLY} Only Peripheral supported
         * {@link LE_DEVICE_ROLE_CENTRAL_ONLY} Only Central supported
         * {@link LE_DEVICE_ROLE_BOTH_PREFER_PERIPHERAL} Central & Peripheral supported; Peripheral Preferred
         * {@link LE_DEVICE_ROLE_BOTH_PREFER_CENTRAL} Only peripheral supported; Central Preferred
         * 0x04 - 0xFF Reserved
         *
         * @hide
         */
        private byte mLeDeviceRole = 0x0;

        /* Optional */
        /**
         * Temporary key value from the Security Manager.
         *
         * <p> Must be {@link LE_DEVICE_ADDRESS_OCTETS
         *
         * @hide
         */
        private byte[] mLeTemporaryKey = null;

        /**
         * Defines the representation of the external appearance of the device.
         *
         * <p>For example, a mouse, remote control, or keyboard.
         *
         * <p>Used for visual on discovering device to represent icon/string/etc...
         *
         * @hide
         */
        private byte[] mLeAppearance = null;

        /**
         * Contains which discoverable mode to use, BR/EDR support and capability.
         *
         * <p>Possible LE Flags:
         * {@link LE_FLAG_LIMITED_DISCOVERY_MODE} LE Limited Discoverable Mode.
         * {@link LE_FLAG_GENERAL_DISCOVERY_MODE} LE General Discoverable Mode.
         * {@link LE_FLAG_BREDR_NOT_SUPPORTED} BR/EDR Not Supported. Bit 37 of LMP Feature Mask Definitions.
         * {@link LE_FLAG_SIMULTANEOUS_CONTROLLER} Simultaneous LE and BR/EDR to Same Device Capable (Controller).
         * Bit 49 of LMP Feature Mask Definitions.
         * {@link LE_FLAG_SIMULTANEOUS_HOST} Simultaneous LE and BR/EDR to Same Device Capable (Host).
         * Bit 55 of LMP Feature Mask Definitions.
         * <b>0x05- 0x07 Reserved</b>
         *
         * @hide
         */
        private byte mLeFlags = 0xF; // Invalid default

        /**
         * Constructing an OobData object for use with LE requires
         * a LE Device Address and LE Device Role as well as the Confirmation
         * and optionally, the Randomizer, however it is recommended to use.
         *
         * <p>The LE Address is .
         *
         * @param leDeviceAddress 7 bytes containing the 6 byte address with the 1 byte address type.
         * @param leDeviceRole indicating device's role and preferences (Central or Peripheral)
         *
         * <p>Possible Values:
         * {@link LE_DEVICE_ROLE_PERIPHERAL_ONLY} Only Peripheral supported
         * {@link LE_DEVICE_ROLE_CENTRAL_ONLY} Only Central supported
         * {@link LE_DEVICE_ROLE_BOTH_PREFER_PERIPHERAL} Central & Peripheral supported; Peripheral Preferred
         * {@link LE_DEVICE_ROLE_BOTH_PREFER_CENTRAL} Only peripheral supported; Central Preferred
         * 0x04 - 0xFF Reserved
         *
         * @throws IllegalArgumentException if leDeviceAddress is not
         *                                  {@link LE_DEVICE_ADDRESS_OCTETS} octets
         *
         * @hide
         */
        private LeBuilder(@NonNull byte[] confirmation, @Nullable byte[] randomizer,
                @NonNull byte[] leDeviceAddress, @NonNull int leDeviceRole) {
            super(confirmation, randomizer);

            if (leDeviceAddress.length != LE_DEVICE_ADDRESS_OCTETS) {
                throw new IllegalArgumentException("leDeviceAddress must be "
                        + LE_DEVICE_ADDRESS_OCTETS + " octets in length.");
            }
            this.mLeDeviceAddress = leDeviceAddress;
            if (leDeviceRole > LE_DEVICE_ROLE_BOTH_PREFER_CENTRAL) {
                throw new IllegalArgumentException("leDeviceRole must be a valid value.");
            }
            this.mLeDeviceRole = (byte) leDeviceRole;
        }

        /**
         * Sets the Temporary Key value to be used by the LE Security Manager during
         * LE pairing.
         *
         * @param leTemporaryKey byte array that shall be 16 bytes. Please see Bluetooth CSSv6,
         * Part A 1.8 for a detailed description.
         *
         * @return {@link OobData#Builder}
         *
         * @throws IllegalArgumentException if the leTemporaryKey is an invalid format.
         *
         * @hide
         */
        @NonNull
        @SystemApi
        public LeBuilder setLeTemporaryKey(@NonNull byte[] leTemporaryKey) {
            if (leTemporaryKey.length != LE_TK_OCTETS) {
                throw new IllegalArgumentException("leTemporaryKey must be "
                        + LE_TK_OCTETS + " octets in length.");
            }
            this.mLeTemporaryKey = leTemporaryKey;
            return this;
        }

        /**
         * Sets the LE Flags necessary for the pairing scenario or discovery mode.
         *
         * @param leFlags enum value representing the 1 octet of data about discovery modes.
         *
         * <p>Possible LE Flags:
         * {@link LE_FLAG_LIMITED_DISCOVERY_MODE} LE Limited Discoverable Mode.
         * {@link LE_FLAG_GENERAL_DISCOVERY_MODE} LE General Discoverable Mode.
         * {@link LE_FLAG_BREDR_NOT_SUPPORTED} BR/EDR Not Supported. Bit 37 of LMP Feature Mask Definitions.
         * {@link LE_FLAG_SIMULTANEOUS_CONTROLLER} Simultaneous LE and BR/EDR to Same Device Capable (Controller).
         * Bit 49 of LMP Feature Mask Definitions.
         * {@link LE_FLAG_SIMULTANEOUS_HOST} Simultaneous LE and BR/EDR to Same Device Capable (Host).
         * Bit 55 of LMP Feature Mask Definitions.
         * 0x05- 0x07 Reserved
         *
         * @throws IllegalArgumentException for invalid flag
         * @hide
         */
        @NonNull
        @SystemApi
        public LeBuilder setLeFlags(@NonNull int leFlags) {
            if (leFlags > LE_FLAG_SIMULTANEOUS_HOST) {
                   throw new IllegalArgumentException("leFlags must be a valid value.");
            }
            this.mLeFlags = (byte) leFlags;
            return this;
        }

        /**
         * Validates and builds the {@link OobData} object for LE Security.
         *
         * @return {@link OobData} with given builder values
         *
         * @throws IllegalStateException if either of the 2 required fields were not set.
         *
         * @hide
         */
        @Nullable
        @SystemApi
        public OobData build() {
            final OobData oob =
                    new OobData(this.mLeDeviceAddress, this.mLeDeviceRole, this.mConfirmation, this.mRandomizer);

            // If we have values, set them, otherwise use default
            oob.mLeTemporaryKey =
                    (this.mLeTemporaryKey != null) ? this.mLeTemporaryKey : oob.mLeTemporaryKey;
            oob.mLeAppearance = (this.mLeAppearance != null) ? this.mLeAppearance : oob.mLeAppearance;
            oob.mLeFlags = (this.mLeFlags != 0xF) ? this.mLeFlags : oob.mLeFlags;
            oob.mDeviceName = (this.mDeviceName != null) ? this.mDeviceName : oob.mDeviceName;
            return oob;
        }
    }

    /**
     * Builds an {@link OobData} object and validates that the required combination
     * of values are present to create the desired OobData type.
     *
     * @hide
     */
    @SystemApi
    public static final class ClassicBuilder extends AbstractCommon<ClassicBuilder> {
        // Classic Only
        /* Required */

        /**
         * This length value provides the absolute length of total OOB data block used for
         * Bluetooth BR/EDR
         *
         * <p>OOB communication, which includes the length field itself and the Bluetooth
         * Device Address.
         *
         * <p>The minimum length that may be represented in this field is 8.
         *
         * @hide
         */
        private final byte[] mOobDataLength;

        /**
         * The Bluetooth Device Address is the address to which the OOB data belongs.
         *
         * <p>The length MUST be {@link OobData#DEVICE_ADDRESS_OCTETS} octets.
         *
         * <p> Address is encoded in Little Endian order.
         *
         * <p>e.g. 00:01:02:03:04:05 would be x05x04x03x02x01x00
         *
         * @hide
         */
        private final byte[] mDeviceAddress;

        /* Optional */

        /**
         * Class of Device information is to be used to provide a graphical representation
         * to the user as part of UI involving operations.
         *
         * <p>This is not to be used to determine a particlar service can be used.
         *
         * <p>The length MUST be {@link OobData#CLASS_OF_DEVICE_OCTETS} octets.
         *
         * @hide
         */
        private byte[] mClassOfDevice = null;

        /**
         * @param confirmation byte array consisting of {@link OobData#CONFIRMATION_OCTETS} octets
         * of data. Data is derived from controller/host stack and is required for pairing OOB.
         * @param randomizer byte array consisting of {@link OobData#RANDOMIZER_OCTETS} octets
         * of data. Data is derived from controller/host stack and is required
         * for pairing OOB. Also, randomizer may be all 0s or null in which case
         * it becomes all 0s.
         * @param oobDataLength byte array representing the length of data from 8-65535 across 2
         * octets (0xXXXX). Inclusive of this value in the length.
         * @param deviceAddress byte array representing the Bluetooth Address of the device
         * that owns the OOB data. (i.e. the originator) [6 octets]
         *
         * @throws IllegalArgumentException if oob data length or device address is invalid
         *
         * @hide
         */
        @SystemApi
        private ClassicBuilder(@NonNull byte[] confirmation, @Nullable byte[] randomizer,
                @NonNull byte[] oobDataLength, @NonNull byte[] deviceAddress) {
            super(confirmation, randomizer);
            if (oobDataLength.length != OOB_LENGTH_OCTETS) {
                throw new IllegalArgumentException("oobDataLength must be "
                        + OOB_LENGTH_OCTETS + " octets in length.");
            }
            if (deviceAddress.length != DEVICE_ADDRESS_OCTETS) {
                throw new IllegalArgumentException("deviceAddress must be "
                        + DEVICE_ADDRESS_OCTETS + " octets in length.");
            }
            this.mOobDataLength = oobDataLength;
            this.mDeviceAddress = deviceAddress;
        }

        /**
         * Sets the Bluetooth Class of Device; used for UI purposes only.
         *
         * <p>Not an indicator of available services!
         *
         * <p>Optional attribute.
         *
         * @param address byte array of {@link OobData#CLASS_OF_DEVICE_OCTETS} octets.
         *
         * @return {@link OobData#ClassicBuilder}
         *
         * @throws IllegalArgumentException if length is not equal to
         * {@link OobData#OOB_LENGTH_OCTETS} octets.
         *
         * @hide
         */
        @NonNull
        @SystemApi
        public ClassicBuilder setClassOfDevice(@NonNull byte[] classOfDevice) {
            if (classOfDevice.length != OobData.CLASS_OF_DEVICE_OCTETS) {
                throw new IllegalArgumentException("classOfDevice must be "
                        + OobData.CLASS_OF_DEVICE_OCTETS + " octets in length.");
            }
            this.mClassOfDevice = classOfDevice;
            return this;
        }

        /**
         * Validates and builds the {@link OobData} object for Classic Security.
         *
         * @return {@link OobData} with previously given builder values.
         *
         * @hide
         */
        @Nullable
        @SystemApi
        public OobData build() {
            final OobData oob =
                    new OobData(this.mOobDataLength, this.mDeviceAddress, this.mConfirmation, this.mRandomizer);
            // If we have values, set them, otherwise use default
            oob.mDeviceName = (this.mDeviceName != null) ? this.mDeviceName : oob.mDeviceName;
            oob.mClassOfDevice = (this.mClassOfDevice != null) ? this.mClassOfDevice : oob.mClassOfDevice;
            return oob;
        }
    }

    // Members (Defaults for Optionals must be set or Parceling fails on NPE)
    // Both
    private final byte[] mConfirmation;
    private final byte[] mRandomizer;
    // Default the name to "Bluetooth Device"
    private byte[] mDeviceName = new byte[] {
        // Bluetooth
        0x42, 0x6c, 0x75, 0x65, 0x74, 0x6f, 0x6f, 0x74, 0x68,
        // <space>Device
        0x20, 0x44, 0x65, 0x76, 0x69, 0x63, 0x65
    };

    // Classic
    private byte[] mOobDataLength = null;
    private byte[] mDeviceAddress = null;
    private byte[] mClassOfDevice = new byte[CLASS_OF_DEVICE_OCTETS];

    // LE
    private byte[] mLeDeviceAddress = null;
    private byte mLeDeviceRole = 0x0;
    private byte[] mLeTemporaryKey = new byte[LE_TK_OCTETS];
    private byte[] mLeAppearance = new byte[LE_APPEARANCE_OCTETS];
    private byte mLeFlags = 0x0;

    /**
     * @return byte array representing the confirmation value
     * which is used to confirm the identity to the controller.
     *
     * @hide
     */
    @NonNull
    @SystemApi
    public byte[] getConfirmation() {
        return mConfirmation;
    }

    /**
     * @return byte array representing the randomizer value
     * which is used to verify the identity of the controller.
     *
     * @hide
     */
    @NonNull
    @SystemApi
    public byte[] getRandom() {
        return mRandomizer;
    }

    /**
     * @return Device Name used for displaying name in UI.
     *
     * <p>Also, this will be populated with the LE Local Name if the data is for LE.
     *
     * @hide
     */
    @Nullable
    @SystemApi
    public byte[] getDeviceName() {
        return mDeviceName;
    }

    /**
     * @return byte array representing the oob data length which is the length
     * of all of the data including these octets.
     *
     * @hide
     */
    @NonNull
    @SystemApi
    public byte[] getOobDataLength() {
        return mOobDataLength;
    }

    /**
     * @return byte array representing the MAC address of a bluetooth device
     *
     * @hide
     */
    @NonNull
    @SystemApi
    public byte[] getDeviceAddress() {
        return mDeviceAddress;
    }

    /**
     * @return byte array representing the class of device for UI display.
     *
     * <p>Does not indicate services available; for display only.
     *
     * @hide
     */
    @NonNull
    @SystemApi
    public byte[] getClassOfDevice() {
        return mClassOfDevice;
    }

    /**
     * @return the LE device address derived from the LE pairing process.
     *
     * @hide
     */
    @Nullable
    @SystemApi
    public byte[] getLeDeviceAddress() {
        return mLeDeviceAddress;
    }

    /**
     * @return Temporary Key used for LE pairing.
     *
     * @hide
     */
    @Nullable
    @SystemApi
    public byte[] getLeTemporaryKey() {
        return mLeTemporaryKey;
    }

    /**
     * @return Appearance used for LE pairing. For use in UI situations
     * when determining what sort of icons or text to display regarding
     * the device.
     *
     * @hide
     */
    @Nullable
    @SystemApi
    public byte[] getLeAppearance() {
        return mLeTemporaryKey;
    }

    /**
     * @return Flags used to determing discoverable mode to use, BR/EDR Support, and Capability.
     *
     * @hide
     */
    @Nullable
    @SystemApi
    public byte[] getLeFlags() {
        return mLeTemporaryKey;
    }

    /**
     * Classic Security Constructor
     */
    private OobData(@NonNull byte[] oobDataLength, @NonNull byte[] deviceAddress,
            @NonNull byte[] confirmation, @NonNull byte[] randomizer) {
        mOobDataLength = oobDataLength;
        mDeviceAddress = deviceAddress;
        mConfirmation = confirmation;
        mRandomizer = randomizer;
    }

    /**
     * LE Security Constructor
     */
    private OobData(@NonNull byte[] leDeviceAddress, @NonNull byte leDeviceRole,
            @NonNull byte[] confirmation, @NonNull byte[] randomizer) {
        mLeDeviceAddress = leDeviceAddress;
        mLeDeviceRole = leDeviceRole;
        mConfirmation = confirmation;
        mRandomizer = randomizer;
    }

    private OobData(Parcel in) {
        // Both
        mConfirmation = in.createByteArray();
        mRandomizer = in.createByteArray();
        mDeviceName = in.createByteArray();

        // Classic
        mOobDataLength = in.createByteArray();
        mDeviceAddress = in.createByteArray();
        mClassOfDevice = in.createByteArray();

        // LE
        mLeDeviceAddress = in.createByteArray();
        mLeDeviceRole = in.readByte();
        mLeTemporaryKey = in.createByteArray();
        mLeAppearance = in.createByteArray();
        mLeFlags = in.readByte();
    }

    /**
     * @hide
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * @hide
     */
    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        // Both
        out.writeByteArray(mConfirmation);
        out.writeByteArray(mRandomizer);
        out.writeByteArray(mDeviceName);

        // Classic
        out.writeByteArray(mOobDataLength);
        out.writeByteArray(mDeviceAddress);
        out.writeByteArray(mClassOfDevice);

        // LE
        out.writeByteArray(mLeDeviceAddress);
        out.writeByte(mLeDeviceRole);
        out.writeByteArray(mLeTemporaryKey);
        out.writeByteArray(mLeAppearance);
        out.writeByte(mLeFlags);
    }

    // For Parcelable
    public static final @android.annotation.NonNull Parcelable.Creator<OobData> CREATOR =
            new Parcelable.Creator<OobData>() {
        public OobData createFromParcel(Parcel in) {
            return new OobData(in);
        }

        public OobData[] newArray(int size) {
            return new OobData[size];
        }
    };

    /**
     * @return a {@link String} representation of the OobData object.
     *
     * @hide
     */
    @Override
    @NonNull
    public String toString() {
        return "OobData: \n\t"
            // Both
            + "Confirmation: " + toHexString(mConfirmation) + "\n\t"
            + "Randomizer: " + toHexString(mRandomizer) + "\n\t"
            + "Device Name: " + toHexString(mDeviceName) + "\n\t"
            // Classic
            + "OobData Length: " +  toHexString(mOobDataLength) + "\n\t"
            + "Device Address: " +  toHexString(mDeviceAddress) + "\n\t"
            + "Class of Device: " +  toHexString(mClassOfDevice) + "\n\t"
            // LE
            + "LE Device Address: " + toHexString(mLeDeviceAddress) + "\n\t"
            + "LE Device Role: " + toHexString(mLeDeviceRole) + "\n\t"
            + "LE Temporary Key: " + toHexString(mLeTemporaryKey) + "\n\t"
            + "LE Appearance: " + toHexString(mLeAppearance) + "\n\t"
            + "LE Flags: " + toHexString(mLeFlags) + "\n\t";
    }

    @NonNull
    private String toHexString(@NonNull byte b) {
        return toHexString(new byte[] {b});
    }

    @NonNull
    private String toHexString(@NonNull byte[] array) {
        StringBuilder builder = new StringBuilder(array.length * 2);
        for (byte b: array) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
