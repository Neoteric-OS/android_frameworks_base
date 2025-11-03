package com.android.internal.os;

import android.content.Context;
import android.os.Build;
import android.os.DropBoxManager;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import dalvik.system.VMRuntime;
import dalvik.system.VMRuntime.LockOrderingViolationLogger;

public class Lockdep {
    private static class LockdepHandler extends LockOrderingViolationLogger<StringBuilder> {
        public LockdepHandler(Context ctx) {
            mContext = ctx;
        }

        @Override
        public StringBuilder beginLoggingViolation() {
            StringBuilder sb = new StringBuilder();
            sb.append("Process: ").append(Process.myProcessName()).append("\n");
            sb.append("PID: ").append(Process.myPid()).append("\n");
            sb.append("UID: ").append(Process.myUid()).append("\n");
            sb.append("Package: ").append(mContext.getPackageName()).append("\n");
            sb.append("Build: ").append(Build.FINGERPRINT).append("\n");
            sb.append("Uptime: ").append(SystemClock.uptimeMillis()).append("\n");
            sb.append("\n");
            return sb;
        }

        @Override
        public void finishLoggingViolation(StringBuilder sb) {
            DropBoxManager dbx = mContext.getSystemService(DropBoxManager.class);
            dbx.addText("lockdep", sb.toString());
        }

        @Override
        public void log(StringBuilder sb, String fmt, Object... args) {
            String str = String.format(fmt, args);
            Log.e("lockdep", str);
            sb.append(str);
            sb.append('\n');
        }

        private Context mContext;
    }

    public static void registerHandler(Context ctx) {
        LockdepHandler lockdepHandler = new LockdepHandler(ctx);
        VMRuntime.setLockOrderingViolationLogger(lockdepHandler);
    }
}
