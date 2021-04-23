/*
 * Copyright (C) 2021 The Android Open Source Project
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

import org.junit.Rule;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;

public class AdaptivePlaybackTest extends CodecDecoderTestBase {
    private static final String LOG_TAG = AdaptivePlaybackTest.class.getSimpleName();
    public long mMaxPts = 0;

    public AdaptivePlaybackTest(String mime) {
        super(mime, null);
    }

    @Rule
    public ActivityTestRule<CodecTestActivity> mActivityRule;

    public static final List<Object[]> exhaustiveArgsList = Arrays.asList(new Object[][]{
            {MediaFormat.MIMETYPE_VIDEO_AVC, new String[]{
                    "bbb_avc_176x144_300kbps_60fps.mp4",
                    "bbb_avc_640x360_768kbps_30fps.mp4",
                    "bbb_800x640_768kbps_30fps_avc_2b.mp4",
                    "bbb_800x640_768kbps_30fps_avc_nob.mp4",
                    "bbb_1280x720_1mbps_30fps_avc_2b.mp4",
                    "bbb_640x360_512kbps_30fps_avc_nob.mp4",
                    "bbb_1280x720_1mbps_30fps_avc_nob.mp4",
                    "bbb_640x360_512kbps_30fps_avc_2b.mp4",
                    "bbb_1280x720_1mbps_30fps_avc_nob.mp4",
                    "bbb_640x360_512kbps_30fps_avc_nob.mp4",
                    "bbb_640x360_512kbps_30fps_avc_2b.mp4",
                    "bbb_1920x1080_3mbps_30fps_avc.mp4"}, CODEC_ALL},
            {MediaFormat.MIMETYPE_VIDEO_HEVC, new String[]{
                    "bbb_hevc_176x144_176kbps_60fps.mp4",
                    "bbb_hevc_640x360_1600kbps_30fps.mp4",
                    "bbb_800x640_768kbps_30fps_hevc_2b.mp4",
                    "bbb_800x640_768kbps_30fps_hevc_nob.mp4",
                    "bbb_1280x720_1mbps_30fps_hevc_2b.mp4",
                    "bbb_640x360_512kbps_30fps_hevc_nob.mp4",
                    "bbb_1280x720_1mbps_30fps_hevc_nob.mp4",
                    "bbb_640x360_512kbps_30fps_hevc_2b.mp4",
                    "bbb_1280x720_1mbps_30fps_hevc_nob.mp4",
                    "bbb_640x360_512kbps_30fps_hevc_nob.mp4",
                    "bbb_640x360_512kbps_30fps_hevc_2b.mp4",
                    "bbb_1920x1080_3mbps_30fps_hevc.mp4"}, CODEC_ALL},
            {MediaFormat.MIMETYPE_VIDEO_VP8, new String[]{
                    "bbb_vp8_176x144_240kbps_60fps.webm",
                    "bbb_vp8_640x360_2mbps_30fps.webm",
                    "bbb_800x640_768kbps_30fps_vp8.webm",
                    "bbb_1280x720_1mbps_30fps_vp8.webm",
                    "bbb_640x360_512kbps_30fps_vp8.webm",
                    "bbb_1920x1080_3mbps_30fps_vp8.webm"}, CODEC_ALL},
            {MediaFormat.MIMETYPE_VIDEO_VP9, new String[]{
                    "bbb_vp9_176x144_285kbps_60fps.webm",
                    "bbb_vp9_640x360_1600kbps_30fps.webm",
                    "bbb_800x640_768kbps_30fps_vp9.webm",
                    "bbb_1280x720_1mbps_30fps_vp9.webm",
                    "bbb_640x360_512kbps_30fps_vp9.webm",
                    "bbb_1920x1080_3mbps_30fps_vp9.webm"}, CODEC_ALL},
            {MediaFormat.MIMETYPE_VIDEO_MPEG4, new String[]{
                    "bbb_128x96_64kbps_12fps_mpeg4.mp4",
                    "bbb_176x144_192kbps_15fps_mpeg4.mp4",
                    "bbb_128x96_64kbps_12fps_mpeg4.mp4"}, CODEC_ALL},
            {MediaFormat.MIMETYPE_VIDEO_AV1, new String[]{
                    "bbb_800x640_768kbps_30fps_av1.webm",
                    "bbb_1280x720_1mbps_30fps_av1.webm",
                    "bbb_640x360_512kbps_30fps_av1.webm",
                    "bbb_1920x1080_2mbps_30fps_av1.mp4"}, CODEC_ALL},
            {MediaFormat.MIMETYPE_VIDEO_MPEG2, new String[]{
                    "bbb_cif_768kbps_30fps_mpeg2_stereo_48kHz_192kbps_mp3.mp4",
                    "bbb_384x216_768kbps_30fps_mpeg2_2b.mp4",
                    "bbb_cif_768kbps_30fps_mpeg2_stereo_48kHz_192kbps_mp3.mp4",
                    "bbb_640x360_1mbps_30fps_mpeg2.mp4",
                    "bbb_800x640_768kbps_30fps_mpeg2_2b.mp4",
                    "bbb_800x640_768kbps_30fps_mpeg2_nob.mp4",
                    "bbb_1280x720_1mbps_30fps_mpeg2_2b.mp4",
                    "bbb_640x360_512kbps_30fps_mpeg2_nob.mp4",
                    "bbb_1280x720_1mbps_30fps_mpeg2_nob.mp4",
                    "bbb_640x360_512kbps_30fps_mpeg2_2b.mp4",
                    "bbb_1280x720_1mbps_30fps_mpeg2_nob.mp4",
                    "bbb_640x360_512kbps_30fps_mpeg2_nob.mp4",
                    "bbb_640x360_512kbps_30fps_mpeg2_2b.mp4",
                    "bbb_640x360_1mbps_30fps_mpeg2.mp4",
                    "bbb_1920x1080_5mbps_30fps_mpeg2.mp4"}, CODEC_ALL},
    });

    @Override
    void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawOutputEOS = true;
        }
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            mOutputBuff.saveOutPTS(info.presentationTimeUs);
            mOutputCount++;
        }
        mCodec.releaseOutputBuffer(bufferIndex, mSurface != null);
    }

    public MediaFormat createInputList(MediaFormat format, ByteBuffer buffer,
            ArrayList<MediaCodec.BufferInfo> list, int offset, long ptsOffset) {
        if (hasCSD(format)) {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            bufferInfo.offset = offset;
            bufferInfo.size = 0;
            bufferInfo.presentationTimeUs = 0;
            bufferInfo.flags = MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
            for (int i = 0; ; i++) {
                String csdKey = "csd-" + i;
                if (format.containsKey(csdKey)) {
                    ByteBuffer csdBuffer = format.getByteBuffer(csdKey);
                    bufferInfo.size += csdBuffer.limit();
                    buffer.put(csdBuffer);
                    format.removeKey(csdKey);
                } else break;
            }
            list.add(bufferInfo);
            offset += bufferInfo.size;
        }
        while (true) {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            bufferInfo.size = mExtractor.readSampleData(buffer, offset);
            if (bufferInfo.size < 0) break;
            bufferInfo.offset = offset;
            bufferInfo.presentationTimeUs = ptsOffset + mExtractor.getSampleTime();
            mMaxPts = Math.max(mMaxPts, bufferInfo.presentationTimeUs);
            int flags = mExtractor.getSampleFlags();
            bufferInfo.flags = 0;
            if ((flags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                bufferInfo.flags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
            }
            list.add(bufferInfo);
            mExtractor.advance();
            offset += bufferInfo.size;
        }
        buffer.clear();
        buffer.position(offset);
        return format;
    }

    public void testAdaptivePlayback(String decoder, boolean isAsync, boolean modeSurface,
            MediaFormat format, ByteBuffer buffer, ArrayList<MediaCodec.BufferInfo> list)
            throws IOException, InterruptedException {
        mOutputBuff = new OutputManager();
        mCodec = MediaCodec.createByCodecName(decoder);
        if (modeSurface) {
            CodecTestActivity activity = mActivityRule.getActivity();
            activity.mSurfaceLock[surfaceIndex].lock();
            boolean isSurfaceInUse = activity.getSurfaceStatus(surfaceIndex);
            if (isSurfaceInUse == false) {
                activity.setSurfaceStatus(true, surfaceIndex);
                activity.setScreenParams(getWidth(format), getHeight(format), true);
                activity.mSurfaceLock[surfaceIndex].unlock();
            } else {
                activity.mSurfaceLock[surfaceIndex].unlock();
                activity.waitTillSurfaceIsFree(surfaceIndex);
            }
        }
        mOutputBuff.reset();
        configureCodec(format, isAsync, false, false);
        mCodec.start();
        doWork(buffer, list);
        queueEOS();
        waitForAllOutputs();
        mCodec.reset();
    }

    public static void isAdaptiveRunPass(AdaptivePlaybackTest apt, String decoder, boolean isAsync,
            boolean surfaceMode, MediaFormat format, ByteBuffer buffer,
            ArrayList<MediaCodec.BufferInfo> list)
            throws IOException, InterruptedException {
        do {
            try {
                apt.testAdaptivePlayback(decoder, isAsync, surfaceMode, format, buffer, list);
                break;
            } catch (MediaCodec.CodecException e) {
                if (e.isTransient()) Thread.sleep(1000);
                else throw e;
            }
        } while (true);
    }
}

class AdaptivePlaybackParallel implements Callable<Void> {
    private final long mSeed = 0x12b9b0a1;  // random seed
    private final Random rand = new Random(mSeed);
    AdaptivePlaybackTest mApt;
    private final String mDecoder;
    private final boolean mSurfaceMode;
    ArrayList<MediaCodec.BufferInfo> mList;

    MediaFormat mFormat;
    ByteBuffer mBuffer;

    public AdaptivePlaybackParallel(AdaptivePlaybackTest apt, String decoder,
            boolean surfaceMode, MediaFormat format, ByteBuffer buffer,
            ArrayList<MediaCodec.BufferInfo> list) {
        mApt = apt;
        mDecoder = decoder;
        mSurfaceMode = surfaceMode;
        mFormat = format;
        mBuffer = buffer;
        mList = list;
    }

    @Override
    public Void call() throws IOException, InterruptedException {
        final boolean isAsync = ((rand.nextInt() & 1) == 0);
        mApt.mCodec = MediaCodec.createByCodecName(mDecoder);
        AdaptivePlaybackTest
                .isAdaptiveRunPass(mApt, mDecoder, isAsync, mSurfaceMode, mFormat, mBuffer, mList);
        mApt.mCodec.release();
        if (mSurfaceMode) {
            CodecTestActivity activity = mApt.mActivityRule.getActivity();
            mApt.mSurface[mApt.surfaceIndex] = null;
            activity.mSurfaceLock[mApt.surfaceIndex].lock();
            activity.setSurfaceStatus(false, mApt.surfaceIndex);
            activity.mSurfaceLock[mApt.surfaceIndex].unlock();
        }
        return null;
    }
}
