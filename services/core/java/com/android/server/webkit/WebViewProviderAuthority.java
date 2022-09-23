/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.webkit;

import android.app.AppGlobals;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.content.res.XmlResourceParser;
import android.os.Build;
import android.os.Bundle;
import android.util.AndroidRuntimeException;
import android.util.Log;
import android.webkit.UserPackage;
import android.webkit.WebViewProviderInfo;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.XmlUtils;
import com.android.server.pm.parsing.pkg.AndroidPackage;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides information about permitted WebView providers.
 *
 * WebViewProviderAuthority can be queried to return the system's configured list of valid WebView
 * providers. WebViewProviderAuthority also provides utilities for checking whether a given package
 * is valid as a given WebView provider.
 * <p>
 * This class is not dependent on any services, so it may be used during early boot when services
 * are not fully initialised or registered.
 * @hide
 */
public final class WebViewProviderAuthority {
    private static final String TAG = WebViewProviderAuthority.class.getSimpleName();
    private static final String TAG_START = "webviewproviders";
    private static final String TAG_WEBVIEW_PROVIDER = "webviewprovider";
    private static final String TAG_PACKAGE_NAME = "packageName";
    private static final String TAG_DESCRIPTION = "description";
    // Whether or not the provider must be explicitly chosen by the user to be used.
    private static final String TAG_AVAILABILITY = "availableByDefault";
    private static final String TAG_SIGNATURE = "signature";
    private static final String TAG_FALLBACK = "isFallback";

    public static final String WEBVIEW_LIBRARY_FLAG = "com.android.webview.WebViewLibrary";

    /**
     * Validation result of a package as a potential WebView provider.
     */
    public enum Validity {
        OK,
        INCORRECT_PACKAGE_NAME,
        INCORRECT_SDK_VERSION,
        INCORRECT_VERSION_CODE,
        INCORRECT_SIGNATURE,
        NO_LIBRARY_FLAG;

        /**
         * Provide a human readable description of the validity result.
         */
        public String description() {
            switch (this) {
                case OK:
                    return "Valid";
                case INCORRECT_PACKAGE_NAME:
                    return "Package name mismatch";
                case INCORRECT_SDK_VERSION:
                    return "SDK version too low";
                case INCORRECT_VERSION_CODE:
                    return "Version code too low";
                case INCORRECT_SIGNATURE:
                    return "Incorrect signature";
                case NO_LIBRARY_FLAG:
                    return "No WebView-library manifest flag";
            }
            throw new AssertionError("Unreachable");
        }

        /**
         * Return true if the result represents a successful validation.
         */
        public boolean isValid() {
            return this == OK;
        }
    };

    private final WebViewProviderInfo[] mWebViewProviderPackages;
    private final boolean mIsDebuggable;

    private static final class LazyHolder {
        private static final WebViewProviderAuthority INSTANCE = createFromResources();
    }

    /**
     * Get a reference to the WebViewProviderAuthority singleton.
     */
    public static WebViewProviderAuthority getInstance() {
        return LazyHolder.INSTANCE;
    }

    private static WebViewProviderAuthority createFromResources() {
        try (XmlResourceParser parser = AppGlobals.getInitialApplication().getResources().getXml(
                com.android.internal.R.xml.config_webview_packages)) {
            return createFromXml(parser, Build.IS_DEBUGGABLE);
        }
    }

