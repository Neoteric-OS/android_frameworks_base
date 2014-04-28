/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * An InputStream that does Base64 decoding on the data read through
 * it.
 */
public class Base64InputStream extends FilterInputStream {
    /**
     * An InputStream that performs Base64 decoding on the data read
     * from the wrapped stream.
     *
     * @param in the InputStream to read the source data from
     * @param flags bit flags for controlling the decoder; see the
     *        constants in {@link Base64}
     */
    public Base64InputStream(InputStream in, int flags) {
        super(createWrappedStream(in, flags, false /* encode */));
    }

    /**
     * Performs Base64 encoding or decoding on the data read from the
     * wrapped InputStream.
     *
     * @param in the InputStream to read the source data from
     * @param flags bit flags for controlling the decoder; see the
     *        constants in {@link Base64}
     * @param encode true to encode, false to decode
     *
     * @hide
     */
    public Base64InputStream(InputStream in, int flags, boolean encode) {
        super(createWrappedStream(in, flags, encode));
    }

    @Override
    public int read() throws IOException {
        try {
            return super.read();
        } catch (libcore.util.Base64DataException e) {
            // Convert to the specific public API exception type callers might be expecting.
            throw new Base64DataException(e.getMessage());
        }
    }

    @Override
    public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
        try {
            return super.read(buffer, byteOffset, byteCount);
        } catch (libcore.util.Base64DataException e) {
            // Convert to the specific public API exception type callers might be expecting.
            throw new Base64DataException(e.getMessage());
        }
    }

    private static InputStream createWrappedStream(InputStream in, int flags, boolean encode) {
        libcore.util.Base64.Coder coder;
        if (encode) {
            coder = Base64.createLibcoreEncoder(flags);
        } else {
            coder = Base64.createLibcoreDecoder(flags);
        }
        return new libcore.util.Base64InputStream(in, coder);
    }
}
