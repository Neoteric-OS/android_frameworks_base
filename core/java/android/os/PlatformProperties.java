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

import android.os.TypedSystemProperty.BoleanSystemProperty;
import android.os.TypedSystemProperty.EnumSystemProperty;
import android.os.TypedSystemProperty.IntegerSystemProperty;
import android.os.TypedSystemProperty.Owner;
import android.os.TypedSystemProperty.StringSystemProperty;

/**
 * Set of platform defined system properties.
 * @hide
 */
public final class PlatformProperties {
    private PlatformProperties() {
    }

    private static class IntegerProperty extends IntegerSystemProperty {
        protected IntegerProperty(String name, Owner owner, boolean isWriteOnce,
                Accessor accessor) {
            super(name, owner, isWriteOnce, accessor);
        }
    }

    private static class BooleanProperty extends BoleanSystemProperty {
        protected BooleanProperty(String name, Owner owner, boolean isWriteOnce,
                Accessor accessor) {
            super(name, owner, isWriteOnce, accessor);
        }
    }

    private static class StringProperty extends StringSystemProperty {
        protected StringProperty(String name, Owner owner, boolean isWriteOnce,
                Accessor accessor) {
            super(name, owner, isWriteOnce, accessor);
        }
    }

    private static class EnumProperty<T extends Enum<T>>
            extends EnumSystemProperty<T> {
        protected EnumProperty(String name, Owner owner, boolean isWriteOnce, Class<T> type,
                Accessor accessor) {
            super(name, owner, isWriteOnce, type, accessor);
        }
    }
}
