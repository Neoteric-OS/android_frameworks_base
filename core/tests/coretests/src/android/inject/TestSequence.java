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
import android.inject.Singleton;

import junit.framework.TestCase;

/**
 * This class contains tests that make sure that array injections that requires
 * the references to be ordered by dependency are made correctly.
 */
public class TestSequence extends TestCase {

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

    interface All {
    }

    interface Some {
    }

    static class C1 implements InjectionTarget, Singleton {

        @Inject(ORDERED)
        All[] m1;

    }

    static class C2 implements InjectionTarget, Singleton, All {

        @Inject
        A m1;

        @Inject
        B m2;

    }

    static class C3 implements InjectionTarget, Singleton, A, All {

        @Inject
        B m1;

    }

    static class C4 implements InjectionTarget, Singleton, B, All {

        @Inject
        C m1;

    }

    static class C5 implements C, All {
    }

    static class C6 implements InjectionTarget, Singleton, F, All, Some {

        @Inject
        E m1;

        @Inject
        G m2;

    }

    static class C7 implements InjectionTarget, Singleton, F, G, All, Some {

        @Inject
        D m1;

    }

    static class C8 implements D, E, All, Some {
    }

    static class C9 implements InjectionTarget, Singleton, All, Some {

        @Inject
        F[] m1;

        @Inject
        E m2;

    }

    static class C10 implements InjectionTarget, Singleton {

        @Inject(ORDERED)
        Some[] m1;

    }

    static class C11 implements InjectionTarget, Singleton, C, All {

        @Inject
        A m1;

    }

    static class C12 implements InjectionTarget, Singleton, All {

        @Inject
        A m1;

    }

    static class C13 implements InjectionTarget, Singleton, A, All {

        @Inject
        B m1;

    }

    static class C14 implements InjectionTarget, Singleton, B, All {

        @Inject
        C m1;

    }

    static class C15 implements InjectionTarget, Singleton, C, All {

        @Inject
        D m1;

    }

    static class C16 implements InjectionTarget, Singleton, D, All {

        @Inject(OPTIONAL)
        A m1;

    }

    static class C17 implements InjectionTarget, Singleton, A {

        @Inject(ORDERED)
        A[] m1;

    }

    static class C18 implements InjectionTarget, Singleton, A {

        @Inject
        A[] m1;

    }

    /*
     * Check that the sequence of references injected into arrays reflects the
     * dependencies between the referenced objects.
     */
    public void testSingleSequence() {

        DuctTape dt = new DuctTape();

        C1 c1 = new C1();
        C2 c2 = new C2();
        C3 c3 = new C3();
        C4 c4 = new C4();
        C5 c5 = new C5();

        dt.add(c1);
        dt.add(c4);
        dt.add(c3);
        dt.add(c2);
        dt.add(c5);
        dt.apply();

        assertNotNull(c1.m1);
        assertEquals(4, c1.m1.length);
        assertEquals(c5, c1.m1[0]);
        assertEquals(c4, c1.m1[1]);
        assertEquals(c3, c1.m1[2]);
        assertEquals(c2, c1.m1[3]);
    }

    /**
     * Check that the call sequence still gets correct even when there are
     * multiple sequence groups present in the dependency graph.
     */
    public void testMultipleSequences() {
        DuctTape dt = new DuctTape();

        C1 c1 = new C1();
        C2 c2 = new C2();
        C3 c3 = new C3();
        C4 c4 = new C4();
        C5 c5 = new C5();
        C6 c6 = new C6();
        C7 c7 = new C7();
        C8 c8 = new C8();
        C9 c9 = new C9();
        C10 c10 = new C10();

        dt.add(c6);
        dt.add(c9);
        dt.add(c1);
        dt.add(c7);
        dt.add(c4);
        dt.add(c8);
        dt.add(c3);
        dt.add(c10);
        dt.add(c2);
        dt.add(c5);
        dt.apply();

        assertNotNull(c1.m1);
        assertEquals(8, c1.m1.length);
        assertTrue(c5 == c1.m1[0] || c8 == c1.m1[0]);
        assertTrue(c4 == c1.m1[1] || c7 == c1.m1[1]);
        assertTrue(c3 == c1.m1[2] || c6 == c1.m1[2]);
        assertTrue(c2 == c1.m1[3] || c9 == c1.m1[3]);
        assertTrue(c5 == c1.m1[4] || c8 == c1.m1[4]);
        assertTrue(c4 == c1.m1[5] || c7 == c1.m1[5]);
        assertTrue(c3 == c1.m1[6] || c6 == c1.m1[6]);
        assertTrue(c2 == c1.m1[7] || c9 == c1.m1[7]);
        assertNotNull(c10.m1);
        assertEquals(4, c10.m1.length);
        assertEquals(c8, c10.m1[0]);
        assertEquals(c7, c10.m1[1]);
        assertEquals(c6, c10.m1[2]);
        assertEquals(c9, c10.m1[3]);
    }

