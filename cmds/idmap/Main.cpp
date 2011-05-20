/*
 * Copyright (C) 2011 The Android Open Source Project
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

#include <utils/AssetManager.h>
#include <utils/ResourceTypes.h>
#include <utils/ZipFileRO.h>

#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <unistd.h>

using namespace android;

/*
 * Program to create idmap files (see frameworks/base/libs/utils/README).
 *
 * Idmap files must be in sync with the apk packages it translates
 * resource IDs between. To this end, the idmap header contains CRC sums
 * of the packages at the time of idmap creation. Idmap files will not
 * be recreated if the CRC sums still match.
 *
 * This program may be invoked in two modes: either this process is expected to
 * open and close the output idmap file, or the parent process has already
 * opened the idmap output file on behalf of this process. This design mirrors
 * how installd interacts with dexopt; see installd for an example of the
 * latter usage.
 */

#ifndef TEMP_FAILURE_RETRY
// Used to retry syscalls that can return EINTR.
#define TEMP_FAILURE_RETRY(exp) ({         \
    typeof (exp) _rc;                      \
    do {                                   \
        _rc = (exp);                       \
    } while (_rc == -1 && errno == EINTR); \
    _rc; })
#endif

static const char* RESOURCES_FILENAME = "resources.arsc";

namespace {
    int verify_apk_readable(const char* apk_path)
    {
        return TEMP_FAILURE_RETRY(access(apk_path, R_OK));
    }

    int get_zip_entry_crc(const char* zip_path, const char* entry_name, uint32_t* crc)
    {
        ZipFileRO zip;
        if (zip.open(zip_path) != NO_ERROR) {
            return -1;
        }
        const ZipEntryRO entry = zip.findEntryByName(entry_name);
        if (entry == NULL) {
            return -1;
        }
        if (!zip.getEntryInfo(entry, NULL, NULL, NULL, NULL, NULL, (long*)crc)) {
            return -1;
        }
        // cache successful lookups?
        return 0;
    }

    bool is_idmap_stale_fd(const char* orig_apk_path, const char* skin_apk_path, int idmap_fd)
    {
        static const size_t N = ResTable::IDMAP_HEADER_SIZE_BYTES;
        struct stat st;
        if (TEMP_FAILURE_RETRY(fstat(idmap_fd, &st)) == -1) {
            return true;
        }
        if (st.st_size < N) {
            // file is empty or corrupt
            return true;
        }

        char buf[N];
        ssize_t bytesLeft = N;
        if (TEMP_FAILURE_RETRY(lseek(idmap_fd, SEEK_SET, 0)) < 0) {
            return -1;
        }
        for (;;) {
            ssize_t r = TEMP_FAILURE_RETRY(read(idmap_fd, buf + N - bytesLeft, bytesLeft));
            if (r < 0) {
                return true;
            }
            bytesLeft -= r;
            if (bytesLeft == 0) {
                break;
            }
            if (r == 0) {
                // "shouldn't happen"
                return -1;
            }
        }

        uint32_t cached_orig_crc, cached_skin_crc;
        if (!ResTable::getIdmapInfo(buf, N, &cached_orig_crc, &cached_skin_crc)) {
            return true;
        }

        uint32_t actual_orig_crc, actual_skin_crc;
        if (get_zip_entry_crc(orig_apk_path, RESOURCES_FILENAME, &actual_orig_crc) == -1) {
            return true;
        }
        if (!get_zip_entry_crc(skin_apk_path, RESOURCES_FILENAME, &actual_skin_crc) == -1) {
            return true;
        }

        return cached_orig_crc != actual_orig_crc || cached_skin_crc != actual_skin_crc;
    }

    bool is_idmap_stale_path(const char* orig_apk_path, const char* skin_apk_path,
            const char* idmap_path)
    {
        struct stat st;
        if (TEMP_FAILURE_RETRY(stat(idmap_path, &st)) == -1) {
            // non-existing idmap is always stale; on other errors, abort idmap generation
            return errno == ENOENT;
        }

        int idmap_fd = TEMP_FAILURE_RETRY(open(idmap_path, O_RDONLY));
        if (idmap_fd == -1) {
            return false;
        }
        bool is_stale = is_idmap_stale_fd(orig_apk_path, skin_apk_path, idmap_fd);
        TEMP_FAILURE_RETRY(close(idmap_fd));
        return is_stale;
    }

    int create_idmap(const char* orig_apk_path, const char* skin_apk_path,
            uint32_t** data, size_t* size)
    {
        uint32_t orig_crc, skin_crc;
        if (get_zip_entry_crc(orig_apk_path, RESOURCES_FILENAME, &orig_crc) == -1) {
            return -1;
        }
        if (get_zip_entry_crc(skin_apk_path, RESOURCES_FILENAME, &skin_crc) == -1) {
            return -1;
        }

        // Assets are only accessible via AssetManager, so offload logic to the latter
        AssetManager am;
        bool b = am.createIdmap(orig_apk_path, skin_apk_path, orig_crc, skin_crc, data, size);
        return b ? 0 : -1;
    }

