
package com.android.server.om;

import android.content.om.OverlayInfo;
import android.content.pm.PackageInfo;

import java.util.List;

/**
 * This class increases the visibility of the package private api to public to be
 * able to mock with Mockito.
 */
public class MockRules extends Rules {

    MockRules() {
        super(null, null);
    }

    @Override
    public int getInitialState(PackageInfo overlay, int userId) {
        throw new RuntimeException("This method should be mocked by Mockito");
    }

    @Override
    public int getUpdatedState(OverlayInfo overlayInfo, PackageInfo overlayPackage, int userId) {
        throw new RuntimeException("This method should be mocked by Mockito");
    }

    @Override
    public int getUpdatedState(OverlayInfo overlay, boolean enable) {
        throw new RuntimeException("This method should be mocked by Mockito");
    }

    @Override
    public int getInsertIndex(OverlayInfo oi, java.util.List<OverlayInfo> overlays) {
        throw new RuntimeException("This method should be mocked by Mockito");
    }

    @Override
    public boolean verifyOverlayOrder(List<OverlayInfo> overlays, int userId) {
        throw new RuntimeException("This method should be mocked by Mockito");
    }
}
