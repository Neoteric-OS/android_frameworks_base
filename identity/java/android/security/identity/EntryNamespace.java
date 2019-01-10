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

    private class EntryData {
        EntryData(Object value, Collection<String> accessControlProfiles,
                byte[] staticAuthenticationData, boolean directlyAccessible) {
            this.mValue = value;
            this.mAccessControlProfiles = accessControlProfiles;
            this.mStaticAuthenticationData = staticAuthenticationData;
            this.mDirectlyAccessible = directlyAccessible;
        }

        Object mValue; // One of String, byte[], Long or Boolean
        Collection<String> mAccessControlProfiles;
        boolean mDirectlyAccessible;
        byte[] mStaticAuthenticationData;
    }

    private String mNamespace;
    private Map<String, EntryData> mEntries = new HashMap<>();

    /**
     * TODO(swillden): Write this.
     */
    public class Builder {
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
        public Builder addBooleanEntry(String name, Collection<String> accessControlProfileNames,
                boolean value, boolean directlyAccessible) {
            return addEntry(name, accessControlProfileNames, value,
                    null /* staticAuthenticationData */, directlyAccessible);
        }

        /**
         * TODO(swillden): Write this.
         */
        public Builder addIntegerEntry(String name, Collection<String> accessControlProfileNames,
                long value, boolean directlyAccessible) {
            return addEntry(name, accessControlProfileNames, value,
                    null /* staticAuthenticationData */, directlyAccessible);
        }

        /**
         * TODO(swillden): Write this.
         */
        public Builder addBytestringEntry(String name, Collection<String> accessControlProfileNames,
                byte[] value, boolean directlyAccessible) {
            return addEntry(name, accessControlProfileNames, value,
                    null /* staticAuthenticationData */, directlyAccessible);
        }

        /**
         * TODO(swillden): Write this.
         */
        public Builder addTextStringEntry(String name, Collection<String> accessControlProfileNames,
                String value, boolean directlyAccessible) {
            return addEntry(name, accessControlProfileNames, value,
                    null /* staticAuthenticationData */, directlyAccessible);
        }

        /**
         * TODO(swillden): Write this.
         */
        Builder addBooleanEntry(String name, Collection<String> accessControlProfileNames,
                boolean value, byte[] staticAuthenticationData, boolean directlyAccessible) {
            return addEntry(name, accessControlProfileNames, value, staticAuthenticationData,
                    directlyAccessible);
        }

        /**
         * TODO(swillden): Write this.
         */
        Builder addIntegerEntry(String name, Collection<String> accessControlProfileNames,
                long value, byte[] staticAuthenticationData, boolean directlyAccessible) {
            return addEntry(name, accessControlProfileNames, value, staticAuthenticationData,
                    directlyAccessible);
        }

        /**
         * TODO(swillden): Write this.
         */
        Builder addBytestringEntry(String name, Collection<String> accessControlProfileNames,
                byte[] value, byte[] staticAuthenticationData, boolean directlyAccessible) {
            return addEntry(name, accessControlProfileNames, value, staticAuthenticationData,
                    directlyAccessible);
        }

        /**
         * TODO(swillden): Write this.
         */
        Builder addTextStringEntry(String name, Collection<String> accessControlProfileNames,
                String value, byte[] staticAuthenticationData, boolean directlyAccessible) {
            return addEntry(name, accessControlProfileNames, value, staticAuthenticationData,
                    directlyAccessible);
        }

        /**
         * TODO(swillden): Write this.
         */
        private Builder addEntry(String name, Collection<String> accessControlProfileNames,
                Object value, byte[] staticAuthenticationData, boolean directlyAccessible) {
            mEntryNamespace.mEntries.put(name, new EntryData(value, accessControlProfileNames,
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
    public Boolean getBooleanEntry(String name) {
        EntryData value = mEntries.get(name);
        if (value != null && value.mValue != null && value.mValue instanceof Boolean) {
            return (Boolean) value.mValue;
        }
        return null;
    }

    /**
     * TODO(swillden): Write this.
     */
    public Long getIntegerEntry(String name) {
        EntryData value = mEntries.get(name);
        if (value != null && value.mValue != null && value.mValue instanceof Long) {
            return (Long) value.mValue;
        }
        return null;
    }

    /**
     * TODO(swillden): Write this.
     */
    public String getTextStringEntry(String name) {
        EntryData value = mEntries.get(name);
        if (value != null && value.mValue != null && value.mValue instanceof Boolean) {
            return (String) value.mValue;
        }
        return null;
    }

    /**
     * TODO(swillden): Write this.
     */
    public byte[] getByteStringEntry(String name) {
        EntryData value = mEntries.get(name);
        if (value != null && value.mValue != null && value.mValue instanceof Boolean) {
            return (byte[]) value.mValue;
        }
        return null;
    }

    /**
     * TODO(swillden): Write this.
     */
    public Collection<String> getAccessControlProfiles(String name) {
        EntryData value = mEntries.get(name);
        if (value != null) {
            return value.mAccessControlProfiles;
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
