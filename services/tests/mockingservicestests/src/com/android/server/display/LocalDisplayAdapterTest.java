/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.display;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Display;
import android.view.DisplayAddress;
import android.view.SurfaceControl;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.dx.mockito.inline.extended.StaticMockitoSession;
import com.android.server.LocalServices;
import com.android.server.lights.LightsManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;


@SmallTest
@RunWith(AndroidJUnit4.class)
public class LocalDisplayAdapterTest {
    private static final long HANDLER_WAIT_MS = 100;

    private static final int PHYSICAL_DISPLAY_ID_MODEL_SHIFT = 8;

    private StaticMockitoSession mMockitoSession;

    private LocalDisplayAdapter mAdapter;

    @Mock
    private DisplayManagerService.SyncRoot mMockedSyncRoot;
    @Mock
    private Context mMockedContext;
    @Mock
    private Resources mMockedResources;
    @Mock
    private LightsManager mMockedLightsManager;

    private Handler mHandler;

    private TestListener mListener = new TestListener();

    private LinkedList<Long> mDisplayIds = new LinkedList<>();

    @Before
    public void setUp() throws Exception {
        mMockitoSession = mockitoSession()
                .initMocks(this)
                .mockStatic(SurfaceControl.class)
                .strictness(Strictness.LENIENT)
                .startMocking();
        mHandler = new Handler(Looper.getMainLooper());
        doReturn(mMockedResources).when(mMockedContext).getResources();
        LocalServices.removeServiceForTest(LightsManager.class);
        LocalServices.addService(LightsManager.class, mMockedLightsManager);
        mAdapter = new LocalDisplayAdapter(mMockedSyncRoot, mMockedContext, mHandler,
                mListener);
        spyOn(mAdapter);
        doReturn(mMockedContext).when(mAdapter).getOverlayContext();
    }

    @After
    public void tearDown() {
        if (mMockitoSession != null) {
            mMockitoSession.finishMocking();
        }
    }

    /**
     * Confirm that display is marked as private when it is listed in
     * com.android.internal.R.array.config_localPrivateDisplayPorts.
     */
    @Test
    public void testPrivateDisplay() throws Exception {
        // needs default one always
        final long displayId0 = 0;
        setUpDisplay(new FakeDisplay(displayId0));
        final long displayId1 = 1;
        setUpDisplay(new FakeDisplay(displayId1));
        final long displayId2 = 2;
        setUpDisplay(new FakeDisplay(displayId2));
        updateAvailableDisplays();
        // display 1 should be marked as private while display 2 is not.
        doReturn(new int[]{(int) displayId1}).when(mMockedResources)
                .getIntArray(com.android.internal.R.array.config_localPrivateDisplayPorts);
        mAdapter.registerLocked();

        waitForHandlerToComplete(mHandler, HANDLER_WAIT_MS);

        // This should be public
        assertDisplay(mListener.addedDisplays.get(0).getDisplayDeviceInfoLocked(), displayId0,
                false);
        // This should be private
        assertDisplay(mListener.addedDisplays.get(1).getDisplayDeviceInfoLocked(), displayId1,
                true);
        // This should be public
        assertDisplay(mListener.addedDisplays.get(2).getDisplayDeviceInfoLocked(), displayId2,
                false);
    }

    /**
     * Confirm that all local displays are public when config_localPrivateDisplayPorts is empty.
     */
    @Test
    public void testPublicDisplaysForNoConfigLocalPrivateDisplayPorts() throws Exception {
        // needs default one always
        final long displayId0 = 0;
        setUpDisplay(new FakeDisplay(displayId0));
        final long displayId1 = 1;
        setUpDisplay(new FakeDisplay(displayId1));
        updateAvailableDisplays();
        // config_localPrivateDisplayPorts is null
        mAdapter.registerLocked();

        waitForHandlerToComplete(mHandler, HANDLER_WAIT_MS);

        // This should be public
        assertDisplay(mListener.addedDisplays.get(0).getDisplayDeviceInfoLocked(), displayId0,
                false);
        // This should be public
        assertDisplay(mListener.addedDisplays.get(1).getDisplayDeviceInfoLocked(), displayId1,
                false);
    }

