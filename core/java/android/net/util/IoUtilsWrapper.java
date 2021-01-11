/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.net.util;

import android.annotation.Nullable;
import android.annotation.SystemApi;

import libcore.io.IoUtils;

import java.io.FileDescriptor;
import java.io.IOException;

/**
 * Wrapper around libcore.io.IoUtils.
 *
 * @hide
 */
@SystemApi
public final class IoUtilsWrapper {

    private IoUtilsWrapper() {}

    /** Closes 'closeable', ignoring any checked exceptions. Does nothing if 'closeable' is null. */
    public static void closeQuietly(@Nullable AutoCloseable closeable) {
        IoUtils.closeQuietly(closeable);
    }

    /** Closes 'fd', ignoring any exceptions. Does nothing if 'fd' is null or invalid. */
    public static void closeQuietly(@Nullable FileDescriptor fd) {
        IoUtils.closeQuietly(fd);
    }

    // /** Closes 'socket', ignoring any exceptions. Does nothing if 'socket' is null. */
    // public static void closeQuietly(Socket socket) {
    //     socket.closeQuietly(socket);
    // }

    /** Sets 'fd' to be blocking or non-blocking, according to the state of 'blocking'. */
    public static void setBlocking(@Nullable FileDescriptor fd, boolean blocking)
            throws IOException {
        IoUtils.setBlocking(fd, blocking);
    }
}
