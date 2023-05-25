package com.android.server.clipboard;

public interface ClipboardManagerInternal {
    /**
     * Package Uri Permission Removed
     * @param pkg
     * @param userId
     */
    void onPackageUriPermissionRemoved(String pkg, int userId);
}
