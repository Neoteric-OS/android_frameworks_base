/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.incident;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IIncidentAuthListener;
import android.os.IIncidentCompanion;
import android.os.IIncidentManager;
import android.os.IncidentManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.util.Log;

import com.android.internal.util.DumpUtils;
import com.android.server.SystemService;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Helper service for incidentd and dumpstated to provide user feedback
 * and authorization for bug and incident reports to be taken.
 */
public class IncidentCompanionService extends SystemService {
    static final String TAG = "IncidentCompanionService";

    private static final String[] RESTRICTED_IMAGE_DUMP_ARGS = new String[]{
            "--hal", "--restricted_image"};

    private static final String[] DUMP_AND_USAGE_STATS_PERMISSIONS = new String[]{
            android.Manifest.permission.DUMP,
            android.Manifest.permission.PACKAGE_USAGE_STATS
    };

    private static final Pattern PATTERN_REPORT_ID =
            Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final Pattern PATTERN_PACKAGE_NAME =
            Pattern.compile("^[a-zA-Z0-9_.]+$");
    private static final Pattern PATTERN_CLASS_NAME =
            Pattern.compile("^[a-zA-Z0-9_.$]+$");

    private static final int MAX_ID_LENGTH = 128;
    private static final int MAX_PACKAGE_LENGTH = 256;
    private static final int MAX_URI_LENGTH = 1024;

    private PendingReports mPendingReports;

    private final class BinderService extends IIncidentCompanion.Stub {

