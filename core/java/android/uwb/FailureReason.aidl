/*
 * Copyright 2020 The Android Open Source Project
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

package android.uwb;

/**
 * @hide
 */
@Backing(type="int")
enum FailureReason {
  /**
   * Unknown reason
   */
  UNKNOWN,

  /**
   * The provided parameters were invalid
   */
  BAD_PARAMETERS,

  /**
   * The maximum number of sessions has been reached preventing the requested
   * action from completing.
   */
  MAX_SESSIONS_REACHED,

  /**
   * The system state prevents the action from completing
   */
  SYSTEM_POLICY,


  /**
   * A protocol specific failure reason has occured. Consult the associated
   * PeristableBundle for more details.
   */
  PROTOCOL_SPECIFIC,
}

