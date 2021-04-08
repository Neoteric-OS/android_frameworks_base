/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include <hwui/Canvas.h>

#include <jni.h>

#include <utils/Color.h>
#include <SkCodec.h>
#include <sys/time.h>

class ImageDecoderPluginBase {
public:

    ImageDecoderPluginBase() {}
    virtual ~ImageDecoderPluginBase() {}
    virtual bool sniff(void* buffer, size_t bytesRead) = 0;
    virtual bool setStream(std::unique_ptr<SkStream> stream) = 0;
    virtual int getWidth() = 0;
    virtual int getHeight() = 0;
    virtual int decode(
            int targetWidth,
            int targetHeight,
            SkIRect rect,
            SkColorType colorType,
            sk_sp<SkColorSpace> colorSpace) = 0;
    virtual SkCodec::Result fillBitmap(void* pixels, size_t dstBufferSize);
    virtual SkISize getSampledSize(int sampleSize) = 0;
    virtual const char* getMimeType() = 0;
    virtual SkImageInfo getOutputInfo() = 0;
    virtual void enableHdr() = 0;
    virtual bool getHdrInfo(int* colorStandard, int* colorRange, int* colorTransfer, void *data);
};

class ImageDecoderPlugin : public ImageDecoderPluginBase{
public:
    ImageDecoderPlugin();
    virtual ~ImageDecoderPlugin();
    virtual bool sniff(void* buffer, size_t bytesRead);
    virtual bool setStream(std::unique_ptr<SkStream> stream);
    virtual int getWidth();
    virtual int getHeight();
    virtual int decode(
            int targetWidth,
            int targetHeight,
            SkIRect rect,
            SkColorType colorType,
            sk_sp<SkColorSpace> colorSpace);
    virtual SkCodec::Result fillBitmap(void* pixels, size_t dstBufferSize);
    virtual SkISize getSampledSize(int sampleSize);
    virtual const char* getMimeType();
    virtual SkImageInfo getOutputInfo();
    virtual void enableHdr();
    virtual bool getHdrInfo(int* colorStandard, int* colorRange, int* colorTransfer, void *data);

private:
    void *mVendorLibHandle;
    ImageDecoderPluginBase *mPluginBase;

    void addImageDecoderPlugin();
    void clearImageDecoderPlugin();
};

void PostHdrColorInfo(JNIEnv* env, jobject jimageDecoder, jlong nativePtr);

// Creates a Java Canvas object from canvas, calls jimageDecoder's PostProcess on it, and then
// releases the Canvas.
// Caller needs to check for exceptions.
jint postProcessAndRelease(JNIEnv* env, jobject jimageDecoder,
                           std::unique_ptr<android::Canvas> canvas);
