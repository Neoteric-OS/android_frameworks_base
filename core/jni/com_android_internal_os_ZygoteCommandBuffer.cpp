/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "com_android_internal_os_Zygote.h"

#include <android-base/logging.h>
#include <async_safe/log.h>
#include <cctype>
#include <chrono>
#include <core_jni_helpers.h>
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <nativehelper/JNIHelp.h>
#include <optional>
#include <poll.h>
#include <unistd.h>
#include <utility>
#include <utils/misc.h>
#include <sys/mman.h>
#include <vector>

#pragma GCC diagnostic ignored "-Wunused-function"  // ??? FIX ME

/* Functions in the callchain during the fork shall not be protected with
   Armv8.3-A Pointer Authentication, otherwise child will not be able to return. */
#ifdef __ARM_FEATURE_PAC_DEFAULT
#ifdef __ARM_FEATURE_BTI_DEFAULT
#define NO_PAC_FUNC __attribute__((target("branch-protection=bti")))
#else
#define NO_PAC_FUNC __attribute__((target("branch-protection=none")))
#endif /* __ARM_FEATURE_BTI_DEFAULT */
#else /* !__ARM_FEATURE_PAC_DEFAULT */
#define NO_PAC_FUNC
#endif /* __ARM_FEATURE_PAC_DEFAULT */

namespace android {

// WARNING: Knows a little about the wire protocol used to communicate with Zygote.
// TODO: Fix error handling.

constexpr int MAX_COMMAND_BYTES = 8100;

// A buffer optionally bundled with a file descriptor from which we can fill it.
// Does not own the file descriptor; destroying a NativeCommandBuffer does not
// close the descriptor.
struct NativeCommandBuffer {
  char buffer[MAX_COMMAND_BYTES];
  uint32_t end;  // Index of first empty byte in the buffer.
  uint32_t next;  // Index of first character past last line returned by readLine.
  int fd;  // Open file descriptor from which we can read more. -1 if none.

  NativeCommandBuffer(int source_fd): end(0), next(0), fd(source_fd) {}

  // Read next line from fd, filling buffer from file descriptor, as needed.
  // Return a pair of pointers pointing to the first character, and one past the
  // end of the line, i.e. at the newline. Returns nothing on failure.
  std::optional<std::pair<char*, char *>> readLine() {
    char* result = buffer + next;
    for (;;) {
      if (next == end) {
        if (end == MAX_COMMAND_BYTES) {
          return {};
        }
        if (fd == -1) {
          ALOGE("ZygoteCommandBuffer.readLine attempted to read from fd -1");
        }
        ssize_t nread = read(fd, buffer + end, MAX_COMMAND_BYTES - end);
ALOGW("Attempted to readLine(), got result %zd, next = %u, fd = %d", nread, next, fd);
        if (nread <= 0) {
ALOGW("Attempted to readLine(), got result %zd, errno = %d", nread, errno);
          if (nread == 0 || errno != EAGAIN) {
            return {};
          }
          continue;
        }
        end += nread;
      }
      while (next < end) {
        // UTF-8 does not allow newline to occur as part of a multibyte character.
        if (buffer[next++] == '\n') {
ALOGW("readLine left next at %u, end = %u, length = %d", next, end, (int)(buffer + next - result - 1));
          return std::make_pair(result, buffer + next - 1);
        }
      }
    }
  }

  void reset() {
    next = 0;
  }

  void clear() {
    // Don't bother to actually clear the buffer; it'll be unmapped in the child anyway.
    reset();
    end = 0;
  }

  // Insert line into the buffer. Checks that the buffer is not associated with an fd.
  // Implicitly adds newline separators. Allows buffer contents to be explicitly set.
  void insert(const char* line, size_t lineLen) {
    CHECK(fd == -1);
    CHECK(end + lineLen < MAX_COMMAND_BYTES);
ALOGW("Insert: lineLen = %zu, length(line) = %zu", lineLen, strlen(line));
    strncpy(buffer + end, line, lineLen);
    buffer[end + lineLen] = '\n';
    end += lineLen + 1;
ALOGW("Inserted %s, end = %u", line, end);
  }

  // Clear buffer, start reading new command, return the number of arguments, leaving buffer
  // positioned at the beginning of first argument. Return 0 on EOF, -1 on error.
  // If there is no associated fd, just reset the current reading position.
  int clearAndGetCount() {
    if (fd == -1) {
      reset();
    } else {
      clear();
    }
    auto line = readLine();
    if (!line.has_value()) {
      return 0;
    }
    char *countString = line.value().first;  // Newline terminated.
    long nArgs = atol(countString);
    if (nArgs <= 0 || nArgs >= MAX_COMMAND_BYTES / 2) {
      return -1;
    }
    return static_cast<int>(nArgs);
  }

