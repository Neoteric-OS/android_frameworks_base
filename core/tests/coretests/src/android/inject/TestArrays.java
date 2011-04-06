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

import static android.inject.DuctTape.OPTIONAL;

import android.inject.DuctTape;
import android.inject.Inject;
import android.inject.InjectionTarget;
import android.inject.InjectionException;
import android.inject.Singleton;

import junit.framework.TestCase;

/**
 * This class contains tests that make sure that fields with array types are
 * injected correctly depending on the situation.
 */
public class TestArrays extends TestCase {

    interface A {
    }

    static class C1 implements InjectionTarget, Singleton {

        @Inject
        A[] m1;

        @Inject(OPTIONAL)
        A m2;

    }

    static class C2 implements InjectionTarget, Singleton, A {
    }

    static class C3 {

        @Inject(OPTIONAL)
        A[] m1;

    }

    static class C4 implements InjectionTarget, Singleton {

        @Inject
        A[][] m1;

        @Inject
        A[][][] m2;

    }

    /**
     * Make sure that 1D-arrays that are added as objects (as opposed to each
     * element being added as a separate object) are properly injected where
     * appropriate.
     */
    public void testOneDimArray() {
        DuctTape dt = new DuctTape();

        C1 c1 = new C1();

        dt.add(c1);
        Object o = new C2[] {
                new C2(), new C2()
        };
        dt.add(o);
        dt.apply();

        assertNotNull(c1.m1);
        assertNull(c1.m2);
        assertEquals(2, c1.m1.length);
        assertEquals(o, c1.m1);
    }

    /**
     * Make sure that a 2D-array that is added as an object is not injected in
     * any way into a field of the same element type but which has only one
     * dimension.
     */
    public void testTooManyDimsArray() {
        DuctTape dt = new DuctTape();

        C3 c3 = new C3();

        dt.add(c3);
        Object o = new C2[][] {
                {}, {
                        new C2(), new C2()
                }
        };
        dt.add(o);
        dt.apply();

        assertNull(c3.m1);
    }

    /**
     * Make sure that a 2D-array is properly injected into both a 2D-field and a
     * 3D-field, where the latter will have its first dimension set to the same
     * size as the number of 2D-arrays injected.
     */
    public void testMultiDimsArray() {
        DuctTape dt = new DuctTape();

        C4 c4 = new C4();

        dt.add(c4);
        Object o = new C2[][] {
                {}, {
                        new C2(), new C2()
                }
        };
        dt.add(o);
        dt.apply();

        assertEquals(o, c4.m1);
        assertNotNull(c4.m2);
        assertEquals(1, c4.m2.length);
        assertEquals(o, c4.m2[0]);
    }

    /**
     * Make sure that when there are both a 1D-array object and several object
     * of the same type that can be injected in a field of array type an
     * exception is thrown.
     */
    public void testAmbiguousArrays() {
        DuctTape dt = new DuctTape();

        C1 c1 = new C1();

        dt.add(c1);
        Object o = new C2[] {
                new C2(), new C2()
        };
        dt.add(o);
        dt.add(new C2());

        try {
            dt.apply();
            fail();
        } catch (InjectionException e) {
        }
    }

}
