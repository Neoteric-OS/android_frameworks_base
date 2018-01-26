/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <ftw.h>
#include <stdio.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

#include <android-base/logging.h>

// This program calls patchoat to verify the cache files in
// /data/dalvik-cache/<ISA>. It then executes the executable passed to it as
// cmdline parameters (1st parameter is the path to the executable, 2nd
// parameter is the first argument to the executable, ...).

#if defined(__arm__)
#define RUNTIME_ISA "arm"
#elif defined(__aarch64__)
#define RUNTIME_ISA "arm64"
#elif defined(__mips__) && !defined(__LP64__)
#define RUNTIME_ISA "mips"
#elif defined(__mips__) && defined(__LP64__)
#define RUNTIME_ISA "mips64"
#elif defined(__i386__)
#define RUNTIME_ISA "x86"
#elif defined(__x86_64__)
#define RUNTIME_ISA "x86_64"
#else
#error "Unknown instruction set"
#endif

static constexpr const char* kInputLocation = "/system/framework/boot.art";
static constexpr const char* kPatchoatPath = "/system/bin/patchoat";
static constexpr const char* kCacheDir = "/data/dalvik-cache/" RUNTIME_ISA;

static int rm_path(const char* fpath, __unused const struct stat* sb,
                   __unused int typeflag, __unused struct FTW* ftwbuf) {
  if (remove(fpath) != 0) {
    PLOG(ERROR) << "Error removing path: " << fpath;
    return -1;
  } else {
    return 0;
  }
}

static bool path_exists(const char* path) {
  struct stat sb;
  return stat(path, &sb) == 0;
}

static bool patchoat_verify() {
  pid_t pid = fork();
  if (pid == -1) {
    PLOG(ERROR) << "Failed to fork";
    return false;
  } else if (pid == 0) {
    char* input_option;
    asprintf(&input_option, "--input-image-location=%s", kInputLocation);
    char* output_option;
    // The filename here doesn't matter. Arbitrarily setting to boot.art.
    asprintf(&output_option, "--output-image-file=%s/boot.art", kCacheDir);

    execl(kPatchoatPath, kPatchoatPath, "--verify", input_option, output_option,
          "--instruction-set=" RUNTIME_ISA, nullptr);

    PLOG(ERROR) << "Failed to execv patchoat";
    return false;
  } else {
    // wait for subprocess to finish
    int status = -1;
    pid_t got_pid = TEMP_FAILURE_RETRY(waitpid(pid, &status, 0));
    if (got_pid != pid) {
      PLOG(ERROR) << "Failed to waitpid";
      return false;
    }
    return WIFEXITED(status) && WEXITSTATUS(status) == EXIT_SUCCESS;
  }
}

int main(int argc, char* argv[]) {
  if (argc < 2) {
    LOG(ERROR) << "Missing operand";
    LOG(ERROR) << "Usage: prezygote COMMAND [ARGS]";
    return 1;
  }

  // We only need to verify if the cache directory exists
  // TODO: change to std::filesystem::exists() when it's supported
  if (path_exists(kCacheDir)) {
    if (!patchoat_verify()) {
      LOG(ERROR) << "Verification failed, deleting untrusted cache...";
      // TODO: change to std::filesystem::remove_all() when it's supported
      constexpr int kNumOpenFd = 10;
      if (nftw(kCacheDir, rm_path, kNumOpenFd, FTW_DEPTH | FTW_PHYS) != 0) {
        PLOG(ERROR) << "Deletion of untrusted cache failed";
        // TODO: Returning here will cause the device to hang. We'll need to
        // consult with the UX team and security team about what the desired
        // behavior in this case would be.
        return 1;
      }
    }
  }
  // Shift arguments so that we call the program that was passed in as arguments
  argv++;
  execv(argv[0], argv);
  PLOG(ERROR) << "Failed to execv supplied command";
  return 1;
}