    private void assertDisplay(DisplayDeviceInfo info, long expectedPort, boolean shouldBePrivate) {
        DisplayAddress.Physical physical = (DisplayAddress.Physical) info.address;
        assertNotNull(physical);
        assertEquals(expectedPort, physical.getPort());
        assertEquals(shouldBePrivate, (info.flags & DisplayDeviceInfo.FLAG_PRIVATE) != 0);
    }

    @Test
    public void testAfterDisplayChange_ModesAreUpdated() throws Exception {
        SurfaceControl.PhysicalDisplayInfo displayInfo = createDummyDisplayInfo(1920, 1080, 60f);
        SurfaceControl.PhysicalDisplayInfo[] configs =
                new SurfaceControl.PhysicalDisplayInfo[]{displayInfo};
        final long displayId0 = 0;
        FakeDisplay display = new FakeDisplay(displayId0, configs, 0);
        setUpDisplay(display);
        updateAvailableDisplays();
        mAdapter.registerLocked();
        waitForHandlerToComplete(mHandler, HANDLER_WAIT_MS);

        assertThat(mListener.addedDisplays.size()).isEqualTo(1);
        assertThat(mListener.changedDisplays).isEmpty();

        DisplayDeviceInfo displayDeviceInfo = mListener.addedDisplays.get(
                0).getDisplayDeviceInfoLocked();

        assertThat(displayDeviceInfo.supportedModes.length).isEqualTo(configs.length);
        assertModeIsSupported(displayDeviceInfo.supportedModes, displayInfo);

        Display.Mode defaultMode = getModeById(displayDeviceInfo, displayDeviceInfo.defaultModeId);
        assertThat(defaultMode.matches(displayInfo.width, displayInfo.height,
                displayInfo.refreshRate)).isTrue();

        Display.Mode activeMode = getModeById(displayDeviceInfo, displayDeviceInfo.modeId);
        assertThat(activeMode.matches(displayInfo.width, displayInfo.height,
                displayInfo.refreshRate)).isTrue();

        // Change the display
        SurfaceControl.PhysicalDisplayInfo addedDisplayInfo = createDummyDisplayInfo(3840, 2160,
                60f);
        configs = new SurfaceControl.PhysicalDisplayInfo[]{displayInfo, addedDisplayInfo};
        display.displayInfos = configs;
        display.activeDisplayInfo = 1;
        setUpDisplay(display);
        mAdapter.registerLocked();
        waitForHandlerToComplete(mHandler, HANDLER_WAIT_MS);

        assertThat(SurfaceControl.getActiveConfig(display.displayToken)).isEqualTo(1);
        assertThat(SurfaceControl.getDisplayConfigs(display.displayToken).length).isEqualTo(2);

        assertThat(mListener.addedDisplays.size()).isEqualTo(1);
        assertThat(mListener.changedDisplays.size()).isEqualTo(1);

        DisplayDevice displayDevice = mListener.changedDisplays.get(0);
        displayDevice.applyPendingDisplayDeviceInfoChangesLocked();
        displayDeviceInfo = displayDevice.getDisplayDeviceInfoLocked();

        assertThat(displayDeviceInfo.supportedModes.length).isEqualTo(configs.length);
        assertModeIsSupported(displayDeviceInfo.supportedModes, displayInfo);
        assertModeIsSupported(displayDeviceInfo.supportedModes, addedDisplayInfo);

        activeMode = getModeById(displayDeviceInfo, displayDeviceInfo.modeId);
        assertThat(activeMode.matches(addedDisplayInfo.width, addedDisplayInfo.height,
                addedDisplayInfo.refreshRate)).isTrue();

        defaultMode = getModeById(displayDeviceInfo, displayDeviceInfo.defaultModeId);
        assertThat(defaultMode.matches(addedDisplayInfo.width, addedDisplayInfo.height,
                addedDisplayInfo.refreshRate)).isTrue();
    }

