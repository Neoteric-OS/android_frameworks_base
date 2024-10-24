/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.nfc.cardemulation;

import android.annotation.FlaggedApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.annotation.IntDef;

/** This class is used to represent NFC system events. */
@FlaggedApi(android.nfc.Flags.FLAG_NFC_EVENT_LISTENER)
public abstract class NfcSystemEvent implements Parcelable {
    @IntDef(
            prefix = {"NFC_STATE_CHANGE_EVENT_"},
            value = {
                NFC_STATE_CHANGE_EVENT_TURNED_ON,
                NFC_STATE_CHANGE_EVENT_TURNED_ON_LOCKED, // secure NFC
                NFC_STATE_CHANGE_EVENT_TURNED_OFF,
                NFC_STATE_CHANGE_EVENT_CRASH_RESTART,
            })
    public @interface StateChangeEvent {}

    public static final int NFC_STATE_CHANGE_EVENT_TURNED_ON = 0;
    public static final int NFC_STATE_CHANGE_EVENT_TURNED_ON_LOCKED = 1;
    public static final int NFC_STATE_CHANGE_EVENT_TURNED_OFF = 2;
    public static final int NFC_STATE_CHANGE_EVENT_CRASH_RESTART = 3;

    /** NFC controller state changed. In NfcService.java */
    public class NfcStateChanged extends NfcSystemEvent {
        /**
         * @param stateChangeEvent the state change event
         */
        public NfcStateChanged(@StateChangeEvent int stateChangeEvent) {
            this.mStateChangeEvent = stateChangeEvent;
        }

        private final int mStateChangeEvent;

        /** The state change event. */
        public @StateChangeEvent int getStateChangeEvent() {
            return mStateChangeEvent;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeInt(mStateChangeEvent);
        }

        public static final @NonNull Parcelable.Creator<NfcStateChanged> CREATOR =
                new Parcelable.Creator<NfcStateChanged>() {

                    @Override
                    public NfcStateChanged createFromParcel(Parcel source) {
                        return new NfcStateChanged(source.readInt());
                    }

                    @Override
                    public NfcStateChanged[] newArray(int size) {
                        return new NfcStateChanged[size];
                    }
                };
    }

    // in HostEmulationManager.java
    /** This class is used to represent AID conflict events. */
    public class AidConflictOccurred extends NfcSystemEvent {
        public AidConflictOccurred(byte[] conflictingAid) {
            this.mConflictingAid = conflictingAid;
        }

        private final byte[] mConflictingAid;

        /** The conflicting AID. */
        public byte[] getConflictingAid() {
            return mConflictingAid;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeByteArray(mConflictingAid);
        }

        public static final @NonNull Parcelable.Creator<AidConflictOccurred> CREATOR =
                new Parcelable.Creator<AidConflictOccurred>() {

                    @Override
                    public AidConflictOccurred createFromParcel(Parcel source) {
                        return new AidConflictOccurred(source.createByteArray());
                    }

                    @Override
                    public AidConflictOccurred[] newArray(int size) {
                        return new AidConflictOccurred[size];
                    }
                };
    }

    /** The type of NFC error that occurred in an NfcErrorOccurred event. */
    @IntDef(
            prefix = {"NFC_ERROR_TYPE_"},
            value = {
                NFC_ERROR_TYPE_COMMAND_TIMEOUT,
                NFC_ERROR_TYPE_ERROR_NOTIFICATION,
                NFC_ERROR_TYPE_AID_OVERFLOW,
                NFC_ERROR_TYPE_HCE_LATE_BINDING,
                NFC_ERROR_TYPE_NFC_ACCESS_CHECK_ERROR,
            })
    public @interface ErrorType {}

    /** The NCI command timed out. */
    public static final int NFC_ERROR_TYPE_COMMAND_TIMEOUT = 0;
    /**
     * An NCI Error notification. The result code is in {@link
     * NfcErrorOccurred#errorNotificationStatusCode}.
     */
    public static final int NFC_ERROR_TYPE_ERROR_NOTIFICATION = 1;
    public static final int NFC_ERROR_TYPE_AID_OVERFLOW = 2;
    public static final int NFC_ERROR_TYPE_HCE_LATE_BINDING = 3;
    public static final int NFC_ERROR_TYPE_NFC_ACCESS_CHECK_ERROR = 4;

    /** This class is used to represent NFC error events. */
    public class NfcErrorOccurred extends NfcSystemEvent {
        /**
         * @param errorType the type of NFC error
         * @param errorNotificationStatusCode the status code of the error notification, for {@link
         *     ErrorType#NFC_ERROR_TYPE_ERROR_NOTIFICATION} events.
         */
        public NfcErrorOccurred(@ErrorType int errorType, int errorNotificationStatusCode) {
            this.mErrorType = errorType;
            this.mErrorNotificationStatusCode = errorNotificationStatusCode;
        }

        private final @ErrorType int mErrorType;
        private final int mErrorNotificationStatusCode;

        /** The type of NFC error that occurred. */
        public @ErrorType int getErrorType() {
            return mErrorType;
        }

        /**
         * The status code of the error notification, for {@link
         * ErrorType#NFC_ERROR_TYPE_ERROR_NOTIFICATION} events.
         */
        public int getErrorNotificationStatusCode() {
            return mErrorNotificationStatusCode;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeInt(mErrorType);
            dest.writeInt(mErrorNotificationStatusCode);
        }

        public static final @NonNull Parcelable.Creator<NfcErrorOccurred> CREATOR =
                new Parcelable.Creator<NfcErrorOccurred>() {

                    @Override
                    public NfcErrorOccurred createFromParcel(Parcel source) {
                        return new NfcErrorOccurred(source.readInt(), source.readInt());
                    }

                    @Override
                    public NfcErrorOccurred[] newArray(int size) {
                        return new NfcErrorOccurred[size];
                    }
                };
    }
}
