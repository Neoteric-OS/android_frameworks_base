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


package android.net.dhcp;

import static com.android.internal.util.Protocol.BASE_DHCP;

/** @hide */
public class DhcpClientConstants {
    /* Commands from controller to start/stop DHCP */
    public static final int CMD_START_DHCP                  = BASE_DHCP + 1;
    public static final int CMD_STOP_DHCP                   = BASE_DHCP + 2;
    /* Notification from DHCP state machine prior to DHCP discovery/renewal */
    public static final int CMD_PRE_DHCP_ACTION             = BASE_DHCP + 3;
    /* Notification from DHCP state machine post DHCP discovery/renewal. Indicates
     * success/failure */
    public static final int CMD_POST_DHCP_ACTION            = BASE_DHCP + 4;
    /* Notification from DHCP state machine before quitting */
    public static final int CMD_ON_QUIT                     = BASE_DHCP + 5;
    /* Command from controller to indicate DHCP discovery/renewal can continue
     * after pre DHCP action is complete */
    public static final int CMD_PRE_DHCP_ACTION_COMPLETE    = BASE_DHCP + 6;
    /* Command and event notification to/from IpManager requesting the setting
     * (or clearing) of an IPv4 LinkAddress.
     */
    public static final int CMD_CLEAR_LINKADDRESS           = BASE_DHCP + 7;
    public static final int CMD_CONFIGURE_LINKADDRESS       = BASE_DHCP + 8;
    public static final int EVENT_LINKADDRESS_CONFIGURED    = BASE_DHCP + 9;
    /* Message.arg1 arguments to CMD_POST_DHCP_ACTION notification */
    public static final int DHCP_SUCCESS = 1;
    public static final int DHCP_FAILURE = 2;
}
