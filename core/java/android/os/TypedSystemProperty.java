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

package android.os;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Base type of all type-safe system properties.
 *
 * @param <T> Type of the value of this system property.
 * @hide
 */
public abstract class TypedSystemProperty<T> {
    private static final String TAG = "TypedSystemProperty";
    private static final String PREFIX_WRITEONCE = "ro.";

    public enum Owner {
        PLATFORM,
        VENDOR,
        ODM;

        private String mNamespace;
        public String getNamespace() {
            return mNamespace;
        }

        static {
            PLATFORM.mNamespace = "";
            VENDOR.mNamespace = "vendor.";
            ODM.mNamespace = "odm.";
        }
    }

    /**
     * Wheter a UID belongs to a system core component or not.
     * TODO: Replace this with Process.isCoreUid()?
     */
    private static boolean isCoreUid(int uid) {
        if (uid >= 0) {
            final int appId = UserHandle.getAppId(uid);
            return appId < Process.FIRST_APPLICATION_UID;
        } else {
            return false;
        }
    }

    /**
     * Implement this to have a custom access rule for system properties
     * @hide
     */
    public interface Accessor extends BiPredicate<TypedSystemProperty<?>, Boolean> {
    }

    /**
     * Predefined access rules for system properties readable from all, but not writable from any
     * @ hide
     */
    public static final Accessor WORLD_READONLY = new Accessor() {
        @Override
        public boolean test(TypedSystemProperty<?> prop, Boolean write) {
            return !write;
        }
    };

    /**
     * Predefined access rules for system properties that are read/writable from platform processes
     * but completely hidden from app processes.
     * @hide
     */
    public static final Accessor PLATFORM_WRITABLE = new Accessor() {
        @Override
        public boolean test(TypedSystemProperty<?> prop, Boolean write) {
            return isCoreUid(Process.myUid());
        }
    };

    /**
     * Predefined access rules for system properties that are read-only from platform processes and
     * completely hidden from app processes.
     * @hide
     */
    public static final Accessor PLATFORM_READONLY = new Accessor() {
        @Override
        public boolean test(TypedSystemProperty<?> prop, Boolean write) {
            return isCoreUid(Process.myUid()) && !write;
        }
    };

    /**
     * Predefined access rules for system properties that only apps can read.
     * @hide
     */
    public static final Accessor APP_READONLY = new Accessor() {
        @Override
        public boolean test(TypedSystemProperty<?> prop, Boolean write) {
            return Process.isApplicationUid(Process.myUid()) && !write;
        }
    };

    private final Owner mOwner;
    private final boolean mIsWriteOnce;
    private final String mFullName;
    private final Function<String, T> mValueParser;
    private final Function<T, String> mValueFormatter;
    private final Accessor mAccessor;

    /**
     * Base class for system properties whose value type is {@link Integer}. This class is
     * intentionally designed an abstract class. Vendors or ODMs should extend this class to define
     * their own type of system properties.
     * @hide
     */
    public abstract static class IntegerSystemProperty extends TypedSystemProperty<Integer> {
        protected IntegerSystemProperty(String name, Owner owner, boolean isWriteOnce,
                Accessor accessor) {
            super(name, owner, isWriteOnce, v -> Integer.parseInt(v), v -> v.toString(),
                    accessor);
        }
    }

    /**
     * Base class for system properties whose value type is {@link Long}. This class is
     * intentionally designed an abstract class. Vendors or ODMs should extend this class to define
     * their own type of system properties.
     * @hide
     */
    public abstract static class LongSystemProperty extends TypedSystemProperty<Long> {
        protected LongSystemProperty(String name, Owner owner, boolean isWriteOnce,
                Accessor accessor) {
            super(name, owner, isWriteOnce, v -> Long.parseLong(v), v -> v.toString(),
                    accessor);
        }
    }

    /**
     * Base class for system properties whose value type is {@link Double}. This class is
     * intentionally designed an abstract class. Vendors or ODMs should extend this class to define
     * their own type of system properties.
     * @hide
     */
    public abstract static class DoubleSystemProperty extends TypedSystemProperty<Double> {
        protected DoubleSystemProperty(String name, Owner owner, boolean isWriteOnce,
                Accessor accessor) {
            super(name, owner, isWriteOnce, v -> Double.parseDouble(v), v -> v.toString(),
                    accessor);
        }
    }

