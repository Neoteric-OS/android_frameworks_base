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
#include <inttypes.h>
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

bool checkAllZeros(const std::vector<uint8_t> &readContent) {
    for (auto i : readContent) {
        if (i != 0) {
            return false;
        }
    }

    return true;
}

bool punchHoles(const char *filePath, const uint64_t zipOffset,
                const std::vector<Elf64_Phdr> &programHeaders) {
    {
        ALOGD("Total number of LOAD segments %zu", programHeaders.size());
        struct stat64 beforePunch;
        lstat64(filePath, &beforePunch);
        ALOGD("Size before punching holes st_blocks: %" PRIu64
              ", st_blksize: %ld, st_size: %" PRIu64 "",
              beforePunch.st_blocks, beforePunch.st_blksize,
              static_cast<uint64_t>(beforePunch.st_size));
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

        if (punchLen < 4096) {
            ALOGD("SKIPPING :Punching holes for length:%llu", punchLen);
            continue;
        }

        uint64_t punchStartOffset;
        if (__builtin_add_overflow(zipOffset, punchOffset, &punchStartOffset)) {
            ALOGW("punchHolesInLib::Overflow occurred when calculating punch start offset "
                  "zipoffset:%llu punchOffset:%llu",
                  zipOffset, punchOffset);
            return false;
        }

        ALOGD("punchHolesInLib::Punching hole in lib start: %llu len:%llu", punchStartOffset,
              punchLen);

        std::vector<uint8_t> buffer(punchLen);
        ReadFullyAtOffset(fd, buffer.data(), punchLen, punchStartOffset);
        ALOGD("Punching holes for length:%zu expected length:%llu content: %s", buffer.size(),
              punchLen, HexString(buffer.data(), buffer.size()).c_str());

        if (!checkAllZeros(buffer)) {
            ALOGE("punchHolesInLib:: Trying to punch non zero content");
            return false;
        }

        // if we have a uncompressed file which is being opened from APK, use the zipoffset to punch
        // native lib inside Apk.
        fallocate(fd, FALLOC_FL_PUNCH_HOLE | FALLOC_FL_KEEP_SIZE, punchStartOffset, punchLen);
    }

    {
        struct stat64 afterPunch;
        lstat64(filePath, &afterPunch);
        ALOGD("Size after punching holes st_blocks: %" PRIu64 ", st_blksize: %ld, st_size: %" PRIu64
              "",
              afterPunch.st_blocks, afterPunch.st_blksize,
              static_cast<uint64_t>(afterPunch.st_size));
    }

    return true;
}

void deleteInputStream(std::ifstream *inputStream) {
    inputStream->close();
    delete inputStream;
}

bool punchHolesInElf64(const char *filePath, const uint64_t zipOffset) {
    // Open Elf file
    Elf64_Ehdr ehdr;

    std::unique_ptr<std::ifstream, decltype(&deleteInputStream)>
            inputStream(new std::ifstream(filePath, std::ifstream::in), &deleteInputStream);

    // If this is a zip file, set the offset so that we can read elf file directly
    inputStream->seekg(zipOffset);
    // read executable headers
    inputStream->read((char *)&ehdr, sizeof(ehdr));
    if (!inputStream->good()) {
        ALOGW("End of input stream while reading executable header");
        return false;
    }

    // only consider elf64 for punching holes
    if (ehdr.e_ident[EI_CLASS] != ELFCLASS64) {
        ALOGW("Provided file is not ELF64");
        return false;
    }

    // read the program headers from elf file
    uint64_t programHeaderOffset = ehdr.e_phoff;
    uint16_t programHeaderNum = ehdr.e_phnum;

    {
        ALOGD("Punching holes in file : %s programHeaderOffset: %" PRIu64 " programHeaderNum: %hu",
              filePath, programHeaderOffset, programHeaderNum);
    }

    // if this is a zip file, also consider elf offset inside a zip file
    inputStream->seekg(zipOffset + programHeaderOffset);

    std::vector<Elf64_Phdr> programHeaders;
    for (int headerIndex = 0; headerIndex < programHeaderNum; headerIndex++) {
        Elf64_Phdr header;
        inputStream->read((char *)&header, sizeof(header));

        if (!inputStream->good()) {
            ALOGW("End of input stream while reading program header");
            return false;
        }

        if (header.p_type != PT_LOAD) {
            continue;
        }
        programHeaders.push_back(header);
    }

    return punchHoles(filePath, zipOffset, programHeaders);
}

bool punchHolesInApk(const char *filePath, uint64_t zipOffset, uint64_t extraFieldLen) {
    android::base::unique_fd fd(open(filePath, O_RDWR | O_CLOEXEC));
    if (!fd.ok()) {
        ALOGW("Can't open file to punch %s", filePath);
        return false;
    }

    // content is preceded by extra field. Zip offset is offset of exact content.
    // move back by extraFieldLen so that scan can be started at start of extra field.
    uint64_t extraFieldStart;
    if (__builtin_sub_overflow(zipOffset, extraFieldLen, &extraFieldStart)) {
        ALOGW("Overflow occurred when calculating start of extra field");
        return false;
    }

    // Read the entire extra fields at once and punch file according to zero stretches.
    std::vector<uint8_t> buffer(extraFieldLen);
    ReadFullyAtOffset(fd, buffer.data(), extraFieldLen, extraFieldStart);
    {
        ALOGD("Extra field content near offset: %lld, is %s", zipOffset,
              HexString(buffer.data(), buffer.size()).c_str());
    }

    {
        struct stat64 beforePunch;
        lstat64(filePath, &beforePunch);
        ALOGD("punchHolesInApk:: Size before punching holes st_blocks: %" PRIu64
              ", st_blksize: %ld, st_size: %" PRIu64 "",
              beforePunch.st_blocks, beforePunch.st_blksize,
              static_cast<uint64_t>(beforePunch.st_size));
    }

    uint64_t currentSize = 0;
    while (currentSize < extraFieldLen) {
        uint64_t end = currentSize;
        while (end < extraFieldLen && buffer[end] == 0) {
            ++end;
        }

        uint64_t punchLen;
        if (__builtin_sub_overflow(end, currentSize, &punchLen)) {
            ALOGW("Overflow occurred when calculating punching length");
            return false;
        }

        // Don't punch for every stretch of zero which is found
        if (punchLen > 4096) {
            uint64_t punchOffset;
            if (__builtin_add_overflow(extraFieldStart, currentSize, &punchOffset)) {
                ALOGW("Overflow occurred when calculating punch start offset");
                return false;
            }

            ALOGD("Punching hole in apk start: %llu len:%llu", punchOffset, punchLen);
            // Punch hole for this entire stretch.
            fallocate(fd, FALLOC_FL_PUNCH_HOLE | FALLOC_FL_KEEP_SIZE, punchOffset, punchLen);
        }
        currentSize = end;
        ++currentSize;
    }

    {
        struct stat64 afterPunch;
        lstat64(filePath, &afterPunch);
        ALOGD("punchHolesInApk:: Size after punching holes st_blocks: %" PRIu64
              ", st_blksize: %ld, st_size: %" PRIu64 "",
              afterPunch.st_blocks, afterPunch.st_blksize,
              static_cast<uint64_t>(afterPunch.st_size));
    }
    return true;
}

}; // namespace android
