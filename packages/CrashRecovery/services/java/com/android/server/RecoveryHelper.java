package com.android.server;

import android.annotation.AnyThread;
import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.VersionedPackage;
import android.net.ConnectivityModuleConnector;
import android.util.Slog;

import com.android.server.pm.ApexManager;

import java.util.Collections;
import java.util.List;
/**
 * @hide
 */
public class RecoveryHelper {
    private static final String TAG = "RecoveryHelper";

    private final ApexManager mApexManager;
    private final Context mContext;
    private final ConnectivityModuleConnector mConnectivityModuleConnector;
    public RecoveryHelper(@NonNull Context context) {
        mContext = context;
        mApexManager = ApexManager.getInstance();
        mConnectivityModuleConnector = ConnectivityModuleConnector.getInstance();
    }

    /**
     * Returns true if the package name is the name of a module.
     * If the package is an APK inside an APEX then it will use the parent's APEX package name
     * do determine if it is a module or not.
     */
    @AnyThread
    public boolean isModule(String packageName) {
        String apexPackageName =
            mApexManager.getActiveApexPackageNameContainingPackage(packageName);
        if (apexPackageName != null) {
            packageName = apexPackageName;
        }

        PackageManager pm = mContext.getPackageManager();
        try {
            return pm.getModuleInfo(packageName, 0) != null;
        } catch (PackageManager.NameNotFoundException ignore) {
            return false;
        }
    }

    /**
     * Returns true if the package name is the name of a module.
     * If the package is an APK inside an APEX then it will use the parent's APEX package name
     * do determine if it is a module or not.
     */
    public void registerExplicitPackageHealthListener(int failureReason) {
        // //register listner for ConnectivityModule
        // mConnectivityModuleConnector.registerHealthListener(
        //     packageName -> {
        //         final VersionedPackage pkg = getVersionedPackage(packageName);
        //         if (pkg == null) {
        //             Slog.wtf(TAG, "NetworkStack failed but could not find its package");
        //             return;
        //         }
        //         final List<VersionedPackage> pkgList = Collections.singletonList(pkg);
        //         onPackageFailure(pkgList, failureReason);
        //     });
    }

}