    private Display.Mode getModeById(DisplayDeviceInfo displayDeviceInfo, int modeId) {
        return Arrays.stream(displayDeviceInfo.supportedModes)
                .filter(mode -> mode.getModeId() == modeId)
                .findFirst()
                .get();
    }

    private void assertModeIsSupported(Display.Mode[] supportedModes,
            SurfaceControl.PhysicalDisplayInfo mode) {
        assertThat(Arrays.stream(supportedModes).anyMatch(
                x -> x.matches(mode.width, mode.height, mode.refreshRate))).isTrue();
    }

    private class FakeDisplay {
        public final long displayId;
        public final IBinder displayToken = new Binder();
        public SurfaceControl.PhysicalDisplayInfo[] displayInfos;
        public int activeDisplayInfo;

        private FakeDisplay(long displayId) {
            this.displayId = displayId | (0x1 << PHYSICAL_DISPLAY_ID_MODEL_SHIFT);
            this.displayInfos = new SurfaceControl.PhysicalDisplayInfo[] {
                    createDummyDisplayInfo(800, 600, 60f)
            };
        }

        private FakeDisplay(long displayId, SurfaceControl.PhysicalDisplayInfo[] displayInfos,
                int activeDisplayInfo) {
            this.displayId = displayId | (0x1 << PHYSICAL_DISPLAY_ID_MODEL_SHIFT);
            this.displayInfos = displayInfos;
            this.activeDisplayInfo = activeDisplayInfo;
        }
    }

    private void setUpDisplay(FakeDisplay config) {
        mDisplayIds.add(config.displayId);
        doReturn(config.displayToken).when(
                () -> SurfaceControl.getPhysicalDisplayToken(config.displayId));
        doReturn(config.displayInfos).when(
                () -> SurfaceControl.getDisplayConfigs(config.displayToken));
        doReturn(config.activeDisplayInfo).when(
                () -> SurfaceControl.getActiveConfig(config.displayToken));
        doReturn(0).when(() -> SurfaceControl.getActiveColorMode(config.displayToken));
        doReturn(new int[]{
                0
        }).when(() -> SurfaceControl.getDisplayColorModes(config.displayToken));
        doReturn(new int[]{
                0
        }).when(() -> SurfaceControl.getAllowedDisplayConfigs(config.displayToken));
    }

    private void updateAvailableDisplays() {
        long[] ids = new long[mDisplayIds.size()];
        int i = 0;
        for (long id : mDisplayIds) {
            ids[i] = id;
            i++;
        }
        doReturn(ids).when(() -> SurfaceControl.getPhysicalDisplayIds());
    }

    private SurfaceControl.PhysicalDisplayInfo createDummyDisplayInfo(int width, int height,
            float refreshRate) {
        SurfaceControl.PhysicalDisplayInfo info = new SurfaceControl.PhysicalDisplayInfo();
        info.density = 100;
        info.xDpi = 100;
        info.yDpi = 100;
        info.secure = false;
        info.width = width;
        info.height = height;
        info.refreshRate = refreshRate;

        return info;
    }

    private void waitForHandlerToComplete(Handler handler, long waitTimeMs)
            throws InterruptedException {
        final Object lock = new Object();
        synchronized (lock) {
            handler.post(() -> {
                synchronized (lock) {
                    lock.notify();
                }
            });
            lock.wait(waitTimeMs);
        }
    }

    private class TestListener implements DisplayAdapter.Listener {
        public ArrayList<DisplayDevice> addedDisplays = new ArrayList<>();
        public ArrayList<DisplayDevice> changedDisplays = new ArrayList<>();

        @Override
        public void onDisplayDeviceEvent(DisplayDevice device, int event) {
            if (event == DisplayAdapter.DISPLAY_DEVICE_EVENT_ADDED) {
                addedDisplays.add(device);
            } else if (event == DisplayAdapter.DISPLAY_DEVICE_EVENT_CHANGED) {
                changedDisplays.add(device);
            }
        }

        @Override
        public void onTraversalRequested() {
        }
    }
}
