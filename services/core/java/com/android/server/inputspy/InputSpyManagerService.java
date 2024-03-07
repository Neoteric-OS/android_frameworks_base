package com.android.server.inputspy;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.inputspy.IInputSpy;
import android.widget.Toast;

import com.android.server.SystemService;


/**
 * Core service for recording and replaying user's input events. Internally uses JNI to inject
 * events into /dev/input/eventX, the very data source of Android Input System.
 */
public class InputSpyManagerService extends IInputSpy.Stub {
    private static final String TAG = InputSpyManagerService.class.getSimpleName();
    private static final String HANDLER_THREAD_NAME = "InputSpyManagerService-HandlerThread";

    private final Context mContext;
    private final HandlerThread mHandlerThread;
    private final Handler mHandler;
    private final Handler mMainHandler;

    public InputSpyManagerService(Context context) {
        mContext = context;
        mHandlerThread = new HandlerThread(HANDLER_THREAD_NAME);
        mHandlerThread.start();
        mHandler = mHandlerThread.getThreadHandler();
        mMainHandler = new Handler(Looper.getMainLooper());
    }

//    private native void nativeStartRecording();

//    private native void nativeStopRecording();

    private native void nativeStartPlaying();

//    private native void nativeStopPlaying();

//    private native void nativeAddCheckPoint();

//    private native void nativeAnalyze();

    private native void nativeTest();

    /**
     * The toast is mandatorily scheduled into main thread, in case that user call this method in a
     * sub thread.
     * <p>
     * The heavy works are mandatorily scheduled into a sub thread, in case that user call this
     * method in main thread.
     * <p>
     * Other methods are similar to this one.
     */
    @Override
    public void startRecording() {
        mMainHandler.post(() -> {
            Log.d(TAG, "show toast at thread " + Thread.currentThread());
            Toast.makeText(mContext, "startRecording", Toast.LENGTH_SHORT).show();
        });
        mHandler.post(() -> {
            Log.d(TAG, "startRecording at thread " + mHandlerThread);
            // nativeStartRecording();
        });
    }

    @Override
    public void stopRecording() {
        mMainHandler.post(() -> {
            Log.d(TAG, "show toast at thread " + Thread.currentThread());
            Toast.makeText(mContext, "stopRecording", Toast.LENGTH_SHORT).show();
        });
        mHandler.post(() -> {
            Log.d(TAG, "stopRecording at thread " + mHandlerThread);
            // nativeStopRecording();
        });
    }

    @Override
    public void startPlaying() {
        mMainHandler.post(() -> {
            Log.d(TAG, "show toast at thread " + Thread.currentThread());
            Toast.makeText(mContext, "startPlaying", Toast.LENGTH_SHORT).show();
        });
        mHandler.post(() -> {
            Log.d(TAG, "startPlaying at thread " + mHandlerThread);
            nativeStartPlaying();
        });
    }

    @Override
    public void stopPlaying() {
        mMainHandler.post(() -> {
            Log.d(TAG, "show toast at thread " + Thread.currentThread());
            Toast.makeText(mContext, "stopPlaying", Toast.LENGTH_SHORT).show();
        });
        mHandler.post(() -> {
            Log.d(TAG, "stopPlaying at thread " + mHandlerThread);
            // nativeStopPlaying();
        });
    }

    @Override
    public void addCheckPoint() {
        mMainHandler.post(() -> {
            Log.d(TAG, "show toast at thread " + Thread.currentThread());
            Toast.makeText(mContext, "addCheckPoint", Toast.LENGTH_SHORT).show();
        });
        mHandler.post(() -> {
            Log.d(TAG, "addCheckPoint at thread " + mHandlerThread);
            // nativeAddCheckPoint();
        });
    }

    @Override
    public void analyze() {
        mMainHandler.post(() -> {
            Log.d(TAG, "show toast at thread " + Thread.currentThread());
            Toast.makeText(mContext, "analyze", Toast.LENGTH_SHORT).show();
        });
        mHandler.post(() -> {
            Log.d(TAG, "analyze at thread " + mHandlerThread);
            // nativeAnalyze();
        });
    }

    @Override
    public void test() {
        mMainHandler.post(() -> {
            Log.d(TAG, "show toast at thread " + Thread.currentThread());
            Toast.makeText(mContext, "test", Toast.LENGTH_SHORT).show();
        });
        mHandler.post(() -> {
            Log.d(TAG, "test at thread " + mHandlerThread);
            nativeTest();
        });
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
