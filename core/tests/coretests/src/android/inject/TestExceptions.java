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
import android.inject.InjectionException;
import android.inject.Singleton;

import junit.framework.TestCase;

/**
 * This class contains tests that verifies that exeptions are throws and handled
 * in the expected manner.
 */
public class TestExceptions extends TestCase {

    interface A {
    }

    static class C1 implements InjectionTarget, Singleton {

        @Inject
        A m1;

    }

    static class C2 implements A {

        static final IllegalArgumentException E = new IllegalArgumentException();

        public C2() {
            throw E;
        }

    }

    static class C3 implements A {

        public C3(int a) {
        }

    }

    static class C4 implements A {

        private C4() {
        }

    }

    static abstract class C5 implements A {

        public C5() {
        }

    }

    static class C6 implements A {

        public C6() {
        }

    }

    /**
     * Test that when an exception occurs during factory production it is
     * reported back as an injection exception.
     */
    public void testFactoryException() {
        DuctTape dt = new DuctTape();

        C1 c1 = new C1();

        dt.add(c1);
        dt.add(C2.class);
        try {
            dt.apply();
            fail();
        } catch (InjectionException e) {
            assertEquals(C2.E, e.getCause());
            assertEquals("Uncaught exception during object creation", e.getMessage());
        }
    }

    /**
     * Test that when a factory does not have a constructor that takes zero
     * arguments an injection exception is thrown.
     */
    public void testNoConstructor() {
        DuctTape dt = new DuctTape();

        C1 c1 = new C1();

        dt.add(c1);
        dt.add(C3.class);
        try {
            dt.apply();
            fail();
        } catch (InjectionException e) {
            assertEquals(C3.class.getCanonicalName()
                    + " has no constructor that takes zero arguments", e.getMessage());
        }
    }

    /**
     * Test that attempting to make an abstract factory produce an object causes
     * an injection exception.
     */
    public void testAbstractFactory() {
        DuctTape dt = new DuctTape();

        C1 c1 = new C1();

        dt.add(c1);
        dt.add(C5.class);
        try {
            dt.apply();
            fail();
        } catch (InjectionException e) {
            assertEquals("Factory class " + C5.class.getCanonicalName() + " is abstract",
                    e.getMessage());
        }
    }

    /**
     * Test that creating a setup with several objects that are candidates for
     * the same non-array injection point causes an injection exception.
     */
    public void testAmbuigity() {
        DuctTape dt = new DuctTape();

        C1 c1 = new C1();

        dt.add(c1);
        dt.add(new C6());
        dt.add(new C6());
        try {
            dt.apply();
            fail();
        } catch (InjectionException e) {
            assertEquals("Several objects available for " + C1.class.getCanonicalName() + ".m1",
                    e.getMessage());
        }
    }

    /**
     * Make sure that tag that have a value outside the valid range is rejected
     * with an exception.
     */
    public void testTagRange() {
        DuctTape dt = new DuctTape();

        final String msg = "Tag value must be in range " + DuctTape.MIN_TAG + " to "
                + DuctTape.MAX_TAG;
        try {
            dt.add(new Object(), DuctTape.MIN_TAG - 1);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals(msg, e.getMessage());
        }

        try {
            dt.add(new Object(), DuctTape.MAX_TAG + 1);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals(msg, e.getMessage());
        }

        try {
            dt.add(new Object(), new int[] {
                    178, 24, DuctTape.MIN_TAG - 4, 13
            });
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals(msg, e.getMessage());
        }

        try {
            dt.add(new Object(), new int[] {
                    178, 24, DuctTape.MAX_TAG + 11, 13
            });
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals(msg, e.getMessage());
        }
    }

}
