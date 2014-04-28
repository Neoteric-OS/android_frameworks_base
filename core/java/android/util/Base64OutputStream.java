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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * An OutputStream that does Base64 encoding on the data written to
 * it, writing the resulting data to another OutputStream.
 */
public class Base64OutputStream extends FilterOutputStream {

    /**
     * Performs Base64 encoding on the data written to the stream,
     * writing the encoded data to another OutputStream.
     *
     * @param out the OutputStream to write the encoded data to
     * @param flags bit flags for controlling the encoder; see the
     *        constants in {@link Base64}
     */
    public Base64OutputStream(OutputStream out, int flags) {
        super(createWrappedStream(out, flags, true /* encode */));
    }

    /**
     * Performs Base64 encoding or decoding on the data written to the
     * stream, writing the encoded/decoded data to another
     * OutputStream.
     *
     * @param out the OutputStream to write the encoded data to
     * @param flags bit flags for controlling the encoder; see the
     *        constants in {@link Base64}
     * @param encode true to encode, false to decode
     *
     * @hide
     */
    public Base64OutputStream(OutputStream out, int flags, boolean encode) {
        super(createWrappedStream(out, flags, encode));
    }

    @Override
    public void write(byte[] buffer, int offset, int length) throws IOException {
        try {
            super.write(buffer, offset, length);
        } catch (libcore.util.Base64DataException e) {
            // Convert to the specific public API exception type callers might be expecting.
            throw new Base64DataException(e.getMessage());
        }
    }

    @Override
    public void write(int oneByte) throws IOException {
        try {
            super.write(oneByte);
        } catch (libcore.util.Base64DataException e) {
            // Convert to the specific public API exception type callers might be expecting.
            throw new Base64DataException(e.getMessage());
        }
    }

    private static OutputStream createWrappedStream(OutputStream out, int flags, boolean encode) {
        libcore.util.Base64.Coder coder;
        if (encode) {
            coder = Base64.createLibcoreEncoder(flags);
        } else {
            coder = Base64.createLibcoreDecoder(flags);
        }
        boolean mustCloseOut = !((flags & Base64.NO_CLOSE) == 0);
        return new libcore.util.Base64OutputStream(out, coder, mustCloseOut);
    }

}
