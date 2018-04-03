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

import android.annotation.SystemApi;
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
 */
@SystemApi
public abstract class TypedSystemProperty<T> {
    private static final String TAG = "TypedSystemProperty";
    public static final String NAMESPACE_PLATFORM = "";
    public static final String NAMESPACE_VENDOR = "vendor.";
    public static final String NAMESPACE_ODM = "odm.";
    private static final String PREFIX_WRITEONCE = "ro.";

    /**
     * Implement this to have a custom access rule for system properties
     */
    @SystemApi
    public interface Accessor extends BiPredicate<TypedSystemProperty<?>, Boolean> {
    }

    /**
     * Predefined access rules for system properties readable from all, but not writable from any
     */
    @SystemApi
    public static final Accessor WORLD_READONLY = new Accessor() {
        @Override
        public boolean test(TypedSystemProperty<?> prop, Boolean write) {
            return !write;
        }
    };

    /**
     * Predefined access rules for system properties that are read/writable from platform processes
     * but completely hidden from app processes.
     */
    @SystemApi
    public static final Accessor PLATFORM_WRITABLE = new Accessor() {
        @Override
        public boolean test(TypedSystemProperty<?> prop, Boolean write) {
            return Process.isCoreUid(Process.myUid());
        }
    };

    /**
     * Predefined access rules for system properties that are read-only from platform processes and
     * completely hidden from app processes.
     */
    @SystemApi
    public static final Accessor PLATFORM_READONLY = new Accessor() {
        @Override
        public boolean test(TypedSystemProperty<?> prop, Boolean write) {
            return Process.isCoreUid(Process.myUid()) && !write;
        }
    };

    /**
     * Predefined access rules for system properties that only apps can read.
     */
    @SystemApi
    public static final Accessor APP_READONLY = new Accessor() {
        @Override
        public boolean test(TypedSystemProperty<?> prop, Boolean write) {
            return Process.isApplicationUid(Process.myUid()) && !write;
        }
    };

    private final String mNamespace;
    private final boolean mIsWriteOnce;
    private final String mFullName;
    private final Function<String, T> mValueParser;
    private final Function<T, String> mValueFormatter;
    private final Accessor mAccessor;

    /**
     * Base class for system properties whose value type is {@link Integer}. This class is
     * intentionally designed an abstract class. Vendors or ODMs should extend this class to define
     * their own type of system properties.
     */
    @SystemApi
    public abstract static class IntegerSystemProperty extends TypedSystemProperty<Integer> {
        protected IntegerSystemProperty(String name, String namespace, boolean isWriteOnce,
                Accessor accessor) {
            super(name, namespace, isWriteOnce, v -> Integer.parseInt(v), v -> v.toString(),
                    accessor);
        }
    }

    /**
     * Base class for system properties whose value type is {@link Long}. This class is
     * intentionally designed an abstract class. Vendors or ODMs should extend this class to define
     * their own type of system properties.
     */
    @SystemApi
    public abstract static class LongSystemProperty extends TypedSystemProperty<Long> {
        protected LongSystemProperty(String name, String namespace, boolean isWriteOnce,
                Accessor accessor) {
            super(name, namespace, isWriteOnce, v -> Long.parseLong(v), v -> v.toString(),
                    accessor);
        }
    }

    /**
     * Base class for system properties whose value type is {@link Double}. This class is
     * intentionally designed an abstract class. Vendors or ODMs should extend this class to define
     * their own type of system properties.
     */
    @SystemApi
    public abstract static class DoubleSystemProperty extends TypedSystemProperty<Double> {
        protected DoubleSystemProperty(String name, String namespace, boolean isWriteOnce,
                Accessor accessor) {
            super(name, namespace, isWriteOnce, v -> Double.parseDouble(v), v -> v.toString(),
                    accessor);
        }
    }