        @Override
        public void authorizeReport(int callingUid, final String callingPackage,
                final String receiverClass, final String reportId,
                final int flags, final IIncidentAuthListener listener) {
            enforceRequestAuthorizationPermission();
            requireValidPackageName(callingPackage);
            requireValidClassName(receiverClass);
            requireValidReportId(reportId);

            final long ident = Binder.clearCallingIdentity();
            try {
                mPendingReports.authorizeReport(callingUid, callingPackage,
                        receiverClass, reportId, flags, listener);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void cancelAuthorization(final IIncidentAuthListener listener) {
            enforceRequestAuthorizationPermission();

            final long ident = Binder.clearCallingIdentity();
            try {
                mPendingReports.cancelAuthorization(listener);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void sendReportReadyBroadcast(String pkg, String cls) {
            enforceRequestAuthorizationPermission();
            requireValidPackageName(pkg);
            requireValidClassName(cls);

            final long ident = Binder.clearCallingIdentity();
            try {
                final Context context = getContext();
                final int currentAdminUser = getCurrentUserIfAdmin();
                if (currentAdminUser == UserHandle.USER_NULL) {
                    return;
                }

                final Intent intent = new Intent(Intent.ACTION_INCIDENT_REPORT_READY);
                intent.setComponent(new ComponentName(pkg, cls));

                Log.d(TAG, "sendReportReadyBroadcast sending currentUser=" + currentAdminUser
                        + " userHandle=" + UserHandle.of(currentAdminUser)
                        + " intent=" + intent);

                context.sendBroadcastAsUserMultiplePermissions(intent,
                        UserHandle.of(currentAdminUser),
                        DUMP_AND_USAGE_STATS_PERMISSIONS);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public List<String> getPendingReports() {
            enforceAuthorizePermission();
            return mPendingReports.getPendingReports();
        }

        @Override
        public void approveReport(String uri) {
            enforceAuthorizePermission();
            requireValidUri(uri);

            final long ident = Binder.clearCallingIdentity();
            try {
                mPendingReports.approveReport(uri);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void denyReport(String uri) {
            enforceAuthorizePermission();
            requireValidUri(uri);

            final long ident = Binder.clearCallingIdentity();
            try {
                mPendingReports.denyReport(uri);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public List<String> getIncidentReportList(String pkg, String cls)
                throws RemoteException {
            enforceAccessReportsPermissions(pkg);
            requireValidPackageName(pkg);
            requireValidClassName(cls);

            final long ident = Binder.clearCallingIdentity();
            try {
                return getIIncidentManager().getIncidentReportList(pkg, cls);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void deleteIncidentReports(String pkg, String cls, String id)
                throws RemoteException {
            enforceAccessReportsPermissions(pkg);
            requireValidPackageName(pkg);
            requireValidClassName(cls);
            requireValidReportId(id);

            final long ident = Binder.clearCallingIdentity();
            try {
                getIIncidentManager().deleteIncidentReports(pkg, cls, id);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void deleteAllIncidentReports(String pkg) throws RemoteException {
            enforceAccessReportsPermissions(pkg);
            requireValidPackageName(pkg);

            final long ident = Binder.clearCallingIdentity();
            try {
                getIIncidentManager().deleteAllIncidentReports(pkg);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public IncidentManager.IncidentReport getIncidentReport(
                String pkg, String cls, String id) throws RemoteException {
            enforceAccessReportsPermissions(pkg);
            requireValidPackageName(pkg);
            requireValidClassName(cls);
            requireValidReportId(id);

            final long ident = Binder.clearCallingIdentity();
            try {
                return getIIncidentManager().getIncidentReport(pkg, cls, id);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        protected void dump(FileDescriptor fd, final PrintWriter writer, String[] args) {
            if (!DumpUtils.checkDumpPermission(getContext(), TAG, writer)) {
                return;
            }
            if (args.length == 1 && "--restricted_image".equals(args[0])) {
                dumpRestrictedImages(fd);
            } else {
                mPendingReports.dump(fd, writer, args);
            }
        }

        private void dumpRestrictedImages(FileDescriptor fd) {
            if (!(Build.IS_ENG || Build.IS_USERDEBUG)) {
                return;
            }
            final Resources res = getContext().getResources();
            final String[] services = res.getStringArray(
                    com.android.internal.R.array.config_restrictedImagesServices);
            final int servicesCount = services.length;
            for (int i = 0; i < servicesCount; i++) {
                final String name = services[i];
                Log.d(TAG, "Looking up service " + name);
                final IBinder service = ServiceManager.getService(name);
                if (service != null) {
                    Log.d(TAG, "Calling dump on service: " + name);
                    try {
                        service.dump(fd, RESTRICTED_IMAGE_DUMP_ARGS);
                    } catch (RemoteException ex) {
                        Log.w(TAG, "dump --restricted_image of " + name + " threw", ex);
                    }
                }
            }
        }

        private void requireValidReportId(String id) {
            if (id == null || id.isEmpty() || id.length() > MAX_ID_LENGTH) {
                throw new IllegalArgumentException(
                        "Report ID is missing or exceeds maximum length");
            }
            if (!PATTERN_REPORT_ID.matcher(id).matches()) {
                throw new IllegalArgumentException(
                        "Report ID contains unsupported characters");
            }
        }

        private void requireValidPackageName(String pkg) {
            if (pkg == null || pkg.isEmpty() || pkg.length() > MAX_PACKAGE_LENGTH) {
                throw new IllegalArgumentException(
                        "Package name is missing or exceeds maximum length");
            }
            if (!PATTERN_PACKAGE_NAME.matcher(pkg).matches()) {
                throw new IllegalArgumentException(
                        "Package name contains unsupported characters");
            }
        }

        private void requireValidClassName(String cls) {
            if (cls == null || cls.isEmpty() || cls.length() > MAX_PACKAGE_LENGTH) {
                throw new IllegalArgumentException(
                        "Class name is missing or exceeds maximum length");
            }
            if (!PATTERN_CLASS_NAME.matcher(cls).matches()) {
                throw new IllegalArgumentException(
                        "Class name contains unsupported characters");
            }
        }

        private void requireValidUri(String uri) {
            if (uri == null || uri.isEmpty() || uri.length() > MAX_URI_LENGTH) {
                throw new IllegalArgumentException(
                        "URI is missing or exceeds maximum length");
            }
            if (uri.contains("..")) {
                throw new IllegalArgumentException("URI format is not supported");
            }
            android.net.Uri parsed = android.net.Uri.parse(uri);
            String scheme = parsed.getScheme();
            if (!"content".equals(scheme) && !"file".equals(scheme)) {
                throw new IllegalArgumentException("Unsupported URI scheme: " + scheme);
            }
        }

        private void enforceRequestAuthorizationPermission() {
            getContext().enforceCallingOrSelfPermission(
                    android.Manifest.permission.REQUEST_INCIDENT_REPORT_APPROVAL, null);
        }

        private void enforceAuthorizePermission() {
            getContext().enforceCallingOrSelfPermission(
                    android.Manifest.permission.APPROVE_INCIDENT_REPORTS, null);
        }

        private void enforceAccessReportsPermissions(String pkg) {
            if (getContext().checkCallingPermission(
                    android.Manifest.permission.APPROVE_INCIDENT_REPORTS)
                    != PackageManager.PERMISSION_GRANTED) {
                getContext().enforceCallingOrSelfPermission(
                        android.Manifest.permission.DUMP, null);
                getContext().enforceCallingOrSelfPermission(
                        android.Manifest.permission.PACKAGE_USAGE_STATS, null);
                if (pkg != null) {
                    enforceCallerIsSameApp(pkg);
                }
            }
        }

        private void enforceCallerIsSameApp(String pkg) throws SecurityException {
            try {
                final int uid = Binder.getCallingUid();
                final int userId = UserHandle.getCallingUserId();
                final ApplicationInfo ai = getContext().getPackageManager()
                        .getApplicationInfoAsUser(pkg, 0, userId);
                if (ai == null) {
                    throw new SecurityException("Unknown package " + pkg);
                }
                if (!UserHandle.isSameApp(ai.uid, uid)) {
                    throw new SecurityException("Calling uid " + uid
                            + " gave package " + pkg
                            + " which is owned by uid " + ai.uid);
                }
            } catch (PackageManager.NameNotFoundException re) {
                throw new SecurityException("Unknown package " + pkg + "\n" + re);
            }
        }
    }

    public IncidentCompanionService(Context context) {
        super(context);
        mPendingReports = new PendingReports(context);
    }

    @Override
    public void onStart() {
        publishBinderService(Context.INCIDENT_COMPANION_SERVICE, new BinderService());
    }

    @Override
    public void onBootPhase(int phase) {
        super.onBootPhase(phase);
        switch (phase) {
            case SystemService.PHASE_BOOT_COMPLETED:
                mPendingReports.onBootCompleted();
                break;
        }
    }

    private IIncidentManager getIIncidentManager() throws RemoteException {
        return IIncidentManager.Stub.asInterface(
                ServiceManager.getService(Context.INCIDENT_SERVICE));
    }

    public static int getCurrentUserIfAdmin() {
        UserInfo currentUser;
        try {
            currentUser = ActivityManager.getService().getCurrentUser();
        } catch (RemoteException ex) {
            throw new RuntimeException(ex);
        }

        if (currentUser == null) {
            Log.w(TAG, "No current user. Nobody to approve the report."
                    + " The report will be denied.");
            return UserHandle.USER_NULL;
        }

        if (!currentUser.isAdmin()) {
            Log.w(TAG, "Only an admin user running in foreground can approve"
                    + " bugreports, but the current foreground user is not an admin user."
                    + " The report will be denied.");
            return UserHandle.USER_NULL;
        }

        return currentUser.id;
    }
}


