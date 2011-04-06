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
 * This class contains tests that make sure that primitive types are handled
 * correctly when injected.
 */
public class TestPrimitives extends TestCase {

    static class C1 implements InjectionTarget, Singleton {

        @Inject(1)
        int m1;

        @Inject(1)
        Integer m2;

        @Inject(2)
        int m3;

    }

    static class C2 implements InjectionTarget, Singleton {

        @Inject(1)
        boolean m1;

        @Inject(1)
        Boolean m2;

        @Inject(2)
        boolean m3;

    }

    static class C3 implements InjectionTarget, Singleton {

        @Inject(1)
        char m1;

        @Inject(1)
        Character m2;

        @Inject(2)
        char m3;

    }

    static class C4 implements InjectionTarget, Singleton {

        @Inject(1)
        float m1;

        @Inject(1)
        Float m2;

        @Inject(2)
        float m3;

    }

    static class C5 implements InjectionTarget, Singleton {

        @Inject(1)
        double m1;

        @Inject(1)
        Double m2;

        @Inject(2)
        double m3;

    }

    static class C6 implements InjectionTarget, Singleton {

        @Inject(1)
        short m1;

        @Inject(1)
        Short m2;

        @Inject(2)
        short m3;

    }

    static class C7 implements InjectionTarget, Singleton {

        @Inject(1)
        long m1;

        @Inject(1)
        Long m2;

        @Inject(2)
        long m3;

    }

    static class C8 implements InjectionTarget, Singleton {

        @Inject(1)
        byte m1;

        @Inject(1)
        Byte m2;

        @Inject(2)
        byte m3;

    }

    static class C9 implements InjectionTarget, Singleton {

        @Inject
        int[] m1;

        @Inject
        short[] m2;

        @Inject
        long[] m3;

        @Inject
        byte[] m4;

        @Inject
        char[] m5;

        @Inject
        float[] m6;

        @Inject
        double[] m7;

        @Inject
        boolean[] m8;

    }

    /**
     * Make sure that it is possible to inject integers in both primitive and
     * object form.
     */
    public void testInteger() {
        DuctTape dt = new DuctTape();

        C1 c1 = new C1();

        dt.add(c1);
        dt.add(12345, 1);
        dt.add(Integer.valueOf(-456), 2);

        dt.apply();

        assertEquals(12345, c1.m1);
        assertNotNull(c1.m2);
        assertEquals(Integer.valueOf(12345), c1.m2);
        assertEquals(-456, c1.m3);
    }

    /**
     * Make sure that it is possible to inject booleans in both primitive and
     * object form.
     */
    public void testBoolean() {
        DuctTape dt = new DuctTape();

        C2 c2 = new C2();

        dt.add(c2);
        dt.add(true, 1);
        dt.add(Boolean.TRUE, 2);

        dt.apply();

        assertEquals(true, c2.m1);
        assertNotNull(c2.m2);
        assertEquals(Boolean.valueOf(true), c2.m2);
        assertEquals(true, c2.m3);
    }

    /**
     * Make sure that it is possible to inject characters in both primitive and
     * object form.
     */
    public void testCharacter() {
        DuctTape dt = new DuctTape();

        C3 c3 = new C3();

        dt.add(c3);
        dt.add('x', 1);
        dt.add(Character.valueOf('a'), 2);

        dt.apply();

        assertEquals('x', c3.m1);
        assertNotNull(c3.m2);
        assertEquals(Character.valueOf('x'), c3.m2);
        assertEquals('a', c3.m3);
    }

    /**
     * Make sure that it is possible to inject float in both primitive and
     * object form.
     */
    public void testFloat() {
        DuctTape dt = new DuctTape();

        C4 c4 = new C4();

        dt.add(c4);
        dt.add(12.34f, 1);
        dt.add(Float.valueOf(-76.3f), 2);

        dt.apply();

        assertEquals(12.34f, c4.m1);
        assertNotNull(c4.m2);
        assertEquals(Float.valueOf(12.34f), c4.m2);
        assertEquals(-76.3f, c4.m3);
    }

    /**
     * Make sure that it is possible to inject doubles in both primitive and
     * object form.
     */
    public void testDouble() {
        DuctTape dt = new DuctTape();

        C5 c5 = new C5();

        dt.add(c5);
        dt.add(12.34d, 1);
        dt.add(Double.valueOf(-116.7d), 2);

        dt.apply();

        assertEquals(12.34d, c5.m1);
        assertNotNull(c5.m2);
        assertEquals(Double.valueOf(12.34d), c5.m2);
        assertEquals(-116.7d, c5.m3);
    }

