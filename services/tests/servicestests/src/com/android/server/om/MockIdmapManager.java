package com.android.server.om;

import android.content.om.OverlayInfo;
import android.content.pm.PackageInfo;

/**
 * This class increases the visibility of the package private api to public to be
 * able to mock with Mockito.
 */
public class MockIdmapManager extends IdmapManager {

    MockIdmapManager() {
        super(null);
    }

    @Override
    public boolean createIdmap(PackageInfo target, PackageInfo overlay) {
        throw new RuntimeException("This method should be mocked by Mockito");
    }

    @Override
    public boolean idmapExists(PackageInfo pi) {
        throw new RuntimeException("This method should be mocked by Mockito");
    }

    @Override
    public boolean isDangerous(PackageInfo overlay) {
        throw new RuntimeException("This method should be mocked by Mockito");
    }

    @Override
    public void removeIdmap(OverlayInfo overlay) {
        throw new RuntimeException("This method should be mocked by Mockito");
    }
}
