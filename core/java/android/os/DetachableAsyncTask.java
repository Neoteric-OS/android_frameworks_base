package android.os;

import android.util.Log;

/**
 * Alternative API for {@link AsyncTask} which uses a pattern of detachable
 * callbacks that permit the implementer to avoid a dependency on the outer
 * class (usually an {@link android.app.Activity} that executes it. This is a
 * practical consideration that many usages of {@link AsyncTask} must manually
 * deal to avoid race conditions or a potential memory leak.
 * <p>
 * Expanding on the original DownloadTask sample we arrive at the following
 * solution:
 *
 * <pre class="prettyprint">
 * public class DownloadActivity extends Activity {
 *     private DownloadTask mTask;
 *     protected void onCreate(Bundle bundle) {
 *         super.onCreate(bundle);
 *         ...
 *         mTask = getLastNonConfigurationInstance();
 *         if (mTask == null) {
 *             mTask = new DownloadTask();
 *         }
 *         mTask.setCallback(mDownloadCallback);
 *         if (mTask.getStatus() == AsyncTask.Status.PENDING) {
 *             mTask.execute(...);
 *         }
 *     }
 *
 *     // Restore the original DownloadTask on screen orientation changes.
 *     protected Object onRetainNonConfigurationInstance() {
 *         return mTask;
 *     }
 *
 *     protected void onDestroy() {
 *         super.onDestroy();
 *         mTask.clearCallback();
 *     }
 *
 *     private static class DownloadTask extends DetachableAsyncTask&lt;URL, Integer, Long&gt; {
 *         protected Long doInBackground(URL... urls) {
 *             int count = urls.length;
 *             long totalSize = 0;
 *             for (int i = 0; i &lt; count; i++) {
 *                 totalSize += Downloader.downloadFile(urls[i]);
 *                 publishProgress((int) ((i / (float) count) * 100));
 *             }
 *             return totalSize;
 *         }
 *     }
 *
 *     private final DetachableAsyncTask.TaskCallbacks<Long, Integer> mTaskCallback =
 *             new DetachableAsyncTask.TaskCallbacks<Long, Integer>() {
 *         protected void onProgressUpdate(Integer... progress) {
 *             setProgressPercent(progress[0]);
 *         }
 *
 *         protected void onPostExecute(Long result) {
 *             showMyDialog(&quot;Downloaded &quot; + result + &quot; bytes&quot;);
 *         }
 *     }
 * }
 * </pre>
 * A complete example is available in the ApiDemos sample under
 * os/DetachableAsyncTaskDemo.java
 */
public abstract class DetachableAsyncTask<Params, Progress, Result> extends
        AsyncTask<Params, Progress, Result> {
    private static final String TAG = DetachableAsyncTask.class.getSimpleName();

    private TaskCallbacks<Result, Progress> mCallback;

    public void clearCallback() {
        mCallback = null;
    }

    public void setCallback(TaskCallbacks<Result, Progress> callback) {
        mCallback = callback;
    }

    @Override
    protected final void onCancelled() {
        if (mCallback != null) {
            mCallback.onCancelled();
        }
    }

    @Override
    protected final void onPostExecute(Result result) {
        if (mCallback != null) {
            mCallback.onPostExecute(result);
        } else {
            /* XXX: not printing the result to avoid accidentally exposing
             * sensitive data from the application. */
            Log.w(TAG, "Dropping async task result on floor");
        }
    }

    @Override
    protected final void onPreExecute() {
        if (mCallback != null) {
            mCallback.onPreExecute();
        }
    }

    @Override
    protected final void onProgressUpdate(Progress... values) {
        if (mCallback != null) {
            mCallback.onProgressUpdate(values);
        }
    }

    public abstract static class TaskCallbacks<R, P> {
        protected void onCancelled() {}
        protected void onPreExecute() {}
        protected void onPostExecute(R result) {}
        protected void onProgressUpdate(P... values) {}
    }
}
