/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.net.netstats;

import static android.annotation.SystemApi.Client.MODULE_LIBRARIES;
import static android.app.usage.NetworkStatsManager.PREFIX_DEV;
import static android.app.usage.NetworkStatsManager.PREFIX_UID;
import static android.app.usage.NetworkStatsManager.PREFIX_UID_TAG;
import static android.app.usage.NetworkStatsManager.PREFIX_XT;
import static android.net.ConnectivityManager.TYPE_MOBILE;
import static android.net.NetworkStats.SET_ALL;
import static android.net.NetworkStats.SET_DEFAULT;
import static android.net.NetworkStats.TAG_NONE;
import static android.net.NetworkStats.UID_ALL;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.net.NetworkIdentity;
import android.net.NetworkIdentitySet;
import android.net.NetworkStatsCollection;
import android.net.NetworkStatsHistory;
import android.os.Environment;
import android.util.AtomicFile;

import com.android.internal.util.FastDataInput;

import libcore.io.IoUtils;

import java.io.BufferedInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ProtocolException;
import java.util.HashMap;

/**
 * Helper class to read old version of persistent network statistics, the implementation is
 * intended to be modified by OEM partners to accommodate their custom changes.
 * @hide
 */
@SystemApi(client = MODULE_LIBRARIES)
public class NetworkStatsDataMigrationUtils {

    private static final HashMap<String, String> sPrefixLegacyFileNameMap =
            new HashMap<String, String>() {{
                put(PREFIX_DEV, "netstats.bin");
                put(PREFIX_XT, "netstats_xt.bin");
                put(PREFIX_UID, "netstats_uid.bin");
                put(PREFIX_UID_TAG, "netstats_uid.bin");
            }};

    // These version constants are copied from NetworkStatsCollection/History, which is okay for
    // OEMs to modify to adopt their own logic.
    private static class CollectionVersion {
        static final int VERSION_NETWORK_INIT = 1;

        static final int VERSION_UID_INIT = 1;
        static final int VERSION_UID_WITH_IDENT = 2;
        static final int VERSION_UID_WITH_TAG = 3;
        static final int VERSION_UID_WITH_SET = 4;

        static final int VERSION_UNIFIED_INIT = 16;
    }

    private static class HistoryVersion {
        static final int VERSION_INIT = 1;
        static final int VERSION_ADD_PACKETS = 2;
        static final int VERSION_ADD_ACTIVE = 3;
    }

    private static class IdentitySetVersion {
        static final int VERSION_INIT = 1;
        static final int VERSION_ADD_ROAMING = 2;
        static final int VERSION_ADD_NETWORK_ID = 3;
        static final int VERSION_ADD_METERED = 4;
        static final int VERSION_ADD_DEFAULT_NETWORK = 5;
        static final int VERSION_ADD_OEM_MANAGED_NETWORK = 6;
    }

    /**
     * File header magic number: "ANET". The definition is copied from NetworkStatsCollection,
     * but it is fine for OEM to re-define to their own value to adopt the legacy file reading
     * logic.
     */
    private static final int FILE_MAGIC = 0x414E4554;
    /** Default buffer size from BufferedInputStream */
    private static final int BUFFER_SIZE = 8192;

    // Construct object through default constructor is not allowed.
    private NetworkStatsDataMigrationUtils() {}

    // Used to read files at /data/system/netstats_*.bin.
    @NonNull
    private static File getPlatformSystemDir() {
        return new File(Environment.getDataDirectory(), "system");
    }

    // Used to read files at /data/system/netstats/<tag>.<start>-<end>.
    @NonNull
    private static File getPlatformBaseDir() {
        File baseDir = new File(getPlatformSystemDir(), "netstats");
        baseDir.mkdirs();
        return baseDir;
    }