    /**
     * Base class for system properties whose value type is {@link Boolean}. This class is
     * intentionally designed an abstract class. Vendors or ODMs should extend this class to define
     * their own type of system properties.
     * @hide
     */
    public abstract static class BoleanSystemProperty extends TypedSystemProperty<Boolean> {
        protected BoleanSystemProperty(String name, Owner owner, boolean isWriteOnce,
                Accessor accessor) {
            super(name, owner, isWriteOnce, (String rawValue) -> {
                switch (rawValue.toLowerCase()) {
                    case "1":
                    case "y":
                    case "yes":
                    case "on":
                    case "true":
                        return true;
                    case "0":
                    case "n":
                    case "no":
                    case "off":
                    case "false":
                        return false;
                    default:
                        return null;
                }
            }, v -> v.toString(), accessor);
        }
    }

    /**
     * Base class for system properties whose value type is {@link String}. This class is
     * intentionally designed an abstract class. Vendors or ODMs should extend this class to define
     * their own type of system properties.
     * @hide
     */
    public abstract static class StringSystemProperty extends TypedSystemProperty<String> {
        protected StringSystemProperty(String name, Owner owner, boolean isWriteOnce,
                Accessor accessor) {
            super(name, owner, isWriteOnce, v -> v, v -> v, accessor);
        }
    }

    /**
     * Base class for system properties whose value type is @{link Enum}. This class is
     * intentionally designed an abstract class. Vendors or ODMs should extend this class to define
     * their own type of system properties.
     *
     * @param <T> Type of the enum
     * @hide
     */
    public abstract static class EnumSystemProperty<T extends Enum<T>>
            extends TypedSystemProperty<T> {
        protected EnumSystemProperty(String name, Owner owner, boolean isWriteOnce,
                Class<T> type, Accessor accessor) {
            super(name, owner, isWriteOnce, v -> Enum.valueOf(type, v), v -> v.toString(),
                    accessor);
        }
    }

    private abstract static class ListSystemProperty<T> extends TypedSystemProperty<List<T>> {
        protected ListSystemProperty(String name, Owner owner, boolean isWriteOnce,
                Function<String, T> elementParser, Function<T, String> elementFormatter,
                Accessor accessor) {
            super(name, owner, isWriteOnce, (String rawValue) -> {
                ArrayList<T> ret = new ArrayList<T>();
                for (String str: rawValue.split(",")) {
                    ret.add(elementParser.apply(str));
                }
                return ret;
            }, (List<T> list) -> {
                StringJoiner joiner = new StringJoiner(",");
                for (T element: list) {
                    joiner.add(elementFormatter.apply(element));
                }
                return joiner.toString();
            }, accessor);
        }
    }

    /**
     * Base class for system properties whose value type is list of {@link Double}. This class is
     * intentionally designed an abstract class. Vendors or ODMs should extend this class to define
     * their own type of system properties.
     * @hide
     */
    public abstract static class IntegerListSystemProperty extends ListSystemProperty<Integer> {
        protected IntegerListSystemProperty(String name, Owner owner, boolean isWriteOnce,
                Accessor accessor) {
            super(name, owner, isWriteOnce, v -> Integer.parseInt(v), v -> v.toString(),
                accessor);
        }
    }

    /**
     * Base class for system properties whose value type is list of {@link Long}. This class is
     * intentionally designed an abstract class. Vendors or ODMs should extend this class to define
     * their own type of system properties.
     * @hide
     */
    public abstract static class LongListSystemProperty extends ListSystemProperty<Long> {
        protected LongListSystemProperty(String name, Owner owner, boolean isWriteOnce,
                Accessor accessor) {
            super(name, owner, isWriteOnce, v -> Long.parseLong(v), v -> v.toString(),
                accessor);
        }
    }

    /**
     * Base class for system properties whose value type is list of {@link Double}. This class is
     * intentionally designed an abstract class. Vendors or ODMs should extend this class to define
     * their own type of system properties.
     * @hide
     */
    public abstract static class DoubleListSystemProperty extends ListSystemProperty<Double> {
        protected DoubleListSystemProperty(String name, Owner owner, boolean isWriteOnce,
                Accessor accessor) {
            super(name, owner, isWriteOnce, v -> Double.parseDouble(v), v -> v.toString(),
                    accessor);
        }
    }

    /**
     * Base class for system properties whose value type is list of {@link Boolean}. This class is
     * intentionally designed an abstract class. Vendors or ODMs should extend this class to define
     * their own type of system properties.
     * @hide
     */
    public abstract static class BoleanListSystemProperty extends ListSystemProperty<Boolean> {
        protected BoleanListSystemProperty(String name, Owner owner, boolean isWriteOnce,
                Accessor accessor) {
            super(name, owner, isWriteOnce, (String rawValue) -> {
                switch (rawValue.toLowerCase()) {
                    case "1":
                    case "y":
                    case "yes":
                    case "on":
                    case "true":
                        return true;
                    case "0":
                    case "n":
                    case "no":
                    case "off":
                    case "false":
                        return false;
                    default:
                        return null;
                }
            }, v -> v.toString(), accessor);
        }
    }

