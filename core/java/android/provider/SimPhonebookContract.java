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

package android.provider;

import static android.provider.SimPhonebookContract.EntityFiles.EF_ADN;
import static android.provider.SimPhonebookContract.EntityFiles.EF_FDN;
import static android.provider.SimPhonebookContract.EntityFiles.EF_SDN;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.SubscriptionInfo;
import android.util.AndroidRuntimeException;

import com.android.internal.util.Preconditions;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * The contract between the provider of contacts on the device's SIM cards and applications.
 * Contains definitions of the supported URIs and columns.
 *
 * <p>This content provider does not support any of the QUERY_ARG_SQL* bundle arguments. An
 * IllegalArgumentException will be thrown if these are included.
 */
public final class SimPhonebookContract {

    /** The authority for the icc provider. */
    public static final String AUTHORITY = "com.android.simphonebook";
    /** The content:// style uri to the authority for the icc provider. */
    @NonNull
    public static final Uri AUTHORITY_URI = Uri.parse("content://com.android.simphonebook");

    private SimPhonebookContract() {
    }

    private static String getEfUriPath(@EntityFiles.EfType int efType) {
        switch (efType) {
            case EF_ADN:
                return "adn";
            case EF_FDN:
                return "fdn";
            case EF_SDN:
                return "sdn";
            default:
                throw new IllegalArgumentException("Unsupported EfType " + efType);
        }
    }

    /** Constants for the contacts on a SIM card. */
    public static final class SimContacts {

        /**
         * The subscription ID of the SIM the record is from.
         *
         * @see SubscriptionInfo#getSubscriptionId()
         */
        public static final String SUBSCRIPTION_ID = "subscription_id";
        /**
         * The type of the entity file the contact record is from.
         *
         * @see EntityFiles#EF_ADN
         * @see EntityFiles#EF_FDN
         * @see EntityFiles#EF_SDN
         */
        public static final String ENTITY_FILE_TYPE = "entity_file_type";
        /**
         * The offset of the record in the entity file that contains it.
         *
         * <p>This can be used to access individual SIM contact records by appending it to the
         * entity file URIs but it is not like a normal database ID because it is not
         * auto-incrementing and it is not unique across SIM cards or entity files. Hence, care
         * should taken when using it to ensure that is applied to the correct SIM and EF.
         *
         * @see #getItemUri(int, int, int)
         */
        public static final String RECORD_EF_INDEX = "record_ef_index";
        /**
         * The name of the contact.
         *
         * <p>An {@link SimPhonebookException} will be thrown by insert and update if this exceeds
         * the maximum supported length or contains unsupported characters.
         *
         * @see EntityFiles#NAME_MAX_LENGTH
         */
        public static final String NAME = "name";
        /**
         * The phone number of the contact.
         *
         * <p>A {@link SimPhonebookException} will be thrown by insert and update if this is null,
         * it is empty, it exceeds the maximum supported length or contains unsupported characters.
         *
         * @see EntityFiles#PHONE_NUMBER_MAX_LENGTH
         */
        public static final String PHONE_NUMBER = "number";

        /** The MIME type of a CONTENT_URI subdirectory of a single SIM contact. */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/sim-contact";
        /** The MIME type of CONTENT_URI providing a directory of SIM contacts. */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/sim-contact";

        private SimContacts() {
        }

        /** Returns the content Uri for the specified entity file on the specified SIM. */
        @NonNull
        public static Uri getContentUri(int subscriptionId, @EntityFiles.EfType int efType) {
            return buildContentUri(subscriptionId, efType).build();
        }

        /**
         * Content Uri for contacts stored in the ADN file of the specified SIM card.
         *
         * @see EntityFiles#EF_ADN
         */
        @NonNull
        public static Uri getAdnUri(int subscriptionId) {
            return getContentUri(subscriptionId, EF_ADN);
        }

        /**
         * Content Uri for contacts stored in the FDN file of the specified SIM card.
         *
         * @see EntityFiles#EF_FDN
         */
        @NonNull
        public static Uri getFdnUri(int subscriptionId) {
            return getContentUri(subscriptionId, EF_FDN);
        }

        /**
         * Content Uri for contacts stored in the SDN file of the specified SIM card.
         *
         * @see EntityFiles#EF_SDN
         */
        @NonNull
        public static Uri getSdnUri(int subscriptionId) {
            return getContentUri(subscriptionId, EF_SDN);
        }

        /** Content Uri for the specific SIM contact with the provided {@link #RECORD_EF_INDEX}. */
        @NonNull
        public static Uri getItemUri(int subscriptionId, int efType, int recordEfIndex) {
            // Entity file record indices are 1-based.
            Preconditions.checkArgument(recordEfIndex > 0, "Invalid recordEfIndex");

            return buildContentUri(subscriptionId, efType)
                    .appendPath(String.valueOf(recordEfIndex))
                    .build();
        }

