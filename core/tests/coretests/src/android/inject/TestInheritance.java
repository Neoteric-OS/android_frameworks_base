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
import android.inject.Singleton;

import junit.framework.TestCase;

/**
 * This class contains tests that verifies that classes that inherit injections
 * in one way or another are handled as expected.
 */
public class TestInheritance extends TestCase {

    interface A {
    }

    interface B {
    }

    static class C1 implements InjectionTarget, Singleton {

        @Inject
        A m1;

    }

    static class C2 implements A {
    }

    static class C3 extends C1 {
    }

    static class C4 extends C3 implements InjectionTarget, Singleton {

        @Inject
        A m2;

    }

    static class C5 extends C1 implements InjectionTarget, Singleton {

        @Inject
        A m2;

    }

    static class C6 extends C5 implements InjectionTarget, Singleton {

        @Inject
        A m3;

    }

    static class C7 implements InjectionTarget, Singleton {

        @Inject
        C4 m1;

    }

    static class C8 extends C3 implements InjectionTarget, Singleton {

        @Inject
        B m2;

    }

    static class C9 implements InjectionTarget, Singleton, B {
    }

    static class C10 implements InjectionTarget, Singleton {

        @Inject(OPTIONAL)
        C8 m1;

    }

    /**
     * Make sure that all fields are injected even though they reside in
     * inherited classes.
     */
    public void testInheritedInjections() {
        DuctTape dt = new DuctTape();

        C2 c2 = new C2();
        C6 c6 = new C6();

        dt.add(c6);
        dt.add(c2);
        dt.apply();

        assertNotNull(c6.m1);
        assertNotNull(c6.m2);
        assertNotNull(c6.m3);
        assertEquals(c2, c6.m1);
        assertEquals(c2, c6.m2);
        assertEquals(c2, c6.m3);
    }

    /**
     * Make sure that even if a class in the the middle of the class hierarchy
     * does implement Injectable when the others do, all fields from ancestors
     * still get injected.
     */
    public void testInheritanceGap() {
        DuctTape dt = new DuctTape();

        C2 c2 = new C2();
        C4 c4 = new C4();

        dt.add(c4);
        dt.add(c2);
        dt.apply();

        assertNotNull(c4.m1);
        assertNotNull(c4.m2);
        assertEquals(c2, c4.m1);
        assertEquals(c2, c4.m2);
    }

    /**
     * Make sure that if a factory that has a complex inheritance chain where
     * inherited classes requires some injections are resolved correctly.
     */
    public void testFactoryInheritance() {
        DuctTape dt = new DuctTape();

        C2 c2 = new C2();
        C7 c7 = new C7();

        dt.add(C4.class);
        dt.add(c2);
        dt.add(c7);
        dt.apply();

        assertNotNull(c7.m1);
        C4 c4 = c7.m1;
        assertNotNull(c4.m1);
        assertNotNull(c4.m2);
        assertEquals(c2, c4.m1);
        assertEquals(c2, c4.m2);
    }

    /**
     * Make sure that a factory that has a complex inheritance chain, where some
     * of the requirements of the inherited classes cannot be met, does not
     * produce.
     */
    public void testInvalidFactoryInheritance() {
        DuctTape dt = new DuctTape();

        C10 c10 = new C10();

        dt.add(C8.class);
        dt.add(new C9());
        dt.add(c10);
        dt.apply();

        assertNull(c10.m1);
    }

}
