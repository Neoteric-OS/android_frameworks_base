/*
 * Copyright 2019 HIMSA II K/S - www.himsa.com. Represented by EHIMA - www.ehima.com.
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
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * @hide
 */
@SystemApi
public final class BluetoothMcsMediaControlRequest implements Parcelable {
    private int opcode;
    private Integer int_arg;

    /**
     * Media control request supported opcodes definition
     * @hide
     */
    public final static class SupportedOpcodes {
        private SupportedOpcodes() {
            // not called
        }
        public static final int NONE = 0x00;
        public static final int PLAY = 0x01;
        public static final int PAUSE = 0x02;
        public static final int FAST_REWIND = 0x04;
        public static final int FAST_FORWARD = 0x08;
        public static final int STOP = 0x10;
        public static final int MOVE_RELATIVE = 0x20;
        public static final int PREVIOUS_SEGMENT = 0x40;
        public static final int NEXT_SEGMENT = 0x80;
        public static final int FIRST_SEGMENT = 0x0100;
        public static final int LAST_SEGMENT = 0x0200;
        public static final int GOTO_SEGMENT = 0x0400;
        public static final int PREVIOUS_TRACK = 0x0800;
        public static final int NEXT_TRACK = 0x1000;
        public static final int FIRST_TRACK = 0x2000;
        public static final int LAST_TRACK = 0x4000;
        public static final int GOTO_TRACK = 0x8000;
        public static final int PREVIOUS_GROUP = 0x010000;
        public static final int NEXT_GROUP = 0x020000;
        public static final int FIRST_GROUP = 0x040000;
        public static final int LAST_GROUP = 0x080000;
        public static final int GOTO_GROUP = 0x100000;
    }

    /**
     * Media control request opcodes definition
     * @hide
     */
    public final static class Opcodes {
        private Opcodes() {
            // not called
        }
        public static final int PLAY = 0x01;
        public static final int PAUSE = 0x02;
        public static final int FAST_REWIND = 0x03;
        public static final int FAST_FORWARD = 0x04;
        public static final int STOP = 0x05;
        public static final int MOVE_RELATIVE = 0x10;
        public static final int PREVIOUS_SEGMENT = 0x20;
        public static final int NEXT_SEGMENT = 0x21;
        public static final int FIRST_SEGMENT = 0x22;
        public static final int LAST_SEGMENT = 0x23;
        public static final int GOTO_SEGMENT = 0x24;
        public static final int PREVIOUS_TRACK = 0x30;
        public static final int NEXT_TRACK = 0x31;
        public static final int FIRST_TRACK = 0x32;
        public static final int LAST_TRACK = 0x33;
        public static final int GOTO_TRACK = 0x34;
        public static final int PREVIOUS_GROUP = 0x40;
        public static final int NEXT_GROUP = 0x41;
        public static final int FIRST_GROUP = 0x42;
        public static final int LAST_GROUP = 0x43;
        public static final int GOTO_GROUP = 0x44;
    }

    /**
     * Media control request results definition
     * @hide
     */
    public final static class Results {
        private Results() {
            // not called
        }
        public static final int SUCCESS = 0x01;
        public static final int OPCODE_NOT_SUPPORTED = 0x02;
        public static final int MEDIA_PLAYER_INACTIVE = 0x03;
        public static final int COMMAND_CANNOT_BE_COMPLETED = 0x04;
    }

    /**
     * Media control request constructor
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @param opcode Control request opcode
     * @param arg Control request argument
     */
    public BluetoothMcsMediaControlRequest(int opcode, int arg) {
            this.opcode = opcode;
            this.int_arg = arg;
    }

    /**
     * Media control request constructor
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @param in Control request parcel
     */
    BluetoothMcsMediaControlRequest(@NonNull Parcel in) {
        opcode = in.readInt();
        // FIXME: Ugh..., this high level logic shouldn't be here
        if ((opcode == Opcodes.MOVE_RELATIVE) ||
                (opcode == Opcodes.GOTO_SEGMENT) ||
                (opcode == Opcodes.GOTO_TRACK) ||
                (opcode == Opcodes.GOTO_GROUP)) {
            int_arg = in.readInt();
        } else {
            int_arg = null;
        }
    }

    /**
     * Media control results opcode getter
     *
     * @return Control request opcode
     * @hide
     */
    public int getOpcode() { return opcode; }

    /**
     * Media control results argument getter
     *
     * @return Control request argument
     * @hide
     */
    public int getIntArg() {
        return int_arg;
    }

    /**
     * Media control request array creator
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     */
    public static final @NonNull Creator<BluetoothMcsMediaControlRequest> CREATOR =
        new Creator<BluetoothMcsMediaControlRequest>() {
        public BluetoothMcsMediaControlRequest createFromParcel(@NonNull Parcel in) {
            return new BluetoothMcsMediaControlRequest(in);
        }

        public BluetoothMcsMediaControlRequest[] newArray(int size) {
            return new BluetoothMcsMediaControlRequest[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(opcode);
        if (int_arg != null)
            dest.writeInt(int_arg);
    }
}
