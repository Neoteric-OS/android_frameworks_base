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

public class EntryNamespace {

    private class EntryData {
        EntryData(Object value, Collection<String> accessControlProfiles,
                byte[] staticAuthenticationData, boolean directlyAccessible) {
            this.value = value;
            this.accessControlProfiles = accessControlProfiles;
            this.staticAuthenticationData = staticAuthenticationData;
            this.directlyAccessible = directlyAccessible;
        }

        Object value; // One of String, byte[], Long or Boolean
        Collection<String> accessControlProfiles;
        boolean directlyAccessible;
        byte[] staticAuthenticationData;
    }

    private String namespace;
    private Map<String, EntryData> entries = new HashMap<>();

    public class Builder {
        private EntryNamespace entryNamespace;

        public Builder(String namespace) {
            this.entryNamespace = new EntryNamespace(namespace);
        }

        public Builder addEntryName(String name) {
            entryNamespace.entries.put(name, null);
            return this;
        }

        public Builder addBooleanEntry(String name, Collection<String> accessControlProfileNames,
                boolean value, boolean directlyAccessible) {
            return addEntry(name, accessControlProfileNames, value,
                    null /* staticAuthenticationData */, directlyAccessible);
        }

        public Builder addIntegerEntry(String name, Collection<String> accessControlProfileNames,
                long value, boolean directlyAccessible) {
            return addEntry(name, accessControlProfileNames, value,
                    null /* staticAuthenticationData */, directlyAccessible);
        }

        public Builder addBytestringEntry(String name, Collection<String> accessControlProfileNames,
                byte[] value, boolean directlyAccessible) {
            return addEntry(name, accessControlProfileNames, value,
                    null /* staticAuthenticationData */, directlyAccessible);
        }

        public Builder addTextStringEntry(String name, Collection<String> accessControlProfileNames,
                String value, boolean directlyAccessible) {
            return addEntry(name, accessControlProfileNames, value,
                    null /* staticAuthenticationData */, directlyAccessible);
        }

        Builder addBooleanEntry(String name, Collection<String> accessControlProfileNames,
                boolean value, byte[] staticAuthenticationData, boolean directlyAccessible) {
            return addEntry(name, accessControlProfileNames, value, staticAuthenticationData,
                    directlyAccessible);
        }

        Builder addIntegerEntry(String name, Collection<String> accessControlProfileNames,
                long value, byte[] staticAuthenticationData, boolean directlyAccessible) {
            return addEntry(name, accessControlProfileNames, value, staticAuthenticationData,
                    directlyAccessible);
        }

        Builder addBytestringEntry(String name, Collection<String> accessControlProfileNames,
                byte[] value, byte[] staticAuthenticationData, boolean directlyAccessible) {
            return addEntry(name, accessControlProfileNames, value, staticAuthenticationData,
                    directlyAccessible);
        }

        Builder addTextStringEntry(String name, Collection<String> accessControlProfileNames,
                String value, byte[] staticAuthenticationData, boolean directlyAccessible) {
            return addEntry(name, accessControlProfileNames, value, staticAuthenticationData,
                    directlyAccessible);
        }

        private Builder addEntry(String name, Collection<String> accessControlProfileNames,
                Object value, byte[] staticAuthenticationData, boolean directlyAccessible) {
            entryNamespace.entries.put(name, new EntryData(value, accessControlProfileNames,
                    staticAuthenticationData, directlyAccessible));
            return this;
        }

        public EntryNamespace build() {
            return entryNamespace;
        }
    }

    private EntryNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getNamespaceName() {
        return namespace;
    }

    public Collection<String> getEntryNames() {
        return Collections.unmodifiableCollection(entries.keySet());
    }

    public boolean isDirectlyAccessible(String name) {
        return entries.get(name).directlyAccessible;
    }

    public Collection<String> getDirectlyAccessibleNames() {
        Collection<String> result = new ArrayList<String>();
        for (Entry<String, EntryData> entry : entries.entrySet()) {
            if (entry.getValue().directlyAccessible) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public Boolean getBooleanEntry(String name) {
        EntryData value = entries.get(name);
        if (value != null && value.value != null && value.value instanceof Boolean) {
            return (Boolean) value.value;
        }
        return null;
    }

    public Long getIntegerEntry(String name) {
        EntryData value = entries.get(name);
        if (value != null && value.value != null && value.value instanceof Long) {
            return (Long) value.value;
        }
        return null;
    }

    public String getTextStringEntry(String name) {
        EntryData value = entries.get(name);
        if (value != null && value.value != null && value.value instanceof Boolean) {
            return (String) value.value;
        }
        return null;
    }

    public byte[] getByteStringEntry(String name) {
        EntryData value = entries.get(name);
        if (value != null && value.value != null && value.value instanceof Boolean) {
            return (byte[]) value.value;
        }
        return null;
    }

    public Object getEntryValue(String name) {
        return entries.get(name).value;
    }

    public byte[] getStaticAuthenticationData(String name) {
        return entries.get(name).staticAuthenticationData;
    }
}
