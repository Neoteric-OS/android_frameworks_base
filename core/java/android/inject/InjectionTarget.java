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
 * Interface that must be implemented by all classes in which injections should
 * be made. In other words this means that all
 * {@link android.inject.Inject} annotations will be completely ignored
 * unless your class implements this interface.
 * <p>
 * <h3>Correct</h3>
 *
 * <pre>
 * class MyClass implements InjectionTarget {
 *
 *     &#064;Inject
 *     int mYeyIWillGetAnInjection;
 *
 * }
 * </pre>
 * <p>
 * <h3>Incorrect</h3>
 *
 * <pre>
 * class MyClass {
 *
 *     &#064;Inject
 *     int mBummerIWillBeIgnored;
 *
 * }
 * </pre>
 * @hide
 */
public interface InjectionTarget {

    // Intentionally empty

}
