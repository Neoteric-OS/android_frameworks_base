/*
 * Copyright 2024 The Android Open Source Project
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

#include "ImageDecoderFuzzerBase.h"

bool imgFuzzerHelper::init(const uint8_t* data, size_t size) {
    bool result = true;
    constexpr char kTestFd[] = "tempFd";
    if (mDataProvider.ConsumeBool()) {
        AImageDecoder_createFromBuffer(data, size, &mDecoder);
    } else {
        int32_t fileDesc = open(kTestFd, O_RDWR | O_CREAT | O_TRUNC);
        write(fileDesc, data, size);
        AImageDecoder_createFromFd(fileDesc, &mDecoder);
        close(fileDesc);
    }
    if (!mDecoder) {
        result = false;
    }
    return result;
}

void imgFuzzerHelper::process() {
    const AImageDecoderHeaderInfo* headerInfo = AImageDecoder_getHeaderInfo(mDecoder);
    AImageDecoderFrameInfo* frameInfo = AImageDecoderFrameInfo_create();
    int32_t height = AImageDecoderHeaderInfo_getHeight(headerInfo);
    int32_t width = AImageDecoderHeaderInfo_getWidth(headerInfo);
    while (mDataProvider.remaining_bytes()) {
        auto invokeImageApi = mDataProvider.PickValueInArray<const std::function<void()>>({
                [&]() {
                    int32_t testHeight =
                            mDataProvider.ConsumeIntegralInRange<int32_t>(kMinDimension,
                                                                          kMaxDimension);
                    int32_t testWidth =
                            mDataProvider.ConsumeIntegralInRange<int32_t>(kMinDimension,
                                                                          kMaxDimension);
                    int32_t result = AImageDecoder_setTargetSize(mDecoder, testHeight, testWidth);
                    if (result == ANDROID_IMAGE_DECODER_SUCCESS) {
                        height = testHeight;
                        width = testWidth;
                    }
                },
                [&]() {
                    AImageDecoder_setUnpremultipliedRequired(mDecoder,
                                                             mDataProvider
                                                                     .ConsumeBool() /* required */);
                },
                [&]() {
                    AImageDecoder_setAndroidBitmapFormat(
                            mDecoder,
                            mDataProvider.ConsumeIntegralInRange<
                                    int32_t>(ANDROID_BITMAP_FORMAT_NONE,
                                             ANDROID_BITMAP_FORMAT_RGBA_1010102) /* format */);
                },
                [&]() {
                    AImageDecoder_setDataSpace(mDecoder,
                                               mDataProvider
                                                       .ConsumeIntegral<int32_t>() /* dataspace */);
                },
                [&]() {
                    ARect rect{mDataProvider.ConsumeIntegral<int32_t>() /* left */,
                               mDataProvider.ConsumeIntegral<int32_t>() /* top */,
                               mDataProvider.ConsumeIntegral<int32_t>() /* right */,
                               mDataProvider.ConsumeIntegral<int32_t>() /* bottom */};
                    AImageDecoder_setCrop(mDecoder, rect);
                },
                [&]() { AImageDecoderHeaderInfo_getWidth(headerInfo); },
                [&]() { AImageDecoderHeaderInfo_getMimeType(headerInfo); },
                [&]() { AImageDecoderHeaderInfo_getAlphaFlags(headerInfo); },
                [&]() { AImageDecoderHeaderInfo_getAndroidBitmapFormat(headerInfo); },
                [&]() {
                    int32_t tempHeight;
                    int32_t tempWidth;
                    AImageDecoder_computeSampledSize(mDecoder,
                                                     mDataProvider.ConsumeIntegral<
                                                             int>() /* sampleSize */,
                                                     &tempWidth, &tempHeight);
                },
                [&]() { AImageDecoderHeaderInfo_getDataSpace(headerInfo); },
                [&]() { AImageDecoder_getRepeatCount(mDecoder); },
                [&]() { AImageDecoder_getFrameInfo(mDecoder, frameInfo); },
                [&]() { AImageDecoderFrameInfo_getDuration(frameInfo); },
                [&]() { AImageDecoderFrameInfo_hasAlphaWithinBounds(frameInfo); },
                [&]() { AImageDecoderFrameInfo_getDisposeOp(frameInfo); },
                [&]() { AImageDecoderFrameInfo_getBlendOp(frameInfo); },
                [&]() {
                    AImageDecoder_setInternallyHandleDisposePrevious(
                            mDecoder, mDataProvider.ConsumeBool() /* handle */);
                },
                [&]() { AImageDecoder_rewind(mDecoder); },
                [&]() { AImageDecoder_advanceFrame(mDecoder); },
                [&]() {
                    size_t stride = AImageDecoder_getMinimumStride(mDecoder);
                    size_t pixelSize = height * stride;
                    auto pixels = PixelPointer(std::malloc(pixelSize));
                    if (!pixels.get()) {
                        return;
                    }
                    AImageDecoder_decodeImage(mDecoder, pixels.get(), stride, pixelSize);
                },
        });
        invokeImageApi();
    }

    AImageDecoderFrameInfo_delete(frameInfo);
    AImageDecoder_delete(mDecoder);
}