     /**
     * Base class for system properties whose value type is list of {@link String}. This class is
     * intentionally designed an abstract class. Vendors or ODMs should extend this class to define
     * their own type of system properties.
     * @hide
     */
    public abstract static class StringListSystemProperty extends ListSystemProperty<String> {
        protected StringListSystemProperty(String name, Owner owner, boolean isWriteOnce,
                Accessor accessor) {
            super(name, owner, isWriteOnce, v -> v, v -> v, accessor);
        }
    }

    /**
     * Base class for system properties whose value type is list of @{link Enum}. This class is
     * intentionally designed an abstract class. Vendors or ODMs should extend this class to define
     * their own type of system properties.
     *
     * @param <T> Type of the enum
     * @hide
     */
    public abstract static class EnumListSystemProperty<T extends Enum<T>>
            extends ListSystemProperty<T> {
        protected EnumListSystemProperty(String name, Owner owner, boolean isWriteOnce,
                Class<T> type, Accessor accessor) {
            super(name, owner, isWriteOnce, v -> Enum.valueOf(type, v), v -> v.toString(),
                    accessor);
        }
    }

    private TypedSystemProperty(String name, Owner owner, boolean isWriteOnce,
            Function<String, T> valueParser,
            Function<T, String> valueFormatter,
            Accessor accessor) {
        this.mOwner = owner;
        this.mIsWriteOnce = isWriteOnce;
        this.mValueParser = valueParser;
        this.mValueFormatter = valueFormatter;
        this.mAccessor = accessor;
        this.mFullName = (isWriteOnce ? "" : PREFIX_WRITEONCE) + owner.getNamespace() + name;
        checkNamespace();
    }

    @Override
    public String toString() {
        return mFullName;
    }

    /**
     * Returns the value of this property.
     */
    public Optional<T> get() {
        if (!mAccessor.test(this, false /* read */)) {
            Log.e(TAG, "This process does not have read access to " + mFullName);
            // TODO: throw checked exception here?
            return Optional.empty();
        }
        final String rawValue = SystemProperties.get(mFullName);
        if (rawValue.isEmpty()) {
            return Optional.empty();
        }

        final T parsedValue;
        try {
            parsedValue = mValueParser.apply(rawValue);
        } catch (Exception e) {
            // Failing to parse the value is considered as being an empty sysprop.
            // Exception is intentionally not triggered as this might be used to kill
            // an app by externally setting the system property to an invalid value.
            Log.e(TAG, "Failed to parse the value of "
                    + mFullName + "(" + rawValue + ")", e);
            return Optional.empty();
        }
        if (parsedValue == null) {
            Log.e(TAG, "Failed to parse the value of "
                    + mFullName + "(" + rawValue + ")");
        }
        return Optional.ofNullable(parsedValue);
    }

    /**
     * Sets the value of the system property.
     *
     * @param value the new value
     */
    public void set(T value) {
        if (!mAccessor.test(this, true /* write */)) {
            Log.e(TAG, "This process does not have write access to " + mFullName);
            // TODO: throw checked exception here?
            return;
        }
        final String formattedValue = mValueFormatter.apply(value);
        if (mIsWriteOnce && get().isPresent()) {
            Log.w(TAG, mFullName + " can't be overwritten. Current value: " + get().get());
            // TODO: throw a checked exception here?
        }
        SystemProperties.set(mFullName, formattedValue);
    }

    private void checkNamespace() throws IllegalArgumentException {
        final boolean isPropDefinedInPlatform = this.getClass()
                .getDeclaringClass() == PlatformProperties.class;
        final boolean isOwnerVendorOrOdm = mOwner == Owner.VENDOR || mOwner == Owner.ODM;

        if (isPropDefinedInPlatform) {
            if (isOwnerVendorOrOdm) {
                throw new IllegalArgumentException(
                        "Namespace of a platform defined system property "
                                + mFullName + " must not be " + mOwner.getNamespace());
            }
        } else {
            if (!isOwnerVendorOrOdm) {
                throw new IllegalArgumentException("Namespace of "
                        + mFullName + " defined in " + this.getClass().getName()
                        + " is \"" + mOwner.getNamespace() + "\"."
                        + "Should be one of " + Owner.VENDOR.getNamespace() + " or "
                        + Owner.ODM.getNamespace());
            }
        }
    }
}