    /**
     * Check that circular dependencies are treated as a single dependency node.
     */
    public void testCircularDependencies() {
        DuctTape dt = new DuctTape();

        C1 c1 = new C1();
        C2 c2 = new C2();
        C3 c3 = new C3();
        C4 c4 = new C4();
        C11 c11 = new C11();
        C12 c12 = new C12();

        dt.add(c1);
        dt.add(c3);
        dt.add(c11);
        dt.add(c2);
        dt.add(c12);
        dt.add(c4);
        dt.apply();

        assertNotNull(c1.m1);
        assertEquals(5, c1.m1.length);
        assertTrue(c4 == c1.m1[0] || c3 == c1.m1[0] || c11 == c1.m1[0]);
        assertTrue(c4 == c1.m1[1] || c3 == c1.m1[1] || c11 == c1.m1[1]);
        assertTrue(c4 == c1.m1[2] || c3 == c1.m1[2] || c11 == c1.m1[2]);
        assertTrue(c2 == c1.m1[3] || c12 == c1.m1[3]);
        assertTrue(c2 == c1.m1[4] || c12 == c1.m1[4]);
    }

    /**
     * Check that optional injections are not treated as dependencies in the
     * call sequence calculation.
     */
    public void testCircularOptionals() {
        DuctTape dt = new DuctTape();

        C1 c1 = new C1();
        C13 c13 = new C13();
        C14 c14 = new C14();
        C15 c15 = new C15();
        C16 c16 = new C16();

        dt.add(c1);
        dt.add(c16);
        dt.add(c14);
        dt.add(c15);
        dt.add(c13);
        dt.apply();

        assertNotNull(c1.m1);
        assertEquals(4, c1.m1.length);
        assertEquals(c16, c1.m1[0]);
        assertEquals(c15, c1.m1[1]);
        assertEquals(c14, c1.m1[2]);
        assertEquals(c13, c1.m1[3]);
    }

    /**
     * Check that when an object has circular dependencies towards several other
     * objects so that there is no determinable dependency at all the injected
     * array is still correct.
     */
    public void testSuperCircularSequence() {
        DuctTape dt = new DuctTape();

        C17 c17 = new C17();
        C18 c181 = new C18();
        C18 c182 = new C18();

        dt.add(c17);
        dt.add(c181);
        dt.add(c182);
        dt.apply();

        assertNotNull(c17.m1);
        assertEquals(3, c17.m1.length);
        assertTrue(c17.m1[0] == c17 ^ c17.m1[1] == c17 ^ c17.m1[2] == c17);
        assertTrue(c17.m1[0] == c181 ^ c17.m1[1] == c181 ^ c17.m1[2] == c181);
        assertTrue(c17.m1[0] == c182 ^ c17.m1[1] == c182 ^ c17.m1[2] == c182);
        assertNotNull(c181.m1);
        assertEquals(3, c181.m1.length);
        assertTrue(c181.m1[0] == c17 ^ c181.m1[1] == c17 ^ c181.m1[2] == c17);
        assertTrue(c181.m1[0] == c181 ^ c181.m1[1] == c181 ^ c181.m1[2] == c181);
        assertTrue(c181.m1[0] == c182 ^ c181.m1[1] == c182 ^ c181.m1[2] == c182);
        assertNotNull(c182.m1);
        assertEquals(3, c182.m1.length);
        assertTrue(c182.m1[0] == c17 ^ c182.m1[1] == c17 ^ c182.m1[2] == c17);
        assertTrue(c182.m1[0] == c181 ^ c182.m1[1] == c181 ^ c182.m1[2] == c181);
        assertTrue(c182.m1[0] == c182 ^ c182.m1[1] == c182 ^ c182.m1[2] == c182);
    }

}
