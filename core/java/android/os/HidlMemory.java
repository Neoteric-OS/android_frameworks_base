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

package android.os;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.annotation.TestApi;

import com.android.internal.util.Preconditions;

import java.io.Closeable;
import java.io.IOException;

/**
 * An abstract representation of a memory block, as representing by the HIDL system.
 *
 * The block is defined by a {name, size, handle} tuple, where the name is used to determine how to
 * interpret the handle.
 *
 * @hide
 */
@SystemApi
@TestApi
public class HidlMemory implements Closeable {
    private final @NonNull String mName;
    private final long mSize;
    private final @Nullable NativeHandle mHandle;
    private long mNativeContext;  // For use of native code.

    /**
     * Constructor.
     *
     * @param name      The name (determined how to interpret the handle).
     * @param size      The size in bytes of the memory block.
     * @param handle    The handle. May be null. This instance will own the handle and will close it
     *                  as soon as {@link #close()} is called or the object is destroyed. This, this
     *                  handle instance should generally not be shared with other clients.
     */
    public HidlMemory(@NonNull String name, long size, @Nullable NativeHandle handle) {
        mName = Preconditions.checkNotNull(name);
        mSize = Preconditions.checkArgumentNonnegative(size);
        mHandle = handle;
    }

    /**
     * Create a copy of this instance, where the underlying file descriptors have been duplicated.
     * It is the responsibility of the caller to also call {@link #close()} in this case.
     */
    @NonNull
    public HidlMemory dup() throws IOException {
        return new HidlMemory(mName, mSize, mHandle != null ? mHandle.dup() : null);
    }

    /**
     * Close the underlying native handle. Only call this if this instance has been created with
     * {@link #dup()}.
     */
    @Override
    public void close() throws IOException {
        if (mHandle != null) {
            mHandle.close();
        }
    }

    /**
     * Disowns the underlying handle and returns it. This object becomes invalid. Only call this
     * if this instance has been created with {@link #dup()}.
     *
     * @return The underlying handle.
     */
    @NonNull
    public NativeHandle releaseHandle() {
        return mHandle;
    }

    /**
     * Gets the name, which represents how the handle is to be interpreted.
     *
     * @return The name.
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * Gets the size of the block, in bytes.
     *
     * @return The size.
     */
    public long getSize() {
        return mSize;
    }

    /**
     * Gets a native handle. The actual interpretation depends on the name and is implementation
     * defined.
     *
     * @return The native handle.
     */
    @Nullable
    public NativeHandle getHandle() {
        return mHandle;
    }

    @Override
    protected void finalize() {
        try {
            close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            nativeFinalize();
        }
    }

    private native void nativeFinalize();
}
