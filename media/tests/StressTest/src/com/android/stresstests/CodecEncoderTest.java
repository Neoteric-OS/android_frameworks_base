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
import android.media.MediaFormat;
import android.opengl.GLES20;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertTrue;

public class CodecEncoderTest extends CodecEncoderTestBase {
    private static final String LOG_TAG = CodecEncoderTest.class.getSimpleName();
    private static final int[] testRGB0 = new int[]{47, 147, 220};
    private static final int[] testRGB1 = new int[]{255, 201, 14};

    private final int[] mBitrates;
    private final int[] mEncParamList1;
    private final int[] mEncParamList2;
    public ArrayList<MediaFormat> mFormats;

    private int mLatency;
    private boolean mReviseLatency;
    private boolean mSurfaceMode;
    private Surface mInpSurface;
    private EGLWindowSurface mEGLWindowInpSurface;

    public enum Menu {
        ENCODE,
        FLUSH,
        RECONFIGURE,
    }

    static final List<CodecEncoderTest.Menu> menuValues = Collections
            .unmodifiableList(Arrays.asList(CodecEncoderTest.Menu.values()));

    public static CodecEncoderTest.Menu randomChoice(Random rand)  {
        return menuValues.get(rand.nextInt(menuValues.size()));
    }

    public CodecEncoderTest(String mime, int[] bitrates, int[] encoderInfo1, int[] encoderInfo2) {
        super(mime);
        mBitrates = bitrates;
        mEncParamList1 = encoderInfo1;
        mEncParamList2 = encoderInfo2;
        mFormats = new ArrayList<>();
        mLatency = mMaxBFrames;
        mReviseLatency = false;
    }