    /**
     * Base class for system properties whose value type is {@link Boolean}. This class is
     * intentionally designed an abstract class. Vendors or ODMs should extend this class to define
     * their own type of system properties.
     */
    @SystemApi
    public abstract static class BoleanSystemProperty extends TypedSystemProperty<Boolean> {
        protected BoleanSystemProperty(String name, String namespace, boolean isWriteOnce,
                Accessor accessor) {
            super(name, namespace, isWriteOnce, (String rawValue) -> {
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
     */
    @SystemApi
    public abstract static class StringSystemProperty extends TypedSystemProperty<String> {
        protected StringSystemProperty(String name, String namespace, boolean isWriteOnce,
                Accessor accessor) {
            super(name, namespace, isWriteOnce, v -> v, v -> v, accessor);
        }
    }

    /**
     * Base class for system properties whose value type is @{link Enum}. This class is
     * intentionally designed an abstract class. Vendors or ODMs should extend this class to define
     * their own type of system properties.
     *
     * @param <T> Type of the enum
     */
    @SystemApi
    public abstract static class EnumSystemProperty<T extends Enum<T>>
            extends TypedSystemProperty<T> {
        protected EnumSystemProperty(String name, String namespace, boolean isWriteOnce,
                Class<T> type, Accessor accessor) {
            super(name, namespace, isWriteOnce, v -> Enum.valueOf(type, v), v -> v.toString(),
                    accessor);
        }
    }

    private abstract static class ListSystemProperty<T> extends TypedSystemProperty<List<T>> {
        protected ListSystemProperty(String name, String namespace, boolean isWriteOnce,
                Function<String, T> elementParser, Function<T, String> elementFormatter,
                Accessor accessor) {
            super(name, namespace, isWriteOnce, (String rawValue) -> {
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
     */
    @SystemApi
    public abstract static class IntegerListSystemProperty extends ListSystemProperty<Integer> {
        protected IntegerListSystemProperty(String name, String namespace, boolean isWriteOnce,
                Accessor accessor) {
            super(name, namespace, isWriteOnce, v -> Integer.parseInt(v), v -> v.toString(),
                accessor);
        }
    }

    /**
     * Base class for system properties whose value type is list of {@link Long}. This class is
     * intentionally designed an abstract class. Vendors or ODMs should extend this class to define
     * their own type of system properties.
     */
    @SystemApi
    public abstract static class LongListSystemProperty extends ListSystemProperty<Long> {
        protected LongListSystemProperty(String name, String namespace, boolean isWriteOnce,
                Accessor accessor) {
            super(name, namespace, isWriteOnce, v -> Long.parseLong(v), v -> v.toString(),
                accessor);
        }
    }

    /**
     * Base class for system properties whose value type is list of {@link Double}. This class is
     * intentionally designed an abstract class. Vendors or ODMs should extend this class to define
     * their own type of system properties.
     */
    @SystemApi
    public abstract static class DoubleListSystemProperty extends ListSystemProperty<Double> {
        protected DoubleListSystemProperty(String name, String namespace, boolean isWriteOnce,
                Accessor accessor) {
            super(name, namespace, isWriteOnce, v -> Double.parseDouble(v), v -> v.toString(),
                    accessor);
        }
    }

    /**
     * Base class for system properties whose value type is list of {@link Boolean}. This class is
     * intentionally designed an abstract class. Vendors or ODMs should extend this class to define
     * their own type of system properties.
     */
    @SystemApi
    public abstract static class BoleanListSystemProperty extends ListSystemProperty<Boolean> {
        protected BoleanListSystemProperty(String name, String namespace, boolean isWriteOnce,
                Accessor accessor) {
            super(name, namespace, isWriteOnce, (String rawValue) -> {
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
     */
    @SystemApi
    public abstract static class StringListSystemProperty extends ListSystemProperty<String> {
        protected StringListSystemProperty(String name, String namespace, boolean isWriteOnce,
                Accessor accessor) {
            super(name, namespace, isWriteOnce, v -> v, v -> v, accessor);
        }
    }

    /**
     * Base class for system properties whose value type is list of @{link Enum}. This class is
     * intentionally designed an abstract class. Vendors or ODMs should extend this class to define
     * their own type of system properties.
     *
     * @param <T> Type of the enum
     */
    @SystemApi
    public abstract static class EnumListSystemProperty<T extends Enum<T>>
            extends ListSystemProperty<T> {
        protected EnumListSystemProperty(String name, String namespace, boolean isWriteOnce,
                Class<T> type, Accessor accessor) {
            super(name, namespace, isWriteOnce, v -> Enum.valueOf(type, v), v -> v.toString(),
                    accessor);
        }
    }

    private TypedSystemProperty(String name, String namespace, boolean isWriteOnce,
            Function<String, T> valueParser,
            Function<T, String> valueFormatter,
            Accessor accessor) {
        this.mNamespace = namespace;
        this.mIsWriteOnce = isWriteOnce;
        this.mValueParser = valueParser;
        this.mValueFormatter = valueFormatter;
        this.mAccessor = accessor;
        this.mFullName = (isWriteOnce ? "" : PREFIX_WRITEONCE) + namespace + name;
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
        final boolean isVendorOrOdmNamespace = mNamespace == TypedSystemProperty.NAMESPACE_VENDOR
                || mNamespace == TypedSystemProperty.NAMESPACE_ODM;

        if (isPropDefinedInPlatform) {
            if (isVendorOrOdmNamespace) {
                throw new IllegalArgumentException(
                        "Namespace of a platform defined system property "
                                + mFullName + " must not be " + mNamespace);
            }
        } else {
            if (!isVendorOrOdmNamespace) {
                throw new IllegalArgumentException("Namespace of "
                        + mFullName + " defined in " + this.getClass().getName()
                        + " is " + mNamespace + ". "
                        + "Should be one of " + TypedSystemProperty.NAMESPACE_VENDOR + " or "
                        + TypedSystemProperty.NAMESPACE_ODM);
            }
        }
    }
}