  // Is the buffer a simple fork command? Moves current position.
  bool isSimpleForkCommand() {
    int remaining_args = clearAndGetCount();
    if (remaining_args <= 0) {
      return false;
    }
    static const char * RUNTIME_ARGS = "--runtime-args";
    static const char * INVOKE_WITH = "--invoke-with";
    static int ra_length = strlen(RUNTIME_ARGS);
    static int iw_length = strlen(INVOKE_WITH);
    auto read_result = readLine();
    if (!read_result.has_value()) {
      return false;
    }
    auto [arg1_start, arg1_end] = read_result.value();
    --remaining_args;
    if (arg1_end - arg1_start != ra_length
        || strncmp(arg1_start, RUNTIME_ARGS, ra_length) != 0) {
      return false;
    }
    for (; remaining_args > 0; --remaining_args) {
      auto read_result = readLine();
      if (!read_result.has_value()) {
        return false;
      }
      auto [arg_start, arg_end] = read_result.value();
      if (arg_end - arg_start == iw_length
          && strncmp(arg1_start, INVOKE_WITH, iw_length) == 0) {
        return false;
      }
    }
    return true;
  }

};

// Return a big-endian int read from fd, if we get one within the specified timeout.
static std::optional<int> readIntWithTimeout(int fd, int timeout_millis) {
  auto start = std::chrono::steady_clock::now();
  auto finish_time = start + std::chrono::milliseconds(10);
  struct pollfd fd_struct;
  fd_struct.fd = fd;
  fd_struct.events = POLLIN;
  unsigned char buf[4];
  static_assert(sizeof(int32_t) == sizeof buf);
  int chars_read = 0;
  do {
    auto remaining_time = finish_time - std::chrono::steady_clock::now();
    auto remaining_ms = std::chrono::duration_cast<std::chrono::milliseconds>(remaining_time).count();
    int remaining_millis = static_cast<int>(remaining_ms);
    if (remaining_millis <= 0) {
      return {};
    }
    int res = poll(&fd_struct, 1, static_cast<int>(remaining_millis));
    if (res == 1 && (fd_struct.revents & POLLIN)) {
      int rres = read(fd, buf + chars_read, sizeof(int32_t) - chars_read);
      if (rres <= 0) {
        return {};
      }
      chars_read += rres;
    }
  } while (chars_read < sizeof(int32_t));
  return (buf[0] << 24) + (buf[1] << 16) + (buf[2] << 8) + buf[3];
}

// Fork a child process, returning the pid in the parent and 0 in the child.  Arguments are passed
// in a NativeCommandBuffer; results are returned via a pipe
NO_PAC_FUNC
static pid_t forkFromBuffer(JNIEnv* env,
                            NativeCommandBuffer buf,
                            int read_pipe_fd,
                            std::vector<int> fds_to_close,
                            std::vector<int> fds_to_ignore,
                            bool purge) {

# if 0
  if (UNLIKELY(fds_to_close == nullptr ??? No longer pointer)) {
    ZygoteFailure(env, "zygote", nice_name???, "Zygote received a null fds_to_close vector.");
  }

  std::vector<int> usap_pipes = MakeUsapPipeReadFDVector();

  fds_to_close.insert(fds_to_close.end(), usap_pipes.begin(), usap_pipes.end());
  fds_to_ignore.insert(fds_to_ignore.end(), usap_pipes.begin(), usap_pipes.end());

  fds_to_close.push_back(gUsapPoolSocketFD);

  if (gUsapPoolEventFD != -1) {
    fds_to_close.push_back(gUsapPoolEventFD);
    fds_to_ignore.push_back(gUsapPoolEventFD);
  }

  if (gSystemServerSocketFd != -1) {
      fds_to_close.push_back(gSystemServerSocketFd);
      fds_to_ignore.push_back(gSystemServerSocketFd);
  }
# endif

  return zygote::ForkCommon(env, /*is_system_server=*/ false, fds_to_close, fds_to_ignore,
                            /*is_priority_fork=*/ true, purge);
}

static int buffersAllocd(0);

// Get a new NativeCommandBuffer. Can only be called once between freeNativeBuffer calls,
// so that only one buffer exists at a time..
jlong com_android_internal_os_ZygoteCommandBuffer_getNativeBuffer(JNIEnv* env, jclass, jint fd) {
  CHECK(buffersAllocd == 0);
  ++buffersAllocd;
  // MMap explicitly to get it page aligned.
  void *bufferMem = mmap(NULL, sizeof(NativeCommandBuffer), PROT_READ | PROT_WRITE,
                         MAP_ANONYMOUS | MAP_PRIVATE | MAP_POPULATE, -1, 0);
  CHECK(bufferMem != MAP_FAILED);
  return (jlong) new(bufferMem) NativeCommandBuffer(fd);
}

// Delete native command buffer.
void com_android_internal_os_ZygoteCommandBuffer_freeNativeBuffer(JNIEnv* env, jclass,
                                                                  jlong jbuffer) {
  CHECK(buffersAllocd == 1);
  NativeCommandBuffer* nbuffer = reinterpret_cast<NativeCommandBuffer*>(jbuffer);
  nbuffer->~NativeCommandBuffer();
  munmap(nbuffer, sizeof(NativeCommandBuffer));
  --buffersAllocd;
}

// Clear the buffer, read the line containing the count, and return the count.
jint com_android_internal_os_ZygoteCommandBuffer_clearAndRetrieveCount(JNIEnv* env, jclass,
                                                                       jlong jbuffer) {
  NativeCommandBuffer* nbuffer = reinterpret_cast<NativeCommandBuffer*>(jbuffer);
  int result = nbuffer->clearAndGetCount();
  if (result == -1) {
    ALOGE("Failed to retrieve count; throwing exception");
    jniThrowException(env, "java/lang/EOFException", "Couldn't read argument count");
  }
  return result;
}

// Explicitly insert a string as the last line (argument) of the buffer.
void com_android_internal_os_ZygoteCommandBuffer_insert(JNIEnv* env, jclass, jlong jbuffer,
                                                        jstring line) {
  NativeCommandBuffer* nbuffer = reinterpret_cast<NativeCommandBuffer*>(jbuffer);
  size_t lineLen = static_cast<size_t>(env->GetStringUTFLength(line));
  const char* cstring = env->GetStringUTFChars(line, NULL);
  nbuffer->insert(cstring, lineLen);
  env->ReleaseStringUTFChars(line, cstring);
}

// Read a line from the buffer, refilling as necessary.
jstring com_android_internal_os_ZygoteCommandBuffer_nextLine(JNIEnv* env, jclass,
                                                             jlong jbuffer) {
  NativeCommandBuffer* nbuffer = reinterpret_cast<NativeCommandBuffer*>(jbuffer);
  auto line = nbuffer->readLine();
  if (!line.has_value()) {
    ALOGE("Failed to retrieve line; throwing exception");
    jniThrowException(env, "java/lang/EOFException", "Incomplete zygote command");
    return (jstring) 0;
  }
  auto [cresult, endp] = line.value();
  // OK to temporarily clobber the buffer, since this is not thread safe, and we're modifying
  // the buffer anyway.
  *endp = '\0';
  jstring result = env->NewStringUTF(cresult);
ALOGW("nextLine returned %s", cresult);
  *endp = '\n';
  return result;
}

// Fork a child as specified by the current command buffer, and refill the command
// buffer from the given socket. So long as the result is another simple fork command,
// repeat this process.
// The initial buffer should be partially or entirely filled; we reset() before
// scanning it. When we return, the buffer contains the command we couldn't
// handle, and has been reset().
// We return -1 in the parent when we see a command we didn't understand,
// and we return a writable pipe file descriptor in each child we fork.
// ??? WIP
NO_PAC_FUNC
jint com_android_internal_os_ZygoteCommandBuffer_forkMany(
            JNIEnv* env,
            jclass,
            jlong jbuffer,
            jintArray managed_fds_to_close,
            jintArray managed_fds_to_ignore) {
CHECK(false); // Incomplee code for now.
#if 0
  NativeCommandBuffer* buffer = reinterpret_cast<NativeCommandBuffer*>(jbuffer);
  int nArgs = buffer->clearAndGetCount();
  std::vector<int> fds_to_close =
      ExtractJIntArray(env, "zygote", nice_name???, managed_fds_to_close).value();
  std::vector<int> fds_to_ignore =
      ExtractJIntArray(env, "zygote", nice_name???, managed_fds_to_ignore).value();
  bool first_time = true;
  bool is_fork_command;
  do {
    if (nArgs <= 0 || nArgs >= MAX_COMMAND_BYTES / 2) {
      buffer->reset();
      CHECK(false);
    }
    int pipe_fds[2];
    // ??? handle --invoke-with ??
    CHECK(pipe2(pipe_fds, O_CLOEXEC) == 0);
    int pid = forkFromBuffer(env, buffer, pipe_fd, fds_to_close, fds_to_ignore,
                             /*purge=*/ first_time);
    if (pid == 0) {
      // ??? close read end????
      return pipe_fds[1];  // Write end.
    }
    // ??? close write end??
    pid_t inner_pid = readIntWithTimeout(pipe_fds[0]);
    // ???? We have to reply to system server with (int)pid and wrapped boolean
    buffer->clear();
  } while (buffer->isSimpleForkCommand());
  buffer->reset();
#endif
  return -1;
}

#define METHOD_NAME(m) com_android_internal_os_ZygoteCommandBuffer_ ## m

static const JNINativeMethod gMethods[] = {
        {"getNativeBuffer", "(I)J", (void *) METHOD_NAME(getNativeBuffer)},
        {"freeNativeBuffer", "(J)V", (void *) METHOD_NAME(freeNativeBuffer)},
        {"insert", "(JLjava/lang/String;)V", (void *) METHOD_NAME(insert)},
        {"nextLine", "(J)Ljava/lang/String;", (void *) METHOD_NAME(nextLine)},
        {"clearAndRetrieveCount", "(J)I", (void *) METHOD_NAME(clearAndRetrieveCount)},
        {"forkMany", "(J[I[I)I", (void *) METHOD_NAME(forkMany)},
};

int register_com_android_internal_os_ZygoteCommandBuffer(JNIEnv* env) {
  return RegisterMethodsOrDie(env, "com/android/internal/os/ZygoteCommandBuffer", gMethods,
                              NELEM(gMethods));
}

}  // namespace android
