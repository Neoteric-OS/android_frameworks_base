/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.telephony;

import static android.Manifest.permission.WRITE_RLOG;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.app.ActivityManager;
import android.os.Build;
import android.os.Process;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.util.Log.Level;
import android.util.SparseIntArray;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


/**
 * A class to log strings to the RADIO LOG.
 *
 * @hide
 */
@SystemApi
public final class Rlog {

    private static final boolean USER_BUILD = Build.IS_USER;

    // Cache of checkUidForRlogPermission: key - uid, value - PackageManager.PERMISSION_*
    private static final SparseIntArray UID_RLOG_PERMISSION = new SparseIntArray();

    private Rlog() {
    }

    /**
     * Send a {@link Log#VERBOSE} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int v(@Nullable String tag, @NonNull String msg) {
        return println(Log.VERBOSE, tag, msg);
    }

    /**
     * Send a {@link Log#VERBOSE} log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static int v(@Nullable String tag, @NonNull String msg, @Nullable Throwable tr) {
        return println(Log.VERBOSE, tag, msg + '\n' + Log.getStackTraceString(tr));
    }

    /**
     * Send a {@link Log#DEBUG} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int d(@Nullable String tag, @NonNull String msg) {
        return println(Log.DEBUG, tag, msg);
    }

    /**
     * Send a {@link Log#DEBUG} log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static int d(@Nullable String tag, @NonNull String msg, @Nullable Throwable tr) {
        return println(Log.DEBUG, tag, msg + '\n' + Log.getStackTraceString(tr));
    }

    /**
     * Send an {@link Log#INFO} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int i(@Nullable String tag, @NonNull String msg) {
        return println(Log.INFO, tag, msg);
    }

    /**
     * Send a {@link Log#INFO} log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static int i(@Nullable String tag, @NonNull String msg, @Nullable Throwable tr) {
        return println(Log.INFO, tag, msg + '\n' + Log.getStackTraceString(tr));
    }

    /**
     * Send a {@link Log#WARN} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int w(@Nullable String tag, @NonNull String msg) {
        return println(Log.WARN, tag, msg);
    }

    /**
     * Send a {@link Log#WARN} log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static int w(@Nullable String tag, @NonNull String msg, @Nullable Throwable tr) {
        return println(Log.WARN, tag, msg + '\n' + Log.getStackTraceString(tr));
    }

    /**
     * Send a {@link Log#WARN} log message for an exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param tr An exception to log
     */
    public static int w(@Nullable String tag, @Nullable Throwable tr) {
        return println(Log.WARN, tag, Log.getStackTraceString(tr));
    }

    /**
     * Send an {@link Log#ERROR} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int e(@Nullable String tag, @NonNull String msg) {
        return println(Log.ERROR, tag, msg);
    }

    /**
     * Send a {@link Log#ERROR} log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static int e(@Nullable String tag, @NonNull String msg, @Nullable Throwable tr) {
        return println(Log.ERROR, tag, msg + '\n' + Log.getStackTraceString(tr));
    }

    /**
     * Low-level logging call.
     * @param priority The priority/type of this log message
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @return The number of bytes written.
     */
    public static int println(@Level int priority, @Nullable String tag, @NonNull String msg) {
        enforceRlogPermissionForUid();
        return Log.println_native(Log.LOG_ID_RADIO, priority, tag, msg);
    }

    /**
     * Checks to see whether or not a log for the specified tag is loggable at the specified level.
     *
     *  The default level of any tag is set to INFO. This means that any level above and including
     *  INFO will be logged. Before you make any calls to a logging method you should check to see
     *  if your tag should be logged. You can change the default level by setting a system property:
     *      'setprop log.tag.&lt;YOUR_LOG_TAG> &lt;LEVEL>'
     *  Where level is either VERBOSE, DEBUG, INFO, WARN, ERROR, or ASSERT.
     *  You can also create a local.prop file that with the following in it:
     *      'log.tag.&lt;YOUR_LOG_TAG>=&lt;LEVEL>'
     *  and place that in /data/local.prop.
     *
     * @param tag The tag to check.
     * @param level The level to check.
     * @return Whether or not that this is allowed to be logged.
     * @throws IllegalArgumentException is thrown if the tag.length() > 23
     *         for Nougat (7.0) releases (API <= 23) and prior, there is no
     *         tag limit of concern after this API level.
     */
    public static boolean isLoggable(@Nullable String tag, @Level int level) {
        return Log.isLoggable(tag, level);
    }

    /**
     * Redact personally identifiable information for production users.
     * @param tag used to identify the source of a log message
     * @param pii the personally identifiable information we want to apply secure hash on.
     * @return If tag is loggable in verbose mode or pii is null, return the original input.
     * otherwise return a secure Hash of input pii
     */
    @NonNull
    public static String pii(@Nullable String tag, @Nullable Object pii) {
        String val = String.valueOf(pii);
        if (pii == null || TextUtils.isEmpty(val) || isLoggable(tag, Log.VERBOSE)) {
            return val;
        }
        return "[" + secureHash(val.getBytes()) + "]";
    }

    /**
     * Redact personally identifiable information for production users.
     * @param enablePiiLogging set when caller explicitly want to enable sensitive logging.
     * @param pii the personally identifiable information we want to apply secure hash on.
     * @return If enablePiiLogging is set to true or pii is null, return the original input.
     * otherwise return a secure Hash of input pii
     */
    @NonNull
    public static String pii(boolean enablePiiLogging, @Nullable Object pii) {
        String val = String.valueOf(pii);
        if (pii == null || TextUtils.isEmpty(val) || enablePiiLogging) {
            return val;
        }
        return "[" + secureHash(val.getBytes()) + "]";
    }

    /**
     * Returns a secure hash (using the SHA1 algorithm) of the provided input.
     *
     * @return "****" if the build type is user, otherwise the hash
     * @param input the bytes for which the secure hash should be computed.
     */
    private static String secureHash(byte[] input) {
        // Refrain from logging user personal information in user build.
        if (USER_BUILD) {
            return "****";
        }

        MessageDigest messageDigest;

        try {
            messageDigest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            return "####";
        }

        byte[] result = messageDigest.digest(input);
        return Base64.encodeToString(
                result, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    private static void enforceRlogPermissionForUid() throws SecurityException {
        int uid = Process.myUid();
        int permissionResult = PERMISSION_DENIED;

        int indexOfUid = UID_RLOG_PERMISSION.indexOfKey(uid);
        if (indexOfUid >= 0) { // indexOfUid >= 0 means uid found in cache
            permissionResult = UID_RLOG_PERMISSION.valueAt(indexOfUid);
        } else {
            permissionResult = ActivityManager.checkUidPermission(WRITE_RLOG, uid);
            UID_RLOG_PERMISSION.put(uid, permissionResult);
        }

        if (permissionResult != PERMISSION_GRANTED) {
            throw new SecurityException();
        }
    }
}