    /**
     * Make sure that it is possible to inject shorts in both primitive and
     * object form.
     */
    public void testShort() {
        DuctTape dt = new DuctTape();

        C6 c6 = new C6();

        dt.add(c6);
        dt.add((short)1234, 1);
        dt.add(Short.valueOf((short)-293), 2);

        dt.apply();

        assertEquals((short)1234, c6.m1);
        assertNotNull(c6.m2);
        assertEquals(Short.valueOf((short)1234), c6.m2);
        assertEquals((short)-293, c6.m3);
    }

    /**
     * Make sure that it is possible to inject longs in both primitive and
     * object form.
     */
    public void testLong() {
        DuctTape dt = new DuctTape();

        C7 c7 = new C7();

        dt.add(c7);
        dt.add(12345678901234567l, 1);
        dt.add(Long.valueOf(-45769432345435l), 2);

        dt.apply();

        assertEquals(12345678901234567l, c7.m1);
        assertNotNull(c7.m2);
        assertEquals(Long.valueOf(12345678901234567l), c7.m2);
        assertEquals(-45769432345435l, c7.m3);
    }

    /**
     * Make sure that it is possible to inject bytes in both primitive and
     * object form.
     */
    public void testByte() {
        DuctTape dt = new DuctTape();

        C8 c8 = new C8();

        dt.add(c8);
        dt.add((byte)123, 1);
        dt.add(Byte.valueOf((byte)-83), 2);

        dt.apply();

        assertEquals((byte)123, c8.m1);
        assertNotNull(c8.m2);
        assertEquals(Byte.valueOf((byte)123), c8.m2);
        assertEquals((byte)-83, c8.m3);
    }

    /**
     * Make sure that fields with primitive array types are properly injected
     * with arrays made up of all matching objects.
     */
    public void testPrimitiveArrays() {
        DuctTape dt = new DuctTape();

        C9 c9 = new C9();
        dt.add(c9);
        dt.add(1);
        dt.add(2);
        dt.add(true);
        dt.add(false);
        dt.add((short)3);
        dt.add((short)4);
        dt.add((long)5);
        dt.add((long)6);
        dt.add((byte)7);
        dt.add((byte)8);
        dt.add(12.3f);
        dt.add(4.56f);
        dt.add(7.8d);
        dt.add(9d);
        dt.add('a');
        dt.add('b');
        dt.apply();

        assertNotNull(c9.m1);
        assertNotNull(c9.m2);
        assertNotNull(c9.m3);
        assertNotNull(c9.m4);
        assertNotNull(c9.m5);
        assertNotNull(c9.m6);
        assertNotNull(c9.m7);
        assertNotNull(c9.m8);

        assertEquals(2, c9.m1.length);
        assertEquals(2, c9.m2.length);
        assertEquals(2, c9.m3.length);
        assertEquals(2, c9.m4.length);
        assertEquals(2, c9.m5.length);
        assertEquals(2, c9.m6.length);
        assertEquals(2, c9.m7.length);
        assertEquals(2, c9.m8.length);

        assertTrue(c9.m1[0] == 1 ^ c9.m1[1] == 1);
        assertTrue(c9.m1[0] == 2 ^ c9.m1[1] == 2);

        assertTrue(c9.m2[0] == 3 ^ c9.m2[1] == 3);
        assertTrue(c9.m2[0] == 4 ^ c9.m2[1] == 4);

        assertTrue(c9.m3[0] == 5 ^ c9.m3[1] == 5);
        assertTrue(c9.m3[0] == 6 ^ c9.m3[1] == 6);

        assertTrue(c9.m4[0] == 7 ^ c9.m4[1] == 7);
        assertTrue(c9.m4[0] == 8 ^ c9.m4[1] == 8);

        assertTrue(c9.m5[0] == 'a' ^ c9.m5[1] == 'a');
        assertTrue(c9.m5[0] == 'b' ^ c9.m5[1] == 'b');

        assertTrue(c9.m6[0] == 12.3f ^ c9.m6[1] == 12.3f);
        assertTrue(c9.m6[0] == 4.56f ^ c9.m6[1] == 4.56f);

        assertTrue(c9.m7[0] == 7.8d ^ c9.m7[1] == 7.8d);
        assertTrue(c9.m7[0] == 9d ^ c9.m7[1] == 9d);

        assertTrue(c9.m8[0] == true ^ c9.m8[1] == true);
        assertTrue(c9.m8[0] == false ^ c9.m8[1] == false);
    }

}
