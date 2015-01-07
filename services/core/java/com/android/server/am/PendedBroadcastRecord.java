/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.server.am;

import android.util.TimeUtils;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;

class PendedBroadcastRecord {
    final BroadcastRecord record;

    static class PendedReceiver {
        final Object receiver;
        long started = -1;
        PendedReceiver(Object receiverInfo) {
            receiver = receiverInfo;
        }
    }

    final ArrayList<PendedReceiver> receivers = new ArrayList<PendedReceiver>();

    PendedBroadcastRecord(BroadcastRecord r) {
        record = r;
        for (Object info: record.receivers) {
            receivers.add(new PendedReceiver(info));
        }
    }

    void updateReceiverStartTime(int idx , long receiverTime) {
        if (idx >= 0 && idx < receivers.size()) {
            receivers.get(idx).started = receiverTime;
        }
    }

    final void dumpLocked(FileDescriptor fd, PrintWriter pw, String[] args, String prefix) {
        long finished = record.finishTime;
        pw.print(prefix); pw.print("pended duration = ");
            TimeUtils.formatDuration(finished, record.dispatchTime, pw);
            pw.println("");

        pw.print(prefix); pw.println("pended receivers");
        for (int j=receivers.size()-1; j>=0; j--) {
            PendedReceiver pr = receivers.get(j);
            if (pr.started != -1) {
                pw.print(prefix); pw.print(" "); pw.print("pended Receiver #" + j + " :"); pw.print(pr.receiver);
                    pw.print(" duration=");
                    TimeUtils.formatDuration(finished, pr.started, pw);
                    pw.println("");
                finished = pr.started;
            }
        }
    }
}
