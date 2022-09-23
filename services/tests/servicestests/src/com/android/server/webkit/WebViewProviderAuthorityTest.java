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

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.content.pm.SigningDetails;
import android.os.Bundle;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Base64;
import android.webkit.UserPackage;
import android.webkit.WebViewProviderInfo;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.pm.parsing.pkg.AndroidPackage;
import com.android.server.pm.parsing.pkg.PackageImpl;
import com.android.server.pm.parsing.pkg.ParsedPackage;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;

/**
 * Tests for WebViewProviderAuthority
 runtest --path frameworks/base/services/tests/servicestests/ \
     -c com.android.server.webkit.WebViewProviderAuthorityTest
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class WebViewProviderAuthorityTest {
    // These do not intentionally correspond with any particular real build.
    // These version codes have a BBBBPPPXX (build, patch, APK type) format.
    // (The last 5 digits of a version code should always be ignored.)
    private static final long MIN_VERSION = 1000_500_50L;
    private static final long EXAMPLE_PACKAGE_VERSION = 1000_499_49L;
    private static final long AWESOME_PACKAGE_VERSION = 1001_501_51L;
    private static final long BAD_PACKAGE_VERSION = 999_501_51L;

    private static Bundle createExampleMetaData() {
        final Bundle metaData = new Bundle();
        metaData.putString(WebViewProviderAuthority.WEBVIEW_LIBRARY_FLAG, "blah");
        return metaData;
    }

    private static Signature[] createExampleSignatures() {
        return new Signature[]{new Signature("example".getBytes())};
    }

    private static PackageInfo createExamplePackageInfo(boolean isSystem) {
        final PackageInfo p = new PackageInfo();
        p.packageName = "com.example.provider";
        p.applicationInfo = new ApplicationInfo();
        p.applicationInfo.metaData = createExampleMetaData();
        p.applicationInfo.targetSdkVersion = UserPackage.MINIMUM_SUPPORTED_SDK;
        if (isSystem) {
            p.applicationInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
        }
        p.setLongVersionCode(EXAMPLE_PACKAGE_VERSION);
        p.signatures = createExampleSignatures();
        return p;
    }

    private static AndroidPackage createExampleAndroidPackage(boolean isSystem) {
        final AndroidPackage p = ((ParsedPackage) PackageImpl.forTesting("com.example.provider")
                .setMetaData(createExampleMetaData())
                .setTargetSdkVersion(UserPackage.MINIMUM_SUPPORTED_SDK)
                .setSigningDetails(new SigningDetails(createExampleSignatures(),
                        SigningDetails.SignatureSchemeVersion.UNKNOWN, null, null))
                .hideAsParsed())
                .setVersionCodeMajor(0)
                .setVersionCode((int) EXAMPLE_PACKAGE_VERSION)
                .setSystem(isSystem)
                .hideAsFinal();
        return p;
    }

    private static WebViewProviderInfo createExampleProvider() {
        return createExampleProviderWithPackageName("com.example.provider");
    }

    private static WebViewProviderInfo createExampleProviderWithPackageName(String packageName) {
        return new WebViewProviderInfo(packageName, "description",
                /*availableByDefault=*/true, /*isFallback=*/false,
                new String[]{Base64.encodeToString("example".getBytes(), Base64.DEFAULT)});
    }

    @Test
    public void testParser() throws XmlPullParserException {
        final WebViewProviderInfo[] expectedProviders = {
                new WebViewProviderInfo("FirstName", "FirstDescription", true, true,
                        new String[]{"11", "22"}),
                new WebViewProviderInfo("SecondName", "SecondDescription", false, false,
                        new String[]{}),
                new WebViewProviderInfo("ThirdName", "ThirdDescription", false, false,
                        new String[]{"33"})};

        final String config =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<webviewproviders>"
                + "  <webviewprovider"
                + "      packageName=\"FirstName\""
                + "      description=\"FirstDescription\""
                + "      availableByDefault=\"true\""
                + "      isFallback=\"true\">"
                + "    <signature>11</signature>"
                + "    <signature>22</signature>"
                + "  </webviewprovider>"
                + "  <webviewprovider"
                + "      packageName=\"SecondName\""
                + "      description=\"SecondDescription\""
                + "      availableByDefault=\"false\""
                + "      isFallback=\"false\">"
                + "  </webviewprovider>"
                + "  <webviewprovider"
                + "      packageName=\"ThirdName\""
                + "      description=\"ThirdDescription\">"
                + "    <signature>33</signature>"
                + "  </webviewprovider>"
                + "</webviewproviders>";
        final XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        final XmlPullParser parser = factory.newPullParser();
        parser.setInput(new StringReader(config));
        final WebViewProviderAuthority authority = WebViewProviderAuthority.createFromXml(parser,
                false);
        final WebViewProviderInfo[] gotProviders = authority.getWebViewPackages();

        Assert.assertNotNull(gotProviders);
        Assert.assertEquals(expectedProviders.length, gotProviders.length);
        for (int i = 0; i < expectedProviders.length; ++i) {
            final WebViewProviderInfo expected = expectedProviders[i];
            final WebViewProviderInfo got = gotProviders[i];
            Assert.assertEquals(expected.packageName, got.packageName);
            Assert.assertEquals(expected.description, got.description);
            Assert.assertEquals(expected.availableByDefault, got.availableByDefault);
            Assert.assertEquals(expected.isFallback, got.isFallback);
            Assert.assertArrayEquals(expected.signatures, got.signatures);
        }
    }

    private void doTestPackageInfoMatching(boolean isSystem, boolean isDebuggable) {
        final WebViewProviderInfo provider = createExampleProvider();
        final WebViewProviderInfo[] providers = {provider};

        final PackageInfo matchingPackage = createExamplePackageInfo(isSystem);
        final PackageInfo awesomePackage = createExamplePackageInfo(isSystem);
        awesomePackage.applicationInfo.targetSdkVersion = UserPackage.MINIMUM_SUPPORTED_SDK + 1;
        awesomePackage.setLongVersionCode(AWESOME_PACKAGE_VERSION);
        final PackageInfo badNamePackage = createExamplePackageInfo(isSystem);
        badNamePackage.packageName = "com.example.bad";
        final PackageInfo badSdkVersionPackage = createExamplePackageInfo(isSystem);
        badSdkVersionPackage.applicationInfo.targetSdkVersion =
                UserPackage.MINIMUM_SUPPORTED_SDK - 1;
        final PackageInfo badVersionCodePackage = createExamplePackageInfo(isSystem);
        badVersionCodePackage.setLongVersionCode(BAD_PACKAGE_VERSION);
        final PackageInfo badSignaturePackage = createExamplePackageInfo(isSystem);
        badSignaturePackage.signatures = new Signature[]{new Signature("bad".getBytes())};
        final PackageInfo noFlagPackage = createExamplePackageInfo(isSystem);
        noFlagPackage.applicationInfo.metaData.remove(
                WebViewProviderAuthority.WEBVIEW_LIBRARY_FLAG);

        final WebViewProviderAuthority debugAuthority = new WebViewProviderAuthority(providers,
                isDebuggable);
        Assert.assertEquals(WebViewProviderAuthority.Validity.OK,
                debugAuthority.validatePackageAsProvider(matchingPackage, provider, MIN_VERSION));
        Assert.assertEquals(WebViewProviderAuthority.Validity.OK,
                debugAuthority.validatePackageAsProvider(awesomePackage, provider, MIN_VERSION));
        Assert.assertEquals(WebViewProviderAuthority.Validity.INCORRECT_PACKAGE_NAME,
                debugAuthority.validatePackageAsProvider(badNamePackage, provider, MIN_VERSION));
        Assert.assertEquals(WebViewProviderAuthority.Validity.INCORRECT_SDK_VERSION,
                debugAuthority.validatePackageAsProvider(
                    badSdkVersionPackage, provider, MIN_VERSION));
        final WebViewProviderAuthority.Validity expectedForBadVersionCode =
                isDebuggable
                ? WebViewProviderAuthority.Validity.OK
                : WebViewProviderAuthority.Validity.INCORRECT_VERSION_CODE;
        Assert.assertEquals(expectedForBadVersionCode,
                debugAuthority.validatePackageAsProvider(
                    badVersionCodePackage, provider, MIN_VERSION));
        final WebViewProviderAuthority.Validity expectedForBadSignature =
                (isDebuggable || isSystem)
                ? WebViewProviderAuthority.Validity.OK
                : WebViewProviderAuthority.Validity.INCORRECT_SIGNATURE;
        Assert.assertEquals(expectedForBadSignature,
                debugAuthority.validatePackageAsProvider(
                    badSignaturePackage, provider, MIN_VERSION));
        Assert.assertEquals(WebViewProviderAuthority.Validity.NO_LIBRARY_FLAG,
                debugAuthority.validatePackageAsProvider(noFlagPackage, provider, MIN_VERSION));
    }

    private void doTestAndroidPackageMatching(boolean isSystem, boolean isDebuggable) {
        final WebViewProviderInfo provider = createExampleProvider();
        final WebViewProviderInfo[] providers = {provider};

        final AndroidPackage matchingPackage = createExampleAndroidPackage(isSystem);
        final AndroidPackage awesomePackage = createExampleAndroidPackage(isSystem);
        ((PackageImpl) awesomePackage).setTargetSdkVersion(UserPackage.MINIMUM_SUPPORTED_SDK + 1);
        ((PackageImpl) awesomePackage).setVersionCode((int) AWESOME_PACKAGE_VERSION);
        final AndroidPackage badNamePackage = createExampleAndroidPackage(isSystem);
        ((PackageImpl) badNamePackage).setPackageName("com.example.bad");
        final AndroidPackage badSdkVersionPackage = createExampleAndroidPackage(isSystem);
        ((PackageImpl) badSdkVersionPackage)
                .setTargetSdkVersion(UserPackage.MINIMUM_SUPPORTED_SDK - 1);
        final AndroidPackage badVersionCodePackage = createExampleAndroidPackage(isSystem);
        ((PackageImpl) badVersionCodePackage).setVersionCode((int) BAD_PACKAGE_VERSION);
        final AndroidPackage badSignaturePackage = createExampleAndroidPackage(isSystem);
        ((PackageImpl) badSignaturePackage).setSigningDetails(new SigningDetails(
                new Signature[]{new Signature("bad".getBytes())},
                SigningDetails.SignatureSchemeVersion.UNKNOWN, null, null));
        final AndroidPackage noFlagPackage = createExampleAndroidPackage(isSystem);
        ((PackageImpl) noFlagPackage).getMetaData().remove(
                WebViewProviderAuthority.WEBVIEW_LIBRARY_FLAG);

        final WebViewProviderAuthority debugAuthority = new WebViewProviderAuthority(providers,
                isDebuggable);
        Assert.assertEquals(WebViewProviderAuthority.Validity.OK,
                debugAuthority.validatePackageAsProvider(matchingPackage, provider, MIN_VERSION));
        Assert.assertEquals(WebViewProviderAuthority.Validity.OK,
                debugAuthority.validatePackageAsProvider(awesomePackage, provider, MIN_VERSION));
        Assert.assertEquals(WebViewProviderAuthority.Validity.INCORRECT_PACKAGE_NAME,
                debugAuthority.validatePackageAsProvider(badNamePackage, provider, MIN_VERSION));
        Assert.assertEquals(WebViewProviderAuthority.Validity.INCORRECT_SDK_VERSION,
                debugAuthority.validatePackageAsProvider(
                    badSdkVersionPackage, provider, MIN_VERSION));
        final WebViewProviderAuthority.Validity expectedForBadVersionCode =
                isDebuggable
                ? WebViewProviderAuthority.Validity.OK
                : WebViewProviderAuthority.Validity.INCORRECT_VERSION_CODE;
        Assert.assertEquals(expectedForBadVersionCode,
                debugAuthority.validatePackageAsProvider(
                    badVersionCodePackage, provider, MIN_VERSION));
        final WebViewProviderAuthority.Validity expectedForBadSignature =
                (isDebuggable || isSystem)
                ? WebViewProviderAuthority.Validity.OK
                : WebViewProviderAuthority.Validity.INCORRECT_SIGNATURE;
        Assert.assertEquals(expectedForBadSignature,
                debugAuthority.validatePackageAsProvider(
                    badSignaturePackage, provider, MIN_VERSION));
        Assert.assertEquals(WebViewProviderAuthority.Validity.NO_LIBRARY_FLAG,
                debugAuthority.validatePackageAsProvider(noFlagPackage, provider, MIN_VERSION));
    }

    @Test
    public void testPackageInfoMatching() {
        doTestPackageInfoMatching(false, false);
    }

    @Test
    public void testPackageInfoMatchingDebuggable() {
        doTestPackageInfoMatching(false, true);
    }

    @Test
    public void testPackageInfoMatchingSystem() {
        doTestPackageInfoMatching(true, false);
    }

    @Test
    public void testPackageInfoMatchingSystemDebuggable() {
        doTestPackageInfoMatching(true, true);
    }

    @Test
    public void testAndroidPackageMatching() {
        doTestAndroidPackageMatching(false, false);
    }

    @Test
    public void testAndroidPackageMatchingDebuggable() {
        doTestAndroidPackageMatching(false, true);
    }

    @Test
    public void testAndroidPackageMatchingSystem() {
        doTestAndroidPackageMatching(true, false);
    }

    @Test
    public void testAndroidPackageMatchingSystemDebuggable() {
        doTestAndroidPackageMatching(true, true);
    }

    @Test
    public void testAndroidPackageIsValidWebView() {
        final WebViewProviderInfo validProvider1 =
                createExampleProviderWithPackageName("com.example.valid1");
        final WebViewProviderInfo validProvider2 =
                createExampleProviderWithPackageName("com.example.valid2");

        final WebViewProviderInfo[] providers = {validProvider1, validProvider2};
        final WebViewProviderAuthority authority = new WebViewProviderAuthority(providers, false);

        final AndroidPackage validPackage1 = createExampleAndroidPackage(false);
        ((PackageImpl) validPackage1).setPackageName("com.example.valid1");
        final AndroidPackage validPackage2 = createExampleAndroidPackage(false);
        ((PackageImpl) validPackage2).setPackageName("com.example.valid2");
        final AndroidPackage invalidPackage = createExampleAndroidPackage(false);
        ((PackageImpl) invalidPackage).setPackageName("com.example.invalid");

        Assert.assertTrue(authority.packageIsValidWebView(validPackage1, MIN_VERSION));
        Assert.assertTrue(authority.packageIsValidWebView(validPackage1, MIN_VERSION));
        Assert.assertFalse(authority.packageIsValidWebView(invalidPackage, MIN_VERSION));
    }
}
