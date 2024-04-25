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

#include <android/imagedecoder.h>
#include <binder/IPCThreadState.h>
#include <fuzzer/FuzzedDataProvider.h>
#include <stddef.h>
#include <stdint.h>

#include <cstdlib>
#include <memory>

constexpr int32_t kMaxDimension = 5000;
constexpr int32_t kMinDimension = 0;

struct PixelFreer {
    void operator()(void* pixels) const {
        std::free(pixels);
    }
};

using PixelPointer = std::unique_ptr<void, PixelFreer>;

class imgFuzzerHelper {
public:
    imgFuzzerHelper(const uint8_t* data, size_t size) : mDataProvider(data, size) {}
    bool init(const uint8_t* data, size_t size);
    void process();
    FuzzedDataProvider mDataProvider;

private:
    AImageDecoder* mDecoder = nullptr;
};
