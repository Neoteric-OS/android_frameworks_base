/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.net;

import android.annotation.Nullable;

import java.io.IOException;

/**
 * IcmpException represents an ICMP error.
 *
 * <p>This exception should be thrown when an unrecoverable error is encountered while sending ICMP
 * datagrams.
 *
 * @see <a href="https://www.iana.org/assignments/icmp-parameters/icmp-parameters.xhtml">ICMP
 *     Parameters</a>
 */
public class IcmpException extends IOException {
    public final int type;
    public final int code;

    /**
     * Constructs an IcmpException instance.
     *
     * @param type The ICMP type for this ICMP error
     * @param code The ICMP code for this ICMP error
     */
    public IcmpException(int type, int code) {
        this(type, code, "");
    }

    /**
     * Constructs an IcmpException instance.
     *
     * @param type The ICMP type for this ICMP error
     * @param code The ICMP code for this ICMP error
     * @param msg The message for this Exception
     */
    public IcmpException(int type, int code, @Nullable String msg) {
        this(type, code, msg, null);
    }

    /**
     * Constructs an IcmpException instance.
     *
     * @param type The ICMP type for this ICMP error
     * @param code The ICMP code for this ICMP error
     * @param msg The message for this Exception
     * @param cause The Throwable that caused this Exception to be thrown
     */
    public IcmpException(int type, int code, @Nullable String msg, @Nullable Throwable cause) {
        super(msg, cause);
        this.type = type;
        this.code = code;
    }
}