    /**
     * Read legacy persisted network stats from disk. This function provides a default
     * implementation to read persisted network stats from two different locations.
     * And this is intended to be modified by OEM to read from custom file format or
     * locations if necessary.
     *
     * @param prefix Type of data which is being read by the service.
     * @param bucketDuration Duration of the buckets of the object, in milliseconds.
     * @return {@link NetworkStatsCollection} instance.
     * @throws IOException
     */
    @NonNull
    public static NetworkStatsCollection readPlatformCollectionLocked(
            @NonNull String prefix, long bucketDuration) throws IOException {
        final NetworkStatsCollection collection = new NetworkStatsCollection(bucketDuration);

        // Import /data/system/netstats_[dev|uid].bin legacy files if exists.
        switch (prefix) {
            case PREFIX_DEV:
                final File devFile = new File(getPlatformSystemDir(),
                        sPrefixLegacyFileNameMap.get(prefix));
                if (devFile.exists()) {
                    final NetworkStatsCollection devCollection = maybeReadLegacyNetwork(devFile,
                            bucketDuration);
                    collection.recordCollection(devCollection);
                }
                break;
            case PREFIX_UID:
            case PREFIX_UID_TAG:
                final File uidFile = new File(getPlatformSystemDir(),
                        sPrefixLegacyFileNameMap.get(prefix));
                if (uidFile.exists()) {
                    final NetworkStatsCollection uidCollection = maybeReadLegacyUid(uidFile,
                            bucketDuration, PREFIX_UID_TAG.equals(prefix) ? true : false);
                    collection.recordCollection(uidCollection);
                }
                break;
            default:
                // Ignore other requests.
        }

        // Import /data/system/netstats/[dev|tx|uid|uid_tag].<start>-<end> legacy files if exists.
        final File platformFiles = new File(getPlatformBaseDir(), "netstats");
        if (platformFiles.exists()) {
            for (String name : platformFiles.list()) {
                final int dotIndex = name.lastIndexOf('.');
                // Skip for unexpected format.
                if (dotIndex == -1) continue;
                // Skip when prefix doesn't match.
                if (!prefix.equals(name.substring(0, dotIndex))) continue;

                final File file = new File(platformFiles, name);
                final FileInputStream is = new FileInputStream(file);
                final FastDataInput dataIn = new FastDataInput(is, BUFFER_SIZE);
                readPlatformCollection(collection, dataIn);
            }
        }
        return collection;
    }

    /**
     * Helper function to read old version of NetworkStatsCollections that resided in the platform.
     */
    private static void readPlatformCollection(@NonNull NetworkStatsCollection collection,
            @NonNull DataInput in) throws IOException {
        // verify file magic header intact
        final int magic = in.readInt();
        if (magic != FILE_MAGIC) {
            throw new ProtocolException("unexpected magic: " + magic);
        }

        final int version = in.readInt();
        switch (version) {
            case CollectionVersion.VERSION_UNIFIED_INIT: {
                // uid := size *(NetworkIdentitySet size *(uid set tag NetworkStatsHistory))
                final int identSize = in.readInt();
                for (int i = 0; i < identSize; i++) {
                    final NetworkIdentitySet ident = readPlatformNetworkIdentitySet(in);

                    final int size = in.readInt();
                    for (int j = 0; j < size; j++) {
                        final int uid = in.readInt();
                        final int set = in.readInt();
                        final int tag = in.readInt();

                        final NetworkStatsCollection.Key key = new NetworkStatsCollection.Key(
                                ident, uid, set, tag);
                        final NetworkStatsHistory history = readPlatformHistory(in);
                        collection.recordHistory(key, history);
                    }
                }
                break;
            }
            default: {
                throw new ProtocolException("unexpected version: " + version);
            }
        }
    }

    // Copied from NetworkStatsHistory#DataStreamUtils.
    private static long[] readFullLongArray(DataInput in) throws IOException {
        final int size = in.readInt();
        if (size < 0) throw new ProtocolException("negative array size");
        final long[] values = new long[size];
        for (int i = 0; i < values.length; i++) {
            values[i] = in.readLong();
        }
        return values;
    }

    // Copied from NetworkStatsHistory#DataStreamUtils.
    private static long[] readVarLongArray(@NonNull DataInput in) throws IOException {
        final int size = in.readInt();
        if (size == -1) return null;
        if (size < 0) throw new ProtocolException("negative array size");
        final long[] values = new long[size];
        for (int i = 0; i < values.length; i++) {
            values[i] = readVarLong(in);
        }
        return values;
    }

    /**
     * Read variable-length {@link Long} using protobuf-style approach.
     */
    // Copied from NetworkStatsHistory#DataStreamUtils.
    private static long readVarLong(DataInput in) throws IOException {
        int shift = 0;
        long result = 0;
        while (shift < 64) {
            byte b = in.readByte();
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
        }
        throw new ProtocolException("malformed long");
    }

