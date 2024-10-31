package android.os;

import android.util.Pair;

public class Process$ravenwood {

    private static final ThreadLocal<Pair<Integer, Boolean>> sThreadPriority =
            ThreadLocal.withInitial(() -> Pair.create(Process.THREAD_PRIORITY_DEFAULT, true));

    /**
     * Called by {@link Process#setThreadPriority(int, int)}
     */
    public static void setThreadPriority(int tid, int priority) {
        if (Process.myTid() == tid) {
            boolean backgroundOk = sThreadPriority.get().second;
            if (priority >= Process.THREAD_PRIORITY_BACKGROUND && !backgroundOk) {
                throw new IllegalArgumentException(
                        "Priority " + priority + " blocked by setCanSelfBackground()");
            }
            sThreadPriority.set(Pair.create(priority, backgroundOk));
        } else {
            throw new UnsupportedOperationException(
                    "Cross-thread priority management not yet available in Ravenwood");
        }
    }

    /**
     * Called by {@link Process#setCanSelfBackground(boolean)}
     */
    public static void setCanSelfBackground(boolean backgroundOk) {
        int priority = sThreadPriority.get().first;
        sThreadPriority.set(Pair.create(priority, backgroundOk));
    }

    /**
     * Called by {@link Process#getThreadPriority(int)}
     */
    private static int getThreadPriority(int tid) {
        if (Process.myTid() == tid) {
            return sThreadPriority.get().first;
        } else {
            throw new UnsupportedOperationException(
                    "Cross-thread priority management not yet available in Ravenwood");
        }
    }
}
