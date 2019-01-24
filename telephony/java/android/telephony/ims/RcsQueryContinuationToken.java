/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.telephony.ims;

import android.os.Parcelable;

/**
 * This interface allows using the same implementation for continuation token usage in
 * {@link com.android.providers.telephony.RcsProvider}
 * @hide
 */
public interface RcsQueryContinuationToken extends Parcelable {

    /**
     * Returns the original raw query used on {@link com.android.providers.telephony.RcsProvider}
     * @hide
     */
    String getRawQuery();

    /**
     * Returns which index this continuation query should start from
     * @hide
     */
    int getOffset();

    /**
     * Increments the offset by the amount of result rows returned with the continuation query for
     * the next query.
     * @hide
     */
    void incrementOffset();

    /**
     * Returns a key unique to the type of query performed
     * @hide
     */
    String getKey();
}
