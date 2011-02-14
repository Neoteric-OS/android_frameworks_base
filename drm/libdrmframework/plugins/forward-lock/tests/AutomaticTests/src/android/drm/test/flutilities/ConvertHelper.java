/*
 * Copyright (C) 2010 The Android Open Source Project
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
package android.drm.test.flutilities;

import android.content.Context;
import android.drm.DrmConvertedStatus;
import android.drm.DrmManagerClient;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Converts Oma drm v1 message file to internal format.
 */
public class ConvertHelper {
private DrmManagerClient mDrmManagerClient;
private int mConvertSessionID = -1;
    public ConvertHelper(DrmManagerClient drmManagerClient) {
        mDrmManagerClient = drmManagerClient;
    }

   /**
    * Start of converting a file.
    *
    * @param context The context of the application running the convert
    *        session.
    * @param mimeType Mimetype of content that shall be converted.
    * @return a convert session identifier or -1 incase an error occurs.
    */
    public int startConvert(String mimeType) {
        if (mimeType != null && !mimeType.equals("")) {
            if (mDrmManagerClient != null) {
                mConvertSessionID = mDrmManagerClient.openConvertSession(mimeType);
            }
        }
        return mConvertSessionID;
    }

    /**
     * Convert a buffer of data to internal format.
     *
     * @param context The context of the application running the convert
     *            session.
     * @param convertSessionId The convert session identifier.
     * @param buffer Buffer filled with data to convert.
     * @param size The number of bytes that shall be converted.
     * @return A Buffer filled with converted data, if execution is ok, in all
     *         other case null.
     */
    public byte[] convert(byte[] buffer, int size) {
        byte[] result = null;
        if (mConvertSessionID >= 0 && buffer != null) {
            DrmConvertedStatus convertedStatus = null;
            if (size != buffer.length) {
                byte[] buf = new byte[size];
                System.arraycopy(buffer, 0, buf, 0, size);
                convertedStatus = mDrmManagerClient.convertData(mConvertSessionID, buf);
            } else {
                convertedStatus = mDrmManagerClient.convertData(mConvertSessionID, buffer);
            }

            if (convertedStatus != null) {
                if (convertedStatus.statusCode == DrmConvertedStatus.STATUS_OK) {
                    if (convertedStatus.convertedData != null) {
                        result = convertedStatus.convertedData;
                    }
                }
            }
        }
        return result;
    }

    /**
     * Ends a conversion session of a file.
     *
     * @param context The context of the application running the convert session.
     * @param fileName The filename of the converted file.
     * @return 0 on success -1 on failure.
     * @throws FileNotFoundException if fileName could not be found.
     * @throws IOException if file filename could not be read.
     * @throws SecurityException if system does not allow access to file filename.
     */
    public int stopConvert(String fileName) throws
            FileNotFoundException, IOException, SecurityException {
        DrmConvertedStatus convertedStatus = null;
        int result = -1;
        if (mConvertSessionID >= 0) {
            convertedStatus = mDrmManagerClient.closeConvertSession(mConvertSessionID);
            if (convertedStatus != null) {
                if (convertedStatus.statusCode == DrmConvertedStatus.STATUS_OK) {
                    if (convertedStatus.convertedData != null) {
                        // write signature.
                        RandomAccessFile rndAccessFile = null;
                        try {
                            rndAccessFile = new RandomAccessFile(fileName, "rw");
                            rndAccessFile.seek(convertedStatus.offset);
                            rndAccessFile.write(convertedStatus.convertedData);
                            result = 0;
                        } finally {
                            if (rndAccessFile != null) {
                                rndAccessFile.close();
                                rndAccessFile = null;
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * Create a filename for a Forward Lock file of internal format
     * based on a filename of a Oma DRM v1 message file.
     *
     * @param filename Filename of a oma drm message file.
     * @return Filename for converted file.
     */
    public String createFileName(String filename) {
        if (filename != null) {

            int extensionIndex;
            extensionIndex = filename.lastIndexOf(".");
            if (extensionIndex != -1) {
                filename = filename.substring(0, extensionIndex);

            }
            filename = filename.concat(".fl");
        }
        return filename;
    }

    /**
     * Destroys the convert helper.
     * Releases DrmManagerClient
     * Closes the convert session(if not already closed)
     */
    public void destroyConvertHelper() {
        if (mConvertSessionID >= 0) {
            if(mDrmManagerClient != null) {
                mDrmManagerClient.closeConvertSession(mConvertSessionID);
            }
        }
    }

}
