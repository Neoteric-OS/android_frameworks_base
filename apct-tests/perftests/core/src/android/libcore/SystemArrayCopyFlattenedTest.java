/*
 * Copyright (C) 2024 The Android Open Source Project
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

import org.junit.Rule;
import org.junit.Test;

public class SystemArrayCopyFlattenedTest {

    @Rule
    public PerfStatusReporter mPerfStatusReporter = new PerfStatusReporter();

    @Test
    public void timeSystemCharArrayCopy_1() {
        final int len = 1;
        char[] src = new char[len];
        char[] dst = new char[len];
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            System.arraycopy(src, 0, dst, 0, len);
        }
    }


    @Test
    public void timeSystemCharArrayCopy_2() {
        final int len = 2;
        char[] src = new char[len];
        char[] dst = new char[len];
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            System.arraycopy(src, 0, dst, 0, len);
        }
    }


    @Test
    public void timeSystemCharArrayCopy_4() {
        final int len = 4;
        char[] src = new char[len];
        char[] dst = new char[len];
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            System.arraycopy(src, 0, dst, 0, len);
        }
    }


    @Test
    public void timeSystemCharArrayCopy_8() {
        final int len = 8;
        char[] src = new char[len];
        char[] dst = new char[len];
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            System.arraycopy(src, 0, dst, 0, len);
        }
    }


    @Test
    public void timeSystemCharArrayCopy_16() {
        final int len = 16;
        char[] src = new char[len];
        char[] dst = new char[len];
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            System.arraycopy(src, 0, dst, 0, len);
        }
    }


    @Test
    public void timeSystemCharArrayCopy_32() {
        final int len = 32;
        char[] src = new char[len];
        char[] dst = new char[len];
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            System.arraycopy(src, 0, dst, 0, len);
        }
    }


    @Test
    public void timeSystemCharArrayCopy_64() {
        final int len = 64;
        char[] src = new char[len];
        char[] dst = new char[len];
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            System.arraycopy(src, 0, dst, 0, len);
        }
    }

    @Test
    public void timeSystemCharArrayCopy_128() {
        final int len = 128;
        char[] src = new char[len];
        char[] dst = new char[len];
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            System.arraycopy(src, 0, dst, 0, len);
        }
    }

    @Test
    public void timeSystemCharArrayCopy_256() {
        final int len = 256;
        char[] src = new char[len];
        char[] dst = new char[len];
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            System.arraycopy(src, 0, dst, 0, len);
        }
    }


    @Test
    public void timeSystemCharArrayCopy_512() {
        final int len = 512;
        char[] src = new char[len];
        char[] dst = new char[len];
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            System.arraycopy(src, 0, dst, 0, len);
        }
    }

    @Test
    public void timeSystemCharArrayCopy_1024() {
        final int len = 1024;
        char[] src = new char[len];
        char[] dst = new char[len];
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            System.arraycopy(src, 0, dst, 0, len);
        }
    }

    @Test
    public void timeSystemCharArrayCopy_2048() {
        final int len = 2048;
        char[] src = new char[len];
        char[] dst = new char[len];
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            System.arraycopy(src, 0, dst, 0, len);
        }
    }


    @Test
    public void timeSystemCharArrayCopy_4096() {
        final int len = 4096;
        char[] src = new char[len];
        char[] dst = new char[len];
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            System.arraycopy(src, 0, dst, 0, len);
        }
    }


    @Test
    public void timeSystemCharArrayCopy_8192() {
        final int len = 8192;
        char[] src = new char[len];
        char[] dst = new char[len];
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            System.arraycopy(src, 0, dst, 0, len);
        }
    }


    @Test
    public void timeSystemCharArrayCopy_16384() {
        final int len = 16384;
        char[] src = new char[len];
        char[] dst = new char[len];
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            System.arraycopy(src, 0, dst, 0, len);
        }
    }


    @Test
    public void timeSystemCharArrayCopy_32768() {
        final int len = 32768;
        char[] src = new char[len];
        char[] dst = new char[len];
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            System.arraycopy(src, 0, dst, 0, len);
        }
    }

    @Test
    public void timeSystemCharArrayCopy_65536() {
        final int len = 65536;
        char[] src = new char[len];
        char[] dst = new char[len];
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            System.arraycopy(src, 0, dst, 0, len);
        }
    }

    @Test
    public void timeSystemCharArrayCopy_131072() {
        final int len = 131072;
        char[] src = new char[len];
        char[] dst = new char[len];
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            System.arraycopy(src, 0, dst, 0, len);
        }
    }

    @Test
    public void timeSystemCharArrayCopy_262144() {
        final int len = 262144;
        char[] src = new char[len];
        char[] dst = new char[len];
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            System.arraycopy(src, 0, dst, 0, len);
        }
    }
}
