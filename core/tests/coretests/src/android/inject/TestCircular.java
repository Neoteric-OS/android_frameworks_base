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
 * This class contains tests that verifies that situations where factories has
 * circular dependencies towards each other are handled properly.
 */
public class TestCircular extends TestCase {

    interface A {
    }

    interface B {
    }

    interface C {
    }

    interface D {
    }

    interface E {
    }

    interface F {
    }

    interface G {
    }

    interface H {
    }

    static class C1 implements InjectionTarget, Singleton {

        @Inject
        A m1;

    }

    static class C2 implements InjectionTarget, Singleton, A, D {

        @Inject
        B m1;

    }

    static class C3 implements InjectionTarget, Singleton, B {

        @Inject
        C m1;

    }

    static class C4 implements InjectionTarget, Singleton, C {

        @Inject
        D m1;

    }

    static class C5 implements InjectionTarget, Singleton, A, D, G {

        @Inject
        B m1;

        @Inject
        E[] m2;

    }

    static class C6 implements InjectionTarget, Singleton, E {

        @Inject
        F m1;

    }

    static class C7 implements InjectionTarget, Singleton, F {

        @Inject
        D m1;

    }

    static class C8 implements InjectionTarget, Singleton, E {

        @Inject
        B m1;

        @Inject
        F m2;

    }

    static class C9 implements InjectionTarget, Singleton, F {

        @Inject
        D m1;

        @Inject
        G[] m2;

    }

    static class C10 implements InjectionTarget, Singleton, B, G {

        @Inject
        C m1;

    }

    static class C11 implements InjectionTarget, Singleton {

        @Inject
        A m1;

    }

    static class C12 implements InjectionTarget, Singleton, H {

        static boolean created;

        @Inject
        C m1;

        C12() {
            created = true;
        }

    }

    static class C13 implements InjectionTarget, Singleton {

        @Inject
        H m1;

    }

    static class C14 implements InjectionTarget, C {

        @Inject
        A m1;

    }

    static class C15 implements InjectionTarget, A {

        static boolean created;

        @Inject
        B m1;

        C15() {
            created = true;
        }

    }

    static class C16 implements InjectionTarget, B {

        static boolean created;

        @Inject
        C[] m1;

        C16() {
            created = true;
        }

    }

    /**
     * Make sure that it is possible to use factories that has circular
     * dependencies to create their objects.
     */
    public void testCircularFactories() {
        DuctTape dt = new DuctTape();

        C1 c1 = new C1();

        dt.add(c1);
        dt.add(new Class<?>[] {
                C2.class, C3.class, C4.class
        });

        dt.apply();

        assertNotNull(c1.m1);
        assertEquals(C2.class, c1.m1.getClass());
        C2 c2 = (C2)c1.m1;
        assertEquals(C3.class, c2.m1.getClass());
        C3 c3 = (C3)c2.m1;
        assertEquals(C4.class, c3.m1.getClass());
        C4 c4 = (C4)c3.m1;
        assertEquals(c2, c4.m1);
    }

    /**
     * Make sure that when a factory is a part of several circular dependency
     * chains it still produces its object when the prerequisites are met for
     * all factories in the chains.
     */
    public void testMultipleCircularFactories() {
        DuctTape dt = new DuctTape();

        C1 c1 = new C1();

        dt.add(c1);
        dt.add(new Class<?>[] {
                C3.class, C4.class, C5.class, C6.class, C7.class, C8.class
        });

        dt.apply();

        assertNotNull(c1.m1);
        assertEquals(C5.class, c1.m1.getClass());
        C5 c5 = (C5)c1.m1;
        assertNotNull(c5.m1);
        assertEquals(C3.class, c5.m1.getClass());
        C3 c3 = (C3)c5.m1;
        assertNotNull(c5.m2);
        assertEquals(2, c5.m2.length);
        assertTrue(c5.m2[0].getClass() == C8.class ^ c5.m2[1].getClass() == C8.class);
        assertTrue(c5.m2[0].getClass() == C6.class ^ c5.m2[1].getClass() == C6.class);
        C8 c8;
        C6 c6;
        if (c5.m2[0].getClass() == C8.class) {
            c8 = (C8)c5.m2[0];
            c6 = (C6)c5.m2[1];
        } else {
            c8 = (C8)c5.m2[1];
            c6 = (C6)c5.m2[0];
        }
        assertEquals(c3, c8.m1);
        assertNotNull(c8.m2);
        assertEquals(C7.class, c8.m2.getClass());
        C7 c7 = (C7)c8.m2;
        assertEquals(c7, c6.m1);
        assertEquals(c5, c7.m1);
        assertNotNull(c3.m1);
        assertEquals(C4.class, c3.m1.getClass());
        C4 c4 = (C4)c3.m1;
        assertEquals(c5, c4.m1);
    }

    /**
     * Make sure that a circular dependency is properly invalidated when one of
     * the included factories are discovered to be invalid at a late stage.
     */
    public void testInvalidCircularFactories() {
        DuctTape dt = new DuctTape();

        C11 c11 = new C11();
        C13 c13 = new C13();
        C12.created = false;

        dt.add(c11);
        dt.add(c13);
        dt.add(new Class<?>[] {
                C4.class, C5.class, C6.class, C10.class, C12.class
        });

        try {
            dt.apply();
            fail();
        } catch (InjectionException e) {
        }
        assertTrue(!C12.created);
    }

    /**
     * Make sure that when non-singleton factories depend on each other, this
     * causes the initial factory to not be used rather than resulting in an
     * infinite instantiation loop.
     */
    public void testCircularInstantiation() {
        DuctTape dt = new DuctTape();

        C14 c14 = new C14();

        dt.add(c14);
        dt.add(C14.class);
        dt.add(C15.class);
        dt.add(C16.class);
        C15.created = false;
        C16.created = false;

        try {
            dt.apply();
            fail();
        } catch (InjectionException e) {
        }
        assertTrue(!C15.created);
        assertTrue(!C16.created);
    }

}
