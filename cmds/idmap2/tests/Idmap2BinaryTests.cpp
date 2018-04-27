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

/*
 * The tests in this file operate on a higher level than the tests in the other
 * files. Here, all tests execute the idmap2 binary and only depend on
 * libidmap2 to verify the output of idmap2.
 */
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#include <cerrno>
#include <cstdlib>
#include <cstring>  // strerror
#include <fstream>
#include <memory>
#include <sstream>
#include <string>
#include <vector>

#include "gmock/gmock.h"
#include "gtest/gtest.h"

#include "idmap2/FileUtils.h"
#include "idmap2/Idmap.h"

#include "TestHelpers.h"

using ::testing::NotNull;

namespace android {
namespace idmap2 {

class Idmap2BinaryTests : public Idmap2Tests {};

struct ProcResult {
  int status;
  std::string stdout;
  std::string stderr;
};

static void ExecuteIdmap2(const std::vector<std::string>& args, struct ProcResult* result) {
  assert(result != nullptr);

  int stdout[2];  // stdout[0] read, stdout[1] write
  if (pipe(stdout) != 0) {
    FAIL() << "Failed to pipe: " << strerror(errno);
    result->status = EXIT_FAILURE;
    return;
  }

  int stderr[2];  // stdout[0] read, stdout[1] write
  if (pipe(stderr) != 0) {
    close(stdout[0]);
    close(stdout[1]);
    FAIL() << "Failed to pipe: " << strerror(errno);
    result->status = EXIT_FAILURE;
    return;
  }

  char const** argv = (char const**)malloc(sizeof(char*) * (args.size() + 2));
  argv[0] = "idmap2";
  for (size_t i = 0; i < args.size(); i++) {
    argv[i + 1] = args[i].c_str();
  }
  argv[args.size() + 1] = nullptr;
  switch (fork()) {
    case -1:
      FAIL() << "Failed to fork: " << strerror(errno);
      result->status = EXIT_FAILURE;
      break;
    case 0: {
      close(stdout[0]);
      if (dup2(stdout[1], STDOUT_FILENO) == -1) {
        abort();
      }
      close(stderr[0]);
      if (dup2(stderr[1], STDERR_FILENO) == -1) {
        abort();
      }
      execvp("idmap2", const_cast<char* const*>(argv));
      FAIL() << "Failed to execvp: " << strerror(errno);
      abort();
    } break;
    default:
      close(stdout[1]);
      close(stderr[1]);
      wait(&result->status);
      std::unique_ptr<std::string> out = utils::ReadFile(stdout[0]);
      result->stdout = out ? *out : "";
      std::unique_ptr<std::string> err = utils::ReadFile(stderr[0]);
      result->stderr = err ? *err : "";
      close(stdout[0]);
      close(stderr[0]);
      break;
  }
  free(argv);
}

static void AssertIdmap(const Idmap& idmap, const std::string& target_apk_path,
                        const std::string& overlay_apk_path) {
  // checked multiple times in this file) briefly check that the idmap file looks reasonable
  // (IdmapTests is responsible for more in-depth verification)
  ASSERT_EQ(idmap.GetHeader()->GetMagic(), 0x504d4449u);
  ASSERT_EQ(idmap.GetHeader()->GetTargetPath(), target_apk_path);
  ASSERT_EQ(idmap.GetHeader()->GetOverlayPath(), overlay_apk_path);
  ASSERT_EQ(idmap.GetData().size(), 1u);
}

#define ASSERT_IDMAP(idmap_ref, target_apk_path, overlay_apk_path)                      \
  do {                                                                                  \
    ASSERT_NO_FATAL_FAILURE(AssertIdmap(idmap_ref, target_apk_path, overlay_apk_path)); \
  } while (0)

TEST_F(Idmap2BinaryTests, CreateIdmapPath) {
  struct ProcResult result;
  // clang-format off
  ExecuteIdmap2({"create",
                  "--target-apk-path", GetTargetApkPath(),
                  "--overlay-apk-path", GetOverlayApkPath(),
                  "--idmap-path", GetIdmapPath()},
                &result);
  // clang-format on
  ASSERT_EQ(result.status, EXIT_SUCCESS) << result.stderr;

  struct stat st;
  ASSERT_EQ(stat(GetIdmapPath().c_str(), &st), 0);

  std::stringstream error;
  std::ifstream fin(GetIdmapPath());
  std::unique_ptr<const Idmap> idmap = Idmap::FromBinaryStream(fin, error);
  fin.close();

  ASSERT_THAT(idmap, NotNull());
  ASSERT_IDMAP(*idmap, GetTargetApkPath(), GetOverlayApkPath());

  unlink(GetIdmapPath().c_str());
}

TEST_F(Idmap2BinaryTests, Dump) {
  struct ProcResult result;
  // clang-format off
  ExecuteIdmap2({"create",
                  "--target-apk-path", GetTargetApkPath(),
                  "--overlay-apk-path", GetOverlayApkPath(),
                  "--idmap-path", GetIdmapPath()},
                &result);
  // clang-format on
  ASSERT_EQ(result.status, EXIT_SUCCESS) << result.stderr;

  ExecuteIdmap2({"dump", "--idmap-path", GetIdmapPath()}, &result);
  ASSERT_EQ(result.status, EXIT_SUCCESS) << result.stderr;
  ASSERT_NE(result.stdout.find("0x7f010000 -> 0x7f010000 integer/int1"), std::string::npos);
  ASSERT_NE(result.stdout.find("0x7f020003 -> 0x7f020000 string/str1"), std::string::npos);
  ASSERT_NE(result.stdout.find("0x7f020005 -> 0x7f020001 string/str3"), std::string::npos);
  ASSERT_EQ(result.stdout.find("00000210:     007f  target package id"), std::string::npos);

  ExecuteIdmap2({"dump", "--verbose", "--idmap-path", GetIdmapPath()}, &result);
  ASSERT_EQ(result.status, EXIT_SUCCESS) << result.stderr;
  ASSERT_NE(result.stdout.find("00000000: 504d4449  magic"), std::string::npos);
  ASSERT_NE(result.stdout.find("00000210:     007f  target package id"), std::string::npos);

  ExecuteIdmap2({"dump", "--verbose", "--idmap-path", GetTestDataPath() + "/DOES-NOT-EXIST"},
                &result);
  ASSERT_NE(result.status, EXIT_SUCCESS);

  unlink(GetIdmapPath().c_str());
}

TEST_F(Idmap2BinaryTests, Scan) {
  const std::string overlay_static_1_apk_path = GetTestDataPath() + "/overlay/overlay-static-1.apk";
  const std::string overlay_static_2_apk_path = GetTestDataPath() + "/overlay/overlay-static-2.apk";
  const std::string idmap_static_1_path =
      Idmap::CanonicalIdmapPathFor(GetTempDirPath(), overlay_static_1_apk_path);
  const std::string idmap_static_2_path =
      Idmap::CanonicalIdmapPathFor(GetTempDirPath(), overlay_static_2_apk_path);

  // single input directory, recursive
  struct ProcResult result;
  // clang-format off
  ExecuteIdmap2({"scan",
                  "--input-directory", GetTestDataPath(),
                  "--recursive",
                  "--target-package-name", "test.target",
                  "--target-package-path", GetTargetApkPath(),
                  "--output-directory", GetTempDirPath()},
                &result);
  // clang-format on
  ASSERT_EQ(result.status, EXIT_SUCCESS) << result.stderr;
  std::stringstream expected;
  expected << idmap_static_1_path << std::endl;
  expected << idmap_static_2_path << std::endl;
  ASSERT_EQ(result.stdout, expected.str());

  std::stringstream error;
  auto idmap_static_1_raw_string = utils::ReadFile(idmap_static_1_path);
  auto idmap_static_1_raw_stream = std::istringstream(*idmap_static_1_raw_string);
  auto idmap_static_1 = Idmap::FromBinaryStream(idmap_static_1_raw_stream, error);
  ASSERT_THAT(idmap_static_1, NotNull());
  ASSERT_IDMAP(*idmap_static_1, GetTargetApkPath(), overlay_static_1_apk_path);

  auto idmap_static_2_raw_string = utils::ReadFile(idmap_static_2_path);
  auto idmap_static_2_raw_stream = std::istringstream(*idmap_static_2_raw_string);
  auto idmap_static_2 = Idmap::FromBinaryStream(idmap_static_2_raw_stream, error);
  ASSERT_THAT(idmap_static_2, NotNull());
  ASSERT_IDMAP(*idmap_static_2, GetTargetApkPath(), overlay_static_2_apk_path);

  unlink(idmap_static_2_path.c_str());
  unlink(idmap_static_1_path.c_str());

  // multiple input directories, non-recursive
  // clang-format off
  ExecuteIdmap2({"scan",
                  "--input-directory", GetTestDataPath() + "/target",
                  "--input-directory", GetTestDataPath() + "/overlay",
                  "--target-package-name", "test.target",
                  "--target-package-path", GetTargetApkPath(),
                  "--output-directory", GetTempDirPath()},
                &result);
  // clang-format on
  ASSERT_EQ(result.status, EXIT_SUCCESS) << result.stderr;
  ASSERT_EQ(result.stdout, expected.str());
  unlink(idmap_static_2_path.c_str());
  unlink(idmap_static_1_path.c_str());

  // the same input directory given twice, but no duplicate entries
  // clang-format off
  ExecuteIdmap2({"scan",
                  "--input-directory", GetTestDataPath(),
                  "--input-directory", GetTestDataPath(),
                  "--recursive",
                  "--target-package-name", "test.target",
                  "--target-package-path", GetTargetApkPath(),
                  "--output-directory", GetTempDirPath()},
                &result);
  // clang-format on
  ASSERT_EQ(result.status, EXIT_SUCCESS) << result.stderr;
  ASSERT_EQ(result.stdout, expected.str());
  unlink(idmap_static_2_path.c_str());
  unlink(idmap_static_1_path.c_str());

  // no APKs in input-directory: ok, but no output
  // clang-format off
  ExecuteIdmap2({"scan",
                  "--input-directory", GetTempDirPath(),
                  "--target-package-name", "test.target",
                  "--target-package-path", GetTargetApkPath(),
                  "--output-directory", GetTempDirPath()},
                &result);
  // clang-format on
  ASSERT_EQ(result.status, EXIT_SUCCESS) << result.stderr;
  ASSERT_EQ(result.stdout, "");
}

TEST_F(Idmap2BinaryTests, Lookup) {
  struct ProcResult result;
  // clang-format off
  ExecuteIdmap2({"create",
                  "--target-apk-path", GetTargetApkPath(),
                  "--overlay-apk-path", GetOverlayApkPath(),
                  "--idmap-path", GetIdmapPath()},
                &result);
  // clang-format on
  ASSERT_EQ(result.status, EXIT_SUCCESS) << result.stderr;

  // clang-format off
  ExecuteIdmap2({"lookup",
                  "--idmap-path", GetIdmapPath(),
                  "--config", "",
                  "--resid", "0x7f020003"},  // string/str1
                &result);
  // clang-format on
  ASSERT_EQ(result.status, EXIT_SUCCESS) << result.stderr;
  ASSERT_NE(result.stdout.find("overlay-1"), std::string::npos);
  ASSERT_EQ(result.stdout.find("overlay-1-sv"), std::string::npos);

  // clang-format off
  ExecuteIdmap2({"lookup",
                  "--idmap-path", GetIdmapPath(),
                  "--config", "",
                  "--resid", "test.target:string/str1"},
                &result);
  // clang-format on
  ASSERT_EQ(result.status, EXIT_SUCCESS) << result.stderr;
  ASSERT_NE(result.stdout.find("overlay-1"), std::string::npos);
  ASSERT_EQ(result.stdout.find("overlay-1-sv"), std::string::npos);

  // clang-format off
  ExecuteIdmap2({"lookup",
                  "--idmap-path", GetIdmapPath(),
                  "--config", "sv",
                  "--resid", "test.target:string/str1"},
                &result);
  // clang-format on
  ASSERT_EQ(result.status, EXIT_SUCCESS) << result.stderr;
  ASSERT_NE(result.stdout.find("overlay-1-sv"), std::string::npos);

  unlink(GetIdmapPath().c_str());
}

TEST_F(Idmap2BinaryTests, InvalidCommandLineOptions) {
  const std::string invalid_target_apk_path = GetTestDataPath() + "/DOES-NOT-EXIST";
  struct ProcResult result;

  // missing mandatory options
  ExecuteIdmap2({"create"}, &result);
  ASSERT_NE(result.status, EXIT_SUCCESS);

  // missing argument to option
  // clang-format off
  ExecuteIdmap2({"create",
                  "--target-apk-path", GetTargetApkPath(),
                  "--overlay-apk-path", GetOverlayApkPath(),
                  "--idmap-path"},
                &result);
  // clang-format on
  ASSERT_NE(result.status, EXIT_SUCCESS);

  // invalid target apk path
  // clang-format off
  ExecuteIdmap2({"create",
                  "--target-apk-path", invalid_target_apk_path,
                  "--overlay-apk-path", GetOverlayApkPath(),
                  "--idmap-path", GetIdmapPath()},
                &result);
  // clang-format on
  ASSERT_NE(result.status, EXIT_SUCCESS);
}

}  // namespace idmap2
}  // namespace android
