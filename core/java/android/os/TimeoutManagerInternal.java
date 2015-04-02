/*
 **
 ** Copyright 2015, The Android Open Source Project
 **
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 **     http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 */

package android.os;

/**
  * Direct interface to the TimeoutManagerService's functionality
  *
  * {@hide}
  */
public abstract class TimeoutManagerInternal {
    public abstract void init(TimeoutManagerCallbacks callbacks);
    public abstract int getTimeout(long lastUserActivityTime);
    public abstract int getScreenDimDuration();
    public abstract boolean isMaximumScreenOffTimeoutFromDeviceAdminEnforced();

    /**
     * Used by the window manager to override the user activity timeout based on the
     * current foreground activity.  It can only be used to make the timeout shorter
     * than usual, not longer.
     *
     * This method must only be called by the window manager.
     *
     * @param timeoutMillis The overridden timeout, or -1 to disable the override.
     */
    public abstract void setUserActivityTimeoutOverrideFromWindowManager(long timeoutMillis);

    /**
     * Used by device administration to set the maximum screen off timeout.
     *
     * This method must only be called by the device administration policy manager.
     *
     * @param timeoutMillis The maximum timeout, or 0 to allow any timeout.
     */
    public abstract void setMaximumScreenOffTimeoutFromDeviceAdmin(int timeoutMillis);

    public interface TimeoutManagerCallbacks {
        void onTimeoutChanged();
    }
}

