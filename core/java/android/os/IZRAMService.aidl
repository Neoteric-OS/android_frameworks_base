/**
 * Copyright (c) 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.os;

/** {@hide} */
interface IZRAMService
{
    /**
     * hot add a zram slot
     * @param size
     * @return number of the zram slot or -1 on failure.
     */
    int hotAdd(int size);

    /**
     * hot remove a zram slot
     * @return success.
     */
    boolean hotRemove(int num);

    /**
     * open a zram slot for read & write
     * @return success
     */
    boolean open(int num);

    /**
     * read from zram
     * @return number of bytes read or -1 on failure
     */
    int read(int num, out byte[] buf);

    /**
     * write to zram
     * @return success
     */
    boolean write(int num, in byte[] buf);

    /**
     * finish read/write
     */
    void close(int num);
}
