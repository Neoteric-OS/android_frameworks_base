/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.overlay;

import com.android.frameworks.coretests.R;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class OverlayTest extends AndroidTestCase {
    static private String OVERLAY_PACKAGE_PATH = "/system/overlay/overlay-FrameworksCoreTests.apk";
    static private int EXPECTED_WIDTH = 72;
    static private int EXPECTED_HEIGHT = EXPECTED_WIDTH;

    private Resources mResources;
    private float mDisplayDensity;

    private String readTextAsset(String path) {
        AssetManager am = mResources.getAssets();
        StringBuilder buf = new StringBuilder();
        try {
            InputStream is = am.open(path);
            int c;
            while ((c = is.read()) != -1) {
                buf.append((char)c);
            }
            is.close();
        } catch (IOException e) {
            fail(e.toString());
        }
        return buf.toString();
    }

    private int normalizeExternal(int x) {
        return (int)(100 * mDisplayDensity) * x;
    }

    private int normalizeInternal(int x) {
        return 100 * x;
    }

    protected void setUp() {
        // verify invariant: overlay package present when running this test
        try {
            File file = new File(OVERLAY_PACKAGE_PATH);
            if (!file.isFile()) {
                throw new Exception();
            }
        } catch (Exception e) {
            fail("Expected overlay package " + OVERLAY_PACKAGE_PATH + " missing");
        }

        mResources = getContext().getResources();

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(metrics);
        mDisplayDensity = metrics.density;
    }

    @SmallTest
    public void testDoNotOverlayString() {
        String str = mResources.getString(R.string.do_not_overlay_string);
        assertEquals("foo", str);
    }

    @SmallTest
    public void testOverlayString() {
        String str = mResources.getString(R.string.overlay_string);
        assertEquals("bar", str);
    }

    @SmallTest
    public void testDoNotOverlayInteger() {
        int i = mResources.getInteger(R.integer.do_not_overlay_integer);
        assertEquals(0xcafebabe, i);
    }

    @SmallTest
    public void testOverlayInteger() {
        int i = mResources.getInteger(R.integer.overlay_integer);
        assertEquals(0x12341234, i);
    }

    @SmallTest
    public void testDoNotOverlayDrawable() {
        Drawable d = mResources.getDrawable(R.drawable.do_not_overlay_drawable);
        assertEquals(normalizeExternal(EXPECTED_WIDTH), normalizeInternal(d.getIntrinsicWidth()));
        assertEquals(normalizeExternal(EXPECTED_HEIGHT), normalizeInternal(d.getIntrinsicHeight()));
    }

    @SmallTest
    public void testOverlayDrawable() {
        Drawable d = mResources.getDrawable(R.drawable.overlay_drawable);
        assertEquals(normalizeExternal(EXPECTED_WIDTH), normalizeInternal(d.getIntrinsicWidth()));
        assertEquals(normalizeExternal(EXPECTED_HEIGHT), normalizeInternal(d.getIntrinsicHeight()));
    }

    @SmallTest
    public void testDoNotOverlayAsset() {
        String str = readTextAsset("do_not_overlay_asset.txt");
        assertEquals("foo", str);
    }

    @SmallTest
    public void testOverlayAsset() {
        String str = readTextAsset("overlay_asset.txt");
        assertEquals("overlay", str);
    }

    /*
     * With resource overlay, a resource may come in four different flavours for a specific
     * configuration X, using an overlay package 'package' targeting a regular package 'target':
     *
     * - overlay/values-X
     * - target/values-X
     * - overlay/values
     * - target/values
     *
     * Resource lookup is done in the above order, beginning with overlay/values-X.
     *
     * In the following tests, both values-port and values-land are used for values-x, to guarantee
     * an alternative resources is used.
     *
     * The tests below use the naming convention ABC, where
     *
     * - A == 1 -> overlay/values-X is present
     * - B == 1 -> target/values-X is present
     * - C == 1 -> overlay/values is present
     *
     * Of course, target/values is always present.
     */
    @SmallTest
    public void testAlternativeInterger000() {
        int i = mResources.getInteger(R.integer.overlay_000);
        assertEquals(1, i);
    }

    @SmallTest
    public void testAlternativeInterger001() {
        int i = mResources.getInteger(R.integer.overlay_001);
        assertEquals(1, i);
    }

    @SmallTest
    public void testAlternativeInterger010() {
        int i = mResources.getInteger(R.integer.overlay_010);
        assertEquals(1, i);
    }

    @SmallTest
    public void testAlternativeInterger011() {
        int i = mResources.getInteger(R.integer.overlay_011);
        assertEquals(1, i);
    }

    @SmallTest
    public void testAlternativeInterger100() {
        int i = mResources.getInteger(R.integer.overlay_100);
        assertEquals(1, i);
    }

    @SmallTest
    public void testAlternativeInterger101() {
        int i = mResources.getInteger(R.integer.overlay_101);
        assertEquals(1, i);
    }

    @SmallTest
    public void testAlternativeInterger110() {
        int i = mResources.getInteger(R.integer.overlay_110);
        assertEquals(1, i);
    }

    @SmallTest
    public void testAlternativeInterger111() {
        int i = mResources.getInteger(R.integer.overlay_111);
        assertEquals(1, i);
    }

    @SmallTest
    public void testAlternativeDrawable000() {
        Drawable d = mResources.getDrawable(R.drawable.overlay_000);
        assertEquals(normalizeExternal(EXPECTED_WIDTH), normalizeInternal(d.getIntrinsicWidth()));
        assertEquals(normalizeExternal(EXPECTED_HEIGHT), normalizeInternal(d.getIntrinsicHeight()));
    }

    @SmallTest
    public void testAlternativeDrawable001() {
        Drawable d = mResources.getDrawable(R.drawable.overlay_001);
        assertEquals(normalizeExternal(EXPECTED_WIDTH), normalizeInternal(d.getIntrinsicWidth()));
        assertEquals(normalizeExternal(EXPECTED_HEIGHT), normalizeInternal(d.getIntrinsicHeight()));
    }

    @SmallTest
    public void testAlternativeDrawable010() {
        Drawable d = mResources.getDrawable(R.drawable.overlay_010);
        assertEquals(normalizeExternal(EXPECTED_WIDTH), normalizeInternal(d.getIntrinsicWidth()));
        assertEquals(normalizeExternal(EXPECTED_HEIGHT), normalizeInternal(d.getIntrinsicHeight()));
    }

    @SmallTest
    public void testAlternativeDrawable011() {
        Drawable d = mResources.getDrawable(R.drawable.overlay_011);
        assertEquals(normalizeExternal(EXPECTED_WIDTH), normalizeInternal(d.getIntrinsicWidth()));
        assertEquals(normalizeExternal(EXPECTED_HEIGHT), normalizeInternal(d.getIntrinsicHeight()));
    }

    @SmallTest
    public void testAlternativeDrawable100() {
        Drawable d = mResources.getDrawable(R.drawable.overlay_100);
        assertEquals(normalizeExternal(EXPECTED_WIDTH), normalizeInternal(d.getIntrinsicWidth()));
        assertEquals(normalizeExternal(EXPECTED_HEIGHT), normalizeInternal(d.getIntrinsicHeight()));
    }

    @SmallTest
    public void testAlternativeDrawable101() {
        Drawable d = mResources.getDrawable(R.drawable.overlay_101);
        assertEquals(normalizeExternal(EXPECTED_WIDTH), normalizeInternal(d.getIntrinsicWidth()));
        assertEquals(normalizeExternal(EXPECTED_HEIGHT), normalizeInternal(d.getIntrinsicHeight()));
    }

    @SmallTest
    public void testAlternativeDrawable110() {
        Drawable d = mResources.getDrawable(R.drawable.overlay_110);
        assertEquals(normalizeExternal(EXPECTED_WIDTH), normalizeInternal(d.getIntrinsicWidth()));
        assertEquals(normalizeExternal(EXPECTED_HEIGHT), normalizeInternal(d.getIntrinsicHeight()));
    }

    @SmallTest
    public void testAlternativeDrawable111() {
        Drawable d = mResources.getDrawable(R.drawable.overlay_111);
        assertEquals(normalizeExternal(EXPECTED_WIDTH), normalizeInternal(d.getIntrinsicWidth()));
        assertEquals(normalizeExternal(EXPECTED_HEIGHT), normalizeInternal(d.getIntrinsicHeight()));
    }

    @SmallTest
    public void testAlternativeString000() {
        String str = mResources.getString(R.string.overlay_000);
        assertEquals("yes", str);
    }

    @SmallTest
    public void testAlternativeString001() {
        String str = mResources.getString(R.string.overlay_001);
        assertEquals("yes", str);
    }

    @SmallTest
    public void testAlternativeString010() {
        String str = mResources.getString(R.string.overlay_010);
        assertEquals("yes", str);
    }

    @SmallTest
    public void testAlternativeString011() {
        String str = mResources.getString(R.string.overlay_011);
        assertEquals("yes", str);
    }

    @SmallTest
    public void testAlternativeString100() {
        String str = mResources.getString(R.string.overlay_100);
        assertEquals("yes", str);
    }

    @SmallTest
    public void testAlternativeString101() {
        String str = mResources.getString(R.string.overlay_101);
        assertEquals("yes", str);
    }

    @SmallTest
    public void testAlternativeString110() {
        String str = mResources.getString(R.string.overlay_110);
        assertEquals("yes", str);
    }

    @SmallTest
    public void testAlternativeString111() {
        String str = mResources.getString(R.string.overlay_111);
        assertEquals("yes", str);
    }
}
