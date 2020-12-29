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
import android.media.MediaExtractor;
import android.media.MediaFormat;

import androidx.test.rule.ActivityTestRule;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CodecDecoderTest extends CodecDecoderTestBase {
    private static final String LOG_TAG = CodecDecoderTest.class.getSimpleName();
    private static final float RMS_ERROR_TOLERANCE = 1.05f;  // 5%

    private final String mRefFile;
    private final String mReconfigFile;
    private final float mRmsError;
    private final long mRefCRC;

    ActivityTestRule<CodecTestActivity> mActivityRule;

    public enum Menu {
        DECODE,
        FLUSH,
        RECONFIGURE,
    }

    static final List<Menu> menuValues = Collections.unmodifiableList(Arrays.asList(Menu.values()));

    public static Menu randomChoice(Random rand)  {
        return menuValues.get(rand.nextInt(menuValues.size()));
    }

    public ArrayList<String> getCodecsList() throws IOException {
        MediaFormat format = setUpSource(mTestFile);
        mExtractor.release();
        MediaFormat newFormat = setUpSource(mReconfigFile);
        mExtractor.release();
        ArrayList<MediaFormat> formats = new ArrayList<>();
        formats.add(format);
        formats.add(newFormat);
        return selectCodecs(mMime, formats, null, false);
    }

    // mime, testClip, referenceClip, reconfigureTestClip, refRmsError, refCRC32
    public static final List<Object[]> exhaustiveArgsList = Arrays.asList(new Object[][]{
            {MediaFormat.MIMETYPE_AUDIO_MPEG, "bbb_1ch_8kHz_lame_cbr.mp3", "bbb_1ch_8kHz_s16le" +
                    ".raw", "bbb_2ch_44kHz_lame_vbr.mp3", 66.1589f, -1L},
            {MediaFormat.MIMETYPE_AUDIO_AMR_WB, "bbb_1ch_16kHz_16kbps_amrwb.3gp",
                    "bbb_1ch_16kHz_s16le.raw", "bbb_1ch_16kHz_23kbps_amrwb.3gp", 1692.438f, -1L},
            {MediaFormat.MIMETYPE_AUDIO_AMR_NB, "bbb_1ch_8kHz_10kbps_amrnb.3gp",
                    "bbb_1ch_8kHz_s16le.raw", "bbb_1ch_8kHz_8kbps_amrnb.3gp", -1.0f, -1L},
            {MediaFormat.MIMETYPE_AUDIO_FLAC, "bbb_1ch_16kHz_flac.mka", "bbb_1ch_16kHz_s16le.raw"
                    , "bbb_2ch_44kHz_flac.mka", 0.0f, -1L},
            {MediaFormat.MIMETYPE_AUDIO_RAW, "bbb_1ch_16kHz.wav", "bbb_1ch_16kHz_s16le.raw",
                    "bbb_2ch_44kHz.wav", 0.0f, -1L},
            {MediaFormat.MIMETYPE_AUDIO_G711_ALAW, "bbb_1ch_8kHz_alaw.wav", "bbb_1ch_8kHz_s16le" +
                    ".raw", "bbb_2ch_8kHz_alaw.wav", 16.912f, -1L},
            {MediaFormat.MIMETYPE_AUDIO_G711_MLAW, "bbb_1ch_8kHz_mulaw.wav", "bbb_1ch_8kHz_s16le" +
                    ".raw", "bbb_2ch_8kHz_mulaw.wav", 17.378f, -1L},
            {MediaFormat.MIMETYPE_AUDIO_MSGSM, "bbb_1ch_8kHz_gsm.wav", "bbb_1ch_8kHz_s16le.raw",
                    "bbb_1ch_8kHz_gsm.wav", 783.409f, -1L},
            {MediaFormat.MIMETYPE_AUDIO_VORBIS, "bbb_1ch_16kHz_vorbis.mka", "bbb_1ch_8kHz_s16le" +
                    ".raw", "bbb_2ch_44kHz_vorbis.mka", -1.0f, -1L},
            {MediaFormat.MIMETYPE_AUDIO_OPUS, "bbb_2ch_48kHz_opus.mka", "bbb_2ch_48kHz_s16le.raw"
                    , "bbb_1ch_48kHz_opus.mka", -1.0f, -1L},
            {MediaFormat.MIMETYPE_AUDIO_AAC, "bbb_1ch_16kHz_aac.mp4", "bbb_1ch_16kHz_s16le.raw",
                    "bbb_2ch_44kHz_aac.mp4", -1.0f, -1L},
            {MediaFormat.MIMETYPE_VIDEO_MPEG2, "bbb_640x360_1mbps_30fps_mpeg2.mp4", null,
                    "bbb_1920x1080_5mbps_30fps_mpeg2.mp4", -1.0f, -1L},
            {MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_1280x720_1mbps_30fps_avc.mp4", null,
                    "bbb_1920x1080_3mbps_30fps_avc.mp4", -1.0f, 4020966648L},
            {MediaFormat.MIMETYPE_VIDEO_HEVC, "bbb_1280x720_768kbps_30fps_hevc.mp4", null,
                    "bbb_1920x1080_3mbps_30fps_hevc.mp4", -1.0f, 2235039068L},
            {MediaFormat.MIMETYPE_VIDEO_MPEG4, "bbb_128x96_64kbps_12fps_mpeg4.mp4", null,
                    "bbb_176x144_192kbps_15fps_mpeg4.mp4", -1.0f, -1L},
            {MediaFormat.MIMETYPE_VIDEO_H263, "bbb_176x144_128kbps_15fps_h263.3gp", null,
                    "bbb_176x144_192kbps_10fps_h263.3gp", -1.0f, -1L},
            {MediaFormat.MIMETYPE_VIDEO_VP8, "bbb_1280x720_1mbps_30fps_vp8.webm", null,
                    "bbb_1920x1080_3mbps_30fps_vp8.webm", -1.0f, 259557557L},
            {MediaFormat.MIMETYPE_VIDEO_VP9, "bbb_1280x720_1mbps_30fps_vp9.webm", null,
                    "bbb_1920x1080_3mbps_30fps_vp9.webm", -1.0f, 1521950687L},
            {MediaFormat.MIMETYPE_VIDEO_AV1, "bbb_1280x720_768kbps_30fps_av1.mp4", null,
                    "bbb_1920x1080_2mbps_30fps_av1.mp4", -1.0f, 3137881634L},
    });

    public CodecDecoderTest(String mime, String testFile, String refFile, String reconfigFile,
            float rmsError, long refCRC) {
        super(mime, testFile);
        mRefFile = refFile;
        mReconfigFile = reconfigFile;
        mRmsError = rmsError;
        mRefCRC = refCRC;
    }

    private short[] setUpAudioReference() throws IOException {
        File refFile = new File(mInpPrefix + mRefFile);
        short[] refData;
        try (FileInputStream refStream = new FileInputStream(refFile)) {
            FileChannel fileChannel = refStream.getChannel();
            int length = (int) refFile.length();
            ByteBuffer refBuffer = ByteBuffer.allocate(length);
            refBuffer.order(ByteOrder.LITTLE_ENDIAN);
            fileChannel.read(refBuffer);
            refData = new short[length / 2];
            refBuffer.position(0);
            for (int i = 0; i < length / 2; i++) {
                refData[i] = refBuffer.getShort();
            }
        }
        return refData;
    }

    private void verify() throws IOException {
        if (mRmsError >= 0) {
            assertTrue(mRefFile != null);
            short[] refData = setUpAudioReference();
            float currError = mOutputBuff.getRmsError(refData);
            float errMargin = mRmsError * RMS_ERROR_TOLERANCE;
            assertTrue(String.format("%s rms error too high exp/got %f/%f", mTestFile,
                    errMargin, currError), currError <= errMargin);
        } else if (mRefCRC >= 0) {
            assertEquals(String.format("%s checksum mismatch", mTestFile), mRefCRC,
                    mOutputBuff.getCheckSumImage());
        }
    }

    public void decodeAndVerify(String decoder, boolean isAsync, boolean eosType, boolean ifVerify,
            boolean modeSurface, int frameLimit) throws IOException, InterruptedException {
        String log = String.format("codec: %s, file: %s, mode: %s, eos type: %s:: ",
                decoder, mTestFile, (isAsync ? "async" : "sync"),
                (eosType ? "eos with last frame" : "eos separate"));
        MediaFormat format = setUpSource(mTestFile);
        mSaveToMem = ifVerify && (mSurface == null);
        mOutputBuff = new OutputManager();
        if (modeSurface) {
            CodecTestActivity activity = mActivityRule.getActivity();
            activity.setScreenParams(getWidth(format), getHeight(format), true);
        }
        assertTrue("codec name act/got: " + mCodec.getName() + '/' + decoder,
                mCodec.getName().equals(decoder));
        assertTrue("error! codec canonical name is null",
                mCodec.getCanonicalName() != null && !mCodec.getCanonicalName().isEmpty());
        validateMetrics(decoder);
        mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        configureCodec(format, isAsync, eosType, false);
        mCodec.start();
        doWork(frameLimit);
        queueEOS();
        waitForAllOutputs();
        validateMetrics(decoder, format);
        mCodec.reset();
        assertTrue(log + " unexpected error", !mAsyncHandle.hasSeenError());
        if (mIsAudio) {
            assertTrue(log + " pts is not strictly increasing",
                    mOutputBuff.isPtsStrictlyIncreasing(mPrevOutputPts));
        } else {
            assertTrue(log + " input pts list and output pts list are not identical",
                    mOutputBuff.isOutPtsListIdenticalToInpPtsList(false));
        }
        mExtractor.release();
        if (mSaveToMem && frameLimit == Integer.MAX_VALUE) verify();
    }

    public void flushAndDecode(String decoder, boolean isAsync, boolean ifVerify,
            boolean modeSurface, int frameLimit) throws IOException, InterruptedException {
        String log = String.format("decoder: %s, input file: %s, mode: %s:: ", decoder,
                mTestFile, (isAsync ? "async" : "sync"));
        MediaFormat format = setUpSource(mTestFile);
        mCsdBuffers.clear();
        for (int i = 0; ; i++) {
            String csdKey = "csd-" + i;
            if (format.containsKey(csdKey)) {
                mCsdBuffers.add(format.getByteBuffer(csdKey));
            } else break;
        }
        final int mode = MediaExtractor.SEEK_TO_CLOSEST_SYNC;
        mSaveToMem = false;
        mOutputBuff = new OutputManager();
        if (modeSurface) {
            CodecTestActivity activity = mActivityRule.getActivity();
            activity.setScreenParams(getWidth(format), getHeight(format), true);
        }

        mExtractor.seekTo(0, mode);
        configureCodec(format, isAsync, true, false);
        mCodec.start();
        flushCodec();  /* test flush in running state before queuing input */
        if (mIsCodecInAsyncMode) mCodec.start();
        queueCodecConfig();  /* flushed codec too soon after start, resubmit csd */

        doWork(1);
        flushCodec();
        if (mIsCodecInAsyncMode) mCodec.start();
        queueCodecConfig();  /* flushed codec too soon after start, resubmit csd */

        mExtractor.seekTo(1000000, mode);
        mOutputBuff.reset();
        doWork(23);
        assertTrue(log + " pts is not strictly increasing",
                mOutputBuff.isPtsStrictlyIncreasing(mPrevOutputPts));

        boolean checkMetrics = (mOutputCount != 0);
        flushCodec();  /* test flush in running state */
        if (checkMetrics) validateMetrics(decoder, format);
        if (mIsCodecInAsyncMode) mCodec.start();
        mOutputBuff.reset();
        mSaveToMem = ifVerify && (mSurface == null);
        mExtractor.seekTo(0, mode);
        doWork(frameLimit);
        queueEOS();
        waitForAllOutputs();
        assertTrue(log + " unexpected error", !mAsyncHandle.hasSeenError());
        if (mIsAudio) {
            assertTrue("reference output pts is not strictly increasing",
                    mOutputBuff.isPtsStrictlyIncreasing(mPrevOutputPts));
        } else {
            assertTrue("input pts list and output pts list are not identical",
                    mOutputBuff.isOutPtsListIdenticalToInpPtsList(false));
        }
        if (mSaveToMem && frameLimit == Integer.MAX_VALUE) verify();

        flushCodec();  /* test flush in eos state */
        if (mIsCodecInAsyncMode) mCodec.start();
        mOutputBuff.reset();
        mExtractor.seekTo(0, mode);
        doWork(frameLimit);
        queueEOS();
        waitForAllOutputs();
        if (mSaveToMem && frameLimit == Integer.MAX_VALUE) verify();

        mCodec.reset();
        assertTrue(log + " unexpected error", !mAsyncHandle.hasSeenError());
        if (mIsAudio) {
            assertTrue("reference output pts is not strictly increasing",
                    mOutputBuff.isPtsStrictlyIncreasing(mPrevOutputPts));
        } else {
            assertTrue("input pts list and output pts list are not identical",
                    mOutputBuff.isOutPtsListIdenticalToInpPtsList(false));
        }
        mExtractor.release();
    }

    public void reConfigure(String decoder, boolean isAsync, boolean ifVerify, boolean modeSurface,
            int frameLimit) throws IOException, InterruptedException {
        MediaFormat format = setUpSource(mTestFile);
        String log = String.format("decoder: %s, input file: %s, mode: %s:: ", decoder,
                mTestFile, (isAsync ? "async" : "sync"));
        final long pts = 500000;
        final int mode = MediaExtractor.SEEK_TO_CLOSEST_SYNC;
        mSaveToMem = false;
        OutputManager ref = new OutputManager();
        mOutputBuff = ref;
        if (modeSurface) {
            CodecTestActivity activity = mActivityRule.getActivity();
            activity.setScreenParams(getWidth(format), getHeight(format), true);
        }
        mExtractor.seekTo(1000000, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        configureCodec(format, isAsync, true, false);
        /* test reconfigure in stopped state */
        reConfigureCodec(format, !isAsync, false, false);
        mCodec.start();
        /* test reconfigure in running state before queuing input */
        reConfigureCodec(format, !isAsync, false, false);
        mCodec.start();
        doWork(23);
        /* test reconfigure codec in running state */
        reConfigureCodec(format, isAsync, true, false);
        mCodec.start();
        mSaveToMem = ifVerify && (mSurface == null);
        mOutputBuff.reset();
        mExtractor.seekTo(0, mode);
        doWork(frameLimit);
        queueEOS();
        waitForAllOutputs();
        if (mSaveToMem && frameLimit == Integer.MAX_VALUE) verify();

        mCodec.reset();
        assertTrue(log + " unexpected error", !mAsyncHandle.hasSeenError());

        /* test reconfigure codec at eos state */
        reConfigureCodec(format, !isAsync, false, false);
        mCodec.start();
        mOutputBuff.reset();
        mExtractor.seekTo(0, mode);
        doWork(Integer.MAX_VALUE);
        queueEOS();
        waitForAllOutputs();
        if (mSaveToMem && frameLimit == Integer.MAX_VALUE) verify();
        mCodec.reset();
        assertTrue(log + " unexpected error", !mAsyncHandle.hasSeenError());

        mExtractor.release();
        /* test reconfigure codec for new file */
        MediaFormat newFormat = setUpSource(mReconfigFile);
        log = String.format("decoder: %s, input file: %s, mode: %s:: ", decoder,
                mReconfigFile, (isAsync ? "async" : "sync"));
        reConfigureCodec(newFormat, isAsync, false, false);
        if (modeSurface) {
            CodecTestActivity activity = mActivityRule.getActivity();
            activity.setScreenParams(getWidth(newFormat), getHeight(newFormat), true);
        }
        mCodec.start();
        mSaveToMem = false;
        mOutputBuff = new OutputManager();
        mExtractor.seekTo(pts, mode);
        doWork(frameLimit);
        queueEOS();
        waitForAllOutputs();
        validateMetrics(decoder, newFormat);
        mCodec.reset();
        assertTrue(log + " unexpected error", !mAsyncHandle.hasSeenError());
        mExtractor.release();
        mOutputBuff = ref;
    }

    public static void isDecoderRunPass(CodecDecoderTest cdt, String decoder, boolean isAsync,
            boolean eosType, boolean verify, boolean surfaceMode, int frames, Menu lunch)
            throws IOException, InterruptedException {
        do {
            try {
                if (lunch == Menu.DECODE)
                    cdt.decodeAndVerify(decoder, isAsync, eosType, verify, surfaceMode, frames);
                else if (lunch == Menu.FLUSH)
                    cdt.flushAndDecode(decoder, isAsync, verify, surfaceMode, frames);
                else if (lunch == Menu.RECONFIGURE)
                    cdt.reConfigure(decoder, isAsync, verify, surfaceMode, frames);
                break;
            } catch (MediaCodec.CodecException e) {
                if (e.isTransient()) Thread.sleep(1000);
                else throw e;
            }
        } while (true);
    }
}

class DecodeParallel implements Callable<Void> {
    private final long mSeed = 0x12b9b0a1;  // random seed
    private final Random rand = new Random(mSeed);
    CodecDecoderTest mCdt;
    private final String mDecoder;
    CodecDecoderTest.Menu mSelector;

    public DecodeParallel(CodecDecoderTest cdt, String decoder, CodecDecoderTest.Menu selector) {
        mCdt = cdt;
        mDecoder = decoder;
        mSelector = selector;
    }

    @Override
    public Void call() throws IOException, InterruptedException {
        final boolean isAsync = ((rand.nextInt() & 1) == 0);
        final boolean eosType = ((rand.nextInt() & 1) == 0);
        final boolean verify = true;
        final boolean surfaceMode = false;
        final int frames = Integer.MAX_VALUE;
        mCdt.mCodec = MediaCodec.createByCodecName(mDecoder);
        CodecDecoderTest.isDecoderRunPass(mCdt, mDecoder, isAsync, eosType, verify, surfaceMode,
                frames, mSelector);
        mCdt.mCodec.release();
        return null;
    }
}
