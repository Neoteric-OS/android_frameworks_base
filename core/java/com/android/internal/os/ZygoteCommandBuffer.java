/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.internal.os;

import android.annotation.Nullable;
import android.net.LocalSocket;

import java.io.EOFException;
import java.io.FileDescriptor;
import java.lang.ref.Reference;  // For reachabilityFence.

/**
 * A native-accessible buffer for Zygote commands.
 * A ZygoteCommandBuffer may have an associated socket from which it can be refilled.
 * Otherwise the contents are explicitly set by getInstance().
 *
 * NOT THREAD-SAFE. No methods may be called concurrently from multiple threads.
 *
 * Only one ZygoteCommandBuffer can exist at a time.
 * Must be explicitly closed before being dropped.
 * All reader functions throw on EOF.
 * @hide
 */
class ZygoteCommandBuffer implements AutoCloseable {
    private static final String TAG = "Zygote";

    private static ZygoteCommandBuffer sTheCommandBuffer = null;

    private long mNativeBuffer;  // Not final so that we can clear it in close().

    /**
     * The command socket.
     *
     * mSocket is retained in the child process in "peer wait" mode, so
     * that it closes when the child process terminates. In other cases,
     * it is closed in the peer.
     */
    private final LocalSocket mSocket;
    private final int mNativeSocket;

    /**
     * Constructs instance from file descriptor from which the command will be read.
     * Only a single instance may be live in a given process. The native code checks.
     *
     * @param fd file descriptor to read from. The setCommand() method may be used if and only if
     * fd is null.
     */
    private ZygoteCommandBuffer(@Nullable LocalSocket socket) {
        mSocket = socket;
        if (socket == null) {
            mNativeSocket = -1;
        } else {
            mNativeSocket = mSocket.getFileDescriptor().getInt$();
        }
        mNativeBuffer = getNativeBuffer(mNativeSocket);
    }

    private static native long getNativeBuffer(int fd);

    // ???? We probably no longer need all the factory methods. Just use constructors?

    /**
     * Return a ZygoteCommandBuffer associated with the given socket. Only one such buffer
     * may be open at a time.
     */
    static ZygoteCommandBuffer getInstance(LocalSocket commandSocket) {
        if (sTheCommandBuffer != null) {
            throw new AssertionError("Tried to get second ZygoteCommandBuffer.");
        } else {
            sTheCommandBuffer = new ZygoteCommandBuffer(commandSocket);
        }
        return sTheCommandBuffer;
    }

    /**
     * Return a ZygoteCommandBuffer containing the given arguments.
     * Currently assumes that no other ZygoteCommandBuffer is in use. Should be closed
     * before another one is created.
     */
    static ZygoteCommandBuffer getInstance(String[] args) {
        if (sTheCommandBuffer != null) {
            throw new AssertionError(
                    "Tried to create explicit ZygoteCommandBuffer with one already open");
        }
        sTheCommandBuffer = new ZygoteCommandBuffer(null);
        sTheCommandBuffer.setCommand(args);
        return sTheCommandBuffer;
    }

    /**
     * Deallocate native resources associated with the one and only command buffer, and prevent
     * reuse. Subsequent calls to getInstance() will yield a new buffer.
     * We do not close the associated socket, if any.
     */
    @Override
    public void close() {
        freeNativeBuffer(mNativeBuffer);
        mNativeBuffer = 0;
        sTheCommandBuffer = null;
    }

    private static native void freeNativeBuffer(long /* NativeCommandBuffer* */ nbuffer);

    /**
     * Read at least the first line of the next command into the buffer, return the argument count
     * from that line. Assumes we are initially positioned at the beginning of the first line of
     * the command. Leave the buffer positioned at the beginning of the second command line, i.e.
     * the first argument. If the buffer has no associated file descriptor, we just reposition to
     * the beginning of the buffer, and reread existing contents.  Returns zero if we started out
     * at EOF.
     */
    int getCount() throws EOFException {
        try {
            return retrieveCount(mNativeBuffer);
        } finally {
            // Make sure the mNativeSocket doesn't get closed due to early finalization.
            Reference.reachabilityFence(mSocket);
        }
    }

    private static native int retrieveCount(long /* NativeCommandBuffer* */ nbuffer)
            throws EOFException;


    /*
     * Set the buffer to contain the supplied sequence of arguments.
     */
    private void setCommand(String[] command) {
        int nArgs = command.length;
        insert(mNativeBuffer, Integer.toString(nArgs));
        for (String s: command) {
            insert(mNativeBuffer, s);
        }
        // Native code checks there is no socket; hence no reachabilityFence.
    }

    private static native void insert(long /* NativeCommandBuffer* */ nbuffer, String s);

    /**
     * Retrieve the next argument/line from the buffer, filling the buffer as necessary.
     */
    public String nextArg() throws EOFException {
        try {
            return nextLine(mNativeBuffer);
        } finally {
            Reference.reachabilityFence(mSocket);
        }
    }

    private static native String nextLine(long /* NativeCommandBuffer* */ nbuffer)
            throws EOFException;


    public void readFullyAndReset() throws EOFException {
        try {
            readAllLinesAndReset(mNativeBuffer);
        } finally {
            Reference.reachabilityFence(mSocket);
        }
    }

    private static native void readAllLinesAndReset(long /* NativeCommandBuffer* */ nbuffer)
            throws EOFException;


    /**
     * Fork a child as specified by the current command in the buffer, and repeat this process
     * after refilling the buffer, so long as the buffer clearly contains another fork command.
     * @return 0 in the child. In the parent: -1 if current command in the buffer still needs
     * to be processed, -2 if something forced us to stop but everything in the buffer has been
     * processed. In the -1 case, the buffer is positioned at the beginning of the command that
     * still needs to be processed.
     */
    public int forkRepeatedly(FileDescriptor zygoteSocket,
                                         int expectedUid,
                                         int minUid) {
        try {
            return forkMany(mNativeBuffer, zygoteSocket.getInt$(), expectedUid, minUid);
        } finally {
            Reference.reachabilityFence(mSocket);
            Reference.reachabilityFence(zygoteSocket);
        }
    }

    /*
     * Repeatedly fork children as above.
     * @return Zero in each child. In the parent: -1 if we couldn't understand the current
     * command in nbuffer, -2 if the current command in the buffer was processed, but something went
     * wrong refilling the buffer.
     */
    private static native int forkMany(long /* NativeCommandBuffer* */ nbuffer,
                                       int zygoteSocketRawFd,
                                       int expectedUid,
                                       int minUid);

}
