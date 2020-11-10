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
#include <sys/types.h>
#include <sys/socket.h>
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
class NativeCommandBuffer {
 public:
  NativeCommandBuffer(int sourceFd): mEnd(0), mNext(0), mLinesLeft(0), mFd(sourceFd) {}

  // Read mNext line from mFd, filling mBuffer from file descriptor, as needed.
  // Return a pair of pointers pointing to the first character, and one past the
  // mEnd of the line, i.e. at the newline. Returns nothing on failure.
  std::optional<std::pair<char*, char *>> readLine() {
    char* result = mBuffer + mNext;
    for (;;) {
      if (mNext == mEnd) {
        if (mEnd == MAX_COMMAND_BYTES) {
          return {};
        }
        if (mFd == -1) {
          ALOGE("ZygoteCommandBuffer.readLine attempted to read from mFd -1");
        }
        ssize_t nread = read(mFd, mBuffer + mEnd, MAX_COMMAND_BYTES - mEnd);
        if (nread <= 0) {
          if (nread == 0 || errno != EAGAIN) {
            return {};
          }
          continue;
        }
        mEnd += nread;
      }
      while (mNext < mEnd) {
        // UTF-8 does not allow newline to occur as part of a multibyte character.
        if (mBuffer[mNext++] == '\n') {
          if (--mLinesLeft < 0) {
            ALOGE("ZygoteCommandBuffer.readLine attempted to read pas mEnd of command");
          }
          return std::make_pair(result, mBuffer + mNext - 1);
        }
      }
    }
  }

  void reset() {
    mNext = 0;
  }

  // Make sure the current command is fully mBuffered, without reading past the current command.
  void readAllLines() {
     while (mLinesLeft > 0) {
       readLine();
    }
  }

  void clear() {
    // Don't bother to actually clear the mBuffer; it'll be unmapped in the child anyway.
    reset();
    mEnd = 0;
  }

  // Insert line into the mBuffer. Checks that the mBuffer is not associated with an mFd.
  // Implicitly adds newline separators. Allows mBuffer contents to be explicitly set.
  void insert(const char* line, size_t lineLen) {
    CHECK(mFd == -1);
    CHECK(mEnd + lineLen < MAX_COMMAND_BYTES);
    strncpy(mBuffer + mEnd, line, lineLen);
    mBuffer[mEnd + lineLen] = '\n';
    mEnd += lineLen + 1;
  }

  // Clear mBuffer, start reading new command, return the number of arguments, leaving mBuffer
  // positioned at the beginning of first argument. Return 0 on EOF, -1 on error.
  int getCount() {
    mLinesLeft = 1;
    auto line = readLine();
    if (!line.has_value()) {
      return 0;
    }
    char *countString = line.value().first;  // Newline terminated.
    long nArgs = atol(countString);
    if (nArgs <= 0 || nArgs >= MAX_COMMAND_BYTES / 2) {
      return -1;
    }
    mLinesLeft = nArgs;
    return static_cast<int>(nArgs);
  }

