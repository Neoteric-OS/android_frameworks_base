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
import com.android.frameworks.coretests.R;
import android.inject.Singleton;
import android.test.AndroidTestCase;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * This class contains test cases that makes sure that views identified by type
 * and ID are properly injected when available.
 */
public class TestViews extends AndroidTestCase {

    interface A {
    }

    static class C1 implements InjectionTarget, Singleton, A {

        @Inject(R.layout.ducttape_layout)
        View m1;

    }

    static class C2 implements InjectionTarget, Singleton {

        @Inject
        A m1;

    }

    static class C3 implements InjectionTarget, Singleton, A {

        @Inject(R.id.view1)
        TextView m1;

    }

    /**
     * Make sure that when a non-inflated layout is referred in an injection it
     * is properly inflated and injected when there is a context available.
     */
    public void testViewInflation() {
        DuctTape dt = new DuctTape(getContext());

        C1 c1 = new C1();

        dt.add(c1);

        dt.apply();

        assertNotNull(c1.m1);
        assertEquals(LinearLayout.class, c1.m1.getClass());
    }

    /**
     * Make sure that when a factory class requires a layout to be inflated it
     * produces its object when a context with the requested resource is
     * available.
     */
    public void testInflateInFactory() {
        DuctTape dt = new DuctTape(getContext());

        C2 c2 = new C2();

        dt.add(c2);
        dt.add(C1.class);

        dt.apply();

        assertNotNull(c2.m1);
        assertEquals(C1.class, c2.m1.getClass());
    }

    /**
     * Make sure that when a view is available and an injection refers to it
     * using its ID it is properly injected.
     */
    public void testViewFinding() {
        DuctTape dt = new DuctTape();

        C3 c3 = new C3();

        dt.add(LayoutInflater.from(getContext()).inflate(R.layout.ducttape_layout, null));
        dt.add(c3);

        dt.apply();

        assertNotNull(c3.m1);
        assertEquals(R.id.view1, c3.m1.getId());
        assertEquals("abcd123", c3.m1.getText());
    }

    /**
     * Make sure that a factory class requiring a specific view to be injected
     * produces its object when the required view is available.
     */
    public void testFindInFactory() {
        DuctTape dt = new DuctTape(getContext());

        C2 c2 = new C2();

        dt.add(c2);
        dt.add(LayoutInflater.from(getContext()).inflate(R.layout.ducttape_layout, null));
        dt.add(C3.class);

        dt.apply();

        assertNotNull(c2.m1);
        assertEquals(C3.class, c2.m1.getClass());
    }

}
