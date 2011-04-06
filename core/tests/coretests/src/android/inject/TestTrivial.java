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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import android.inject.DuctTape;
import android.inject.Inject;
import android.inject.InjectionTarget;
import android.inject.Singleton;

import junit.framework.TestCase;

/**
 * This class contains tests that are trivial in nature and do not directly fall
 * into any other existing test category.
 */
public class TestTrivial extends TestCase {

    interface A {
    }

    interface B {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface A1 {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface A2 {
    }

    static class C1 implements InjectionTarget, Singleton, A {

        @Inject
        B m1;

        @A2
        int dummy;

    }

    static class C2 implements InjectionTarget, Singleton, A {

        @A2
        @Inject
        @A1
        B m1;

    }

    static class C3 implements InjectionTarget, Singleton, B {

        @Inject
        A[] m1;

        @A1
        @A2
        int dummy;

    }

    /**
     * Make sure that an untouched setup is still possible to tape.
     */
    public void testUntouched() {
        new DuctTape().apply();
    }

    /**
     * Try gluing a setup where there are no objects at all.
     */
    public void testNoObjects() {
        DuctTape dt = new DuctTape();

        // Make dirty
        dt.add(Object.class);
        assertTrue(!dt.isRobust());

        dt.apply();
    }

    /**
     * Try a simple setup, where all objects already exist, and verify that they
     * are bound together correctly.
     */
    public void testExistingObjects() {
        DuctTape dt = new DuctTape();

        C1 c1 = new C1();
        C2 c2 = new C2();
        C3 c3 = new C3();

        dt.add(c1);
        dt.add(c2);
        dt.add(c3);
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
     * Try simple setup where some required interfaces are missing, but is
     * available from known classes. This should result in that the classes are
     * instantiated and bound correctly to the class in need of their
     * interfaces.
     */
    public void testFactories() {
        DuctTape dt = new DuctTape();

        C3 c3 = new C3();

        dt.add(C1.class);
        dt.add(C2.class);
        dt.add(c3);
        dt.apply();

        assertEquals(2, c3.m1.length);
        assertNotNull(c3.m1[0]);
        assertNotNull(c3.m1[1]);
        assertTrue(c3.m1[0].getClass() == C1.class ^ c3.m1[1].getClass() == C1.class);
        assertTrue(c3.m1[0].getClass() == C2.class ^ c3.m1[1].getClass() == C2.class);
    }

    /**
     * Try clearing the state and verify that it does indeed reset the instance
     * to its initial state.
     */
    public void testClear() {
        DuctTape dt = new DuctTape();

        C3 c3 = new C3();
        C2 c2 = new C2();

        dt.add(c3);
        dt.add(new C1());
        dt.add(new C1());
        dt.add(new C1());
        dt.add(C1.class);
        dt.clear();
        dt.add(c3);
        dt.add(c2);

        dt.apply();

        assertNotNull(c2.m1);
        assertNotNull(c3.m1);
        assertEquals(1, c3.m1.length);
        assertEquals(c2, c3.m1[0]);
        assertEquals(c3, c2.m1);
    }

}