  // Is the mBuffer a simple fork command?
  // We disallow request to wrap the child process, child zygotes, anything that
  // mentions capabilities or requests uid < minUid.
  // We insist that --setuid and --setgid arguments are explicitly included and that the
  // command starts with --runtime-args.
  // Assumes we are positioned at the beginning of the command after the argument count,
  // and leaves the position at some indeterminate position in the buffer.
  bool isSimpleForkCommand(int minUid) {
    if (mLinesLeft <= 0 || mLinesLeft  >= MAX_COMMAND_BYTES / 2) {
      return false;
    }
    static const char* RUNTIME_ARGS = "--runtime-args";
    static const char* INVOKE_WITH = "--invoke-with";
    static const char* CHILD_ZYGOTE = "--start-child-zygote";
    static const char* SETUID = "--setuid=";
    static const char* SETGID = "--setgid=";
    static const char* CAPABILITIES = "--capabilities";
    static const size_t RA_LENGTH = strlen(RUNTIME_ARGS);
    static const size_t IW_LENGTH = strlen(INVOKE_WITH);
    static const size_t CZ_LENGTH = strlen(CHILD_ZYGOTE);
    static const size_t SU_LENGTH = strlen(SETUID);
    static const size_t SG_LENGTH = strlen(SETGID);
    static const size_t CA_LENGTH = strlen(CAPABILITIES);
    bool saw_setuid = false, saw_setgid = false;
    bool saw_runtime_args = false;

    while (mLinesLeft > 0) {
      auto read_result = readLine();
      --mLinesLeft;
      if (!read_result.has_value()) {
        return false;
      }
      auto [arg_start, arg_end] = read_result.value();
      if (arg_end - arg_start == RA_LENGTH
          && strncmp(arg_start, RUNTIME_ARGS, RA_LENGTH) == 0) {
        saw_runtime_args = true;
        continue;
      }
      if (arg_end - arg_start == IW_LENGTH
          && strncmp(arg_start, INVOKE_WITH, IW_LENGTH) == 0) {
        // This also removes the need for invoke-with security checks here.
        return false;
      }
      if (arg_end - arg_start == CZ_LENGTH
          && strncmp(arg_start, CHILD_ZYGOTE, CZ_LENGTH) == 0) {
        return false;
      }
      if (arg_end - arg_start >= CA_LENGTH
          && strncmp(arg_start, CAPABILITIES, CA_LENGTH) == 0) {
        return false;
      }
      if (arg_end - arg_start >= SU_LENGTH
          && strncmp(arg_start, SETUID, SU_LENGTH) == 0) {
        int uid = digitsVal(arg_start + SU_LENGTH, arg_end);
        if (uid < minUid) {
          return false;
        }
        saw_setuid = true;
        continue;
      }
      if (arg_end - arg_start >= SG_LENGTH
          && strncmp(arg_start, SETGID, SG_LENGTH) == 0) {
        int gid = digitsVal(arg_start + SG_LENGTH, arg_end);
        if (gid == -1) {
          return false;
        }
        saw_setgid = true;
      }
    }
    return saw_runtime_args && saw_setuid && saw_setgid;
  }

  void setFd(int new_fd) {
    mFd = new_fd;
  }

  int getFd() {
    return mFd;
  }

  // Debug only:
  void logState() {
    ALOGD("mbuffer starts with %c%c, mEnd = %u, mNext = %u, mLinesLeft = %d, mFd = %d",
          mBuffer[0], (mBuffer[1] == '\n' ? ' ' : mBuffer[1]),
          static_cast<unsigned>(mEnd), static_cast<unsigned>(mNext),
          static_cast<int>(mLinesLeft), mFd);
  }

 private:
  // Picky version of atoi(). No sign or unexpected characters allowed. Return -1 on failure.
  static int digitsVal(char* start, char* end) {
    int result = 0;
    if (end - start > 6) {
      return -1;
    }
    for (char* dp = start; dp < end; ++dp) {
      if (*dp < '0' || *dp > '9') {
        ALOGW("Argument failed integer format check");
        return -1;
      }
      result = 10 * result + (*dp - '0');
    }
    return result;
  }

  char mBuffer[MAX_COMMAND_BYTES];
  uint32_t mEnd;  // Index of first empty byte in the mBuffer.
  uint32_t mNext;  // Index of first character past last line returned by readLine.
  int32_t mLinesLeft;  // Lines in current command that haven't yet been read.
  int mFd;  // Open file descriptor from which we can read more. -1 if none.
};

static int buffersAllocd(0);

// Get a new NativeCommandBuffer. Can only be called once between freeNativeBuffer calls,
// so that only one buffer exists at a time.
jlong com_android_internal_os_ZygoteCommandBuffer_getNativeBuffer(JNIEnv* env, jclass, jint fd) {
  CHECK(buffersAllocd == 0);
  ++buffersAllocd;
  // MMap explicitly to get it page aligned.
  void *bufferMem = mmap(NULL, sizeof(NativeCommandBuffer), PROT_READ | PROT_WRITE,
                         MAP_ANONYMOUS | MAP_PRIVATE | MAP_POPULATE, -1, 0);
  // Currently we mmap and unmap one for every request handled by the Java code.
  // That could be improved, but unclear it matters.
  CHECK(bufferMem != MAP_FAILED);
  return (jlong) new(bufferMem) NativeCommandBuffer(fd);
}

// Delete native command buffer.
void com_android_internal_os_ZygoteCommandBuffer_freeNativeBuffer(JNIEnv* env, jclass,
                                                                  jlong j_buffer) {
  CHECK(buffersAllocd == 1);
  NativeCommandBuffer* n_buffer = reinterpret_cast<NativeCommandBuffer*>(j_buffer);
  n_buffer->~NativeCommandBuffer();
  munmap(n_buffer, sizeof(NativeCommandBuffer));
  --buffersAllocd;
}

