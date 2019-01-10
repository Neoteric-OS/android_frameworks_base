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

package android.security.identity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * An object that contains a set of data entries in one namespace. This is used to provision data
 * into a newly-created IdentityCredential and to retrieve data entries.
 *
 * @see WritableIdentityCredential#personalize
 * @see IdentityCredential#getEntries
 */
public class EntryNamespace {

    private static class EntryData {
        EntryData(Object value, Collection<Byte> accessControlProfileIds,
                byte[] staticAuthenticationData, boolean directlyAccessible) {
            this.mValue = value;
            this.mAccessControlProfileIds = accessControlProfileIds;
            this.mStaticAuthenticationData = staticAuthenticationData;
            this.mDirectlyAccessible = directlyAccessible;
        }

        Object mValue; // One of String, byte[], Long or Boolean
        Collection<Byte> mAccessControlProfileIds;
        boolean mDirectlyAccessible;
        byte[] mStaticAuthenticationData;
    }

    private String mNamespace;
    private Map<String, EntryData> mEntries = new HashMap<>();

    /**
     * TODO(swillden): Write this.
     */
    public static class Builder {
        private EntryNamespace mEntryNamespace;

        /**
         * TODO(swillden): Write this.
         */
        public Builder(String namespace) {
            this.mEntryNamespace = new EntryNamespace(namespace);
        }

        /**
         * TODO(swillden): Write this.
         */
        public Builder addEntryName(String name) {
            mEntryNamespace.mEntries.put(name, null);
            return this;
        }

        /**
         * TODO(swillden): Write this.
         */
        public Builder addBooleanEntry(String name, Collection<Byte> accessControlProfileIds,
                boolean value) {
            return addEntry(name, accessControlProfileIds, value,
                    null /* staticAuthenticationData */, false /* directlyAccessible */);
        }

        /**
         * TODO(swillden): Write this.
         */
        public Builder addBooleanEntry(String name, Collection<Byte> accessControlProfileIds,
                boolean value, boolean directlyAccessible) {
            return addEntry(name, accessControlProfileIds, value,
                    null /* staticAuthenticationData */, directlyAccessible);
        }

        /**
         * TODO(swillden): Write this.
         */
        public Builder addLongEntry(String name, Collection<Byte> accessControlProfileIds,
                long value) {
            return addEntry(name, accessControlProfileIds, value,
                    null /* staticAuthenticationData */, false /* directlyAccessible */);
        }

        /**
         * TODO(swillden): Write this.
         */
        public Builder addLongEntry(String name, Collection<Byte> accessControlProfileIds,
                long value, boolean directlyAccessible) {
            return addEntry(name, accessControlProfileIds, value,
                    null /* staticAuthenticationData */, directlyAccessible);
        }

        /**
         * TODO(swillden): Write this.
         */
        public Builder addIntEntry(String name, Collection<Byte> accessControlProfileIds,
                int value) {
            return addEntry(name, accessControlProfileIds, (long) value,
                    null /* staticAuthenticationData */, false /* directlyAccessible */);
        }

        /**
         * TODO(swillden): Write this.
         */
        public Builder addIntEntry(String name, Collection<Byte> accessControlProfileIds,
                int value, boolean directlyAccessible) {
            return addEntry(name, accessControlProfileIds, (long) value,
                    null /* staticAuthenticationData */, directlyAccessible);
        }

        /**
         * TODO(swillden): Write this.
         */
        public Builder addBytestringEntry(String name, Collection<Byte> accessControlProfileIds,
                byte[] value, boolean directlyAccessible) {
            return addEntry(name, accessControlProfileIds, value,
                    null /* staticAuthenticationData */, directlyAccessible);
        }

        /**
         * TODO(swillden): Write this.
         */
        public Builder addBytestringEntry(String name, Collection<Byte> accessControlProfileIds,
                byte[] value) {
            return addEntry(name, accessControlProfileIds, value,
                    null /* staticAuthenticationData */, false /* directlyAccessible */);
        }

        /**
         * TODO(swillden): Write this.
         */
        public Builder addStringEntry(String name, Collection<Byte> accessControlProfileIds,
                String value, boolean directlyAccessible) {
            return addEntry(name, accessControlProfileIds, value,
                    null /* staticAuthenticationData */, directlyAccessible);
        }

