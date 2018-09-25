/*
 * Copyright 2017 The Android Open Source Project
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

package com.android.server.pm.dex;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.util.EventLog;
import dalvik.system.DexClassLoader;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

/**
 * Integration tests for {@link com.android.server.pm.dex.DexLogger}.
 *
 * The setup for the test dynamically loads code in a jar extracted
 * from our assets (a secondary dex file).
 *
 * We then use adb to trigger secondary dex file reconcilation (and
 * wait for it to complete). As a side-effect of this DexLogger should
 * be notified of the file and should log the hash of the file's name
 * and content.  We verify that this message appears in the event log.
 *
 * Run with "atest DexLoggerIntegrationTests".
 */
@LargeTest
@RunWith(JUnit4.class)
public final class DexLoggerIntegrationTests {

    private static final String PACKAGE_NAME = "com.android.frameworks.dexloggertest";

    // Event log tag used for SNET related events
    private static final int SNET_TAG = 0x534e4554;

    // Subtag used to distinguish dynamic code loading events
    private static final String DCL_SUBTAG = "dcl";

    // All the tags we care about
    private static final int[] TAG_LIST = new int[] { SNET_TAG };

    private static Context context;
    private static int myUid;

    @BeforeClass
    public static void setUpAll() throws Exception {
        context = InstrumentationRegistry.getTargetContext();
        myUid = android.os.Process.myUid();
    }

    @Before
    public void primeEventLog() {
        // Force a round trip to logd to make sure everything is up to date.
        // Without this the first test passes and others don't - we don't see new events in the
        // log. The exact reason is unclear.
        EventLog.writeEvent(SNET_TAG, "Dummy event");
    }

    @Test
    public void testDexLoggerGeneratesEvents() throws Exception {
        File privateCopyFile = fileForJar("copied.jar");
        // Obtained via "echo -n copied.jar | sha256sum"
        String expectedNameHash =
            "1B6C71DB26F36582867432CCA12FB6A517470C9F9AABE9198DD4C5C030D6DC0C";
        String expectedContentHash = copyAndHashJar(privateCopyFile);

        // Feed the jar to a class loader and make sure it contains what we expect.
        ClassLoader parentClassLoader = context.getClass().getClassLoader();
        ClassLoader loader =
                new DexClassLoader(privateCopyFile.toString(), null, null, parentClassLoader);
        loader.loadClass("com.android.dcl.Simple");

        // And make sure we log events about it
        long previousEventNanos = mostRecentEventTimeNanos();
        runDexLogger();

        assertDclLoggedSince(previousEventNanos, expectedNameHash, expectedContentHash);
    }

    @Test
    @Ignore  // TODO(b/111336847): Make this pass
    public void testDexLoggerGeneratesEvents_unknownClassLoader() throws Exception {
        File privateCopyFile = fileForJar("copied2.jar");
        String expectedNameHash =
            "202158B6A3169D78F1722487205A6B036B3F2F5653FDCFB4E74710611AC7EB93";
        String expectedContentHash = copyAndHashJar(privateCopyFile);

        // This time make sure an unknown class loader is an ancestor of the class loader we use.
        ClassLoader parentClassLoader = new UnknownClassLoader(context.getClass().getClassLoader());
        ClassLoader loader =
                new DexClassLoader(privateCopyFile.toString(), null, null, parentClassLoader);
        loader.loadClass("com.android.dcl.Simple");

        // And make sure we log events about it
        long previousEventNanos = mostRecentEventTimeNanos();
        runDexLogger();

        assertDclLoggedSince(previousEventNanos, expectedNameHash, expectedContentHash);
    }

    private static File fileForJar(String name) {
        return new File(context.getDir("jars", Context.MODE_PRIVATE), name);
    }

    private static String copyAndHashJar(File copyTo) throws Exception {
        MessageDigest hasher = MessageDigest.getInstance("SHA-256");

        // Copy the jar from our Java resources to a private data directory
        Class<?> thisClass = DexLoggerIntegrationTests.class;
        try (InputStream input = thisClass.getResourceAsStream("/javalib.jar");
                OutputStream output = new FileOutputStream(copyTo)) {
            byte[] buffer = new byte[1024];
            while (true) {
                int numRead = input.read(buffer);
                if (numRead < 0) {
                    break;
                }
                output.write(buffer, 0, numRead);
                hasher.update(buffer, 0, numRead);
            }
        }

        // Compute the SHA-256 of the file content so we can check that it is the same as the value
        // we see logged.
        Formatter formatter = new Formatter();
        for (byte b : hasher.digest()) {
            formatter.format("%02X", b);
        }

        return formatter.toString();
    }

    private static long mostRecentEventTimeNanos() throws Exception {
        List<EventLog.Event> events = new ArrayList<>();

        EventLog.readEvents(TAG_LIST, events);
        return events.isEmpty() ? 0 : events.get(events.size() - 1).getTimeNanos();
    }

    private static void runDexLogger() throws Exception {
        Process process = Runtime.getRuntime().exec(
            "cmd package reconcile-secondary-dex-files " + PACKAGE_NAME);
        int exitCode = process.waitFor();
        assertThat(exitCode).isEqualTo(0);
    }

    private static void assertDclLoggedSince(long previousEventNanos, String expectedNameHash,
            String expectedContentHash) throws Exception {
        List<EventLog.Event> events = new ArrayList<>();
        EventLog.readEvents(TAG_LIST, events);
        int found = 0;
        for (EventLog.Event event : events) {
            if (event.getTimeNanos() <= previousEventNanos) {
                continue;
            }
            Object[] data = (Object[]) event.getData();

            // We only care about DCL events that we generated.
            String subTag = (String) data[0];
            if (!DCL_SUBTAG.equals(subTag)) {
                continue;
            }
            int uid = (int) data[1];
            if (uid != myUid) {
                continue;
            }

            String message = (String) data[2];
            if (!message.startsWith(expectedNameHash)) {
                continue;
            }

            assertThat(message).endsWith(expectedContentHash);
            ++found;
        }

        assertThat(found).isEqualTo(1);
    }

    /** A class loader that does nothing useful, but importantly doesn't extend BaseDexClassLoader. */
    private static class UnknownClassLoader extends ClassLoader {
        public UnknownClassLoader(ClassLoader parent) {
            super(parent);
        }
    }
}
