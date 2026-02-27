package android.app.appfunctions;

import android.annotation.Nullable;

/**
 * Internal class to hold execution information for an app function.
 *
 * @hide
 */
public class AppFunctionExecutionInfo {
    private final boolean mEnabled;
    @Nullable private final String mServiceName;

    public AppFunctionExecutionInfo(boolean enabled, @Nullable String serviceName) {
        mEnabled = enabled;
        mServiceName = serviceName;
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    @Nullable
    public String getServiceName() {
        return mServiceName;
    }
}
