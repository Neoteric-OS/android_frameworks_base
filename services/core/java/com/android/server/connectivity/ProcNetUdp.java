/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.server.connectivity;

import static android.os.Process.INVALID_UID;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Scanner;

/**
 * @hide
 */
public class ProcNetUdp {
    public static final String TAG = "ProcNetUdp";

    /**
     * Reads and parses a {@code /proc/net/{udp|udp6} file extracting the mapping between
     * local port and uid.
     */
    private static int readProcNet(String procFilePath, InetSocketAddress lookup)
            throws IOException {
        // Sample output of "cat /proc/net/tcp" on emulator:
        //
        // sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  ...
        // 0: 0100007F:13AD 00000000:0000 0A 00000000:00000000 00:00000000 00000000     0   ...
        // 1: 00000000:15B3 00000000:0000 0A 00000000:00000000 00:00000000 00000000     0   ...
        // 2: 0F02000A:15B3 0202000A:CE8A 01 00000000:00000000 00:00000000 00000000     0   ...

        File procFile = new File(procFilePath);
        InputStream in = null;
        try {
            in = new BufferedInputStream(new FileInputStream(procFile));
            return parseProcNet(in, lookup);
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    private static int parseProcNet(InputStream in, InetSocketAddress lookup) throws IOException {
        Scanner scanner = null;
        try {
            scanner = new Scanner(in);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();

                // Skip column headers
                if (line.startsWith("sl")) {
                    continue;
                }

                String[] fields = line.split("\\s+");

                int uid = Integer.parseInt(fields[7]);
                InetAddress address = addrToInet(fields[1].split(":")[0]);
                int port = Integer.parseInt(fields[1].split(":")[1], 16);
                if (address.equals(lookup.getAddress()) && (lookup.getPort() == port)) {
                    return uid;
                }
            }
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
        return INVALID_UID;
    }

    /**
     * Convert a string stored in little endian format to an IP address.
     */
    private static InetAddress addrToInet(String s) throws UnknownHostException {
        int len = s.length();
        if (len != 8 && len != 32) {
            throw new IllegalArgumentException(len + "");
        }
        byte[] retval = new byte[len / 2];

        for (int i = 0; i < len / 2; i += 4) {
            retval[i] = (byte) ((Character.digit(s.charAt(2 * i + 6), 16) << 4)
                    + Character.digit(s.charAt(2 * i + 7), 16));
            retval[i + 1] = (byte) ((Character.digit(s.charAt(2 * i + 4), 16) << 4)
                    + Character.digit(s.charAt(2 * i + 5), 16));
            retval[i + 2] = (byte) ((Character.digit(s.charAt(2 * i + 2), 16) << 4)
                    + Character.digit(s.charAt(2 * i + 3), 16));
            retval[i + 3] = (byte) ((Character.digit(s.charAt(2 * i), 16) << 4)
                    + Character.digit(s.charAt(2 * i + 1), 16));
        }
        return InetAddress.getByAddress(retval);
    }

    private static final String PROC_NET_UDP_FILES[] = {"/proc/net/udp6", "/proc/net/udp"};

    private static int lookupConnection(InetSocketAddress lookup) {
        /* Scan through all connections for the protocol */
        for (String file : PROC_NET_UDP_FILES) {
            try {
                int uid = readProcNet(file, lookup);
                if (uid != INVALID_UID) return uid;
            } catch (IOException e) {
                Log.e(TAG, "Error reading " + file + ": " + e.getMessage());
                /* continue reading files */
            }
        }

        return INVALID_UID;
    }

    public static int getConnectionOwnerUid(InetSocketAddress lookup) {
        return lookupConnection(lookup);
    }
}
