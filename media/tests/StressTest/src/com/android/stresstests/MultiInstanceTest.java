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

import android.app.Application;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.os.Debug;

import androidx.test.filters.LargeTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
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
    private static ArrayList<Object[]> mDecoderList = new ArrayList<Object[]>();
    private static ArrayList<Object[]> mEncoderList = new ArrayList<Object[]>();

    private static int getMaxSupportedInstances(String name, String mime) throws IOException {
        MediaCodec codec = MediaCodec.createByCodecName(name);
        MediaCodecInfo info = codec.getCodecInfo();
        MediaCodecInfo.CodecCapabilities cap = info.getCapabilitiesForType(mime);
        // TODO: Capping the maximum number of software instances to 4
        int instances = info.isHardwareAccelerated() ? cap.getMaxSupportedInstances() : 4;
        codec.release();
        return instances;
    }

    private static ArrayList<Object[]> getCodecList(boolean isEncoder) {
        if (mDecoderList.isEmpty()) {
            mDecoderList.addAll(CodecDecoderTestBase
                    .prepareParamList(CodecDecoderTest.exhaustiveArgsList, false, true,
                            true, true));
        }
        if (mEncoderList.isEmpty()) {
            mEncoderList.addAll(CodecEncoderTestBase
                    .prepareParamList(CodecEncoderTest.exhaustiveArgsList, true, true,
                            true, true));
        }
        if (isEncoder) return mEncoderList;
        return mDecoderList;
    }

    @RunWith(Parameterized.class)
    public static class DecoderTest {
        private final String mMime;
        private final String mTestFile;
        private final String mRefFile;
        private final String mReconfigFile;
        private final float mRmsError;
        private final long mRefCRC;
        Debug.MemoryInfo mMemInfoStart;
        Debug.MemoryInfo mMemInfoEnd;

        @Parameterized.Parameters(name = "{index}({0})")
        public static Collection<Object[]> input() {
            return getCodecList(false);
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

        @Before
        public void initMemoryUses() {
            mMemInfoStart = CodecTestBase.getMemoryStats(Application.getProcessName());
        }

        @After
        public void checkMemoryUses() {
            mMemInfoEnd = CodecTestBase.getMemoryStats(Application.getProcessName());
            CodecTestBase.assertNotLeaking(mMemInfoStart, mMemInfoEnd);
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
            Collections.shuffle(task, rand);
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
        Debug.MemoryInfo mMemInfoStart;
        Debug.MemoryInfo mMemInfoEnd;

        public EncoderTest(String mime, int[] bitrates, int[] encoderInfo1, int[] encoderInfo2) {
            mMime = mime;
            mBitrates = bitrates;
            mParamList1 = encoderInfo1;
            mParamList2 = encoderInfo2;
        }

        @Parameterized.Parameters(name = "{index}({0})")
        public static Collection<Object[]> input() {
            return getCodecList(true);
        }

        @Before
        public void initMemoryUses() {
            mMemInfoStart = CodecTestBase.getMemoryStats(Application.getProcessName());
        }

        @After
        public void checkMemoryUses() {
            mMemInfoEnd = CodecTestBase.getMemoryStats(Application.getProcessName());
            CodecTestBase.assertNotLeaking(mMemInfoStart, mMemInfoEnd);
        }

        byte[] setUpSource(String inpPath) throws IOException {
            try (FileInputStream fInp = new FileInputStream(inpPath)) {
                int size = (int) new File(inpPath).length();
                byte[] inputData = new byte[size];
                fInp.read(inputData, 0, size);
                return inputData;
            }
        }

        @LargeTest
        @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
        public void testEncoderMultiInstance()
                throws IOException, InterruptedException, ExecutionException {
            CodecEncoderTest cet = new CodecEncoderTest(mMime, mBitrates, mParamList1, mParamList2);
            cet.setUpParams(0);
            byte[] inputData = setUpSource(cet.mInpPrefix + cet.mInputFile);
            ArrayList<String> listOfEncoders = selectCodecs(mMime, cet.mFormats, null, true);
            Assume.assumeTrue("no suitable codecs found for mime: " + mMime,
                    !listOfEncoders.isEmpty());
            int cores = Runtime.getRuntime().availableProcessors();
            int ThreadCount = cores * 2;
            ExecutorService pool = Executors.newFixedThreadPool(ThreadCount);
            ArrayList<EncodeParallel> task = new ArrayList<>();
            for (String encoder : listOfEncoders) {
                int instances = getMaxSupportedInstances(encoder, mMime);
                for (int i = 0; i < instances; i++) {
                    cet = new CodecEncoderTest(mMime, mBitrates, mParamList1, mParamList2);
                    EncodeParallel dp = new EncodeParallel(cet, encoder,
                            CodecEncoderTest.randomChoice(rand), inputData);
                    task.add(dp);
                }
            }
            Collections.shuffle(task, rand);
            List<Future<Void>> resultList = pool.invokeAll(task);
            for (int i = 0; i < resultList.size(); i++) {
                resultList.get(i).get();
            }
            task.clear();
            pool.shutdown();
        }

        @LargeTest
        @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
        public void testCodecMultiInstance()
                throws IOException, InterruptedException, ExecutionException {
            ArrayList<Callable<Void>> task = new ArrayList<>();
            int cores = Runtime.getRuntime().availableProcessors();
            int ThreadCount = cores * 2;
            ExecutorService pool = Executors.newFixedThreadPool(ThreadCount);
            byte[] yuvInput = null;
            byte[] pcmInput = null;
            byte[] inputData;
            for (int numInstances = 0; numInstances < cores; ) {
                if (numInstances == 0) {
                    CodecEncoderTest cet =
                            new CodecEncoderTest(mMime, mBitrates, mParamList1, mParamList2);
                    cet.setUpParams(0);
                    inputData = setUpSource(cet.mInpPrefix + cet.mInputFile);
                    if (mMime.startsWith("audio/")) pcmInput = inputData;
                    else yuvInput = inputData;
                    ArrayList<String> listOfCodecs =
                            selectCodecs(cet.mMime, cet.mFormats, null, true);
                    Assume.assumeTrue("no suitable codecs found for mime: " + mMime,
                            !listOfCodecs.isEmpty());
                    int compIndex = rand.nextInt(listOfCodecs.size());
                    String compName = listOfCodecs.get(compIndex);
                    EncodeParallel dp =
                            new EncodeParallel(cet, compName, CodecEncoderTest.randomChoice(rand),
                                    inputData);
                    task.add(dp);
                    numInstances++;
                } else {
                    int codecIndex = rand.nextInt(mEncoderList.size());
                    Object[] args = mEncoderList.get(codecIndex);
                    if (!mMime.equals(args[0])) {
                        CodecEncoderTest cet =
                                new CodecEncoderTest((String) args[0], (int[]) args[1],
                                        (int[]) args[2], (int[]) args[3]);
                        cet.setUpParams(0);
                        ArrayList<String> listOfCodecs =
                                selectCodecs(cet.mMime, cet.mFormats, null, true);
                        if (!listOfCodecs.isEmpty()) {
                            int compIndex = rand.nextInt(listOfCodecs.size());
                            String compName = listOfCodecs.get(compIndex);
                            if ((cet.mIsAudio && pcmInput == null) ||
                                    (!cet.mIsAudio && yuvInput == null)) {
                                inputData = setUpSource(cet.mInpPrefix + cet.mInputFile);
                                if (cet.mIsAudio) pcmInput = inputData;
                                else yuvInput = inputData;
                            } else if (cet.mIsAudio) {
                                inputData = pcmInput;
                            } else {
                                inputData = yuvInput;
                            }
                            EncodeParallel dp =
                                    new EncodeParallel(cet, compName,
                                            CodecEncoderTest.randomChoice(rand), inputData);
                            task.add(dp);
                            numInstances++;
                        }
                    }
                }
                int codecIndex = rand.nextInt(mDecoderList.size()); // add decoder instances
                Object[] args = mDecoderList.get(codecIndex);
                CodecDecoderTest cdt =
                        new CodecDecoderTest((String) args[0], (String) args[1],
                                (String) args[2], (String) args[3], (float) args[4],
                                (long) args[5]);
                ArrayList<String> listOfCodecs = cdt.getCodecsList();
                if (listOfCodecs.isEmpty()) continue;
                int compIndex = rand.nextInt(listOfCodecs.size());
                String compName = listOfCodecs.get(compIndex);
                DecodeParallel dp =
                        new DecodeParallel(cdt, compName, CodecDecoderTest.randomChoice(rand));
                task.add(dp);
                numInstances++;
            }
            Collections.shuffle(task, rand);
            List<Future<Void>> resultList = pool.invokeAll(task);
            for (int i = 0; i < resultList.size(); i++) {
                resultList.get(i).get();
            }
            task.clear();
            pool.shutdown();
        }
    }
}
