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

import android.inject.DuctTape;
import android.inject.Inject;
import android.inject.InjectionTarget;
import android.inject.Singleton;

import junit.framework.TestCase;

/**
 * This class contains tests that make sure that duplicates of different kinds
 * are handled in the correct way.
 */
public class TestDuplicates extends TestCase {

    interface A {
    }

    interface B {
    }

    static class C1 implements InjectionTarget, Singleton, A {

        @Inject
        B m1;

    }

    static class C2 implements InjectionTarget, Singleton, A {

        @Inject
        B m1;

    }

    static class C3 implements InjectionTarget, Singleton, B {

        @Inject
        A[] m1;

    }

    /**
     * Verify that only a single instance of an object is included, no matter
     * how many times it was added.
     */
    public void testExistingObjects() {
        DuctTape dt = new DuctTape();

        C1 c1 = new C1();
        C2 c2 = new C2();
        C3 c3 = new C3();

        dt.add(c1);
        dt.add(c1);
        dt.add(new Object[] {
                c1, c1, c2, c1
        });
        dt.add(c3);
        dt.add(c2);
        dt.add(c2);
        dt.add(c3);
        dt.add(c1);
        dt.add(c2);
        dt.add(c3);
        dt.add(c2);
        dt.add(c3);
        dt.add(c3);
        dt.add(new Object[] {
                c3, c3, c2, c1
        });
        dt.apply();

        assertEquals(c3, c1.m1);
        assertEquals(c3, c2.m1);
        assertEquals(2, c3.m1.length);
        assertNotNull(c3.m1[0]);
        assertNotNull(c3.m1[1]);
        assertTrue(c3.m1[0] == c1 ^ c3.m1[1] == c1);
        assertTrue(c3.m1[0] == c2 ^ c3.m1[1] == c2);
    }

    /**
     * Verify that even though a factory is added several times, it is only used
     * once to produce an object.
     */
    public void testExistingFactories() {
        DuctTape dt = new DuctTape();

        C1 c1 = new C1();

        dt.add(c1);
        dt.add(C3.class);
        dt.add(C3.class);
        dt.add(new Class<?>[] {
            C2.class
        });
        dt.add(C3.class);
        dt.add(new Class<?>[] {
                C3.class, C2.class, C3.class, C3.class
        });
        dt.apply();

        assertEquals(C3.class, c1.m1.getClass());
        C3 c3 = (C3)c1.m1;
        assertEquals(2, c3.m1.length);
        assertTrue(c3.m1[0] == c1 ^ c3.m1[1] == c1);
    }

    /**
     * Verify that when an object is added several times the robustness of the
     * tape is not affected.
     */
    public void testUnaffectedRobustness() {
        DuctTape dt = new DuctTape();

        Object o = new Object();
        dt.add(o);
        dt.apply();
        dt.add(o);

        assertTrue(dt.isRobust());

        dt.add(new Object[] {
                o, o, o, o, o
        });

        assertTrue(dt.isRobust());
    }

}
