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
package android.net.vcn;

import android.annotation.NonNull;
import android.content.Context;
import android.net.ipsec.ike.IkeSessionParams;
import android.os.PersistableBundle;

/**
 * This class will be expected as public API
 *
 * @hide
 */
public final class VcnIkeConfig {
    private final IkeSessionParams mIkeParams;

    /**
     * This method will be expected as public API
     *
     * @hide
     */
    public VcnIkeConfig(@NonNull IkeSessionParams mIkeParams) {
        this.mIkeParams = mIkeParams;
    }

    /** @hide */
    public VcnIkeConfig(Context context, PersistableBundle bundle) {
        this.mIkeParams = IkeSessionParams.fromPersistableBundle(context, bundle);
    }

    /** @hide */
    public PersistableBundle toPersistableBundle() {
        return mIkeParams.toPersistableBundle();
    }
}
