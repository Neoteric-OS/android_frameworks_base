/*
 * Copyright (C) 2010 The Android Open Source Project
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

#include <unistd.h>
#include <fcntl.h>

#include <binder/IMemory.h>
#include <surfaceflinger/SurfaceComposerClient.h>

using namespace android;

/* This version number defines the format of the fbinfo struct.
   It must match versioning in ddms where this data is consumed. */
#define DDMS_RAWIMAGE_VERSION 1
struct fbinfo {
    unsigned int version;
    unsigned int bpp;
    unsigned int size;
    unsigned int width;
    unsigned int height;
    unsigned int red_offset;
    unsigned int red_length;
    unsigned int blue_offset;
    unsigned int blue_length;
    unsigned int green_offset;
    unsigned int green_length;
    unsigned int alpha_offset;
    unsigned int alpha_length;
} __attribute__((packed));

int main(int argc, char** argv)
{
    ScreenshotClient screenshot;
    if (screenshot.update() != NO_ERROR)
        return -1;

    uint32_t f = screenshot.getFormat();
    if (f != PIXEL_FORMAT_RGBA_8888)
        return -1;

    struct fbinfo fbinfo;
    fbinfo.version = DDMS_RAWIMAGE_VERSION;

    fbinfo.bpp = 32;
    fbinfo.red_offset = 0;
    fbinfo.red_length = 8;
    fbinfo.green_offset = 8;
    fbinfo.green_length = 8;
    fbinfo.blue_offset = 16;
    fbinfo.blue_length = 8;
    fbinfo.alpha_offset = 0;
    fbinfo.alpha_length = 0;
    fbinfo.width = screenshot.getWidth();
    fbinfo.height = screenshot.getHeight();
    fbinfo.size = fbinfo.width * fbinfo.height * fbinfo.bpp / 8;

    int fd = dup(STDOUT_FILENO);
    void const* base = screenshot.getPixels();

    write(fd, &fbinfo, sizeof fbinfo);
    write(fd, base, fbinfo.size);
    close(fd);
    return 0;
}
