/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.os;

import android.os.ParcelFileDescriptor;

/**
 * Interface exposed by GMSCore and called by the system server (PFS)
 * to upload profcollect reports.
 *
 * NOTE: This method is annotated with @RequiresNoPermission because access control
 * is enforced dynamically rather than via manifest permissions:
 * 1. Receiver-side (GMSCore): The implementation MUST verify that the calling UID
 *    is Process.SYSTEM_UID (1000) or has a platform signature.
 * 2. Caller-side (System Server): PFS MUST verify that the target GMSCore package
 *    is authentic (platform-signed) before binding.
 *
 * {@hide}
 */
@VintfStability
interface IProfcollectUploader {
    /**
     * Queues a profile file for background upload via Scotty.
     *
     * @param category The category identifier for policy enforcement (e.g., "PROFCOLLECT_PROFILE").
     * @param pfd The File Descriptor pointing to the profile ZIP. The receiver (GMSCore)
     *            is responsible for closing this descriptor after consumption. The caller
     *            remains responsible for closing its own copy of the descriptor.
     * @param uuid A unique string representation of the UUID for correlation. The receiver
     *             MUST validate that this is a valid UUID string (e.g., catching
     *             IllegalArgumentException during parsing) to avoid service crashes.
     */
    @RequiresNoPermission
    oneway void upload(in String category, in ParcelFileDescriptor pfd, in String uuid);
}
