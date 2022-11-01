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

package com.android.internal.os;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.os.Build;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Test SafeZipPathCallback.
 */
@RunWith(AndroidJUnit4.class)
public class SafeZipPathCallbackTest {

    private void writeZipOutputStreamWithEmptyEntry(OutputStream os, String entryName)
            throws IOException {
        ZipOutputStream zos = new ZipOutputStream(os);
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        zos.write(new byte[2]);
        zos.closeEntry();
        zos.close();
    }

    @Test
    public void testZipPathTraversalBlockedInZipFile() throws Exception {
        assumeTrue("This feature is enabled in debuggable build only", Build.isDebuggable());
        final String[] entryNames = {
                "../foo.bar",
                "foo/../bar.baz",
                "foo/../../bar.baz",
                "foo.bar/..",
                "foo.bar/../",
                "..",
                "../",
                "/foo",
        };
        for (String entryName : entryNames) {
            final File tempFile = File.createTempFile("smdc", "zip");
            try {
                FileOutputStream tempFileStream = new FileOutputStream(tempFile);
                writeZipOutputStreamWithEmptyEntry(tempFileStream, entryName);
                tempFileStream.close();

                assertThrows("ZipException expected for entry: " + entryName,
                        ZipException.class, () -> {
                            new ZipFile(tempFile);
                        });
            } finally {
                tempFile.delete();
            }
        }
    }

    @Test
    public void testZipPathTraversalBlockedInZipInputStream() throws Exception {
        assumeTrue("This feature is enabled in debuggable build only", Build.isDebuggable());
        final String[] entryNames = {
                "../foo.bar",
                "foo/../bar.baz",
                "foo/../../bar.baz",
                "foo.bar/..",
                "foo.bar/../",
                "..",
                "../",
                "/foo",
        };
        for (String entryName : entryNames) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            writeZipOutputStreamWithEmptyEntry(bos, entryName);
            byte[] badZipBytes = bos.toByteArray();
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(badZipBytes))) {
                assertThrows("ZipException expected for entry: " + entryName,
                        ZipException.class, () -> {
                            zis.getNextEntry();
                        });
            }
        }
    }

    @Test
    public void testNormalZipPathAllowedInZipFile() throws Exception {
        assumeTrue("This feature is enabled in debuggable build only", Build.isDebuggable());
        final String[] entryNames = {
                "foo",
                "foo.bar",
        };
        for (String entryName : entryNames) {
            final File tempFile = File.createTempFile("smdc", "zip");
            try {
                FileOutputStream tempFileStream = new FileOutputStream(tempFile);
                writeZipOutputStreamWithEmptyEntry(tempFileStream, entryName);
                tempFileStream.close();
                try {
                    ZipFile zf = new ZipFile((tempFile));
                } catch (ZipException e) {
                    throw new AssertionError("ZipException not expected for entry: " + entryName);
                }
            } finally {
                tempFile.delete();
            }
        }
    }

    @Test
    public void testNormalZipPathAllowedInZipInputStream() throws Exception {
        assumeTrue("This feature is enabled in debuggable build only", Build.isDebuggable());
        final String[] entryNames = {
                "foo",
                "foo.bar",
        };
        for (String entryName : entryNames) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            writeZipOutputStreamWithEmptyEntry(bos, entryName);
            byte[] zipBytes = bos.toByteArray();
            try {
                ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes));
                zis.getNextEntry();
            } catch (ZipException e) {
                throw new AssertionError("ZipException not expected for entry: " + entryName);
            }
        }
    }
}
