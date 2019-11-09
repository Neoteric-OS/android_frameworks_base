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

package com.android.dynsystem;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.MemoryFile;
import android.os.ParcelFileDescriptor;
import android.os.image.DynamicSystemManager;
import android.util.Log;
import android.webkit.URLUtil;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

class InstallationAsyncTask extends AsyncTask<String, InstallationAsyncTask.Progress, Throwable> {

    private static final String TAG = "InstallationAsyncTask";
    private static final long PROGRESS_UPDATE_MS = 100;
    private static final int READ_BUFFER_SIZE = 1 << 13;
    private static final long MIN_PROGRESS_TO_PUBLISH = 1 << 23;

    private static final List<String> UNSUPPORTED_PARTITIONS =
            Arrays.asList("vbmeta", "boot", "userdata", "dtbo", "super_empty", "system_other");

    private class UnsupportedUrlException extends RuntimeException {
        private UnsupportedUrlException(String message) {
            super(message);
        }
    }

    private class UnsupportedFormatException extends RuntimeException {
        private UnsupportedFormatException(String message) {
            super(message);
        }
    }

    /** UNSET means the installation is not completed */
    static final int RESULT_UNSET = 0;

    static final int RESULT_OK = 1;
    static final int RESULT_CANCELLED = 2;
    static final int RESULT_ERROR_IO = 3;
    static final int RESULT_ERROR_UNSUPPORTED_URL = 4;
    static final int RESULT_ERROR_UNSUPPORTED_FORMAT = 5;
    static final int RESULT_ERROR_EXCEPTION = 6;

    class Progress {
        String mPartitionName;
        long mPartitionSize;
        long mInstalledSize;

        int mNumInstalledPartitions;

        Progress(String partitionName, long partitionSize, long installedSize, int numInstalled) {
            mPartitionName = partitionName;
            mPartitionSize = partitionSize;
            mInstalledSize = installedSize;

            mNumInstalledPartitions = numInstalled;
        }
    }

    interface ProgressListener {
        void onProgressUpdate(Progress progress);

        void onResult(int resultCode, Throwable detail);
    }

    private final String mUrl, mDsuSlot;
    private final long mSystemSize;
    private final long mUserdataSize;
    private final Context mContext;
    private final DynamicSystemManager mDynSystem;
    private final ProgressListener mListener;
    private DynamicSystemManager.Session mInstallationSession;

    private boolean mIsCompleted;

    InstallationAsyncTask(
            String url,
            String dsuSlot,
            long systemSize,
            long userdataSize,
            Context context,
            DynamicSystemManager dynSystem,
            ProgressListener listener) {
        mUrl = url;
        mDsuSlot = dsuSlot;
        mSystemSize = systemSize;
        mUserdataSize = userdataSize;
        mContext = context;
        mDynSystem = dynSystem;
        mListener = listener;
    }

    @Override
    protected Throwable doInBackground(String... voids) {
        Log.d(TAG, "Start doInBackground(), URL: " + mUrl);

        InputStream is = null;

        try {
            mDynSystem.remove();
            // init input stream before creating userdata, which takes 90 seconds.
            is = initInputStream();
            mDynSystem.startInstallation(mDsuSlot);
            installUserdata();

            if (isCancelled()) {
                return null;
            }
            if (mUrl == null) {
                mDynSystem.finishInstallation();
                return null;
            }
            String extension = mUrl.substring(mUrl.lastIndexOf('.') + 1);

            if ("gz".equals(extension)) {
                installImage("system", mSystemSize, new GZIPInputStream(is), 1);
            } else if ("zip".equals(extension)) {
                ZipInputStream zis = new ZipInputStream(is);
                ZipEntry zipEntry = null;

                int numInstalledPartitions = 1;

                while ((zipEntry = zis.getNextEntry()) != null) {
                    String name = zipEntry.getName();

                    if (!name.endsWith(".img")) {
                        continue;
                    }

                    String partitionName = name.substring(0, name.length() - 4);

                    if (UNSUPPORTED_PARTITIONS.contains(partitionName)) {
                        Log.i(TAG, "Skip: " + partitionName);
                        continue;
                    }
                    SparseInputStream in = new SparseInputStream(new BufferedInputStream(zis));
                    long partitionSize = in.getUnsparseSize();
                    Log.i(TAG, partitionName + " size from SparseInputStream:" + partitionSize);
                    if (partitionSize < 0) {
                        partitionSize = zipEntry.getSize();
                    }
                    Log.i(TAG, partitionName + " size from zip:" + partitionSize);
                    if (partitionSize < -1) {
                        throw new IOException(
                                "Cannot get uncompressed file size for" + partitionName);
                    }

                    installImage(partitionName, partitionSize, in, numInstalledPartitions);

                    if (isCancelled()) {
                        break;
                    }

                    numInstalledPartitions++;
                }
            } else {
                throw new UnsupportedFormatException(
                        String.format(Locale.US, "Unsupported file format: %s", mUrl));
            }
            mDynSystem.finishInstallation();
            return null;

        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return e;
        } finally {
            close(is);
        }
    }