    /**
     * Create an authority with WebView providers derived from an XML document.
     * @param parser XmlPullParser for the XML definitions of valid WebView providers.
     * @param isDebuggable Whether the device is debuggable and thus relaxing some validation.
     * @return
     */
    @VisibleForTesting
    public static WebViewProviderAuthority createFromXml(XmlPullParser parser,
            boolean isDebuggable) {
        int numFallbackPackages = 0;
        int numAvailableByDefaultPackages = 0;
        List<WebViewProviderInfo> webViewProviders = new ArrayList<WebViewProviderInfo>();
        try {
            XmlUtils.beginDocument(parser, TAG_START);
            while (true) {
                XmlUtils.nextElement(parser);
                String element = parser.getName();
                if (element == null) {
                    break;
                }
                if (element.equals(TAG_WEBVIEW_PROVIDER)) {
                    String packageName = parser.getAttributeValue(null, TAG_PACKAGE_NAME);
                    if (packageName == null) {
                        throw new AndroidRuntimeException(
                                "WebView provider in framework resources missing package name");
                    }
                    String description = parser.getAttributeValue(null, TAG_DESCRIPTION);
                    if (description == null) {
                        throw new AndroidRuntimeException(
                                "WebView provider in framework resources missing description");
                    }
                    boolean availableByDefault = "true".equals(
                            parser.getAttributeValue(null, TAG_AVAILABILITY));
                    boolean isFallback = "true".equals(
                            parser.getAttributeValue(null, TAG_FALLBACK));
                    WebViewProviderInfo currentProvider = new WebViewProviderInfo(
                            packageName, description, availableByDefault, isFallback,
                            readSignatures(parser));
                    if (currentProvider.isFallback) {
                        numFallbackPackages++;
                        if (!currentProvider.availableByDefault) {
                            throw new AndroidRuntimeException(
                                    "Each WebView fallback package must be available by default.");
                        }
                        if (numFallbackPackages > 1) {
                            throw new AndroidRuntimeException(
                                    "There can be at most one WebView fallback package.");
                        }
                    }
                    if (currentProvider.availableByDefault) {
                        numAvailableByDefaultPackages++;
                    }
                    webViewProviders.add(currentProvider);
                } else {
                    Log.e(TAG, "Found an element that is not a WebView provider");
                }
            }
        } catch (XmlPullParserException | IOException e) {
            throw new AndroidRuntimeException("Error when parsing WebView config " + e);
        }
        if (numAvailableByDefaultPackages == 0) {
            throw new AndroidRuntimeException("There must be at least one WebView package "
                    + "that is available by default");
        }

        return new WebViewProviderAuthority(webViewProviders.toArray(
                new WebViewProviderInfo[webViewProviders.size()]), isDebuggable);
    }

    /**
     * Create an authority with the given WebView providers.
     * @param providers The definitions of valid WebView providers.
     * @param isDebuggable Whether the device is debuggable and thus relaxing some validation.
     * @return
     */
    @VisibleForTesting
    public WebViewProviderAuthority(WebViewProviderInfo[] providers, boolean isDebuggable) {
        mWebViewProviderPackages = providers;
        mIsDebuggable = isDebuggable;
    }

    /**
     * Reads all signatures at the current depth (within the current provider) from the XML parser.
     */
    private static String[] readSignatures(XmlPullParser parser) throws IOException,
            XmlPullParserException {
        List<String> signatures = new ArrayList<String>();
        int outerDepth = parser.getDepth();
        while (XmlUtils.nextElementWithin(parser, outerDepth)) {
            if (parser.getName().equals(TAG_SIGNATURE)) {
                // Parse the value within the signature tag
                String signature = parser.nextText();
                signatures.add(signature);
            } else {
                Log.e(TAG, "Found an element in a webview provider that is not a signature");
            }
        }
        return signatures.toArray(new String[signatures.size()]);
    }

    /**
     * Returns all packages declared in the framework resources as potential WebView providers.
     */
    public WebViewProviderInfo[] getWebViewPackages() {
        return mWebViewProviderPackages;
    }