        private static Uri.Builder buildContentUri(
                int subscriptionId, @EntityFiles.EfType int efType) {
            return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                    .authority(AUTHORITY)
                    .appendPath("subid")
                    .appendPath(String.valueOf(subscriptionId))
                    .appendPath(getEfUriPath(efType));
        }
    }

    /**
     * Constants for metadata about the contact operations supported by the entify files of the SIM
     * cards in the phone.
     */
    public static final class EntityFiles {

        /** {@link SubscriptionInfo#getSimSlotIndex()} of the SIM for this row. */
        public static final String SLOT_INDEX = "slot_index";
        /** {@link SubscriptionInfo#getSubscriptionId()} of the SIM for this row. */
        public static final String SUBSCRIPTION_ID = "subscription_id";
        /**
         * The entity file type for this row.
         *
         * @see EntityFiles#EF_ADN
         * @see EntityFiles#EF_FDN
         * @see EntityFiles#EF_SDN
         */
        public static final String EF_TYPE = "ef_type";
        /** The maximum number of contacts supported by the entity file. */
        public static final String MAX_CONTACTS = "max_contacts";
        /** Count of the number of contacts that are currently stored in the entity file. */
        public static final String CONTACT_COUNT = "contact_count";
        /** The maximum length supported for the name of a contact in the entity file. */
        public static final String NAME_MAX_LENGTH = "name_max_length";
        /** The maximum length supported for the phone number of a contact in the entity file. */
        public static final String PHONE_NUMBER_MAX_LENGTH = "phone_number_max_length";

        /**
         * A value for an entity file that is not recognized.
         *
         * <p>Generally this should be ignored. If new values are added then this will be used
         * for apps that target SDKs where they aren't defined.
         */
        public static final int EF_UNKNOWN = 0;
        /**
         * Type for accessing contacts in the "abbreviated dialing number" (ADN) entity file on the
         * SIM.
         *
         * <p>ADN contacts are typically user created contacts.
         */
        public static final int EF_ADN = 1;
        /**
         * Type for accessing contacts in the "fixed dialing number" (FDN) entity file on the SIM.
         *
         * <p>FDN numbers are the numbers that are allowed to dialed for outbound calls when FDN is
         * enabled.
         *
         * <p>FDN contacts cannot be modified by applications. Hence, insert, update and
         * delete methods operating on this Uri will throw UnsupportedOperationException
         */
        public static final int EF_FDN = 2;
        /**
         * Type for accessing contacts in the "service dialing number" (SDN) entity file on the
         * SIM.
         *
         * <p>Typically SDNs are preset numbers provided by the carrier for common operations (e.g.
         * voicemail, check balance, etc).
         *
         * <p>SDN contacts cannot be modified by applications. Hence, insert, update and delete
         * methods
         * operating on this Uri will throw UnsupportedOperationException
         */
        public static final int EF_SDN = 3;
        /** The MIME type of CONTENT_URI providing a directory of ADN-like entity files. */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/sim-entity-file";
        /** The MIME type of a CONTENT_URI subdirectory of a single ADN-like entity file. */
        public static final String CONTENT_ITEM_TYPE =
                "vnd.android.cursor.item/sim-entity-file";
        /** Content URI for the ADN-like entity files available on the device. */
        @NonNull
        public static final Uri CONTENT_URI = AUTHORITY_URI
                .buildUpon()
                .appendPath("entity_files").build();

        private EntityFiles() {
        }

        /**
         * Returns a content uri for a specific entity file.
         *
         * <p>If a SIM with the specified subscriptionId is not present an exception will be thrown.
         * If the SIM doesn't support the specified entity file it will have a zero value for
         * {@link #MAX_CONTACTS}.
         */
        @NonNull
        public static Uri getItemUri(int subscriptionId, @EfType int efType) {
            return CONTENT_URI.buildUpon().appendPath("subid")
                    .appendPath(String.valueOf(subscriptionId))
                    .appendPath(getEfUriPath(efType))
                    .build();
        }

        /**
         * Annotation for the valid entity file types.
         *
         * @hide
         */
        @Retention(RetentionPolicy.SOURCE)
        @IntDef(
                prefix = {"EF"},
                value = {EF_UNKNOWN, EF_ADN, EF_FDN, EF_SDN})
        public @interface EfType {
        }
    }

