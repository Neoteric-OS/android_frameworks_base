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
 * Icmpv6Exception represents an ICMPv6 error.
 *
 * <p>This exception should be thrown when an unrecoverable error is encountered while sending
 * ICMPv6 datagrams.
 *
 * @see <a href="https://www.iana.org/assignments/icmpv6-parameters/icmpv6-parameters.xhtml">ICMPv6
 *     Parameters</a>
 */
public class Icmpv6Exception extends IOException {
    public final int type;
    public final int code;

    /**
     * Constructs an Icmpv6Exception instance.
     *
     * @param type The ICMPv6 type for this ICMPv6 error
     * @param code The ICMPv6 code for this ICMPv6 error
     */
    public Icmpv6Exception(int type, int code) {
        this(type, code, "");
    }

    /**
     * Constructs an Icmpv6Exception instance.
     *
     * @param type The ICMPv6 type for this ICMPv6 error
     * @param code The ICMPv6 code for this ICMPv6 error
     * @param msg The message for this Exception
     */
    public Icmpv6Exception(int type, int code, @Nullable String msg) {
        this(type, code, "", null);
    }

    /**
     * Constructs an Icmpv6Exception instance.
     *
     * @param type The ICMPv6 type for this ICMPv6 error
     * @param code The ICMPv6 code for this ICMPv6 error
     * @param msg The message for this Exception
     * @param cause The Throwable that caused this Exception to be thrown
     */
    public Icmpv6Exception(int type, int code, @Nullable String msg, @Nullable Throwable cause) {
        super(msg, cause);
        this.type = type;
        this.code = code;
    }
}