    void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        super.dequeueOutput(bufferIndex, info);
    }

    public static final List<Object[]> exhaustiveArgsList = Arrays.asList(new Object[][]{
            // Audio - CodecMime, arrays of bit-rates, sample rates, channel counts
            {MediaFormat.MIMETYPE_AUDIO_AAC, new int[]{192000}, new int[]{44100, 48000},
                    new int[]{2}},
            {MediaFormat.MIMETYPE_AUDIO_OPUS, new int[]{6600, 23850}, new int[]{16000},
                    new int[]{1}},
            {MediaFormat.MIMETYPE_AUDIO_AMR_NB, new int[]{4750, 12200}, new int[]{8000},
                    new int[]{1}},
            {MediaFormat.MIMETYPE_AUDIO_AMR_WB, new int[]{6600, 23850}, new int[]{16000},
                    new int[]{1}},
            {MediaFormat.MIMETYPE_AUDIO_FLAC, new int[]{5}, new int[]{8000, 192000}, new int[]{2}},

            // Video - CodecMime, arrays of bit-rates, height, width
            {MediaFormat.MIMETYPE_VIDEO_H263, new int[]{128000, 128000}, new int[]{176},
                    new int[]{144}},
            {MediaFormat.MIMETYPE_VIDEO_MPEG4, new int[]{32000, 64000}, new int[]{64, 96},
                    new int[]{32, 64}},
            {MediaFormat.MIMETYPE_VIDEO_AVC, new int[]{1500000}, new int[]{1280, 1024},
                    new int[]{720, 768}},
            {MediaFormat.MIMETYPE_VIDEO_HEVC, new int[]{256000}, new int[]{176, 352},
                    new int[]{144, 240}},
            {MediaFormat.MIMETYPE_VIDEO_VP8, new int[]{1500000}, new int[]{1280, 1024},
                    new int[]{720, 768}},
            {MediaFormat.MIMETYPE_VIDEO_VP9, new int[]{1500000}, new int[]{1280, 1024},
                    new int[]{720, 768}},
            {MediaFormat.MIMETYPE_VIDEO_AV1, new int[]{256000}, new int[]{176, 352},
                    new int[]{144, 240}},
    });

    void configureCodec(MediaFormat format, boolean isAsync, boolean signalEOSWithLastFrame,
            boolean isEncoder) {
        super.configureCodec(format, isAsync, signalEOSWithLastFrame, true);
        if (mSurfaceMode) {
            if (mCodec.getInputFormat().containsKey(MediaFormat.KEY_LATENCY)) {
                mReviseLatency = true;
                mLatency = mCodec.getInputFormat().getInteger(MediaFormat.KEY_LATENCY);
            }
            mInpSurface = mCodec.createInputSurface();
            assertTrue("Surface is not valid", mInpSurface.isValid());
            mEGLWindowInpSurface = new EGLWindowSurface(mInpSurface);
        }
    }

    void setUpSource() throws IOException {
        setUpSource(mInputFile);
    }

    public void setUpParams(int limit) {
        int count = 0;
        for (int bitrate : mBitrates) {
            if (mIsAudio) {
                for (int rate : mEncParamList1) {
                    for (int channels : mEncParamList2) {
                        MediaFormat format = new MediaFormat();
                        format.setString(MediaFormat.KEY_MIME, mMime);
                        if (mMime.equals(MediaFormat.MIMETYPE_AUDIO_FLAC)) {
                            format.setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, bitrate);
                        } else {
                            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
                        }
                        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, rate);
                        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channels);
                        mFormats.add(format);
                        count++;
                        if (count >= limit) return;
                    }
                }
            } else {
                assertTrue("Wrong number of height, width parameters",
                        mEncParamList1.length == mEncParamList2.length);
                for (int i = 0; i < mEncParamList1.length; i++) {
                    MediaFormat format = new MediaFormat();
                    format.setString(MediaFormat.KEY_MIME, mMime);
                    format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
                    format.setInteger(MediaFormat.KEY_WIDTH, mEncParamList1[i]);
                    format.setInteger(MediaFormat.KEY_HEIGHT, mEncParamList2[i]);
                    format.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
                    format.setInteger(MediaFormat.KEY_MAX_B_FRAMES, mMaxBFrames);
                    format.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, 1.0f);
                    format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
                    mFormats.add(format);
                    count++;
                    if (count >= limit) return;
                }
            }
        }
    }

    private void checkErrorAndVerifyPTS(String log)  {
        assertTrue(log + " unexpected error", !mAsyncHandle.hasSeenError());
        assertTrue(log + " pts is not strictly increasing",
                mOutputBuff.isPtsStrictlyIncreasing(mPrevOutputPts));
        if (!mIsAudio) {
            if (mInputCount != mOutputCount)
                Log.e(LOG_TAG,log + "input count != output count, act/exp: " +
                        mOutputCount + " / " + mInputCount);
            if (!mOutputBuff.isOutPtsListIdenticalToInpPtsList((mMaxBFrames != 0)))
                Log.e(LOG_TAG, log + " input pts list and output pts list are not identical");
        }
    }

    private void initParams(MediaFormat format) {
        if (mIsAudio) {
            mSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            mChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        } else {
            mWidth = format.getInteger(MediaFormat.KEY_WIDTH);
            mHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
        }
    }

    private long computePresentationTime(int frameIndex) {
        return frameIndex * 1000000L / mFrameRate;
    }

    private void generateSurfaceFrame(int frameIndex) {
        frameIndex %= 8;

        int startX, startY;
        if (frameIndex < 4) {
            // (0,0) is bottom-left in GL
            startX = frameIndex * (mWidth / 4);
            startY = mHeight / 2;
        } else {
            startX = (7 - frameIndex) * (mWidth / 4);
            startY = 0;
        }

        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(testRGB0[0] / 255.0f, testRGB0[1] / 255.0f, testRGB0[2] / 255.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(startX, startY, mWidth / 4, mHeight / 2);
        GLES20.glClearColor(testRGB1[0] / 255.0f, testRGB1[1] / 255.0f, testRGB1[2] / 255.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    }

    void doWork(int frameLimit) throws InterruptedException, IOException {
        int frameCount = 0;
        if (mIsCodecInAsyncMode) {
            // dequeue output after inputEOS is expected to be done in waitForAllOutputs()
            while (!mAsyncHandle.hasSeenError() && !mSawInputEOS) {
                int retry = 0;
                while (mReviseLatency) {
                    if (mAsyncHandle.hasOutputFormatChanged()) {
                        mReviseLatency = false;
                        int actualLatency = mAsyncHandle.getOutputFormat()
                                .getInteger(MediaFormat.KEY_LATENCY, mLatency);
                        if (mLatency < actualLatency) {
                            mLatency = actualLatency;
                            return;
                        }
                    } else {
                        if (retry > 10) throw new InterruptedException(
                                "did not receive output format changed for encoder");
                        Thread.sleep(Q_DEQ_TIMEOUT_US / 1000);
                        retry ++;
                    }
                }
                if (mSurfaceMode && (mInputCount - mOutputCount <= mLatency)) {
                    if (frameCount < frameLimit) {
                        long pts = mInputOffsetPts + computePresentationTime(mInputCount);
                        mEGLWindowInpSurface.makeCurrent();
                        generateSurfaceFrame(mInputCount);
                        mEGLWindowInpSurface.setPresentationTime(pts * 1000);
                        if (ENABLE_LOGS) Log.d(LOG_TAG, "inputSurface swapBuffers");
                        mEGLWindowInpSurface.swapBuffers();
                        mOutputBuff.saveInPTS(pts);
                        mInputCount++;
                        frameCount++;
                    } else {
                        mCodec.signalEndOfInputStream();
                        mSawInputEOS = true;
                    }
                    continue;
                }
                Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getWork();
                if (element != null) {
                    int bufferID = element.first;
                    MediaCodec.BufferInfo info = element.second;
                    if (info != null) {
                        // <id, info> corresponds to output callback. Handle it accordingly
                        dequeueOutput(bufferID, info);
                    } else {
                        // <id, null> corresponds to input callback. Handle it accordingly
                        if (frameCount < frameLimit) {
                            enqueueInput(bufferID);
                            frameCount++;
                        } else {
                            enqueueEOS(bufferID);
                        }
                    }
                }
            }
        } else {
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            // dequeue output after inputEOS is expected to be done in waitForAllOutputs()
            while (!mSawInputEOS) {
                if (mSurfaceMode) {
                    if (mInputCount - mOutputCount <= mLatency) {
                        if (frameCount < frameLimit) {
                            long pts = mInputOffsetPts + computePresentationTime(mInputCount);
                            mEGLWindowInpSurface.makeCurrent();
                            generateSurfaceFrame(mInputCount);
                            mEGLWindowInpSurface.setPresentationTime(pts * 1000);
                            if (ENABLE_LOGS) Log.d(LOG_TAG, "inputSurface swapBuffers");
                            mEGLWindowInpSurface.swapBuffers();
                            mOutputBuff.saveInPTS(pts);
                            mInputCount++;
                            frameCount++;
                        } else {
                            mCodec.signalEndOfInputStream();
                            mSawInputEOS = true;
                        }
                        continue;
                    }
                } else {
                    int inputBufferId = mCodec.dequeueInputBuffer(Q_DEQ_TIMEOUT_US);
                    if (inputBufferId != -1) {
                        if (frameCount < frameLimit) {
                            enqueueInput(inputBufferId);
                            frameCount++;
                        } else {
                            enqueueEOS(inputBufferId);
                        }
                    }
                }
                int outputBufferId = mCodec.dequeueOutputBuffer(outInfo, Q_DEQ_TIMEOUT_US);
                if (outputBufferId >= 0) {
                    dequeueOutput(outputBufferId, outInfo);
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    mOutFormat = mCodec.getOutputFormat();
                    mLatency = mOutFormat.getInteger(MediaFormat.KEY_LATENCY, mLatency);
                    mSignalledOutFormatChanged = true;
                }
            }
        }
    }

    public void encode(String encoder, boolean isAsync, boolean eosType, boolean modeSurface,
            int frameLimit, byte[] inputData) throws IOException, InterruptedException {
        mSurfaceMode = !mIsAudio && modeSurface;
        setUpParams(Integer.MAX_VALUE);
        if (!mSurfaceMode) mInputData = inputData;
        mSaveToMem = false;
        mOutputBuff = new OutputManager();
        assertTrue("codec name act/got: " + mCodec.getName() + '/' + encoder,
                mCodec.getName().equals(encoder));
        assertTrue("error! codec canonical name is null",
                mCodec.getCanonicalName() != null && !mCodec.getCanonicalName().isEmpty());
        for (MediaFormat format : mFormats) {
            initParams(format);
            String log =
                    String.format("format: %s \n codec: %s, file: %s, mode: %s, eos type: %s:: ",
                            format, encoder, mInputFile, (isAsync ? "async" : "sync"),
                            (eosType ? "eos with last frame" : "eos separate"));
            mOutputBuff.reset();
            configureCodec(format, isAsync, eosType, true);
            mCodec.start();
            doWork(frameLimit);
            queueEOS();
            waitForAllOutputs();
            if (mSurfaceMode) {
                mEGLWindowInpSurface.release();
                mInpSurface.release();
                mInpSurface = null;
            }
            mCodec.reset();
            checkErrorAndVerifyPTS(log);
        }
    }

    public void flushAndEncode(String encoder, boolean isAsync, boolean modeSurface, int frameLimit,
            byte[] inputData) throws IOException, InterruptedException {
        // TODO: flush don't set all flags required e.g. mEndOfStreamSent from
        //  GraphicBufferSource due to which unable to submit input after flush
        // mSurfaceMode = !mIsAudio && modeSurface;
        mSurfaceMode = false;
        setUpParams(1);
        if (!mSurfaceMode) mInputData = inputData;
        mInputData = inputData;
        mSaveToMem = false;
        mOutputBuff = new OutputManager();
        MediaFormat format = mFormats.get(0);
        initParams(format);
        String log = String.format("encoder: %s, input file: %s, mode: %s:: ", encoder,
                mInputFile, (isAsync ? "async" : "sync"));
        configureCodec(format, isAsync, true, true);
        mCodec.start();

        /* test flush in running state before queuing input */
        flushCodec();
        mOutputBuff.reset();
        if (mIsCodecInAsyncMode) mCodec.start();
        doWork(23);
        assertTrue(log + " pts is not strictly increasing",
                mOutputBuff.isPtsStrictlyIncreasing(mPrevOutputPts));

        /* test flush in running state */
        flushCodec();
        mOutputBuff.reset();
        if (mIsCodecInAsyncMode) mCodec.start();
        doWork(frameLimit);
        queueEOS();
        waitForAllOutputs();
        checkErrorAndVerifyPTS(log);

        /* test flush in eos state */
        flushCodec();
        mOutputBuff.reset();
        if (mIsCodecInAsyncMode) mCodec.start();
        doWork(frameLimit);
        queueEOS();
        waitForAllOutputs();
        if (mSurfaceMode) {
            mEGLWindowInpSurface.release();
            mInpSurface.release();
            mInpSurface = null;
        }
        mCodec.reset();
        checkErrorAndVerifyPTS(log);
    }

    public void reconfigure(String encoder, boolean isAsync, boolean modeSurface, int frameLimit,
            byte[] inputData) throws IOException, InterruptedException {
        mSurfaceMode = !mIsAudio && modeSurface;
        setUpParams(2);
        if (!mSurfaceMode) mInputData = inputData;
        mInputData = inputData;
        MediaFormat format = mFormats.get(0);
        initParams(format);
        mSaveToMem = false;
        mOutputBuff = new OutputManager();
        String log = String.format("encoder: %s, input file: %s, mode: %s:: ", encoder,
                mInputFile, (isAsync ? "async" : "sync"));
        configureCodec(format, isAsync, true, true);

        /* test reconfigure in stopped state */
        reConfigureCodec(format, !isAsync, false, true);
        mCodec.start();

        /* test reconfigure in running state before queuing input */
        reConfigureCodec(format, !isAsync, false, true);
        mCodec.start();
        doWork(23);

        /* test reconfigure codec in running state */
        reConfigureCodec(format, isAsync, true, true);
        mCodec.start();
        mOutputBuff.reset();
        doWork(frameLimit);
        queueEOS();
        waitForAllOutputs();
        mCodec.reset();
        checkErrorAndVerifyPTS(log);

        /* test reconfigure codec at eos state */
        reConfigureCodec(format, !isAsync, false, true);
        mCodec.start();
        mOutputBuff.reset();
        doWork(frameLimit);
        queueEOS();
        waitForAllOutputs();
        mCodec.reset();
        checkErrorAndVerifyPTS(log);

        /* test reconfigure codec for new format */
        if (mFormats.size() > 1) {
            format = mFormats.get(1);
            reConfigureCodec(format, isAsync, false, true);
            initParams(format);
            mCodec.start();
            mOutputBuff.reset();
            doWork(frameLimit);
            queueEOS();
            waitForAllOutputs();
            mCodec.reset();
            checkErrorAndVerifyPTS(log);
        }
        if (mSurfaceMode) {
            mEGLWindowInpSurface.release();
            mInpSurface.release();
            mInpSurface = null;
        }
    }

    public static void isEncoderRunPass(CodecEncoderTest cet, String encoder, boolean isAsync,
            boolean eosType, boolean modeSurface, int frames, Menu lunch, byte[] inputData)
            throws IOException, InterruptedException {
        do {
            try {
                if (lunch == Menu.ENCODE)
                    cet.encode(encoder, isAsync, eosType, modeSurface, frames, inputData);
                else if (lunch == Menu.FLUSH)
                    cet.flushAndEncode(encoder, isAsync, modeSurface, frames, inputData);
                else if (lunch == Menu.RECONFIGURE)
                    cet.reconfigure(encoder, isAsync, modeSurface, frames, inputData);
                break;
            } catch (MediaCodec.CodecException e) {
                if (e.isTransient()) Thread.sleep(1000);
                else throw e;
            }
        } while (true);
    }
}

class EncodeParallel implements Callable<Void> {
    private final long mSeed = 0x12b9b0a1;  // random seed
    private final Random rand = new Random(mSeed);
    private final int mMaxSamples = 1000;
    CodecEncoderTest mCet;
    private final String mEncoder;
    CodecEncoderTest.Menu mSelector;
    byte[] mInputData;

    public EncodeParallel(CodecEncoderTest cet, String encoder, CodecEncoderTest.Menu selector,
            byte[] inputData) {
        mCet = cet;
        mEncoder = encoder;
        mSelector = selector;
        mInputData = inputData;
    }

    @Override
    public Void call() throws IOException, InterruptedException {
        final boolean isAsync = ((rand.nextInt() & 1) == 0);
        final boolean eosType = ((rand.nextInt() & 1) == 0);
        final boolean modeSurface = ((rand.nextInt() & 1) == 0);
        int frames = rand.nextInt(Integer.MAX_VALUE);
        if (modeSurface) frames = Math.min(mMaxSamples, frames);
        mCet.mCodec = MediaCodec.createByCodecName(mEncoder);
        CodecEncoderTest.isEncoderRunPass(mCet, mEncoder, isAsync, eosType, modeSurface, frames,
                mSelector, mInputData);
        mCet.mCodec.release();
        return null;
    }
}
