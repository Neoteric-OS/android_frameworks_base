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
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Debug;

import androidx.test.filters.LargeTest;
import androidx.test.filters.SmallTest;
import androidx.test.rule.ActivityTestRule;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;

@RunWith(Parameterized.class)
public class LoopMediaDecoderTest {
    private final int mMaxSamples = 90;
    private final int mMaxIterations = 30;
    private final long mSeed = 0x12b9b0a1;  // random seed
    private final String mMime;
    private final String mTestFile;
    private final String mRefFile;
    private final String mReconfigFile;
    private final float mRmsError;
    private final long mRefCRC;
    Debug.MemoryInfo mMemInfoStart;
    Debug.MemoryInfo mMemInfoEnd;

    public LoopMediaDecoderTest(String mime, String testFile, String refFile, String reconfigFile,
            float rmsError, long refCRC) {
        mMime = mime;
        mTestFile = testFile;
        mRefFile = refFile;
        mReconfigFile = reconfigFile;
        mRmsError = rmsError;
        mRefCRC = refCRC;
    }

    @Rule
    public ActivityTestRule<CodecTestActivity> mActivityRule =
            new ActivityTestRule<>(CodecTestActivity.class);

    @Parameterized.Parameters(name = "{index}({0})")
    public static Collection<Object[]> input() {
        final boolean isEncoder = false;
        final boolean needAudio = true;
        final boolean needVideo = true;
        return CodecDecoderTestBase
                .prepareParamList(CodecDecoderTest.exhaustiveArgsList, isEncoder, needAudio,
                        needVideo, true);
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

    @SmallTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testConfigureAndReset() throws IOException {
        CodecDecoderTest cdt = new CodecDecoderTest(mMime, mTestFile, mRefFile, mReconfigFile,
                mRmsError, mRefCRC);
        ArrayList<String> listOfDecoders = cdt.getCodecsList();
        Assume.assumeTrue("no codecs for mime" + mMime, !listOfDecoders.isEmpty());
        MediaFormat format = cdt.setUpSource(mTestFile);
        Random rand = new Random(mSeed);
        for (String decoder : listOfDecoders) {
            cdt.mCodec = MediaCodec.createByCodecName(decoder);
            for (int i = 0; i < mMaxIterations; i++) {
                boolean isAsync = ((rand.nextInt() & 1) == 0);
                cdt.configureCodec(format, isAsync, false, false);
                cdt.mCodec.reset();
            }
            cdt.mCodec.release();
        }
        cdt.mExtractor.release();
    }

    @SmallTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testConfigureAndStop() throws IOException {
        CodecDecoderTest cdt = new CodecDecoderTest(mMime, mTestFile, mRefFile, mReconfigFile,
                mRmsError, mRefCRC);
        ArrayList<String> listOfDecoders = cdt.getCodecsList();
        Assume.assumeTrue("no codecs for mime" + mMime, !listOfDecoders.isEmpty());
        MediaFormat format = cdt.setUpSource(mTestFile);
        Random rand = new Random(mSeed);
        for (String decoder : listOfDecoders) {
            cdt.mCodec = MediaCodec.createByCodecName(decoder);
            for (int i = 0; i < mMaxIterations; i++) {
                boolean isAsync = ((rand.nextInt() & 1) == 0);
                cdt.mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                cdt.configureCodec(format, isAsync, false, false);
                cdt.mCodec.start();
                cdt.mCodec.stop();
            }
            cdt.mCodec.release();
        }
        cdt.mExtractor.release();
    }

    @LargeTest
    @Test(timeout = mMaxIterations * CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testDecoder() throws IOException, InterruptedException {
        CodecDecoderTest cdt = new CodecDecoderTest(mMime, mTestFile, mRefFile, mReconfigFile,
                mRmsError, mRefCRC);
        ArrayList<String> listOfDecoders = cdt.getCodecsList();
        Assume.assumeTrue("no codecs for mime" + mMime, !listOfDecoders.isEmpty());
        cdt.mActivityRule = mActivityRule;
        Random rand = new Random(mSeed);
        boolean ifVerify = true;
        if (cdt.mIsAudio && mRmsError == -1.0f)
            ifVerify = false;
        if (!cdt.mIsAudio && mRefCRC == -1L)
            ifVerify = false;
        for (String decoder : listOfDecoders) {
            cdt.mCodec = MediaCodec.createByCodecName(decoder);
            for (int i = 1; i <= mMaxIterations; i++) {
                boolean isAsync = ((rand.nextInt() & 1) == 0);
                boolean eosType = ((rand.nextInt() & 1) == 0);
                boolean surfaceMode = ((rand.nextInt() & 1) == 0);
                int numFrames = rand.nextInt(mMaxSamples);
                if ((i & (i - 1)) == 0) numFrames = Integer.MAX_VALUE;
                if (surfaceMode) {
                    cdt.surfaceIndex = 0;
                    cdt.setUpSurface(mActivityRule.getActivity());
                }
                CodecDecoderTest.isDecoderRunPass(cdt, decoder, isAsync, eosType,
                        ifVerify && (!surfaceMode && (numFrames == Integer.MAX_VALUE)), surfaceMode,
                        numFrames, CodecDecoderTest.randomChoice(rand));
                if (surfaceMode) {
                    CodecTestActivity activity = cdt.mActivityRule.getActivity();
                    cdt.mSurface[cdt.surfaceIndex] = null;
                    activity.mSurfaceLock[cdt.surfaceIndex].lock();
                    activity.setSurfaceStatus(false, cdt.surfaceIndex);
                    activity.mSurfaceLock[cdt.surfaceIndex].unlock();
                }
            }
            cdt.mCodec.release();
        }
        cdt.tearDownSurface(cdt.surfaceIndex);
    }
}
