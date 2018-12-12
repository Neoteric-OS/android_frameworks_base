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
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

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
                + COLUMNNAME_DATANAME + " " + COLUMNTYPE_DATANAME + ") "
                + "PRIMARY KEY ("
                + COLUMNNAME_L2KEY + ", "
                + COLUMNNAME_CLIENT + ", "
                + COLUMNNAME_DATANAME + ")";
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
}
