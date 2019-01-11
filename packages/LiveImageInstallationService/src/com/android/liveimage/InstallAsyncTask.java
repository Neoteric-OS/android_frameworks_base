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

package com.android.liveimage;

import android.os.AsyncTask;
import android.os.LiveImage;
import android.util.Log;
import android.webkit.URLUtil;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HttpsURLConnection;

class InstallAsyncTask extends AsyncTask<String, Long, Integer> {

    private static final String TAG = "InstallAsyncTask";

    private static final boolean IMAGE_IS_ZIPPED = true;

    private static final int READ_BUFFER_SIZE = 1 << 19;

    private class UnsupportedImageSourceException extends RuntimeException {
        private UnsupportedImageSourceException(String message) {
            super(message);
        }
    }

    private class NetworkErrorException extends RuntimeException {
        private NetworkErrorException(String message) {
            super(message);
        }
    }

    /** Not completed, including being cancelled */
    static final int NO_RESULT = 0;
    static final int RESULT_OK = 1;
    static final int RESULT_ERROR_IO = 2;
    static final int RESULT_ERROR_FILE_NOT_FOUND = 3;
    static final int RESULT_ERROR_UNSUPPORTED_IMAGE_SOURCE = 4;
    static final int RESULT_ERROR_NETWORK = 5;
    static final int RESULT_ERROR_EXCEPTION = 6;

    interface InstallStatusListener {
        void onProgressUpdate(long installedSize);
        void onResult(int resultCode);
        void onCancelled();
    }

    private final String mUrl;
    private final long mSystemSize;
    private final long mUserdataSize;
    private final LiveImage mLiveImage;
    private final InstallStatusListener mListener;

    private long mInstalledSize;
    private long mReportedInstalledSize;
    private int mResult = NO_RESULT;

    private HttpsURLConnection mHttpsConnection;
    private InputStream mStream;


    InstallAsyncTask(String url, long systemSize, long userdataSize, LiveImage liveImage,
                InstallStatusListener listener) {
        mUrl = url;
        mSystemSize = systemSize;
        mUserdataSize = userdataSize;
        mLiveImage = liveImage;
        mListener = listener;
    }

    @Override
    protected void onPreExecute() {
        mListener.onProgressUpdate(0);
    }

    @Override
    protected Integer doInBackground(String... voids) {
        Log.d(TAG, "Start doInBackground(), URL: " + mUrl);

        try {
            // call start in background
            mLiveImage.start(mSystemSize, mUserdataSize);

            initInputStream();

            byte[] bytes = new byte[READ_BUFFER_SIZE];

            int numBytesRead;
            long minStepToReport = mSystemSize / 100;

            Log.d(TAG, "Start installation loop");
            while ((numBytesRead = mStream.read(bytes, 0, READ_BUFFER_SIZE)) != -1) {
                if (isCancelled()) {
                    break;
                }

                byte[] writeBuffer = numBytesRead == READ_BUFFER_SIZE ?
                        bytes :Arrays.copyOf(bytes, numBytesRead);

                if (!mLiveImage.write(writeBuffer)) {
                    throw new IOException("Failed write() to LiveImage");
                }

                mInstalledSize += numBytesRead;

                if (mInstalledSize > mReportedInstalledSize + minStepToReport) {
                    publishProgress(mInstalledSize);
                    mReportedInstalledSize = mInstalledSize;
                }
            }

            return RESULT_OK;

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return RESULT_ERROR_FILE_NOT_FOUND;

        } catch (IOException e) {
            e.printStackTrace();
            return RESULT_ERROR_IO;

        } catch (UnsupportedImageSourceException e) {
            e.printStackTrace();
            return RESULT_ERROR_UNSUPPORTED_IMAGE_SOURCE;

        } catch (NetworkErrorException e) {
            e.printStackTrace();
            return RESULT_ERROR_NETWORK;

        } catch (Exception e) {
            e.printStackTrace();
            return RESULT_ERROR_EXCEPTION;

        } finally {
            close();
        }
    }

    @Override
    protected void onCancelled() {
        Log.d(TAG, "onCancelled(), URL: " + mUrl);

        close();

        mListener.onCancelled();
    }

    @Override
    protected void onPostExecute(Integer result) {
        Log.d(TAG, "onPostExecute(), URL: " + mUrl + ", result: " + result);

        close();

        mResult = result;
        mListener.onResult(mResult);
    }

    @Override
    protected void onProgressUpdate(Long... values) {
        long progress = values[0];
        mListener.onProgressUpdate(progress);
    }

    private void initInputStream() throws IOException, UnsupportedImageSourceException {
        /*
        if (URLUtil.isNetworkUrl(mUrl)) {
            initNetworkInputStream();
        } else if (URLUtil.isFileUrl(mUrl)) {
            initFileInputStream();
        } else {
            throw new UnsupportedImageSourceException(
                    String.format(Locale.US, "Unsupported file source: %s", mUrl));
        }

        if (IMAGE_IS_ZIPPED) {
            mStream = new GZIPInputStream(mStream);
        }

        mStream = new BufferedInputStream(mStream);
        */
        mStream = new BufferedInputStream(new GZIPInputStream(new URL(mUrl).openStream()));
    }

    private void initNetworkInputStream() {
        try {
            URL url = new URL(mUrl);

            mHttpsConnection = (HttpsURLConnection) url.openConnection();
            mHttpsConnection.setReadTimeout(3000);
            mHttpsConnection.setConnectTimeout(3000);
            mHttpsConnection.setRequestMethod("GET");
            mHttpsConnection.setDoInput(true);
            mHttpsConnection.connect();

            int responseCode = mHttpsConnection.getResponseCode();

            if (responseCode != HttpsURLConnection.HTTP_OK) {
                throw new NetworkErrorException("HTTP code: " + responseCode + ", URL: " + mUrl);
            }

            mStream = mHttpsConnection.getInputStream();
        } catch (NetworkErrorException e) {
            throw e;
        } catch (Exception e) {
            throw new NetworkErrorException("Network error while accessing " + mUrl);
        }
    }

    private void initFileInputStream() throws FileNotFoundException {
        mStream = new FileInputStream(mUrl);
    }

    private void close() {
        try {
            if (mStream != null) {
                mStream.close();
                mStream = null;
            }
        } catch (IOException e) {
            // ignore
        }

        if (mHttpsConnection != null) {
            mHttpsConnection.disconnect();
            mHttpsConnection = null;
        }
    }

    int getResult() {
        return mResult;
    }
}