    // Copied from NetworkIdentitySet.
    private static String readOptionalString(DataInput in) throws IOException {
        if (in.readByte() != 0) {
            return in.readUTF();
        } else {
            return null;
        }
    }

    /**
     * This is copied from NetworkStatsHistory#NetworkStatsHistory(DataInput in). But it is fine
     * for OEM to re-write the logic to adopt the legacy file reading.
     */
    @NonNull
    private static NetworkStatsHistory readPlatformHistory(@NonNull DataInput in)
            throws IOException {
        final long bucketDuration;
        final long[] bucketStart;
        final long[] rxBytes;
        final long[] rxPackets;
        final long[] txBytes;
        final long[] txPackets;
        final long[] operations;
        final int bucketCount;
        long[] activeTime = new long[0];

        final int version = in.readInt();
        switch (version) {
            case HistoryVersion.VERSION_INIT: {
                bucketDuration = in.readLong();
                bucketStart = readFullLongArray(in);
                rxBytes = readFullLongArray(in);
                rxPackets = new long[bucketStart.length];
                txBytes = readFullLongArray(in);
                txPackets = new long[bucketStart.length];
                operations = new long[bucketStart.length];
                bucketCount = bucketStart.length;
                break;
            }
            case HistoryVersion.VERSION_ADD_PACKETS:
            case HistoryVersion.VERSION_ADD_ACTIVE: {
                bucketDuration = in.readLong();
                bucketStart = readVarLongArray(in);
                activeTime = (version >= HistoryVersion.VERSION_ADD_ACTIVE)
                        ? readVarLongArray(in)
                        : new long[bucketStart.length];
                rxBytes = readVarLongArray(in);
                rxPackets = readVarLongArray(in);
                txBytes = readVarLongArray(in);
                txPackets = readVarLongArray(in);
                operations = readVarLongArray(in);
                bucketCount = bucketStart.length;
                break;
            }
            default: {
                throw new ProtocolException("unexpected version: " + version);
            }
        }

        final NetworkStatsHistory.Builder historyBuilder =
                new NetworkStatsHistory.Builder(bucketDuration, bucketCount);
        for (int i = 0; i < bucketCount; i++) {
            final NetworkStatsHistory.Entry entry = new NetworkStatsHistory.Entry(
                    bucketStart[i], activeTime[i],
                    rxBytes[i], rxPackets[i], txBytes[i], txPackets[i], operations[i]);
            historyBuilder.addEntry(entry);
        }

        return historyBuilder.build();
    }

    @NonNull
    private static NetworkIdentitySet readPlatformNetworkIdentitySet(@NonNull DataInput in)
            throws IOException {
        final int version = in.readInt();
        final int size = in.readInt();
        final NetworkIdentitySet set = new NetworkIdentitySet();
        for (int i = 0; i < size; i++) {
            if (version <= IdentitySetVersion.VERSION_INIT) {
                final int ignored = in.readInt();
            }
            final int type = in.readInt();
            final int ratType = in.readInt();
            final String subscriberId = readOptionalString(in);
            final String networkId;
            if (version >= IdentitySetVersion.VERSION_ADD_NETWORK_ID) {
                networkId = readOptionalString(in);
            } else {
                networkId = null;
            }
            final boolean roaming;
            if (version >= IdentitySetVersion.VERSION_ADD_ROAMING) {
                roaming = in.readBoolean();
            } else {
                roaming = false;
            }

            final boolean metered;
            if (version >= IdentitySetVersion.VERSION_ADD_METERED) {
                metered = in.readBoolean();
            } else {
                // If this is the old data and the type is mobile, treat it as metered. (Note that
                // if this is a mobile network, TYPE_MOBILE is the only possible type that could be
                // used.)
                metered = (type == TYPE_MOBILE);
            }

            final boolean defaultNetwork;
            if (version >= IdentitySetVersion.VERSION_ADD_DEFAULT_NETWORK) {
                defaultNetwork = in.readBoolean();
            } else {
                defaultNetwork = true;
            }

            final int oemNetCapabilities;
            if (version >= IdentitySetVersion.VERSION_ADD_OEM_MANAGED_NETWORK) {
                oemNetCapabilities = in.readInt();
            } else {
                oemNetCapabilities = NetworkIdentity.OEM_NONE;
            }

            set.add(new NetworkIdentity(type, ratType, subscriberId, networkId, roaming, metered,
                    defaultNetwork, oemNetCapabilities));
        }
        return set;
    }

