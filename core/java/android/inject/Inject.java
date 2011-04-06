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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field for injection. This means that
 * {@link android.inject.DuctTape#apply()} will try to find one or more
 * (depending on whether the field is an array or not) type compatible object
 * references and automatically set the field to that value.
 * @hide
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Inject {

    /**
     * Specifies a filter which excludes all objects that do not have <i>at
     * least one</i> of the given tags. However, if no tags are specified all
     * objects passes the filter.
     * <p>
     * Example:
     *
     * <pre>
     * &#064;Inject
     * EventListener[] mListeners;
     * </pre>
     *
     * will inject all objects of type <code>EventListener</code>, while
     *
     * <pre>
     * &#064;Inject({
     *         IMPORTANT, SOMEWHAT_IMPORTANT
     * })
     * EventListener[] mListeners;
     * </pre>
     *
     * injects only those objects tagged either <code>IMPORTANT</code> or
     * <code>SOMEWHAT_IMPORTANT</code> (which in this example are integer
     * constants assumed to be defined somewhere else in the code).
     * <p>
     * Note that Java allows omitting the curly braces when there is only one
     * value in the array, so injecting all objects with one specific tag looks
     * something like this:
     * <p>
     *
     * <pre>
     * &#064;Inject(OPTIONAL)
     * NetworkClient mClient;
     * </pre>
     *
     * @return Tags on the objects to be injected.
     */
    int[] value() default {};

}
