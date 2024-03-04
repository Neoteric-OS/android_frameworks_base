package com.android.server.inputspy;

import android.content.Context;
import android.os.IBinder;
import android.util.Log;
import android.inputspy.IInputSpy;

import com.android.server.SystemService;

public class InputSpyManagerService extends IInputSpy.Stub {
    private static final String TAG = InputSpyManagerService.class.getSimpleName();
    private final Context mContext;

    public InputSpyManagerService(Context context) {
        mContext = context;
    }

    @Override
    public void startRecording() {
        Log.d(TAG, "startRecording");
    }


    @Override
    public void stopRecording() {
        Log.d(TAG, "stopRecording");
    }

    @Override
    public void startPlaying() {
        Log.d(TAG, "startPlaying");
    }

    @Override
    public void stopPlaying() {
        Log.d(TAG, "stopPlaying");
    }

    @Override
    public void addCheckPoint() {
        Log.d(TAG, "addCheckPoint");
    }

    @Override
    public void analyze() {
        Log.d(TAG, "analyze");
    }

    public static class Lifecycle extends SystemService {
        InputSpyManagerService mService;

        public Lifecycle(Context context) {
            super(context);
            mService = new InputSpyManagerService(context);
        }

        @Override
        public void onStart() {
            // will finally trigger ServiceManager.addService
            try {
                publishBinderService(Context.INPUT_SPY_SERVICE, (IBinder) mService);
                Log.d(TAG, "published binder service" + mService);
            } catch (Exception e) {
                Log.e(TAG, e.getLocalizedMessage());
            }
        }
    }
}
