/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.inject;

/**
 * When a class implements this interface the injection framework will only
 * spawn one instance of the class and then injected it everywhere, as opposed
 * to the default behavior where a new instance is injected in for each injected
 * field.
 * @hide
 */
public interface Singleton {

    // Intentionally empty

}
