/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.mediastresstest;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;

import androidx.test.filters.LargeTest;

import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.android.mediastresstest.CodecTestBase.selectCodecs;
import static org.junit.Assert.assertTrue;

@RunWith(Enclosed.class)
public class MultiInstanceTest {
    private static final String LOG_TAG = MultiInstanceTest.class.getSimpleName();
    private static final int PER_TEST_TIMEOUT_LARGE_TEST_MS = 1800000;
    private static final long mSeed = 0x12b9b0a1;  // random seed
    private static final Random rand = new Random(mSeed);

    private static int getMaxSupportedInstances(String name, String mime) throws IOException {
        MediaCodec codec = MediaCodec.createByCodecName(name);
        MediaCodecInfo info = codec.getCodecInfo();
        MediaCodecInfo.CodecCapabilities cap = info.getCapabilitiesForType(mime);
        int instances = info.isHardwareAccelerated() ? cap.getMaxSupportedInstances() : 4;
        codec.release();
        return instances;
    }

    @RunWith(Parameterized.class)
    public static class DecoderTest {
        private final String mMime;
        private final String mTestFile;
        private final String mRefFile;
        private final String mReconfigFile;
        private final float mRmsError;
        private final long mRefCRC;

        @Parameterized.Parameters(name = "{index}({0})")
        public static Collection<Object[]> input() {
            final boolean isEncoder = false;
            final boolean needAudio = true;
            final boolean needVideo = true;
            return CodecDecoderTestBase
                    .prepareParamList(CodecDecoderTest.exhaustiveArgsList, isEncoder, needAudio,
                            needVideo, true);
        }

        public DecoderTest(String mime, String testFile, String refFile, String reconfigFile,
                float rmsError, long refCRC) {
            mMime = mime;
            mTestFile = testFile;
            mRefFile = refFile;
            mReconfigFile = reconfigFile;
            mRmsError = rmsError;
            mRefCRC = refCRC;
        }

        @LargeTest
        @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
        public void testDecoderMultiInstance()
                throws IOException, InterruptedException, ExecutionException {
            CodecDecoderTest cdt = new CodecDecoderTest(mMime, mTestFile, mRefFile, mReconfigFile,
                    mRmsError, mRefCRC);
            ArrayList<String> listOfDecoders = cdt.getCodecsList();
            Assume.assumeTrue("no codecs for mime" + mMime, !listOfDecoders.isEmpty());
            int cores = Runtime.getRuntime().availableProcessors();
            int ThreadCount = cores * 2;
            ExecutorService pool = Executors.newFixedThreadPool(ThreadCount);
            ArrayList<DecodeParallel> task = new ArrayList<>();
            for (String decoder : listOfDecoders) {
                int instances = getMaxSupportedInstances(decoder, mMime);
                for (int i = 0; i < instances; i++) {
                    cdt = new CodecDecoderTest(mMime, mTestFile, mRefFile, mReconfigFile, mRmsError,
                            mRefCRC);
                    DecodeParallel dp = new DecodeParallel(cdt, decoder,
                            CodecDecoderTest.randomChoice(rand));
                    task.add(dp);
                }
            }
            List<Future<Void>> resultList = pool.invokeAll(task);
            OutputManager outBuff = null;
            for (int i = 0; i < resultList.size(); i++) {
                resultList.get(i).get();
                if (i == 0) outBuff = task.get(i).mCdt.mOutputBuff;
                else assertTrue(outBuff.equals(task.get(i).mCdt.mOutputBuff));
            }
            task.clear();
            pool.shutdown();
        }
    }

    @RunWith(Parameterized.class)
    public static class EncoderTest {
        private final String mMime;
        private final int[] mBitrates;
        private final int[] mParamList1;
        private final int[] mParamList2;

        public EncoderTest(String mime, int[] bitrates, int[] encoderInfo1, int[] encoderInfo2) {
            mMime = mime;
            mBitrates = bitrates;
            mParamList1 = encoderInfo1;
            mParamList2 = encoderInfo2;
        }

        @Parameterized.Parameters(name = "{index}({0})")
        public static Collection<Object[]> input() {
            final boolean isEncoder = true;
            final boolean needAudio = true;
            final boolean needVideo = true;
            return CodecEncoderTestBase
                    .prepareParamList(CodecEncoderTest.exhaustiveArgsList, isEncoder, needAudio,
                            needVideo, true);
        }

        @LargeTest
        @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
        public void testEncoderMultiInstance()
                throws IOException, InterruptedException, ExecutionException {
            CodecEncoderTest cet = new CodecEncoderTest(mMime, mBitrates, mParamList1, mParamList2);
            cet.setUpParams(0);
            cet.setUpSource();
            ArrayList<String> listOfEncoders = selectCodecs(mMime, cet.mFormats, null, true);
            Assume.assumeTrue("no suitable codecs found for mime: " + mMime, !listOfEncoders.isEmpty());
            int cores = Runtime.getRuntime().availableProcessors();
            int ThreadCount = cores * 2;
            ExecutorService pool = Executors.newFixedThreadPool(ThreadCount);
            ArrayList<EncodeParallel> task = new ArrayList<>();
            for (String encoder : listOfEncoders) {
                int instances = getMaxSupportedInstances(encoder, mMime);
                for (int i = 0; i < instances; i++) {
                    cet = new CodecEncoderTest(mMime, mBitrates, mParamList1, mParamList2);
                    EncodeParallel dp = new EncodeParallel(cet, encoder,
                            CodecEncoderTest.randomChoice(rand));
                    task.add(dp);
                }
            }
            List<Future<Void>> resultList = pool.invokeAll(task);
            for (int i = 0; i < resultList.size(); i++) {
                resultList.get(i).get();
            }
            task.clear();
            pool.shutdown();
        }
    }
}
