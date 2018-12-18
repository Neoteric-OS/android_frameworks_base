/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.server.net.ipmemorystore;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.NetworkUtils;
import android.net.ipmemorystore.NetworkAttributes;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.util.List;

/**
 * Encapsulating class for using the SQLite database backing the memory store.
 *
 * This class groups together the contracts and the SQLite helper used to
 * use the database.
 *
 * @hide
 */
public class IpMemoryStoreDatabase {
    /**
     * Contract class for the Network Attributes table.
     */
    public static class NetworkAttributesContract {
        public static final String TABLENAME = "NetworkAttributes";

        public static final String COLUMNNAME_L2KEY = "l2Key";
        public static final String COLUMNTYPE_L2KEY = "TEXT NOT NULL";

        public static final String COLUMNNAME_EXPIRYDATE = "expiryDate";
        // Milliseconds since the Epoch, in true Java style
        public static final String COLUMNTYPE_EXPIRYDATE = "BIGINT";

        public static final String COLUMNNAME_ASSIGNEDV4ADDRESS = "assignedV4Address";
        public static final String COLUMNTYPE_ASSIGNEDV4ADDRESS = "INTEGER";

        // Please note that the group hint is only a *hint*, hence its name. The client can offer
        // this information to nudge the grouping in the decision it thinks is right, but it can't
        // decide for the memory store what is the same L3 network.
        public static final String COLUMNNAME_GROUPHINT = "groupHint";
        public static final String COLUMNTYPE_GROUPHINT = "TEXT";

        public static final String COLUMNNAME_DNSADDRESSES = "dnsAddresses";
        // Stored in marshalled form as is
        public static final String COLUMNTYPE_DNSADDRESSES = "BLOB";

        public static final String COLUMNNAME_MTU = "mtu";
        public static final String COLUMNTYPE_MTU = "INTEGER";

        public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS "
                + TABLENAME + " ("
                + COLUMNNAME_L2KEY + " " + COLUMNTYPE_L2KEY + " PRIMARY KEY NOT NULL, "
                + COLUMNNAME_EXPIRYDATE + " " + COLUMNTYPE_EXPIRYDATE + ", "
                + COLUMNNAME_ASSIGNEDV4ADDRESS + " " + COLUMNTYPE_ASSIGNEDV4ADDRESS + ", "
                + COLUMNNAME_GROUPHINT + " " + COLUMNTYPE_GROUPHINT + ", "
                + COLUMNNAME_DNSADDRESSES + " " + COLUMNTYPE_DNSADDRESSES + ", "
                + COLUMNNAME_MTU + " " + COLUMNTYPE_MTU + ")";
        public static final String DROP_TABLE = "DROP TABLE IF EXISTS " + TABLENAME;
    }

    /**
     * Contract class for the Private Data table.
     */
    public static class PrivateDataContract {
        public static final String TABLENAME = "PrivateData";

        public static final String COLUMNNAME_L2KEY = "l2Key";
        public static final String COLUMNTYPE_L2KEY = "TEXT NOT NULL";

        public static final String COLUMNNAME_CLIENT = "client";
        public static final String COLUMNTYPE_CLIENT = "TEXT NOT NULL";

        public static final String COLUMNNAME_DATANAME = "dataName";
        public static final String COLUMNTYPE_DATANAME = "TEXT NOT NULL";

        public static final String COLUMNNAME_DATA = "data";
        public static final String COLUMNTYPE_DATA = "BLOB NOT NULL";

        public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS "
                + TABLENAME + " ("
                + COLUMNNAME_L2KEY + " " + COLUMNTYPE_L2KEY + ", "
                + COLUMNNAME_CLIENT + " " + COLUMNTYPE_CLIENT + ", "
                + COLUMNNAME_DATANAME + " " + COLUMNTYPE_DATANAME + ", "
                + COLUMNNAME_DATA + " " + COLUMNTYPE_DATA + ", "
                + "PRIMARY KEY ("
                + COLUMNNAME_L2KEY + ", "
                + COLUMNNAME_CLIENT + ", "
                + COLUMNNAME_DATANAME + "))";
        public static final String DROP_TABLE = "DROP TABLE IF EXISTS " + TABLENAME;
    }

    /** The SQLite DB helper */
    public static class DbHelper extends SQLiteOpenHelper {
        // Update this whenever changing the schema.
        private static final int SCHEMA_VERSION = 1;
        private static final String DATABASE_FILENAME = "IpMemoryStore.db";

        public DbHelper(@NonNull final Context context) {
            super(context, DATABASE_FILENAME, null, SCHEMA_VERSION);
        }

        /** Called when the database is created */
        public void onCreate(@NonNull final SQLiteDatabase db) {
            db.execSQL(NetworkAttributesContract.CREATE_TABLE);
            db.execSQL(PrivateDataContract.CREATE_TABLE);
        }

        /** Called when the database is upgraded */
        public void onUpgrade(@NonNull final SQLiteDatabase db, final int oldVersion,
                final int newVersion) {
            // No upgrade supported yet.
            db.execSQL(NetworkAttributesContract.DROP_TABLE);
            db.execSQL(PrivateDataContract.DROP_TABLE);
            onCreate(db);
        }

