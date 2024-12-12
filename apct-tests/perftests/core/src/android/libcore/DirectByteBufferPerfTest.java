/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.libcore;

import android.perftests.utils.BenchmarkState;
import android.perftests.utils.PerfStatusReporter;

import androidx.test.filters.LargeTest;

import java.nio.channels.FileChannel.MapMode;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.DirectByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.Random;

@RunWith(JUnitParamsRunner.class)
@LargeTest
public class DirectByteBufferPerfTest {
    @Rule public PerfStatusReporter mPerfStatusReporter = new PerfStatusReporter();

    private static final int BUFFER_SIZE = 8192;
    private static final int FILE_SIZE = 16 * 1024 * 1024;

    public File setUp(String fileName) throws Exception {
        File file = File.createTempFile(getClass().getName() + "-" + fileName, ".zip");
        file.deleteOnExit();
        FileOutputStream fileOutputStream = new FileOutputStream(file);
        byte[] writeBuffer = new byte[BUFFER_SIZE];
        Random random = new Random();
        int bytesLeft = FILE_SIZE;
        while (bytesLeft > 0) {
            int bytesToWrite = Math.min(bytesLeft, writeBuffer.length);
            random.nextBytes(writeBuffer);
            fileOutputStream.write(writeBuffer, 0, bytesToWrite);
            bytesLeft -= bytesToWrite;
        }
        return file;
    }

    @Test
    public void timeReadRandomAccessFile() throws Exception {
        File file = setUp("RandomAccessFile");
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            RandomAcceessFile randomAccessFile = new RandomAccessFile(file, "r");
            byte[] readBuffer = new byte[BUFFER_SIZE];
            int bytesRead = 0;
            while (bytesRead < FILE_SIZE) {
                int bytesToRead = Math.min(BUFFER_SIZE, FILE_SIZE - bytesRead);
                randomAccessFile.readFully(readBuffer, 0, bytesToRead);
                bytesRead += bytesToRead;
            }
            fileInputStream.close();
        }
        file.close();
    }

    @Test
    public void timeReadMappedDirectByteBuffer() throws Exception {
        File file = setUp("DirectByteBuffer");
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            RandomAcceessFile randomAccessFile = new RandomAccessFile(file, "r");
            DirectByteBuffer directByteBuffer =
                    (DirectByteBuffer)
                            randomAccessFile.getChannel().map(MapMode.READ_ONLY, 0, FILE_SIZE);
            directByteBuffer.load();
            byte[] readBuffer = new byte[BUFFER_SIZE];
            int bytesRead = 0;
            while (bytesRead < FILE_SIZE) {
                int bytesToRead = Math.min(BUFFER_SIZE, FILE_SIZE - bytesRead);
                directByteBuffer.get(readBuffer, 0, bytesToRead);
                bytesRead += bytesToRead;
            }
            randomAccessFile.close();
        }
        file.close();
    }
}
