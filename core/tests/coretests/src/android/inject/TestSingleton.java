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

import junit.framework.TestCase;
import android.inject.DuctTape;
import android.inject.Inject;
import android.inject.InjectionTarget;
import android.inject.Singleton;

public class TestSingleton extends TestCase {

    interface A {
    }

    interface B {
    }

    interface C {
    }

    interface D {
    }

    static class C1 implements InjectionTarget, Singleton {

        @Inject
        A m1;

        @Inject
        B m2;

        @Inject
        C m3;

        @Inject
        D m4;

    }

    static class C2 implements A, B {
    }

    static class C3 implements Singleton, C, D {
    }

    static class C4 implements InjectionTarget, Singleton {

        @Inject
        A[] m1;

        @Inject
        A[] m2;

    }

    static class C5 implements Singleton, A {
    }

    static class C6 implements InjectionTarget, Singleton {

        @Inject
        A m1;

        @Inject
        B[] m2;

    }

    /**
     * Test that unique instances of non-singletons get injected in each field.
     */
    public void testInstantiation() {
        DuctTape dt = new DuctTape();

        C1 c1 = new C1();

        dt.add(c1);
        dt.add(C2.class);
        dt.add(C3.class);
        dt.apply();

        assertNotNull(c1.m1);
        assertNotNull(c1.m2);
        assertTrue(c1.m1 != c1.m2);
        assertEquals(C2.class, c1.m1.getClass());

        assertNotNull(c1.m3);
        assertNotNull(c1.m4);
        assertTrue(c1.m3 == c1.m4);
        assertEquals(C3.class, c1.m3.getClass());
    }

    /**
     * Test that array entries gets updated properly with unique instances.
     */
    public void testUpdatingArray() {
        DuctTape dt = new DuctTape();

        C4 c4 = new C4();

        dt.add(c4);
        dt.add(C2.class);
        dt.add(C5.class);
        dt.apply();

        assertNotNull(c4.m1);
        assertNotNull(c4.m2);
        assertEquals(2, c4.m1.length);
        assertEquals(2, c4.m2.length);
        assertTrue(C5.class == c4.m1[0].getClass() ^ C5.class == c4.m1[1].getClass());
        assertTrue(C2.class == c4.m1[0].getClass() ^ C2.class == c4.m1[1].getClass());
        assertTrue((C2.class == c4.m1[0].getClass() && c4.m1[0] != c4.m2[0])
                || (C2.class == c4.m1[1].getClass() && c4.m1[1] != c4.m2[1]));
    }

    /**
     * Test that when a field already contains an instance of a non-singleton, that
     * instance is reused on a call to apply().
     */
    public void testReuse() {
        DuctTape dt = new DuctTape();

        C6 c6 = new C6();

        dt.add(c6);
        dt.add(C2.class);
        dt.apply();

        assertNotNull(c6.m1);
        C2 c21 = (C2)c6.m1;
        assertNotNull(c6.m2);
        assertEquals(1, c6.m2.length);
        C2 c22 = (C2)c6.m2[0];
        assertTrue(c22 != c21);

        // Affect robustness to trigger re-processing
        dt.add(C1.class);
        dt.remove(C1.class);

        dt.apply();
        assertEquals(c21, c6.m1);
        assertNotNull(c6.m2);
        assertEquals(1, c6.m2.length);
        assertEquals(c22, c6.m2[0]);
    }

}
