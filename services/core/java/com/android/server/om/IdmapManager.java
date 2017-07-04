/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.om;

import static com.android.server.om.OverlayManagerService.DEBUG;
import static com.android.server.om.OverlayManagerService.TAG;

import android.annotation.NonNull;
import android.content.om.OverlayInfo;
import android.os.UserHandle;
import android.util.Slog;

import com.android.server.pm.Installer.InstallerException;
import com.android.server.pm.Installer;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Handle the creation and deletion of idmap files.
 *
 * The actual work is performed by the idmap binary, launched through Installer
 * and installd.
 *
 * Note: this class is subclassed in the OMS unit tests, and hence not marked as final.
 */
public class IdmapManager {
    // TODO(martenkongstad): when mEnabledOverlayPaths is moved from
    // PackageManagerService to OverlayManagerService, make this class and
    // getIdmapPath package private again
    private final Installer mInstaller;

    IdmapManager(final Installer installer) {
        mInstaller = installer;
    }

    boolean createIdmap(@NonNull final PackageInfoLite targetPackage,
            @NonNull final PackageInfoLite overlayPackage, int userId) {
        // unused userId: see comment in OverlayManagerServiceImpl.removeIdmapIfPossible
        if (DEBUG) {
            Slog.d(TAG, "create idmap for " + targetPackage.packageName + " and "
                    + overlayPackage.packageName);
        }
        final int sharedGid = UserHandle.getSharedAppGid(targetPackage.uid);
        final String targetPath = targetPackage.codePath;
        final String overlayPath = overlayPackage.codePath;
        final String idmapPath = getIdmapPath(overlayPath, userId);
        try {
            mInstaller.createIdmap(targetPath, overlayPath, sharedGid, idmapPath);
        } catch (InstallerException e) {
            Slog.w(TAG, "failed to generate idmap " + idmapPath + ": " + e.getMessage());
            return false;
        }
        return true;
    }

    boolean removeIdmap(@NonNull final OverlayInfo oi, final int userId) {
        // unused userId: see comment in OverlayManagerServiceImpl.removeIdmapIfPossible
        if (DEBUG) {
            Slog.d(TAG, "remove idmap " + oi.idmapPath);
        }
        try {
            mInstaller.removeIdmap(oi.idmapPath);
        } catch (InstallerException e) {
            Slog.w(TAG, "failed to remove idmap " + oi.idmapPath + ": " + e.getMessage());
            return false;
        }
        return true;
    }

    boolean idmapExists(@NonNull final OverlayInfo oi) {
        // unused OverlayInfo.userId: see comment in OverlayManagerServiceImpl.removeIdmapIfPossible
        return oi.idmapPath != null ? new File(oi.idmapPath).isFile() : false;
    }

    boolean idmapExists(@NonNull final PackageInfoLite overlayPackage, final int userId) {
        final String idmapPath =
            getIdmapPath(overlayPackage.codePath, userId);
        return new File(idmapPath).isFile();
    }

    public static String getIdmapPath(@NonNull final String baseCodePath, final int userId) {
        // unused userId: see comment in OverlayManagerServiceImpl.removeIdmapIfPossible
        final StringBuilder sb = new StringBuilder("/data/resource-cache/");
        sb.append(baseCodePath.substring(1).replace('/', '@'));
        sb.append("@idmap");
        return sb.toString();
    }
}
