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
import static android.inject.DuctTape.ORDERED;

import android.inject.DuctTape;
import android.inject.Inject;
import android.inject.InjectionTarget;
import android.inject.InjectionException;
import android.inject.Singleton;

import junit.framework.TestCase;

/**
 * This class contains tests that make sure that injections marked as optional
 * are handled correctly.
 */
public class TestOptional extends TestCase {

    interface A {
    }

    interface B {
    }

    static class C1 implements InjectionTarget, Singleton {

        @Inject
        A m1;

        @Inject({
                ORDERED, OPTIONAL
        })
        B[] m2;

    }

    static class C2 implements InjectionTarget, Singleton, A {

        @Inject
        A m1;

        @Inject({
                OPTIONAL, ORDERED
        })
        B m2;

    }

    static class C3 implements B {
    }

    /**
     * Make sure that it is possible to tape when there are no available
     * implementations for optional fields.
     */
    public void testMissing() {
        DuctTape dt = new DuctTape();

        C1 c1 = new C1();
        C2 c2 = new C2();

        dt.add(c1);
        dt.add(c2);
        dt.apply();

        assertEquals(c2, c1.m1);
        assertEquals(c2, c2.m1);
        assertNull(c1.m2);
        assertNull(c2.m2);
    }

    /**
     * Make sure that optional fields can handle being injected when there are
     * matching references.
     */
    public void testExisting() {
        DuctTape dt = new DuctTape();

        C1 c1 = new C1();
        C2 c2 = new C2();
        C3 c3 = new C3();

        dt.add(c1);
        dt.add(c2);
        dt.add(c3);
        dt.apply();

        assertEquals(c2, c1.m1);
        assertEquals(c2, c2.m1);
        assertEquals(c3, c2.m2);
        assertEquals(1, c1.m2.length);
        assertEquals(c3, c1.m2[0]);
    }

    /**
     * Provoke an exception by trying to tape when some required interface
     * references cannot be resolved.
     */
    public void testNegative() {
        DuctTape dt = new DuctTape();

        C1 c1 = new C1();

        dt.add(c1);
        try {
            dt.apply();
            fail();
        } catch (InjectionException e) {
        }
    }

}
