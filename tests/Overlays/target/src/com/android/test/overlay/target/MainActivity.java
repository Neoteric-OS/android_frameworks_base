package com.android.test.overlay.target;

import static android.os.UserHandle.USER_CURRENT;
import static android.os.UserHandle.USER_SYSTEM;

import android.app.Activity;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = "target";

    private static final String TARGET_PACKAGE_NAME = "com.android.test.overlay.target";

    static class Overlay {
        public final String packageName;
        public final int userId;

        Overlay(String packageName, int userId) {
            this.packageName = packageName;
            this.userId = userId;
        }
    };

    private static final Overlay OVERLAY_RED =
            new Overlay("com.android.test.overlay.red", USER_CURRENT);
    private static final Overlay OVERLAY_GREEN =
            new Overlay("com.android.test.overlay.green", USER_CURRENT);
    private static final Overlay OVERLAY_BLUE =
            new Overlay("com.android.test.overlay.blue", USER_CURRENT);
    private static final Overlay OVERLAY_SYSTEM =
            new Overlay("com.android.test.overlay.system", USER_SYSTEM);

    private static final Overlay[] ALL_OVERLAY_PACKAGES = {
        OVERLAY_RED,
        OVERLAY_GREEN,
        OVERLAY_BLUE,
        OVERLAY_SYSTEM,
    };

    private static final String ERR_REMOVE_EXCEPTION =
            "failed to communicate with system service %s: %s";

    private static final String ERR_SECURITY_EXCEPTION =
            "insufficient permissions, try running adb exec-out pm grant "
            + "com.android.test.overlay.target android.permission.CHANGE_OVERLAY_PACKAGES: %s";

    private IOverlayManager mOverlayManager;

    private void log(String msg) {
        Log.d(TAG, msg);

        Toast toast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        toast.show();
    }

    private IOverlayManager getOverlayManager() {
        final IBinder service = ServiceManager.getService(OVERLAY_SERVICE);
        if (service == null) {
            log(String.format("failed to get system service %s", OVERLAY_SERVICE));
            return null;
        }
        return IOverlayManager.Stub.asInterface(service);
    }

    private void enableOverlays(boolean enable, Overlay... overlays) {
        if (mOverlayManager == null) {
            return;
        }
        try {
            for (Overlay o : overlays) {
                final int userId = o.userId == USER_CURRENT ? UserHandle.myUserId() : o.userId;
                final boolean status = mOverlayManager.setEnabled(o.packageName, enable, userId);
                if (!status) {
                    log(String.format("failed to %s %s for user %d", enable ? "enable" : "disable",
                                o.packageName, userId));
                }
            }
        } catch (RemoteException e) {
            log(String.format(ERR_REMOVE_EXCEPTION, OVERLAY_SERVICE, e));
        } catch (SecurityException e) {
            log(String.format(ERR_SECURITY_EXCEPTION, e));
        }
    }

    private void setPriority(String[] overlayPackageNames, int userId) {
        if (mOverlayManager == null) {
            return;
        }
        if (overlayPackageNames.length < 2) {
            return;
        }

        Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    mOverlayManager.setLowestPriority(overlayPackageNames[0], userId);
                    for (int i = 1; i < overlayPackageNames.length; i++) {
                        mOverlayManager.setPriority(overlayPackageNames[i],
                                overlayPackageNames[i - 1], userId);
                    }
                } catch (RemoteException e) {
                    log(String.format(ERR_REMOVE_EXCEPTION, OVERLAY_SERVICE, e));
                } catch (SecurityException e) {
                    log(String.format(ERR_SECURITY_EXCEPTION, e));
                }
            }
        };
        runOnUiThread(task);
    }

    private void sortOverlays() {
        final String[] sortedNames = {
            OVERLAY_RED.packageName,
            OVERLAY_GREEN.packageName,
            OVERLAY_BLUE.packageName
        };
        final int userId = UserHandle.myUserId();
        setPriority(sortedNames, userId);
    }

    private void shuffleOverlays() {
        if (mOverlayManager == null) {
            return;
        }
        final int userId = UserHandle.myUserId();
        List<OverlayInfo> overlays;
        try {
            overlays = mOverlayManager.getOverlayInfosForTarget(TARGET_PACKAGE_NAME, userId);
        } catch (RemoteException e) {
            log(String.format("failed to communicate with system service %s: %s",
                        OVERLAY_SERVICE, e));
            return;
        }
        final int size = overlays.size();
        if (size < 2) {
            return;
        }

        List<String> currentOrder = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            currentOrder.add(overlays.get(i).packageName);
        }

        List<String> newOrder = new ArrayList<>(currentOrder);
        do {
            Collections.shuffle(newOrder);
        } while (newOrder.equals(currentOrder));

        setPriority(newOrder.toArray(new String[size]), userId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mOverlayManager = getOverlayManager();

        Button b = (Button) findViewById(R.id.enableRedButton);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                enableOverlays(true, OVERLAY_RED);
            }
        });

        b = (Button) findViewById(R.id.disableRedButton);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                enableOverlays(false, OVERLAY_RED);
            }
        });

        b = (Button) findViewById(R.id.enableAllButton);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                enableOverlays(true, ALL_OVERLAY_PACKAGES);
            }
        });

        b = (Button) findViewById(R.id.disableAllButton);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                enableOverlays(false, ALL_OVERLAY_PACKAGES);
            }
        });

        b = (Button) findViewById(R.id.sortButton);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sortOverlays();
            }
        });

        b = (Button) findViewById(R.id.shuffleButton);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                shuffleOverlays();
            }
        });
    }
}