    /**
     * Read legacy network summary statistics file format into the collection,
     * See {@code NetworkStatsService#maybeUpgradeLegacyStatsLocked}.
     */
    @NonNull
    private static NetworkStatsCollection maybeReadLegacyNetwork(
            @NonNull File file, long bucketDuration) throws IOException {
        final NetworkStatsCollection collection = new NetworkStatsCollection(bucketDuration);
        final AtomicFile inputFile = new AtomicFile(file);

        DataInputStream in = null;
        try {
            in = new DataInputStream(new BufferedInputStream(inputFile.openRead()));

            // verify file magic header intact
            final int magic = in.readInt();
            if (magic != FILE_MAGIC) {
                throw new ProtocolException("unexpected magic: " + magic);
            }

            final int version = in.readInt();
            switch (version) {
                case CollectionVersion.VERSION_NETWORK_INIT: {
                    // network := size *(NetworkIdentitySet NetworkStatsHistory)
                    final int size = in.readInt();
                    for (int i = 0; i < size; i++) {
                        final NetworkIdentitySet ident = new NetworkIdentitySet(in);
                        final NetworkStatsHistory history = new NetworkStatsHistory(in);

                        final NetworkStatsCollection.Key key =
                                new NetworkStatsCollection.Key(ident, UID_ALL, SET_ALL, TAG_NONE);
                        collection.recordHistory(key, history);
                    }
                    break;
                }
                default: {
                    throw new ProtocolException("unexpected version: " + version);
                }
            }
        } catch (FileNotFoundException | ProtocolException e) {
            // missing stats is okay, probably first boot
        } finally {
            IoUtils.closeQuietly(in);
        }
        return collection;
    }

    /**
     * Read legacy Uid statistics file format into the collection,
     * See {@code NetworkStatsService#maybeUpgradeLegacyStatsLocked}.
     */
    @NonNull
    private static NetworkStatsCollection maybeReadLegacyUid(@NonNull File file,
            long bucketDuration, boolean onlyTags) throws IOException {
        final NetworkStatsCollection collection = new NetworkStatsCollection(bucketDuration);
        final AtomicFile inputFile = new AtomicFile(file);
        DataInputStream in = null;
        try {
            in = new DataInputStream(new BufferedInputStream(inputFile.openRead()));

            // verify file magic header intact
            final int magic = in.readInt();
            if (magic != FILE_MAGIC) {
                throw new ProtocolException("unexpected magic: " + magic);
            }

            final int version = in.readInt();
            switch (version) {
                case CollectionVersion.VERSION_UID_INIT: {
                    // uid := size *(UID NetworkStatsHistory)

                    // drop this data version, since we don't have a good
                    // mapping into NetworkIdentitySet.
                    break;
                }
                case CollectionVersion.VERSION_UID_WITH_IDENT: {
                    // uid := size *(NetworkIdentitySet size *(UID NetworkStatsHistory))

                    // drop this data version, since this version only existed
                    // for a short time.
                    break;
                }
                case CollectionVersion.VERSION_UID_WITH_TAG:
                case CollectionVersion.VERSION_UID_WITH_SET: {
                    // uid := size *(NetworkIdentitySet size *(uid set tag NetworkStatsHistory))
                    final int identSize = in.readInt();
                    for (int i = 0; i < identSize; i++) {
                        final NetworkIdentitySet ident = readPlatformNetworkIdentitySet(in);

                        final int size = in.readInt();
                        for (int j = 0; j < size; j++) {
                            final int uid = in.readInt();
                            final int set = (version >= CollectionVersion.VERSION_UID_WITH_SET)
                                    ? in.readInt()
                                    : SET_DEFAULT;
                            final int tag = in.readInt();

                            final NetworkStatsCollection.Key key = new NetworkStatsCollection.Key(
                                    ident, uid, set, tag);
                            final NetworkStatsHistory history = readPlatformHistory(in);

                            if ((tag == TAG_NONE) != onlyTags) {
                                collection.recordHistory(key, history);
                            }
                        }
                    }
                    break;
                }
                default: {
                    throw new ProtocolException("unexpected version: " + version);
                }
            }
        } catch (FileNotFoundException | ProtocolException e) {
            // missing stats is okay, probably first boot
        } finally {
            IoUtils.closeQuietly(in);
        }
        return collection;
    }
}
