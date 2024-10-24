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

/** This class is used to represent NFC system events. */
@FlaggedApi(android.nfc.Flags.FLAG_NFC_EVENT_LISTENER)
public abstract class NfcSystemEvent {
    // NFC controller state changed. In NfcService.java
    public enum StateChangeEvent {
        TURNED_ON,
        TURNED_ON_LOCKED, // secure NFC
        TURNED_OFF,
        CRASH_RESTART,
    };

    public class NfcStateChanged extends NfcSystemEvent {
        StateChangeEvent stateChangeEvent;
    }

    // in HostEmulationManager.java
    /**
     * This class is used to represent AID conflict events.
     */
    public class AidConflictOccurred extends NfcSystemEvent {
        public AidConflictOccurred(byte[] conflictingAid) {
            this.conflictingAid = conflictingAid;
        }
        private final byte[] conflictingAid;

        /**
         * The conflicting AID.
         */
        public byte[] getConflictingAid() {
            return conflictingAid;
        }
    }

    /**
     * The type of NFC error that occurred in an NfcErrorOccurred event.
     */
    public enum ErrorType {
        /**
         * The NCI command timed out.
         */
        COMMAND_TIMEOUT,
        /**
         * An NCI Error notification. The result code is in {@link NfcErrorOccurred#errorNotificationStatusCode}.
         */
        ERROR_NOTIFICATION,
        AID_OVERFLOW,
        HCE_LATE_BINDING,
        NFC_ACCESS_CHECK_ERROR,
    };

    /** This class is used to represent NFC error events. */
    public class NfcErrorOccurred extends NfcSystemEvent {
        /**
         * @param errorType the type of NFC error
         * @param errorNotificationStatusCode the status code of the error notification, for
         *     {@link ErrorType#ERROR_NOTIFICATION} events.
         */
        public NfcErrorOccurred(ErrorType errorType, int errorNotificationStatusCode) {
            this.errorType = errorType;
            this.errorNotificationStatusCode = errorNotificationStatusCode;
        }

        private final ErrorType errorType;
        private final int errorNotificationStatusCode;

        /**
         * The type of NFC error that occurred.
         */
        public ErrorType getErrorType() {
            return errorType;
        }

        /**
         * The status code of the error notification, for {@link ErrorType#ERROR_NOTIFICATION}
         * events.
         */
        public int getErrorNotificationStatusCode() {
            return errorNotificationStatusCode;
        }
    }
}
