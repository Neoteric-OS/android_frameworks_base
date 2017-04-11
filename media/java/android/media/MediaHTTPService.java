/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.media;

import android.os.IBinder;
import android.util.Log;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.util.List;

/** @hide */
public class MediaHTTPService extends IMediaHTTPService.Stub {
    private static final String TAG = "MediaHTTPService";
    private List<HttpCookie> mCookies;
    private Object mCookieManagerLock = new Object();
    private CookieManager mCookieManager;

    public MediaHTTPService(List<HttpCookie> cookies) {
        mCookies = cookies;
        Log.v(TAG, "MediaHTTPService(" + this + "): Cookies: " + cookies);
    }

    public IMediaHTTPConnection makeHTTPConnection() {
        synchronized (mCookieManagerLock) {
            // Only need to do it once for all connections
            if ( mCookieManager == null )  {
                mCookieManager = new CookieManager();
                Log.v(TAG, "makeHTTPConnection: CookieManager created: " + mCookieManager);
            } else {
                Log.v(TAG, "makeHTTPConnection: CookieHandler (" + mCookieManager + ") exists.");
            }
            // Applying the bootstrapping cookies
            if ( mCookies != null ) {
                CookieStore store = mCookieManager.getCookieStore();
                for ( HttpCookie cookie : mCookies ) {
                    try {
                        store.add(null, cookie);
                    } catch ( Exception e ) {
                        Log.v(TAG, "makeHTTPConnection: CookieStore.add" + e);
                    }
                    //for extended debugging when needed
                    //Log.v(TAG, "MediaHTTPConnection adding Cookie[" + cookie.getName() +
                    //        "]: " + cookie);
                }
            }   // mCookies


            Log.v(TAG, "makeHTTPConnection(" + this + "): cookieHandler: " + cookieHandler +
                    " Cookies: " + mCookies);
        }   // synchronized

        return new MediaHTTPConnection(cookieManager);
    }

    /* package private */static IBinder createHttpServiceBinderIfNecessary(
            String path) {
        return createHttpServiceBinderIfNecessary(path, null);
    }

    // when cookies are provided
    static IBinder createHttpServiceBinderIfNecessary(
            String path, List<HttpCookie> cookies) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return (new MediaHTTPService(cookies)).asBinder();
        } else if (path.startsWith("widevine://")) {
            Log.d(TAG, "Widevine classic is no longer supported");
        }

        return null;
    }
}