        /**
         * TODO(swillden): Write this.
         */
        public Builder addStringEntry(String name, Collection<Byte> accessControlProfileIds,
                String value) {
            return addEntry(name, accessControlProfileIds, value,
                    null /* staticAuthenticationData */, false /* directlyAccessible */);
        }

        Builder addLongEntry(String name, Collection<Byte> accessControlProfileIds,
                long value, byte[] staticAuthenticationData, boolean directlyAccessible) {
            return addEntry(name, accessControlProfileIds, value, staticAuthenticationData,
                    directlyAccessible);
        }

        Builder addBooleanEntry(String name, Collection<Byte> accessControlProfileIds,
                boolean value, byte[] staticAuthenticationData, boolean directlyAccessible) {
            return addEntry(name, accessControlProfileIds, value, staticAuthenticationData,
                    directlyAccessible);
        }

        Builder addBytestringEntry(String name, Collection<Byte> accessControlProfileIds,
                byte[] value, byte[] staticAuthenticationData, boolean directlyAccessible) {
            return addEntry(name, accessControlProfileIds, value, staticAuthenticationData,
                    directlyAccessible);
        }

        Builder addStringEntry(String name, Collection<Byte> accessControlProfileIds,
                String value, byte[] staticAuthenticationData, boolean directlyAccessible) {
            return addEntry(name, accessControlProfileIds, value, staticAuthenticationData,
                    directlyAccessible);
        }

        /**
         * TODO(swillden): Write this.
         */
        private Builder addEntry(String name, Collection<Byte> accessControlProfileIds,
                Object value, byte[] staticAuthenticationData, boolean directlyAccessible) {
            mEntryNamespace.mEntries.put(name, new EntryData(value, accessControlProfileIds,
                    staticAuthenticationData, directlyAccessible));
            return this;
        }

        /**
         * TODO(swillden): Write this.
         */
        public EntryNamespace build() {
            return mEntryNamespace;
        }
    }

    private EntryNamespace(String namespace) {
        this.mNamespace = namespace;
    }

    /**
     * TODO(swillden): Write this.
     */
    public String getNamespaceName() {
        return mNamespace;
    }

    /**
     * TODO(swillden): Write this.
     */
    public Collection<String> getEntryNames() {
        return Collections.unmodifiableCollection(mEntries.keySet());
    }

    /**
     * TODO(swillden): Write this.
     */
    public boolean isDirectlyAccessible(String name) {
        return mEntries.get(name).mDirectlyAccessible;
    }

    /**
     * TODO(swillden): Write this.
     */
    public Collection<String> getDirectlyAccessibleNames() {
        Collection<String> result = new ArrayList<String>();
        for (Entry<String, EntryData> entry : mEntries.entrySet()) {
            if (entry.getValue().mDirectlyAccessible) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * TODO(swillden): Write this.
     */
    public boolean getBooleanEntry(String name) {
        EntryData value = mEntries.get(name);
        if (value != null && value.mValue != null && value.mValue instanceof Boolean) {
            return (Boolean) value.mValue;
        }
        return false;
    }

    /**
     * TODO(swillden): Write this.
     */
    public long getIntegerEntry(String name) {
        EntryData value = mEntries.get(name);
        if (value != null && value.mValue != null && value.mValue instanceof Long) {
            return (Long) value.mValue;
        }
        return 0;
    }

    /**
     * TODO(swillden): Write this.
     */
    public String getTextStringEntry(String name) {
        EntryData value = mEntries.get(name);
        if (value != null && value.mValue != null && value.mValue instanceof String) {
            return (String) value.mValue;
        }
        return null;
    }

    /**
     * TODO(swillden): Write this.
     */
    public byte[] getByteStringEntry(String name) {
        EntryData value = mEntries.get(name);
        if (value != null && value.mValue != null && value.mValue instanceof byte[]) {
            return (byte[]) value.mValue;
        }
        return null;
    }

    /**
     * TODO(swillden): Write this.
     */
    public Collection<Byte> getAccessControlProfiles(String name) {
        EntryData value = mEntries.get(name);
        if (value != null) {
            return value.mAccessControlProfileIds;
        }
        return null;
    }

    /**
     * TODO(swillden): Write this.
     */
    public Object getEntryValue(String name) {
        return mEntries.get(name).mValue;
    }

    /**
     * TODO(swillden): Write this.
     */
    public byte[] getStaticAuthenticationData(String name) {
        return mEntries.get(name).mStaticAuthenticationData;
    }
}
