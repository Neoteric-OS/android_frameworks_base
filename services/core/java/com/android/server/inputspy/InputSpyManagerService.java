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

//
//    private native void nativeStartRecording();
//
//    private native void nativeStopRecording();
//
    private native void nativeStartPlaying();
//
//    private native void nativeStopPlaying();
//
//    private native void nativeAddCheckPoint();
//
//    private native void nativeAnalyze();

    private native void nativeTest();

    @Override
    public void startRecording() {
        Log.d(TAG, "startRecording");
        // nativeStartRecording();
    }

    @Override
    public void stopRecording() {
        Log.d(TAG, "stopRecording");
        // nativeStopRecording();
    }

    @Override
    public void startPlaying() {
        Log.d(TAG, "startPlaying");
        nativeStartPlaying();
    }

    @Override
    public void stopPlaying() {
        Log.d(TAG, "stopPlaying");
        // nativeStopPlaying();
    }

    @Override
    public void addCheckPoint() {
        Log.d(TAG, "addCheckPoint");
        // nativeAddCheckPoint();
    }

    @Override
    public void analyze() {
        Log.d(TAG, "analyze");
        // nativeAnalyze();
    }

    @Override
    public void test() {
        Log.d(TAG, "test");
        nativeTest();
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
