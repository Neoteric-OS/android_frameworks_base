/*
 * Copyright (C) 2017 The Android Open Source Project
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

#define LOG_TAG "UpdateEngine-JNI"
#include <android-base/logging.h>

#include <string>

#include "jni.h"
#include "core_jni_helpers.h"
#include "error_code.h"
#include "update_engine/update_status.h"


namespace android {

constexpr char update_engine_path_name[] = "android/os/UpdateEngine";

static void initConstant(JNIEnv* env, const std::string& class_name, const std::string& field_name,
                         int value) {
  std::string name = std::string(update_engine_path_name) + "$" + class_name;
  jclass clazz = FindClassOrDie(env, name.c_str());
  jfieldID fid = GetStaticFieldIDOrDie(env, clazz, field_name.c_str(), "I");
  env->SetStaticIntField(clazz, fid, value);
}

static void android_os_UpdateEngine_initConstants(JNIEnv* env) {
  initConstant(env, "ErrorCodeConstants", "SUCCESS",
               static_cast<int>(chromeos_update_engine::ErrorCode::kSuccess));
  initConstant(env, "ErrorCodeConstants", "ERROR",
               static_cast<int>(chromeos_update_engine::ErrorCode::kError));
  initConstant(env, "ErrorCodeConstants", "FILESYSTEM_COPIER_ERROR",
               static_cast<int>(chromeos_update_engine::ErrorCode::kFilesystemCopierError));
  initConstant(env, "ErrorCodeConstants", "POST_INSTALL_RUNNER_ERROR",
               static_cast<int>(chromeos_update_engine::ErrorCode::kPostinstallRunnerError));
  initConstant(env, "ErrorCodeConstants", "PAYLOAD_MISMATCHED_TYPE_ERROR",
               static_cast<int>(chromeos_update_engine::ErrorCode::kPayloadMismatchedType));
  initConstant(env, "ErrorCodeConstants", "INSTALL_DEVICE_OPEN_ERROR",
               static_cast<int>(chromeos_update_engine::ErrorCode::kInstallDeviceOpenError));
  initConstant(env, "ErrorCodeConstants", "KERNEL_DEVICE_OPEN_ERROR",
               static_cast<int>(chromeos_update_engine::ErrorCode::kKernelDeviceOpenError));
  initConstant(env, "ErrorCodeConstants", "DOWNLOAD_TRANSFER_ERROR",
               static_cast<int>(chromeos_update_engine::ErrorCode::kDownloadTransferError));
  initConstant(env, "ErrorCodeConstants", "PAYLOAD_HASH_MISMATCH_ERROR",
               static_cast<int>(chromeos_update_engine::ErrorCode::kPayloadHashMismatchError));
  initConstant(env, "ErrorCodeConstants", "PAYLOAD_SIZE_MISMATCH_ERROR",
               static_cast<int>(chromeos_update_engine::ErrorCode::kPayloadSizeMismatchError));
  initConstant(env, "ErrorCodeConstants", "DOWNLOAD_PAYLOAD_VERIFICATION_ERROR",
               static_cast<int>(chromeos_update_engine::ErrorCode::kDownloadPayloadVerificationError));

  initConstant(env, "UpdateStatusConstants", "IDLE",
               static_cast<int>(update_engine::UpdateStatus::IDLE));
  initConstant(env, "UpdateStatusConstants", "CHECKING_FOR_UPDATE",
               static_cast<int>(update_engine::UpdateStatus::CHECKING_FOR_UPDATE));
  initConstant(env, "UpdateStatusConstants", "UPDATE_AVAILABLE",
               static_cast<int>(update_engine::UpdateStatus::UPDATE_AVAILABLE));
  initConstant(env, "UpdateStatusConstants", "DOWNLOADING",
               static_cast<int>(update_engine::UpdateStatus::DOWNLOADING));
  initConstant(env, "UpdateStatusConstants", "VERIFYING",
               static_cast<int>(update_engine::UpdateStatus::VERIFYING));
  initConstant(env, "UpdateStatusConstants", "FINALIZING",
               static_cast<int>(update_engine::UpdateStatus::FINALIZING));
  initConstant(env, "UpdateStatusConstants", "UPDATED_NEED_REBOOT",
               static_cast<int>(update_engine::UpdateStatus::UPDATED_NEED_REBOOT));
  initConstant(env, "UpdateStatusConstants", "REPORTING_ERROR_EVENT",
               static_cast<int>(update_engine::UpdateStatus::REPORTING_ERROR_EVENT));
  initConstant(env, "UpdateStatusConstants", "ATTEMPTING_ROLLBACK",
               static_cast<int>(update_engine::UpdateStatus::ATTEMPTING_ROLLBACK));
  initConstant(env, "UpdateStatusConstants", "DISABLED",
               static_cast<int>(update_engine::UpdateStatus::DISABLED));
}

static JNINativeMethod methods[] = {
    {"initConstants", "()V", (void*)android_os_UpdateEngine_initConstants},
};

int register_android_os_UpdateEngine(JNIEnv* env) {
  return RegisterMethodsOrDie(env, update_engine_path_name, methods, NELEM(methods));
}

}