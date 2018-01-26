#include <stdio.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

// TODO: remove once we get rid of system() call
#include <stdlib.h>

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


bool path_exists(const char* path)
{
  struct stat sb;
  return stat(path, &sb) == 0;
}

bool patchoat()
{
  pid_t pid = fork();
  if (pid == -1)
  {
    PLOG(ERROR) << "Failed to fork";
    return false;
  }
  else if (pid == 0)
  {
    char* input_option;
    asprintf(&input_option, "--input-image-location=%s", kInputLocation);
    char* output_option;
    asprintf(&output_option, "--output-image-file=%s/bobloblaw.art", kCacheDir);
    execl(kPatchoatPath, kPatchoatPath, "--verify", input_option, output_option,
          "--instruction-set=" RUNTIME_ISA, nullptr);

    PLOG(ERROR) << "Failed to execv patchoat";
    return false;
  }
  else
  {
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

int main(int argc, char* argv[])
{
  if (argc < 2)
  {
    PLOG(ERROR) << "Missing operand";
    PLOG(ERROR) << "Usage: prezygote COMMAND [ARGS]";
    return 1;
  }

  // We only need to verify if the cache directory exists
  // TODO: change to std::filesystem::exists() when it's supported
  if (path_exists(kCacheDir))
  {
    if (!patchoat())
    {
      // TODO: change to std::filesystem::remove_all() when it's supported
      char* rm_cmd;
      asprintf(&rm_cmd, "rm -rf %s", kCacheDir);
      system(rm_cmd);
    }
  }
  // Shift arguments so that we call the program that was passed in as arguments
  argv++;
  execv(argv[0], argv);
  PLOG(ERROR) << "Failed to execv supplied command";
  return 1;
}
