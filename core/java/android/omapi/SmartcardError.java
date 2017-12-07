/*
 * Copyright (C) 2017 The Android Open Source Project
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
/*
 * Copyright (c) 2015-2017, The Linux Foundation.
 */
/*
 * Contributed by: Giesecke & Devrient GmbH.
 */

package android.omapi;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.io.IOException;
import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * Smartcard service parameter class used to marshal exception information from
 * the smartcard service to clients.
 */
public class SmartcardError implements Parcelable {

    /**
     * The allowed exceptions as per OMAPI.
     */
    private static final Class[] ALLOWED_EXCEPTIONS = {
            java.io.IOException.class,
            java.lang.SecurityException.class,
            java.util.NoSuchElementException.class,
            java.lang.IllegalStateException.class,
            java.lang.IllegalArgumentException.class,
            java.lang.UnsupportedOperationException.class,
            java.lang.NullPointerException.class
    };

    public static final Parcelable.Creator<SmartcardError> CREATOR =
            new Parcelable.Creator<SmartcardError>() {
                public SmartcardError createFromParcel(Parcel in) {
                    return new SmartcardError(in);
            }

            public SmartcardError[] newArray(int size) {
                return new SmartcardError[size];
            }
        };

    /**
     * The class of the exception.
     */
    private String mClaz;

    /**
     * The message of the exception.
     */
    private String mMessage;

    /**
     * Creates an empty smartcard error container.
     */
    public SmartcardError() {
        this.mClaz = "";
        this.mMessage = "";
    }

    /**
     * Creates a Smartcard error from a Parcel
     *
     * @param in The Parcel that contains the information.
     */
    private SmartcardError(Parcel in) {
        readFromParcel(in);
    }

    /**
     * Sets the error to a given exception and message.
     *
     * @param e The excpetion to be thrown
     *
     * @throws IllegalArgumentException If the given class is not a valid according to OMAPI.
     */
    public void set(Exception e) throws IllegalArgumentException {
        if (e == null) {
            throw new IllegalArgumentException("Cannot set a null exception");
        }
        Class clazz = e.getClass();
        if (!Arrays.asList(ALLOWED_EXCEPTIONS).contains(clazz)) {
            throw new IllegalArgumentException("Unexpected exception class: "
                + clazz.getCanonicalName());
        }
        mClaz = clazz.getCanonicalName();
        mMessage = e.getMessage() != null ? e.getMessage() : "";
    }

    /**
     * @return true if this error has been set, false otherwise.
     */
    public boolean isSet() {
        return mClaz != null && !mClaz.isEmpty();
    }

    /**
     * Sets the error information.
     *
     * @param clazz the exception class. <code>null</code> to reset the error
     *            information.
     * @param message the exception message.
     */
    @SuppressWarnings({ "rawtypes" })
    public void setError(Class clazz, String message) {
        this.mClaz = (clazz == null) ? "" : clazz.getName();
        this.mMessage = (message == null) ? "" : message;
    }

    /**
     * Throws the exception this object represents.
     *
     * @throws IOException
     * @throws SecurityException
     * @throws NoSuchElementException
     * @throws IllegalStateException
     * @throws IllegalArgumentException
     * @throws UnsupportedOperationException
     * @throws NullPointerException
     */
    public void throwException() throws
            IOException,
            SecurityException,
            NoSuchElementException,
            IllegalStateException,
            IllegalArgumentException,
            UnsupportedOperationException,
            NullPointerException {
        if (mClaz.equals(java.io.IOException.class.getCanonicalName())) {
            throw new IOException(mMessage);
        } else if (mClaz.equals(java.lang.SecurityException.class.getCanonicalName())) {
            throw new SecurityException(mMessage);
        } else if (mClaz.equals(java.security.AccessControlException.class.getCanonicalName())) {
            throw new SecurityException(mMessage);
        } else if (mClaz.equals(java.util.NoSuchElementException.class.getCanonicalName())) {
            throw new NoSuchElementException(mMessage);
        } else if (mClaz.equals(java.lang.IllegalStateException.class.getCanonicalName())) {
            throw new IllegalStateException(mMessage);
        } else if (mClaz.equals(java.lang.IllegalArgumentException.class.getCanonicalName())) {
            throw new IllegalArgumentException(mMessage);
        } else if (mClaz.equals(java.lang.UnsupportedOperationException.class.getCanonicalName())) {
            throw new UnsupportedOperationException(mMessage);
        } else if (mClaz.equals(java.lang.NullPointerException.class.getCanonicalName())) {
            throw new NullPointerException(mMessage);
        } else {
            Log.wtf(getClass().getSimpleName(),
                    "SmartcardError.throwException() finished without throwing exception. mClazz: "
                    + mClaz);
        }
    }

    /**
     * Required implementation of describeContents for Parcelable.
     * There are no special object types in this instance.
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /** Flatten SmartcardError instance to a Parcel. */
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mClaz);
        out.writeString(mMessage);
    }

    /** Extract SmartcardError from a Parcel. */
    public void readFromParcel(Parcel in) {
        mClaz = in.readString();
        mMessage = in.readString();
    }
}