    int write_idmap_fd(int idmap_fd, const uint32_t* data, size_t size)
    {
        if (TEMP_FAILURE_RETRY(lseek(idmap_fd, SEEK_SET, 0)) < 0) {
            return -1;
        }
        size_t bytesLeft = size;
        while (bytesLeft > 0) {
            size_t w = TEMP_FAILURE_RETRY(write(idmap_fd, data + size - bytesLeft, bytesLeft));
            bytesLeft -= w;
            if (w == 0) {
                // "shouldn't happen"
                return -1;
            }
        }
        return 0;
    }

    int write_idmap_path(const char* idmap_path, const uint32_t* data, size_t size)
    {
        // this is identical to write_idmap_fd, except we need to open the output file ourselves
        int idmap_fd = -1;

        idmap_fd = TEMP_FAILURE_RETRY(open(idmap_path, O_WRONLY | O_CREAT | O_TRUNC, 0644));
        if (idmap_fd == -1) {
            goto fail;
        }
        if (TEMP_FAILURE_RETRY(flock(idmap_fd, LOCK_EX | LOCK_NB)) != 0) {
            goto fail;
        }
        if (write_idmap_fd(idmap_fd, data, size) == -1) {
            goto fail;
        }
        TEMP_FAILURE_RETRY(close(idmap_fd));
        return 0;
fail:
        if (idmap_fd >= 0) {
            TEMP_FAILURE_RETRY(close(idmap_fd));
            TEMP_FAILURE_RETRY(unlink(idmap_path));
        }
        return -1;
    }
}

/*
 * Expected arguments:
 *   idmap --path orig_apk_path skin_apk_path idmap_path
 *   idmap --fd   orig_apk_path skin_apk_path idmap_fd
 * where
 *   idmap         : binary name
 *   orig_apk_path : path, original apk
 *   skin_apk_path : path, skin apk
 *   idmap_path    : path, idmap output
 *   idmap_fd      : file descriptor, idmap output; opened read-write, locked with flock
 */
int main(int argc, char* argv[])
{
    const char* mode, *orig_apk_path, *skin_apk_path;
    uint32_t* data = NULL;
    size_t size;

    if (argc != 5) {
        goto usage;
    }
    mode = argv[1];
    if (strcmp(mode, "--path") != 0 && strcmp(mode, "--fd") != 0) {
        goto usage;
    }
    orig_apk_path = argv[2];
    if (verify_apk_readable(orig_apk_path) == -1) {
        fprintf(stderr, "Error: failed to read original apk %s\n", orig_apk_path);
        goto fail;
    }
    skin_apk_path = argv[3];
    if (verify_apk_readable(skin_apk_path) == -1) {
        fprintf(stderr, "Error: failed to read skin apk %s\n", orig_apk_path);
        goto fail;
    }

    if (!strcmp(mode, "--path")) {
        const char* idmap_path = argv[4];
        if (is_idmap_stale_path(orig_apk_path, skin_apk_path, idmap_path)) {
            if (create_idmap(orig_apk_path, skin_apk_path, &data, &size) == -1) {
                fprintf(stderr, "Error: failed to create idmap file %s\n", idmap_path);
                goto fail;
            }
            if (write_idmap_path(idmap_path, data, size) == -1) {
                fprintf(stderr, "Error: failed to write idmap file %s\n", idmap_path);
                goto fail;
            }
        } else {
            printf("Nothing to do, idmap already up to date.\n");
        }
    } else if (!strcmp(mode, "--fd")) {
        char* endptr;
        int idmap_fd = strtol(argv[4], &endptr, 10);
        if (*endptr != '\0') {
            fprintf(stderr, "Error: failed to read file descriptor argument %s\n", argv[4]);
            goto fail;
        }
        if (is_idmap_stale_fd(orig_apk_path, skin_apk_path, idmap_fd)) {
            if (create_idmap(orig_apk_path, skin_apk_path, &data, &size) == -1) {
                fprintf(stderr, "Error: failed to create idmap\n");
                goto fail;
            }
            if (write_idmap_fd(idmap_fd, data, size) == -1) {
                fprintf(stderr, "Error: failed to write idmap\n");
                goto fail;
            }
        } else {
            printf("Nothing to do, idmap already up to date.\n");
        }
    }

    free(data);
    return 0;
usage:
    fprintf(stderr, "Usage: don't use this (cf dexopt usage).\n");
    return 1;
fail:
    free(data);
    return 1;
}