    private void installUserdata() throws Exception {
        Thread thread =
                new Thread(
                        () -> {
                            mInstallationSession =
                                    mDynSystem.createPartition("userdata", mUserdataSize, true);
                        });

        Log.d(TAG, "Creating partition: userdata");
        thread.start();

        long installedSize = 0;
        Progress progress = new Progress("userdata", mUserdataSize, installedSize, 0);

        while (thread.isAlive()) {
            if (isCancelled()) {
                return;
            }

            installedSize = mDynSystem.getInstallationProgress().bytes_processed;

            if (installedSize > progress.mInstalledSize + MIN_PROGRESS_TO_PUBLISH) {
                progress.mInstalledSize = installedSize;
                publishProgress(progress);
            }

            Thread.sleep(PROGRESS_UPDATE_MS);
        }
        Log.d(TAG, "userdata created");
        if (mInstallationSession == null) {
            throw new IOException(
                    "Failed to start installation with requested size: " + mUserdataSize);
        }
    }

    private void installImage(
            String partitionName, long partitionSize, InputStream is, int numInstalledPartitions)
            throws Exception {
        Thread thread =
                new Thread(
                        () -> {
                            mInstallationSession =
                                    mDynSystem.createPartition(partitionName, partitionSize, true);
                        });

        Log.d(TAG, "Creating partition: " + partitionName);
        thread.start();

        while (thread.isAlive()) {
            if (isCancelled()) {
                return;
            }

            Thread.sleep(PROGRESS_UPDATE_MS);
        }

        Log.d(TAG, partitionName + " created");
        if (mInstallationSession == null) {
            throw new IOException(
                    "Failed to start installation with requested size: " + partitionSize);
        }

        Log.d(TAG, "Start installation: " + partitionName);

        MemoryFile memoryFile = new MemoryFile("dsu_" + partitionName, READ_BUFFER_SIZE);
        ParcelFileDescriptor pfd = new ParcelFileDescriptor(memoryFile.getFileDescriptor());

        mInstallationSession.setAshmem(pfd, READ_BUFFER_SIZE);

        long installedSize = 0;
        Progress progress =
                new Progress(partitionName, partitionSize, installedSize, numInstalledPartitions);

        byte[] bytes = new byte[READ_BUFFER_SIZE];
        int numBytesRead;

        BufferedInputStream bis = new BufferedInputStream(is);

        while ((numBytesRead = bis.read(bytes, 0, READ_BUFFER_SIZE)) != -1) {
            if (isCancelled()) {
                return;
            }

            memoryFile.writeBytes(bytes, 0, 0, numBytesRead);

            if (!mInstallationSession.submitFromAshmem(numBytesRead)) {
                throw new IOException("Failed write() to DynamicSystem");
            }

            installedSize += numBytesRead;

            if (installedSize > progress.mInstalledSize + MIN_PROGRESS_TO_PUBLISH) {
                progress.mInstalledSize = installedSize;
                publishProgress(progress);
            }
        }

        Log.d(TAG, partitionName + " is installed");
    }

    @Override
    protected void onCancelled() {
        Log.d(TAG, "onCancelled(), URL: " + mUrl);

        if (mDynSystem.abort()) {
            Log.d(TAG, "Installation aborted");
        } else {
            Log.w(TAG, "DynamicSystemManager.abort() returned false");
        }
        mDynSystem.remove();
        mListener.onResult(RESULT_CANCELLED, null);
    }

    @Override
    protected void onPostExecute(Throwable detail) {
        int result = RESULT_UNSET;

        if (detail == null) {
            result = RESULT_OK;
            mIsCompleted = true;
        } else if (detail instanceof IOException) {
            result = RESULT_ERROR_IO;
        } else if (detail instanceof UnsupportedUrlException) {
            result = RESULT_ERROR_UNSUPPORTED_URL;
        } else if (detail instanceof UnsupportedFormatException) {
            result = RESULT_ERROR_UNSUPPORTED_FORMAT;
        } else {
            result = RESULT_ERROR_EXCEPTION;
        }

        Log.d(TAG, "onPostExecute(), URL: " + mUrl + ", result: " + result);

        mListener.onResult(result, detail);
    }

    @Override
    protected void onProgressUpdate(Progress... values) {
        Progress progress = values[0];
        mListener.onProgressUpdate(progress);
    }

    private InputStream initInputStream() throws IOException, UnsupportedUrlException {
        if (URLUtil.isNetworkUrl(mUrl) || URLUtil.isFileUrl(mUrl)) {
            return new URL(mUrl).openStream();
        } else if (URLUtil.isContentUrl(mUrl)) {
            return mContext.getContentResolver().openInputStream(Uri.parse(mUrl));
        } else {
            throw new UnsupportedUrlException(
                    String.format(Locale.US, "Unsupported file source: %s", mUrl));
        }
    }

    private void close(InputStream is) {
        try {
            if (is != null) {
                is.close();
            }
        } catch (IOException e) {
            // ignore
        }
    }

    boolean isCompleted() {
        return mIsCompleted;
    }

    boolean commit() {
        return mDynSystem.setEnable(true, true);
    }
}