// Clear the buffer, read the line containing the count, and return the count.
jint com_android_internal_os_ZygoteCommandBuffer_retrieveCount(JNIEnv* env, jclass,
                                                                       jlong j_buffer) {
  NativeCommandBuffer* n_buffer = reinterpret_cast<NativeCommandBuffer*>(j_buffer);
  int result = n_buffer->getCount();
  if (result == -1) {
    ALOGE("Failed to retrieve count; throwing exception");
    jniThrowException(env, "java/lang/EOFException", "Couldn't read argument count");
  }
  return result;
}

// Explicitly insert a string as the last line (argument) of the buffer.
void com_android_internal_os_ZygoteCommandBuffer_insert(JNIEnv* env, jclass, jlong j_buffer,
                                                        jstring line) {
  NativeCommandBuffer* n_buffer = reinterpret_cast<NativeCommandBuffer*>(j_buffer);
  size_t lineLen = static_cast<size_t>(env->GetStringUTFLength(line));
  const char* cstring = env->GetStringUTFChars(line, NULL);
  n_buffer->insert(cstring, lineLen);
  env->ReleaseStringUTFChars(line, cstring);
}

// Read a line from the buffer, refilling as necessary.
jstring com_android_internal_os_ZygoteCommandBuffer_nextLine(JNIEnv* env, jclass,
                                                             jlong j_buffer) {
  NativeCommandBuffer* n_buffer = reinterpret_cast<NativeCommandBuffer*>(j_buffer);
  auto line = n_buffer->readLine();
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

// Read all lines from the current command into the buffer, and then reset the buffer, so
// we will start reading again at the beginning of the command, starting with the argument
// count. And we don't need access to the fd to do so.
void com_android_internal_os_ZygoteCommandBuffer_readAllLinesAndReset(JNIEnv* env, jclass,
                                                                      jlong j_buffer) {
  NativeCommandBuffer* n_buffer = reinterpret_cast<NativeCommandBuffer*>(j_buffer);
  n_buffer->readAllLines();
  n_buffer->reset();
}

// Fork a child as specified by the current command buffer, and refill the command
// buffer from the given socket. So long as the result is another simple fork command,
// repeat this process.
// It must contain a fork command, which is currently restricted not to fork another
// zygote or involve a wrapper process.
// The initial buffer should be partially or entirely read; we read it fully and reset it.
// When we return, the buffer contains the command we couldn't handle, and has been reset().
// We return -1 in the parent when we see a command we didn't understand, and thus the
// command in the buffer still needs to be executed.
// We return -2 if, for some reason, we didn't manage to read the next command, but
// the current command has been executed. We return zero in each child.
// We only process fork commands if the peer uid matches expected_uid.
// For every fork command after the first, we check that the requested uid is at
// least minUid.
NO_PAC_FUNC
jint com_android_internal_os_ZygoteCommandBuffer_forkMany(
            JNIEnv* env,
            jclass,
            jlong j_buffer,
            jint zygote_socket_fd,
            jint expected_uid,
            jint minUid) {

  NativeCommandBuffer* n_buffer = reinterpret_cast<NativeCommandBuffer*>(j_buffer);
  int session_socket = n_buffer->getFd();
  std::vector<int> session_socket_fds {zygote_socket_fd, session_socket};

  struct pollfd fd_structs[2];
  static const int ZYGOTE_IDX = 0;
  static const int SESSION_IDX = 1;
  fd_structs[ZYGOTE_IDX].fd = zygote_socket_fd;
  fd_structs[ZYGOTE_IDX].events = POLLIN;
  fd_structs[SESSION_IDX].fd = session_socket;
  fd_structs[SESSION_IDX].events = POLLIN;

  struct timeval timeout;
  socklen_t timeout_size = sizeof timeout;
  if (getsockopt(session_socket, SOL_SOCKET, SO_RCVTIMEO, &timeout, &timeout_size) != 0) {
    ALOGE("Failed to retrieve session socket timeout");
    timeout.tv_sec = 1;
    timeout.tv_usec = 0;
  }

  struct ucred credentials;
  socklen_t cred_size = sizeof credentials;
  if (getsockopt(n_buffer->getFd(), SOL_SOCKET, SO_PEERCRED, &credentials, &cred_size) == -1
      || cred_size != sizeof credentials) {
    ALOGE("ForkMany failed to get initial credentials, errno = %d", errno);
    return -1;
  }

  bool first_time = true;
  do {
    if (credentials.uid != expected_uid) {
      return -1;
    }
    n_buffer->readAllLines();
    n_buffer->reset();
    int pid = zygote::forkApp(env, /* no pipe FDs */ -1, -1, session_socket_fds,
                              /*args_known=*/ true, /*is_priority_fork=*/ true,
                              /*purge=*/ first_time);
    first_time = false;
    if (pid == 0) {
      return 0;
    }
    // We're in the parent. Write big-endian pid, followed by a boolean.
    char pid_buf[5];
    int tmp_pid = pid;
    for (int i = 3; i >= 0; --i) {
      pid_buf[i] = tmp_pid & 0xff;
      tmp_pid >>= 8;
    }
    pid_buf[4] = 0;  // Process is not wrapped.
    int res = write(session_socket, pid_buf, 5);
    if (res != 5) {
      if (res == -1) {
        ALOGE("Pid write error %d: %s", errno, strerror(errno));
      } else {
        ALOGE("Write unexpectedly returned short: %d < 5", res);
      }
      return -2;
    }
    // Clear buffer and get count from next command.
    n_buffer->clear();
    for (;;) {
      // Poll isn't strictly necessary for now. But without it, disconnect is hard to detect.
      int poll_res;
      do {
        poll_res = poll(fd_structs, 2, -1 /* infinite timeout */);
      } while (poll_res == -1 && errno == EINTR);
      if ((fd_structs[SESSION_IDX].revents & POLLIN) != 0) {
        if (n_buffer->getCount() != 0) {
          break;
        }  // else disconnected;
      } else if (poll_res == 0 || (fd_structs[ZYGOTE_IDX].revents & POLLIN) == 0) {
        ALOGE("Poll returned with no descriptors ready!");
        sleep(1);
        continue;
      }
      // We've now seen either a disconnect or connect request.
      close(session_socket);
      int new_fd = accept(zygote_socket_fd, nullptr, nullptr);
      if (new_fd == -1) {
        ALOGE("Accept(%d) failed %d: %s", zygote_socket_fd, errno, strerror(errno));
        return -2;
      }
      if (new_fd != session_socket) {
          // Move new_fd back to the old value, so that we don't have to change Java-level data
          // structures to reflect a change. This implicitly closes the old one.
          if (dup2(new_fd, session_socket) != session_socket) {
            ALOGE("Failed to move fd %d to %d, errno = %d", new_fd, session_socket, errno);
          }
          close(new_fd);
      }
      // If we ever return, we effectively reuse the old Java ZygoteConnection.
      // None of its state needs to change.
      if (setsockopt(session_socket, SOL_SOCKET, SO_RCVTIMEO, &timeout, timeout_size) != 0) {
        ALOGE("Failed to set receive timeout for socket %d", session_socket);
      }
      if (setsockopt(session_socket, SOL_SOCKET, SO_SNDTIMEO, &timeout, timeout_size) != 0) {
        ALOGE("Failed to set send timeout for socket %d", session_socket);
      }
      if (getsockopt(session_socket, SOL_SOCKET, SO_PEERCRED, &credentials, &cred_size) == -1
          || cred_size != sizeof credentials) {
        ALOGE("ForkMany failed to get credentials, errno = %d", errno);
        return -1;
      }
    }
  } while (n_buffer->isSimpleForkCommand(minUid));
  ALOGW("forkMany terminated due to non-simple command");
  n_buffer->logState();
  n_buffer->reset();
  return -1;
}

#define METHOD_NAME(m) com_android_internal_os_ZygoteCommandBuffer_ ## m

static const JNINativeMethod gMethods[] = {
        {"getNativeBuffer", "(I)J", (void *) METHOD_NAME(getNativeBuffer)},
        {"freeNativeBuffer", "(J)V", (void *) METHOD_NAME(freeNativeBuffer)},
        {"insert", "(JLjava/lang/String;)V", (void *) METHOD_NAME(insert)},
        {"nextLine", "(J)Ljava/lang/String;", (void *) METHOD_NAME(nextLine)},
        {"readAllLinesAndReset", "(J)V", (void *) METHOD_NAME(readAllLinesAndReset)},
        {"retrieveCount", "(J)I", (void *) METHOD_NAME(retrieveCount)},
        {"forkMany", "(JIII)I", (void *) METHOD_NAME(forkMany)},
};

int register_com_android_internal_os_ZygoteCommandBuffer(JNIEnv* env) {
  return RegisterMethodsOrDie(env, "com/android/internal/os/ZygoteCommandBuffer", gMethods,
                              NELEM(gMethods));
}

}  // namespace android
