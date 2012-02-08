package com.android.overlaytest;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.test.AndroidTestCase;
import java.io.InputStream;
import java.util.Locale;

public abstract class OverlayBaseTest extends AndroidTestCase {
    private Resources mResources;
    protected int mMode; // will be set by subclasses
    static final protected int MODE_NO_OVERLAY = 0;
    static final protected int MODE_SINGLE_OVERLAY = 1;
    static final protected int MODE_MULTIPLE_OVERLAYS = 2;

    protected void setUp() {
        mResources = getContext().getResources();
    }

    private int calculateRawResourceChecksum(int resId) throws Throwable {
        InputStream input = null;
        try {
            input = mResources.openRawResource(resId);
            int ch, checksum = 0;
            while ((ch = input.read()) != -1) {
                checksum = (checksum + ch) % 0xffddbb00;
            }
            return checksum;
        } finally {
            input.close();
        }
    }

    private void setLocale(String code) {
        Locale locale = new Locale(code);
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.locale = locale;
        mResources.updateConfiguration(config, mResources.getDisplayMetrics());
    }

    private boolean getExpected(boolean no, boolean so, boolean mo) {
        switch (mMode) {
            case MODE_NO_OVERLAY:
                return no;
            case MODE_SINGLE_OVERLAY:
                return so;
            case MODE_MULTIPLE_OVERLAYS:
                return mo;
            default:
                fail("Unknown mode!");
                return no;
        }
    }

    private String getExpected(String no, String so, String mo) {
        switch (mMode) {
            case MODE_NO_OVERLAY:
                return no;
            case MODE_SINGLE_OVERLAY:
                return so;
            case MODE_MULTIPLE_OVERLAYS:
                return mo;
            default:
                fail("Unknown mode!");
                return no;
        }
    }

    private int[] getExpected(int[] no, int[] so, int[] mo) {
        switch (mMode) {
            case MODE_NO_OVERLAY:
                return no;
            case MODE_SINGLE_OVERLAY:
                return so;
            case MODE_MULTIPLE_OVERLAYS:
                return mo;
            default:
                fail("Unknown mode!");
                return no;
        }
    }

    private void assertResource(int resId, boolean no, boolean so, boolean mo) throws Throwable {
        boolean expected = getExpected(no, so, mo);
        boolean actual = mResources.getBoolean(resId);
        assertEquals(expected, actual);
    }

    private void assertResource(int resId, String no, String so, String mo) throws Throwable {
        String expected = getExpected(no, so, mo);
        String actual = mResources.getString(resId);
        assertEquals(expected, actual);
    }

    private void assertResource(int resId, int[] no, int[] so, int[] mo) throws Throwable {
        int[] expected = getExpected(no, so, mo);
        int[] actual = mResources.getIntArray(resId);
        assertEquals("length:", expected.length, actual.length);
        for (int i = 0; i < actual.length; ++i) {
            assertEquals("index " + i + ":", actual[i], expected[i]);
        }
    }

    public void testBooleanOverlay() throws Throwable {
        // config_automatic_brightness_available has the value:
        // - false when no overlay exists (MODE_NO_OVERLAY)
        // - true when a single overlay exists (MODE_SINGLE_OVERLAY)
        // - false when multiple overlays exists (MODE_MULTIPLE_OVERLAYS)
        final int resId = com.android.internal.R.bool.config_automatic_brightness_available;
        assertResource(resId, false, true, false);
    }

    public void testBoolean() throws Throwable {
        // config_bypass_keyguard_if_slider_open has no overlay
        final int resId = com.android.internal.R.bool.config_bypass_keyguard_if_slider_open;
        assertResource(resId, true, true, true);
    }

    public void testStringOverlay() throws Throwable {
        // phoneTypeCar has an overlay (default config), which shouldn't shadow
        // the Swedish translation
        final int resId = com.android.internal.R.string.phoneTypeCar;
        setLocale("sv_SE");
        assertResource(resId, "Bil", "Bil", "Bil");
    }

    public void testStringSwedishOverlay() throws Throwable {
        // phoneTypeWork has overlay only for lang=sv. The values are applied as follows:
        // - "Arbete" when no overlay exists (MODE_NO_OVERLAY)
        // - "Jobb" when a single overlay exists (MODE_SINGLE_OVERLAY)
        // - "2nd overlay Jobb" when multiple overlays exists (MODE_MULTIPLE_OVERLAYS)
        final int resId = com.android.internal.R.string.phoneTypeWork;
        setLocale("en_US");
        assertResource(resId, "Work", "Work", "Work");
        setLocale("sv_SE");
        assertResource(resId, "Arbete", "Jobb", "2nd overlay Jobb");
    }

    public void testString() throws Throwable {
        // phoneTypeHome has no overlay
        final int resId = com.android.internal.R.string.phoneTypeHome;
        setLocale("en_US");
        assertResource(resId, "Home", "Home", "Home");
        setLocale("sv_SE");
        assertResource(resId, "Hem", "Hem", "Hem");
    }

    public void testIntegerArrayOverlay() throws Throwable {
        // config_scrollBarrierVibePattern has the values:
        // - {0, 15, 10, 10} when no overlay exists (MODE_NO_OVERLAY)
        // - {100, 200, 300} when a single overlay exists (MODE_SINGLE_OVERLAY)
        // - {100, 200, 300} when multiple overlays exists (MODE_MULTIPLE_OVERLAYS)
        final int resId = com.android.internal.R.array.config_scrollBarrierVibePattern;
        assertResource(resId, new int[]{0, 15, 10, 10}, new int[]{100, 200, 300},
                new int[]{100, 200, 300});
    }

    public void testIntegerArray() throws Throwable {
        // config_virtualKeyVibePattern has no overlay
        final int resId = com.android.internal.R.array.config_virtualKeyVibePattern;
        final int[] expected = {0, 10, 20, 30};
        assertResource(resId, expected, expected, expected);
    }

    public void testAsset() throws Throwable {
        // drawable/default_background.jpg is applied when no overlay exists (MODE_NO_OVERLAY).
        // drawable/default_background.jpg is overlayed when a single or multiple overlay exists
        // (MODE_SINGLE_OVERLAY or MODE_MULTIPLE_OVERLAYS).
        final int resId = com.android.internal.R.drawable.default_wallpaper;
        int actual = calculateRawResourceChecksum(resId);
        int expected = 0x12345678;
        switch (mMode) {
            case MODE_NO_OVERLAY:
                expected = 0x0014ebce;
                break;
            case MODE_SINGLE_OVERLAY:
                expected = 0x000051da;
                break;
            case MODE_MULTIPLE_OVERLAYS:
                expected = 0x000051da;
                break;
            default:
                fail("Unknown mode!");
        }
        assertEquals(expected, actual);
    }
}
