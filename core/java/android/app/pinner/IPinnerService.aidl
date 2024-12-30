package android.app.pinner;

import android.app.pinner.PinnedFileStat;

/**
 * Interface for processes to communicate with system's PinnerService.
 * @hide
 */
interface IPinnerService {
    @EnforcePermission("DUMP")
    List<PinnedFileStat> getPinnerStats();
    void pinApp(int key);
    void unpinApp(int key);
    void pinFile(String fileName);
    void unpinFile(String fileName);
    void pinFiles();
    void unpinFiles();
    void pinApps();
    void unpinApps();
}