        /** Called when the database is downgraded */
        public void onDowngrade(@NonNull final SQLiteDatabase db, final int oldVersion,
                final int newVersion) {
            // Downgrades always nuke all data and recreate an empty table.
            db.execSQL(NetworkAttributesContract.DROP_TABLE);
            db.execSQL(PrivateDataContract.DROP_TABLE);
            onCreate(db);
        }
    }

    @NonNull
    private static byte[] encodeAddressList(@NonNull final List<InetAddress> addresses) {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        for (final InetAddress address : addresses) {
            final byte[] b = address.getAddress();
            os.write(b.length);
            os.write(b, 0, b.length);
        }
        return os.toByteArray();
    }

    // Convert a NetworkAttributes object to content values to store them in a table compliant
    // with the contract defined in NetworkAttributesContract.
    @NonNull
    private static ContentValues toContentValues(@NonNull final String key,
            @Nullable final NetworkAttributes attributes, final long expiry) {
        final ContentValues values = new ContentValues();
        values.put(NetworkAttributesContract.COLUMNNAME_L2KEY, key);
        values.put(NetworkAttributesContract.COLUMNNAME_EXPIRYDATE, expiry);
        if (null != attributes) {
            if (null != attributes.assignedV4Address) {
                values.put(NetworkAttributesContract.COLUMNNAME_ASSIGNEDV4ADDRESS,
                        NetworkUtils.inet4AddressToIntHTH(attributes.assignedV4Address));
            }
            if (null != attributes.groupHint) {
                values.put(NetworkAttributesContract.COLUMNNAME_GROUPHINT, attributes.groupHint);
            }
            if (null != attributes.dnsAddresses) {
                values.put(NetworkAttributesContract.COLUMNNAME_DNSADDRESSES,
                        encodeAddressList(attributes.dnsAddresses));
            }
            if (null != attributes.mtu) {
                values.put(NetworkAttributesContract.COLUMNNAME_MTU, attributes.mtu);
            } else {
                // Negative MTU is invalid : using -1 to represent a non-existent value.
                values.put(NetworkAttributesContract.COLUMNNAME_MTU, -1);
            }
        }
        return values;
    }

    // Convert a byte array into content values to store it in a table compliant with the
    // contract defined in PrivateDataContract.
    @NonNull
    private static ContentValues toContentValues(@NonNull final String key,
            @NonNull final String clientId, @NonNull final String name,
            @NonNull final byte[] data) {
        final ContentValues values = new ContentValues();
        values.put(PrivateDataContract.COLUMNNAME_L2KEY, key);
        values.put(PrivateDataContract.COLUMNNAME_CLIENT, clientId);
        values.put(PrivateDataContract.COLUMNNAME_DATANAME, name);
        values.put(PrivateDataContract.COLUMNNAME_DATA, data);
        return values;
    }

    private static final String[] EXPIRY_COLUMN = new String[] {
        NetworkAttributesContract.COLUMNNAME_EXPIRYDATE
    };
    static final int EXPIRY_ERROR = -1; // Legal values for expiry are positive

    // Returns the expiry date of the specified row, or one of the error codes above if the
    // row is not found or some other error
    static long getExpiry(@NonNull final SQLiteDatabase db, @NonNull final String key) {
        final Cursor cursor = db.query(NetworkAttributesContract.TABLENAME,
                EXPIRY_COLUMN, // columns
                NetworkAttributesContract.COLUMNNAME_L2KEY + " = ?", // selection
                new String[] { key }, // selectionArgs
                null, // groupBy
                null, // having
                null // orderBy
        );
        // L2KEY is the primary key ; it should not be possible to get more than one
        // result here. 0 results means the key was not found.
        if (cursor.getCount() != 1) return EXPIRY_ERROR;
        cursor.moveToFirst();
        return cursor.getLong(0); // index in the EXPIRY_COLUMN array
    }

    static final int RELEVANCE_ERROR = -1; // Legal values for relevance are positive

    // Returns the relevance of the specified row, or one of the error codes above if the
    // row is not found or some other error
    static int getRelevance(@NonNull final SQLiteDatabase db, @NonNull final String key) {
        final long expiry = getExpiry(db, key);
        return expiry < 0 ? (int) expiry : RelevanceUtils.computeRelevanceForNow(expiry);
    }

    // If the attributes are null, this will only write the expiry.
    static void storeNetworkAttributes(@NonNull final SQLiteDatabase db, @NonNull final String key,
            final long expiry, @Nullable final NetworkAttributes attributes) {
        db.insertWithOnConflict(NetworkAttributesContract.TABLENAME, null,
                toContentValues(key, attributes, expiry), SQLiteDatabase.CONFLICT_REPLACE);
    }

    static void storeBlob(@NonNull final SQLiteDatabase db, @NonNull final String key,
            @NonNull final String clientId, @NonNull final String name,
            @NonNull final byte[] data) {
        db.insertWithOnConflict(PrivateDataContract.TABLENAME, null,
                toContentValues(key, clientId, name, data), SQLiteDatabase.CONFLICT_REPLACE);
    }
}
