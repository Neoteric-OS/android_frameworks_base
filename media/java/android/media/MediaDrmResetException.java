/*
 * Copyright 2015 The Android Open Source Project
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

package android.media;

/**
 * This exception is thrown when the MediaDrm instance has become unusable
 * due to a restart of the mediaserver process.  To continue, the app must
 * release the MediaDrm object, then create and initialize a new one.
 */
public class MediaDrmResetException extends IllegalStateException {
    public MediaDrmResetException(String detailMessage) {
        this(detailMessage, 0, 0, 0);
    }

    /**
     * @hide
     */
    public MediaDrmResetException(String message, int vendorError, int oemError, int errorContext) {
        super(message);
        mVendorError = vendorError;
        mOemError = oemError;
        mErrorContext = errorContext;
    }

    /**
     * Returns {@link MediaDrm} plugin vendor defined error code associated with this {@link
     * MediaDrmResetException}.
     * <p>
     * Please consult the {@link MediaDrm} plugin vendor for details on the error code.
     *
     * @return an error code defined by the {@link MediaDrm} plugin vendor if available,
     * otherwise 0.
     */
    public int getVendorError() {
        return mVendorError;
    }

    /**
     * Returns OEM or SOC specific error code associated with this {@link
     * MediaDrmResetException}.
     * <p>
     * Please consult the {@link MediaDrm} plugin, chip, or device vendor for details on the
     * error code.
     *
     * @return an OEM or SOC specific error code if available, otherwise 0.
     */
    public int getOemError() {
        return mOemError;
    }

    /**
     * Returns {@link MediaDrm} plugin vendor defined error context associated with this {@link
     * MediaDrmResetException}.
     * <p>
     * Please consult the {@link MediaDrm} plugin vendor for details on the error context.
     *
     * @return an opaque integer that would help the @{@link MediaDrm} vendor locate the
     * source of the error if available, otherwise 0.
     */
    public int getErrorContext() {
        return mErrorContext;
    }

    private final int mVendorError, mOemError, mErrorContext;
}
