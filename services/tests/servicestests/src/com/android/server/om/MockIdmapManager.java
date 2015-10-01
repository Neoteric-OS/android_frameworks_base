package com.android.server.om;

import android.annotation.NonNull;
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
    public boolean createIdmap(@NonNull PackageInfo targetPackage,
            @NonNull PackageInfo overlayPackage, int userId) {
        throw new RuntimeException("This method should be mocked by Mockito");
    }

    @Override
    public boolean removeIdmap(@NonNull OverlayInfo oi, int userId) {
        throw new RuntimeException("This method should be mocked by Mockito");
    }

    @Override
    public boolean idmapExists(@NonNull PackageInfo overlayPackage, int userId) {
        throw new RuntimeException("This method should be mocked by Mockito");
    }

    @Override
    public boolean isDangerous(@NonNull PackageInfo overlayPackage, int userId) {
        throw new RuntimeException("This method should be mocked by Mockito");
    }
}
