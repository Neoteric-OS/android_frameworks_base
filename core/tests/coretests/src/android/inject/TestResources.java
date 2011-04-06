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

import android.content.res.ColorStateList;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.inject.DuctTape;
import android.inject.Inject;
import android.inject.InjectionTarget;
import com.android.frameworks.coretests.R;
import android.inject.Singleton;
import android.test.AndroidTestCase;

/**
 * This class contains tests that make sure that application resources can be
 * extracted as expected when a context is given.
 */
public class TestResources extends AndroidTestCase {

    interface A {
    }

    static class C1 implements InjectionTarget, Singleton, A {

        @Inject(R.color.c1)
        int m1;

        @Inject(R.string.s1)
        String m2;

        @Inject(R.dimen.d1)
        float m3;

        @Inject(R.array.ia1)
        int[] m4;

        @Inject(R.array.sa1)
        String[] m5;

        @Inject(R.bool.b1)
        boolean m6;

        @Inject(R.drawable.box)
        Drawable m7;

        @Inject(R.color.ducttape_colors)
        ColorStateList m8;

        @Inject(R.xml.ducttape_xml)
        XmlResourceParser m9;

    }

    static class C2 implements InjectionTarget, Singleton {

        @Inject
        A m1;

    }

    /**
     * Make sure application resources are properly injected into fields which
     * has resource ID tags.
     */
    public void testResourceInjection() {
        DuctTape dt = new DuctTape(getContext());

        C1 c1 = new C1();

        dt.add(c1);

        dt.apply();

        assertEquals(0xcafebabe, c1.m1);
        assertEquals("blurp", c1.m2);
        assertEquals(14f, c1.m3);
        assertEquals(2, c1.m4.length);
        assertEquals(78, c1.m4[0]);
        assertEquals(10, c1.m4[1]);
        assertEquals(3, c1.m5.length);
        assertEquals("hello", c1.m5[0]);
        assertEquals("funny", c1.m5[1]);
        assertEquals("man", c1.m5[2]);
        assertEquals(true, c1.m6);
        assertNotNull(c1.m7);
        assertNotNull(c1.m8);
        assertNotNull(c1.m9);
    }

    /**
     * Make sure that a factory class that requires application resources is
     * indeed used to instantiate an object when a context containing the needed
     * resources is available.
     */
    public void testResourcesInFactory() {
        DuctTape dt = new DuctTape(getContext());

        C2 c2 = new C2();

        dt.add(c2);
        dt.add(C1.class);

        dt.apply();

        assertNotNull(c2.m1);
        assertEquals(C1.class, c2.m1.getClass());
    }

}
