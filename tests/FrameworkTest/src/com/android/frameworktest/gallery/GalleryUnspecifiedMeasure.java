
package com.android.frameworktest.gallery;

import android.app.Activity;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.LargeTest;
import android.widget.Gallery;

import android.test.TouchUtils;

import com.android.frameworktest.R;

public class GalleryUnspecifiedMeasure<T extends Activity> extends
        ActivityInstrumentationTestCase2<T> {
    private T mActivity;
    private Gallery mGallery;

    public GalleryUnspecifiedMeasure(String pkg, Class<T> activityClass) {
        super(pkg, activityClass);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mActivity = getActivity();
        mGallery = (Gallery)mActivity.findViewById(R.id.gallery);
    }

    @MediumTest
    public void testPreconditions() {
        assertNotNull(mActivity);
        assertNotNull(mGallery);
    }

    @MediumTest
    public void testWasMeasured() {
        assertTrue(mGallery.getMeasuredWidth() > 0);
        assertTrue(mGallery.getWidth() > 0);
        assertTrue(mGallery.getMeasuredHeight() > 0);
        assertTrue(mGallery.getHeight() > 0);
    }

    @LargeTest
    public void testScrolling() {
        int firstVisibleItem = mGallery.getSelectedItemPosition();
        this.sendKeys(android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT, android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                android.view.KeyEvent.KEYCODE_DPAD_DOWN);
        TouchUtils.dragQuarterScreenUp(this, mActivity);
        assertTrue("Scroll did not happen",
                mGallery.getSelectedItemPosition() > firstVisibleItem);
    }

}

