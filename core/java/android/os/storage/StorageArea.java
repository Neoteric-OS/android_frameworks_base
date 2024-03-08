/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.os.storage;

import android.annotation.NonNull;
import android.annotation.FlaggedApi;
import android.security.Flags;

import dalvik.system.CloseGuard;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Objects;

/**
 * A handle to an open storage area.
 */
@FlaggedApi(Flags.FLAG_UNLOCKED_STORAGE_API)
public class StorageArea implements Closeable {

    private final StorageManager mManager;
    private final String mName;
    private final File mDirectory;
    private boolean mClosed;

    private final CloseGuard mCloseGuard = CloseGuard.get();

    /** {@hide} */
    public StorageArea(StorageManager manager, String name, File directory) {
        mManager = Objects.requireNonNull(manager);
        mName = Objects.requireNonNull(name);
        mDirectory = Objects.requireNonNull(directory);
        // tracker to warn if a user did not call "close"
        mCloseGuard.open("close");
    }

    /** {@hide} */
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * Returns a directory that the app can read and write while the storage area is open.
     *
     * @return the directory path
     */
    @NonNull
    @FlaggedApi(Flags.FLAG_UNLOCKED_STORAGE_API)
    public File getDirectory() {
        return mDirectory;
    }

    /**
     * Closes the storage area. This complies with the {@link Closeable} interface, so that {@link
     * StorageArea} can be used with try-with-resources.
     * <p>
     * If other {@link StorageArea}s exist for the same storage area, then this method only
     * decrements an internal reference count. Otherwise, this method actually closes the storage
     * area. In this case, there must no longer be any open files in the storage area; otherwise an
     * {@link IOException} will be thrown and the storage area is not guaranteed to be securely
     * closed.
     * <p>
     * Accessing a closed storage area without first re-opening it, even in the case where an {@link
     * IOException} was thrown due to open files, has unspecified behavior.
     * <p>
     * If this method was already called, then calling it again has no effect.
     *
     * @throws IOException if the storage area failed to be properly closed
     */
    @Override
    @FlaggedApi(Flags.FLAG_UNLOCKED_STORAGE_API)
    public void close() throws IOException {
        if (!mClosed) {
            mManager.closeStorageArea(this);
            mClosed = true;
        }
    }

    /** {@hide} */
    @Override
    public void finalize() throws IOException {
        if (mCloseGuard != null) {
            mCloseGuard.warnIfOpen();
        }
        this.close();
    }
}
