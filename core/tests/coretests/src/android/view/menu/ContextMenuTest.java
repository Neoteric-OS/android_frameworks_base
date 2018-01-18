/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.view.menu;

import android.content.Context;
import android.graphics.Point;
import android.support.test.filters.MediumTest;
import android.test.ActivityInstrumentationTestCase;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.espresso.ContextMenuUtils;

@MediumTest
public class ContextMenuTest extends ActivityInstrumentationTestCase<ContextMenuActivity> {

    private static final int WAIT_TIME = 1000;

    public ContextMenuTest() {
        super("com.android.frameworks.coretests", ContextMenuActivity.class);
    }

    public void testContextMenuPositionLtr() throws InterruptedException {
        testMenuPosition(getActivity().getTargetLtr());
    }

    public void testContextMenuPositionRtl() throws InterruptedException {
        testMenuPosition(getActivity().getTargetRtl());
    }

    private void testMenuPosition(View target) throws InterruptedException {
        int offsetX = target.getWidth() / 2;
        int offsetY = target.getHeight() / 2;

        getInstrumentation().runOnMainSync(() -> target.performLongClick(offsetX, offsetY));

        Thread.sleep(WAIT_TIME);
        ContextMenuUtils.assertContextMenuContainsItemEnabled(ContextMenuActivity.LABEL_ITEM);
        ContextMenuUtils.assertContextMenuAlignment(target, offsetX, offsetY);

        ContextMenuUtils.clickOnContextMenu(ContextMenuActivity.LABEL_SUBMENU);

        Thread.sleep(WAIT_TIME);
        ContextMenuUtils.assertContextMenuContainsItemEnabled(ContextMenuActivity.LABEL_SUBITEM);

        if (!cascadingSubmenu()) {
            // Not checking cascading submenu in this test, as it is positioned differently.
            ContextMenuUtils.assertContextMenuAlignment(target, offsetX, offsetY);
        }
    }

    private boolean cascadingSubmenu() {
        // Code copied from MenuPopupHelper.createPopup()
        final WindowManager windowManager = (WindowManager) getActivity().getSystemService(
                Context.WINDOW_SERVICE);
        final Display display = windowManager.getDefaultDisplay();
        final Point displaySize = new Point();
        display.getRealSize(displaySize);

        final int smallestWidth = Math.min(displaySize.x, displaySize.y);
        final int minSmallestWidthCascading = getActivity().getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.cascading_menus_min_smallest_width);
        return smallestWidth >= minSmallestWidthCascading;
    }
}
