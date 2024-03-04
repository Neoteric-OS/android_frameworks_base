package android.inputspy;

import android.annotation.SuppressLint;
import android.annotation.SystemService;
import android.content.Context;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;


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
        Toast.makeText(mContext, "startRecording", Toast.LENGTH_SHORT).show();
        try {
            mService.startRecording();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void stopRecording() {
        Log.d(TAG, "stopRecording");
        Toast.makeText(mContext, "stopRecording", Toast.LENGTH_SHORT).show();
        try {
            mService.stopRecording();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void startPlaying() {
        Log.d(TAG, "startPlaying");
        Toast.makeText(mContext, "startPlaying", Toast.LENGTH_SHORT).show();
        try {
            mService.startPlaying();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void stopPlaying() {
        Log.d(TAG, "stopPlaying");
        Toast.makeText(mContext, "stopPlaying", Toast.LENGTH_SHORT).show();
        try {
            mService.stopPlaying();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void addCheckPoint() {
        Log.d(TAG, "addCheckPoint");
        Toast.makeText(mContext, "addCheckPoint", Toast.LENGTH_SHORT).show();
        try {
            mService.addCheckPoint();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void analyze() {
        Log.d(TAG, "analyze");
        Toast.makeText(mContext, "analyze", Toast.LENGTH_SHORT).show();
        try {
            mService.analyze();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
