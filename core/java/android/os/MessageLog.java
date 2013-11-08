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

package android.os;

import java.io.PrintWriter;
import java.lang.StringBuilder;

/**
 * @hide
 */
public final class MessageLog {
    private final int CACHE_SIZE = 128;
    private long[] begin = new long[CACHE_SIZE];
    private long[] end = new long[CACHE_SIZE];
    private long[] when = new long[CACHE_SIZE];
    private int[] what = new int[CACHE_SIZE];
    private int[] arg1 = new int[CACHE_SIZE];
    private String[] callback = new String[CACHE_SIZE];
    private String[] target = new String[CACHE_SIZE];

    private int head;
    private int next;
    private int count;

    public MessageLog() {
        head = next = count = 0;
    }

    public synchronized void logBegin(Message msg, long now) {
        begin[next] = now;
        end[next] = 0;
        when[next] = msg.when;
        if (msg.target == null) {
            target[next] = null;
            arg1[next] = msg.arg1;
        } else {
            target[next] = msg.target.getClass().getName();
            what[next] = msg.what;
            callback[next] = null;
            if (msg.callback != null) {
                callback[next] = msg.callback.getClass().getName();
            }
        }

        if (count < CACHE_SIZE) {
            count++;
        } else {
            head = (head + 1) % CACHE_SIZE;
        }
    }

    public synchronized void logEnd(Message msg, long now) {
        end[next] = now;
        next = (next + 1) % CACHE_SIZE;
    }

    public synchronized void dump(PrintWriter pw) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.setLength(0);
            int pos = (head + i) % CACHE_SIZE;
            sb.append("when=")
                .append(when[pos])
                .append(" wait=")
                .append(begin[pos] - when[pos])
                .append(" run=");
            // Last message is not finished yet
            if (i == count-1 && end[pos] == 0) {
                sb.append(SystemClock.uptimeMillis() - begin[pos]);
            } else {
                sb.append(end[pos] - begin[pos]);
            }
            if (target[pos] == null) {
                sb.append(" barrier=")
                    .append(arg1[pos]);
            } else {
                sb.append(" target=")
                    .append(target[pos])
                    .append(" what=")
                    .append(what[pos])
                    .append(" callback=")
                    .append(callback[pos]);
            }
            pw.println(sb.toString());
        }
    }
}
