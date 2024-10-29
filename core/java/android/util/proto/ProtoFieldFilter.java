/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.util.proto;

import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Predicate;

/**
 * A utility class for filtering fields from a ProtoInputStream and writing the result
 * to an OutputStream.
 * @hide
 */
public class ProtoFieldFilter {
    private final Predicate<Integer> mFieldPredicate;

    /**
     * Constructs a ProtoFieldFilter with a predicate that considers depth.
     *
     * @param fieldPredicate A predicate that returns true if a field should be removed.
     */
    public ProtoFieldFilter(Predicate<Integer> fieldPredicate) {
        this.mFieldPredicate = fieldPredicate;
    }

    /**
     * Filters the ProtoInputStream according to the predicate and writes the result
     * to the OutputStream.
     *
     * @param protoInputStream The input ProtoInputStream to filter.
     * @param outputStream The output stream to write the filtered proto to.
     * @throws IOException If an I/O error occurs.
     */
    public void filter(ProtoInputStream protoInputStream,
                OutputStream outputStream) throws IOException {
        final PassThroughBuffer buffer = new PassThroughBuffer(outputStream);
        processFields(protoInputStream, buffer);
        buffer.flush();
    }

    private void processFields(ProtoInputStream protoInputStream,
                PassThroughBuffer buffer) throws IOException {
        int fieldNumber;
        while ((fieldNumber = protoInputStream.nextField()) != ProtoInputStream.NO_MORE_FIELDS) {
            int wireType = protoInputStream.getWireType();
            // Skip this field if the predicate returns true
            if (mFieldPredicate.test(fieldNumber)) {
                protoInputStream.skip();
            } else {
                // Write tag
                writeFieldTag(buffer, fieldNumber, wireType);
                // Process and write the field
                copyField(protoInputStream, buffer, fieldNumber, wireType);
            }
        }
    }

    private void copyField(ProtoInputStream protoInputStream, PassThroughBuffer buffer,
                int fieldNumber, int wireType) throws IOException {
        // Handle the field according to its wire type. For more information,
        // see the encoding guide at https://protobuf.dev/programming-guides/encoding/
        switch (wireType) {
            case ProtoStream.WIRE_TYPE_VARINT:
                long varintValue = protoInputStream.readRawVarint(/* markSuccess=*/true);
                buffer.writeRawVarint64(varintValue);
                break;
            case ProtoStream.WIRE_TYPE_FIXED64:
                long fixed64Value = protoInputStream.readRawFixed64(/* markSuccess=*/true);
                buffer.writeRawFixed64(fixed64Value);
                break;
            case ProtoStream.WIRE_TYPE_LENGTH_DELIMITED:
                int length = (int) protoInputStream.readRawVarint(/* markSuccess=*/false);
                buffer.writeRawVarint32(length);
                byte[] bytes =
                        protoInputStream.readRawBytesWithMark(length, /* markSuccess=*/true);
                buffer.writeRawBuffer(bytes);
                break;
            case ProtoStream.WIRE_TYPE_FIXED32:
                int fixed32Value = protoInputStream.readRawFixed32(/* markSuccess=*/true);
                buffer.writeRawFixed32(fixed32Value);
                break;
            // Groups are not supported in ProtoFieldFilter
            // case ProtoStream.WIRE_TYPE_START_GROUP:
            // case ProtoStream.WIRE_TYPE_END_GROUP:
            default:
                throw new IOException("Unsupported wire type: " + wireType);
        }
    }

    private void writeFieldTag(PassThroughBuffer buffer,
            int fieldNumber, int wireType)  throws IOException {
        int tag = (fieldNumber << ProtoStream.FIELD_ID_SHIFT) | wireType;
        buffer.writeRawVarint32(tag);
    }

    private static class PassThroughBuffer {
        private final EncodedBuffer mEncodedBuffer;
        private final int mFlushThreshold;
        private final OutputStream mOutputStream;
        private static final int FLUSH_THRESHOLD_BYTES = 8 * 1024;

        PassThroughBuffer(OutputStream outputStream) {
            this(FLUSH_THRESHOLD_BYTES, outputStream);
        }

        PassThroughBuffer(int flushThreshold, OutputStream outputStream) {
            this.mEncodedBuffer = new EncodedBuffer();
            this.mFlushThreshold = flushThreshold;
            this.mOutputStream = outputStream;
        }

        void writeRawVarint32(int val) throws IOException {
            mEncodedBuffer.writeRawVarint32(val);
            flushIfNecessary();
        }

        void writeRawFixed32(int val) throws IOException {
            mEncodedBuffer.writeRawFixed32(val);
            flushIfNecessary();
        }

        void writeRawVarint64(long val) throws IOException {
            mEncodedBuffer.writeRawVarint64(val);
            flushIfNecessary();
        }

        void writeRawFixed64(long val) throws IOException {
            mEncodedBuffer.writeRawFixed64(val);
            flushIfNecessary();
        }

        void writeRawBuffer(byte[] buffer) throws IOException {
            mEncodedBuffer.writeRawBuffer(buffer);
            flushIfNecessary();
        }

        private void flushIfNecessary() throws IOException {
            if (mEncodedBuffer.getWritePos() >= mFlushThreshold) {
                flush();
            }
        }

        void flush() throws IOException {
            mEncodedBuffer.startEditing();
            int readableSize = mEncodedBuffer.getReadableSize();
            if (readableSize > 0) {
                mOutputStream.write(mEncodedBuffer.getBytes(readableSize));
            }
        }
    }
}
