/*
 * Copyright (C) 2024 The Android Open Source Project
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

#define LOG_TAG "FileSystemUtils"

#include "com_android_internal_content_FileSystemUtils.h"

#include <android-base/file.h>
#include <android-base/hex.h>
#include <android-base/unique_fd.h>
#include <elf.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/fs.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <utils/Log.h>

#include <array>
#include <fstream>
#include <vector>

using android::base::HexString;
using android::base::ReadFullyAtOffset;

namespace android {

bool punchHoles(const char *filePath, const uint64_t zipOffset,
                const std::vector<Elf64_Phdr> &programHeaders) {
    IF_ALOGD() {
        ALOGD("Total number of LOAD segments %zu", programHeaders.size());
        struct stat beforePunch;
        lstat(filePath, &beforePunch);
        ALOGD("Size before punching holes st_blocks: %ld, st_blksize: %ld, st_size: %ld",
              beforePunch.st_blocks, beforePunch.st_blksize, beforePunch.st_size);
    }

    android::base::unique_fd fd(open(filePath, O_RDWR | O_CLOEXEC));
    if (!fd.ok()) {
        ALOGE("Can't open file to punch %s", filePath);
        return false;
    }

    for (size_t index = 0; programHeaders.size() >= 2 && index < programHeaders.size() - 1;
         index++) {
        // find LOAD segments from program headers, calculate padding and punch holes
        uint64_t punchOffset;
        if (__builtin_add_overflow(programHeaders[index].p_offset, programHeaders[index].p_filesz,
                                   &punchOffset)) {
            ALOGE("Overflow occurred when adding offset and filesize");
            return false;
        }

        uint64_t punchLen;
        if (__builtin_sub_overflow(programHeaders[index + 1].p_offset, punchOffset, &punchLen)) {
            ALOGE("Overflow occurred when calculating length");
            return false;
        }

        IF_ALOGD() {
            std::vector<uint8_t> buffer(punchLen);
            ReadFullyAtOffset(fd, buffer.data(), punchLen, zipOffset + punchOffset);
            ALOGD("Punching holes for content %s", HexString(buffer.data(), buffer.size()).c_str());
        }

        // if we have a uncompressed file which is being opened from APK, use the zipoffset to punch
        // native lib inside Apk.
        fallocate(fd, FALLOC_FL_PUNCH_HOLE | FALLOC_FL_KEEP_SIZE, zipOffset + punchOffset,
                  punchLen);
    }

    IF_ALOGD() {
        struct stat afterPunch;
        lstat(filePath, &afterPunch);
        ALOGD("Size after punching holes st_blocks: %ld, st_blksize: %ld, st_size: %ld",
              afterPunch.st_blocks, afterPunch.st_blksize, afterPunch.st_size);
    }

    return true;
}

bool punchHolesInElf64(const char *filePath, const uint64_t zipOffset) {
    // Open Elf file
    Elf64_Ehdr ehdr;
    std::ifstream inputStream;
    inputStream.open(filePath, std::ifstream::in);

    // If this is a zip file, set the offset so that we can read elf file directly
    inputStream.seekg(zipOffset);
    // read executable headers
    inputStream.read((char *)&ehdr, sizeof(ehdr));
    if (!inputStream.good()) {
        return false;
    }

    // only consider elf64 for punching holes
    if (ehdr.e_ident[EI_CLASS] != ELFCLASS64) {
        ALOGE("Provided file is not ELF64");
        return false;
    }

    // read the program headers from elf file
    uint64_t programHeaderOffset = ehdr.e_phoff;
    uint16_t programHeaderNum = ehdr.e_phnum;

    IF_ALOGD() {
        ALOGD("Punching holes in file : %s programHeaderOffset: %lu programHeaderNum: %hu",
              filePath, programHeaderOffset, programHeaderNum);
    }

    // if this is a zip file, also consider elf offset inside a zip file
    inputStream.seekg(zipOffset + programHeaderOffset);

    std::vector<Elf64_Phdr> programHeaders;
    for (int headerIndex = 0; headerIndex < programHeaderNum; headerIndex++) {
        Elf64_Phdr header;
        inputStream.read((char *)&header, sizeof(header));

        if (!inputStream.good()) {
            return false;
        }

        if (header.p_type != PT_LOAD) {
            continue;
        }
        programHeaders.push_back(header);
    }
    inputStream.close();

    return punchHoles(filePath, zipOffset, programHeaders);
}

}; // namespace android
