package android.inputspy;

import android.annotation.SuppressLint;
import android.annotation.SystemService;
import android.content.Context;
import android.os.RemoteException;
import android.util.Log;


@SuppressLint("NewApi")
@SystemService(Context.INPUT_SPY_SERVICE)
public class InputSpyManager {
    private static final String TAG = InputSpyManager.class.getSimpleName();
    private Context mContext;
    private IInputSpy mService;


    /**
     * {@hide}
     */
    public InputSpyManager(Context mContext, IInputSpy mService) {
        this.mContext = mContext;
        this.mService = mService;
    }

    public void startRecording() {
        Log.d(TAG, "startRecording");
        try {
            mService.startRecording();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void stopRecording() {
        Log.d(TAG, "stopRecording");
        try {
            mService.stopRecording();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void startPlaying() {
        Log.d(TAG, "startPlaying");
        try {
            mService.startPlaying();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void stopPlaying() {
        Log.d(TAG, "stopPlaying");
        try {
            mService.stopPlaying();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void addCheckPoint() {
        Log.d(TAG, "addCheckPoint");
        try {
            mService.addCheckPoint();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void analyze() {
        Log.d(TAG, "analyze");
        try {
            mService.analyze();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void test() {
        Log.d(TAG, "test");
        try {
            mService.test();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