    /**
     * Return true if the AndroidPackage validates OK as any of the configured WebView providers.
     *
     * Wraps {@link #validatePackageAsProvider(AndroidPackage, WebViewProviderInfo, long)} in a
     * loop. See that method for more details.
     */
    public boolean packageIsValidWebView(AndroidPackage androidPackage, long minimumVersionCode) {
        for (WebViewProviderInfo providerInfo : mWebViewProviderPackages) {
            if (validatePackageAsProvider(androidPackage, providerInfo, minimumVersionCode)
                    .isValid()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validate the given PackageInfo against the given WebView provider.
     *
     * @param packageInfo The package to check WebView validity for.
     * @param providerInfo The provider to check packageInfo against.
     * @param minimumVersionCode The minimum longVersionCode the packageInfo must have to be
     *                           considered a valid provider. This may be set to 0 to skip version
     *                           code checking. Version codes may be ignored on debug builds.
     * @return {@link #Validity} enum describing the result of the validation. If there are multiple
     *         reasons for why a package fails validation, only a single reason is reported.
     */
    public Validity validatePackageAsProvider(PackageInfo packageInfo,
            WebViewProviderInfo providerInfo, long minimumVersionCode) {
        return validityResult(
                providerInfo,
                packageInfo.packageName,
                packageInfo.applicationInfo.targetSdkVersion,
                packageInfo.getLongVersionCode(),
                minimumVersionCode,
                packageInfo.applicationInfo.isSystemApp(),
                packageInfo.signatures,
                packageInfo.applicationInfo.metaData);
    }

    /**
     * Validate the given AndroidPackage against the given WebView provider.
     *
     * See {@link #validatePackage(PackageInfo, WebViewProviderInfo, long)}
     */
    public Validity validatePackageAsProvider(AndroidPackage androidPackage,
            WebViewProviderInfo providerInfo, long minimumVersionCode) {
        return validityResult(
                providerInfo,
                androidPackage.getPackageName(),
                androidPackage.getTargetSdkVersion(),
                androidPackage.getLongVersionCode(),
                minimumVersionCode,
                androidPackage.isSystem(),
                androidPackage.getSigningDetails().getSignatures(),
                androidPackage.getMetaData());
    }

    /**
     * Contains the logic for validity checks.
     */
    private Validity validityResult(WebViewProviderInfo configInfo, String packageName,
            int targetSdkVersion, long versionCode, long minimumVersionCode, boolean isSystemApp,
            Signature[] signatures, Bundle metadata) {
        if (!configInfo.packageName.equals(packageName)) {
            return Validity.INCORRECT_PACKAGE_NAME;
        }
        // Ensure the provider targets this framework release (or a later one).
        if (targetSdkVersion < UserPackage.MINIMUM_SUPPORTED_SDK) {
            return Validity.INCORRECT_SDK_VERSION;
        }
        if (!mIsDebuggable) {
            // WebView providers may be downgraded arbitrarily low, prevent that by enforcing
            // minimum version code. This check is only enforced for user builds.
            if (!versionCodeGE(versionCode, minimumVersionCode)) {
                return Validity.INCORRECT_VERSION_CODE;
            }
            // System apps (factory or otherwise) are allowed to be WebView regardless of signature.
            if (!isSystemApp && !validateSignature(configInfo, signatures)) {
                return Validity.INCORRECT_SIGNATURE;
            }
        }
        if (metadata == null || metadata.getString(WEBVIEW_LIBRARY_FLAG) == null) {
            return Validity.NO_LIBRARY_FLAG;
        }
        return Validity.OK;
    }

    /**
     * Both versionCodes should be from a WebView provider package implemented by Chromium.
     * VersionCodes from other kinds of packages won't make any sense in this method.
     *
     * An introduction to Chromium versionCode scheme:
     * "BBBBPPPXX"
     * BBBB: 4 digit branch number. It monotonically increases over time.
     * PPP: patch number in the branch. It is padded with zeroes to the left. These three digits
     * may change their meaning in the future.
     * XX: Digits to differentiate different APK builds of the same source version.
     *
     * This method takes the "BBBB" of versionCodes and compare them.
     *
     * https://www.chromium.org/developers/version-numbers describes general Chromium versioning;
     * https://source.chromium.org/chromium/chromium/src/+/master:build/util/android_chrome_version.py
     * is the canonical source for how Chromium versionCodes are calculated.
     *
     * @return true if versionCode1 is higher than or equal to versionCode2.
     */
    private static boolean versionCodeGE(long versionCode1, long versionCode2) {
        long v1 = versionCode1 / 100000;
        long v2 = versionCode2 / 100000;

        return v1 >= v2;
    }

    private static boolean validateSignature(WebViewProviderInfo provider, Signature[] signatures) {
        // We don't support packages with multiple signatures.
        if (signatures.length != 1) return false;

        // If any of the declared signatures match the package signature, it's valid.
        for (Signature signature : provider.signatures) {
            if (signature.equals(signatures[0])) return true;
        }

        return false;
    }
}