    /**
     * Exception thrown by the SIM phonebook provider when errors occur.
     */
    public static final class SimPhonebookException extends AndroidRuntimeException implements
            Parcelable {
        /**
         * An error occurred during an operation that did not match any other specific
         * {@link ErrorCode}.
         */
        public static final int ERROR_UNSPECIFIED = 0;
        /**
         * The SIM card that this phonebook is on is not present (e.g. because the user removed it
         * or it is unavailable).
         */
        public static final int ERROR_MISSING_SIM = 1;
        /**
         * The entity file type that was specified for an operation isn't supported by the SIM card
         * that was specified.
         */
        public static final int ERROR_UNSUPPORTED_EF = 2;
        /**
         * An insert operation could not be performed because the specified EF on the specified SIM
         * already has the maximum number of contacts it can support.
         */
        public static final int ERROR_EF_FULL = 3;
        /**
         * An insert or update operation could not be performed because the values provided were
         * too long or contained unsupported characters.
         *
         * @see #getSanitizedValues()
         */
        public static final int ERROR_INVALID_DATA = 4;
        /**
         * An insert, update or delete operation timed out waiting for another write operation
         * to complete.
         */
        public static final int ERROR_TIMEOUT = 5;

        @NonNull
        public static final Creator<SimPhonebookException> CREATOR =
                new Creator<SimPhonebookException>() {

                    @Override
                    public SimPhonebookException createFromParcel(@NonNull Parcel in) {
                        return new SimPhonebookException(in);
                    }

                    @NonNull
                    @Override
                    public SimPhonebookException[] newArray(int size) {
                        return new SimPhonebookException[size];
                    }
                };

        private final int mErrorCode;
        private final ContentValues mSanitizedValues;

        public SimPhonebookException(int errorCode) {
            this(errorCode, null);
        }

        public SimPhonebookException(int errorCode, @Nullable String message) {
            this(errorCode, message, (Throwable) null);
        }

        public SimPhonebookException(int errorCode, @Nullable String message,
                @Nullable Throwable cause) {
            this(errorCode, message, cause, new ContentValues());
        }

        public SimPhonebookException(
                int errorCode, @Nullable String message, @NonNull ContentValues sanitizedValues) {
            this(errorCode, message, null, sanitizedValues);
        }

        private SimPhonebookException(@NonNull Parcel in) {
            this(in.readInt(), in.readString(), null,
                    in.readParcelable(ContentValues.class.getClassLoader()));
        }

        private SimPhonebookException(int errorCode,
                @Nullable String message,
                @Nullable Throwable cause,
                ContentValues sanitizedValues) {
            super(message != null ? message : getErrorName(errorCode), cause);
            this.mErrorCode = errorCode;
            this.mSanitizedValues = sanitizedValues;
        }

        /** Returns a then name of the provided error code. */
        @NonNull
        public static String getErrorName(@ErrorCode int errorCode) {
            switch (errorCode) {
                case ERROR_UNSPECIFIED:
                    return "ERROR_UNSPECIFIED";
                case ERROR_MISSING_SIM:
                    return "ERROR_MISSING_SIM";
                case ERROR_UNSUPPORTED_EF:
                    return "ERROR_UNSUPPORTED_EF";
                case ERROR_EF_FULL:
                    return "ERROR_EF_FULL";
                case ERROR_INVALID_DATA:
                    return "ERROR_INVALID_DATA";
                default:
                    return "UNRECOGNIZED";
            }
        }

        /** Returns an error code indicating the reason for the failure. */
        @ErrorCode
        public int getErrorCode() {
            return mErrorCode;
        }

        /**
         * Returns a sanitized copy of the values that were passed for an operation in the case
         * that the error code was {@link #ERROR_INVALID_DATA}.
         *
         * <p>These values will include only fields and characters that are supported by the SIM
         * that they are being saved to. The original values will be truncated to the maximum
         * length supported by the SIM. In the case of {@link SimContacts#NAME} unsupported
         * characters will be replaced by spaces. In the case of {@link SimContacts#PHONE_NUMBER}
         * unsupported characters will be removed. Unsupported keys are omitted.
         *
         * <p>Comparing the original values against those returned by this method can be used to
         * detect unsupported input.
         */
        @NonNull
        public ContentValues getSanitizedValues() {
            return mSanitizedValues;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeInt(mErrorCode);
            dest.writeString(getMessage());
            dest.writeParcelable(mSanitizedValues, 0);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        /**
         * Annotation for the errors that may occur when performing SIM phonebook operations.
         *
         * @hide
         */
        @Retention(RetentionPolicy.SOURCE)
        @IntDef(
                prefix = {"ERROR"},
                value = {
                        ERROR_UNSPECIFIED,
                        ERROR_MISSING_SIM,
                        ERROR_UNSUPPORTED_EF,
                        ERROR_EF_FULL,
                        ERROR_INVALID_DATA,
                        ERROR_TIMEOUT
                })
        public @interface ErrorCode {
        }
    }
}
