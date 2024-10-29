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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;


/**
 * Unit tests for {@link android.util.proto.ProtoFieldFilter}.
 *
 *  Build/Install/Run:
 *  atest FrameworksCoreTests:ProtoFieldFilterTest
 *
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ProtoFieldFilterTest {

    @Test
    public void testNoFieldsFiltered() throws IOException {
        ProtoOutputStream protoOutputStream = new ProtoOutputStream();

        int fieldNumber1 = 1; // varint (int64)
        int fieldNumber2 = 2; // fixed64
        int fieldNumber3 = 3; // length-delimited (bytes)
        int fieldNumber4 = 4; // fixed32

        long fieldId1 =
                ProtoStream.makeFieldId(fieldNumber1,
                    ProtoStream.FIELD_TYPE_INT64 | ProtoStream.FIELD_COUNT_SINGLE);
        long fieldId2 =
                ProtoStream.makeFieldId(fieldNumber2,
                    ProtoStream.FIELD_TYPE_FIXED64 | ProtoStream.FIELD_COUNT_SINGLE);
        long fieldId3 =
                ProtoStream.makeFieldId(fieldNumber3,
                    ProtoStream.FIELD_TYPE_BYTES | ProtoStream.FIELD_COUNT_SINGLE);
        long fieldId4 =
                ProtoStream.makeFieldId(fieldNumber4,
                    ProtoStream.FIELD_TYPE_FIXED32 | ProtoStream.FIELD_COUNT_SINGLE);

        protoOutputStream.writeInt64(fieldId1, 12345L);
        protoOutputStream.writeFixed64(fieldId2, 0x1234567890ABCDEFL);
        protoOutputStream.writeBytes(fieldId3, new byte[]{1, 2, 3, 4, 5});
        protoOutputStream.writeFixed32(fieldId4, 0xDEADBEEF);

        byte[] inputBytes = protoOutputStream.getBytes();
        ProtoInputStream protoInputStream =
                new ProtoInputStream(new ByteArrayInputStream(inputBytes));

        // Create a ProtoFieldFilter with a predicate that always returns false
        // (no fields are filtered)
        ProtoFieldFilter filter = new ProtoFieldFilter(fieldNumber -> false);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        filter.filter(protoInputStream, outputStream);
        byte[] outputBytes = outputStream.toByteArray();

        // Verify that outputBytes equals inputBytes
        assertArrayEquals("Output bytes should be the same as input bytes when "
                + "no fields are filtered", inputBytes, outputBytes);
    }

    @Test
    public void testAllFieldsFiltered() throws IOException {
        ProtoOutputStream protoOutputStream = new ProtoOutputStream();

        int fieldNumber1 = 1; // varint
        int fieldNumber2 = 2; // fixed64
        int fieldNumber3 = 3; // length-delimited
        int fieldNumber4 = 4; // fixed32

        long fieldId1 =
                ProtoStream.makeFieldId(fieldNumber1,
                    ProtoStream.FIELD_TYPE_INT64 | ProtoStream.FIELD_COUNT_SINGLE);
        long fieldId2 =
                ProtoStream.makeFieldId(fieldNumber2,
                    ProtoStream.FIELD_TYPE_FIXED64 | ProtoStream.FIELD_COUNT_SINGLE);
        long fieldId3 =
                ProtoStream.makeFieldId(fieldNumber3,
                    ProtoStream.FIELD_TYPE_BYTES | ProtoStream.FIELD_COUNT_SINGLE);
        long fieldId4 =
                ProtoStream.makeFieldId(fieldNumber4,
                    ProtoStream.FIELD_TYPE_FIXED32 | ProtoStream.FIELD_COUNT_SINGLE);

        protoOutputStream.writeInt64(fieldId1, 12345L);
        protoOutputStream.writeFixed64(fieldId2, 0x1234567890ABCDEFL);
        protoOutputStream.writeBytes(fieldId3, new byte[]{1, 2, 3, 4, 5});
        protoOutputStream.writeFixed32(fieldId4, 0xDEADBEEF);

        byte[] inputBytes = protoOutputStream.getBytes();
        ProtoInputStream protoInputStream =
                new ProtoInputStream(new ByteArrayInputStream(inputBytes));

        // Create a ProtoFieldFilter with a predicate that always returns true
        // (all fields are filtered)
        ProtoFieldFilter filter = new ProtoFieldFilter(fieldNumber -> true);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        filter.filter(protoInputStream, outputStream);

        byte[] outputBytes = outputStream.toByteArray();

        // Verify that outputBytes is empty
        assertEquals("Output bytes should be empty when all fields are filtered",
                0, outputBytes.length);
    }

    @Test
    public void testSpecificFieldsFiltered() throws IOException {

        ProtoOutputStream protoOutputStream = new ProtoOutputStream();

        // Write some fields
        int fieldNumber1 = 1; // varint
        int fieldNumber2 = 2; // fixed64
        int fieldNumber3 = 3; // length-delimited
        int fieldNumber4 = 4; // fixed32

        long fieldId1 =
                ProtoStream.makeFieldId(fieldNumber1,
                    ProtoStream.FIELD_TYPE_INT64 | ProtoStream.FIELD_COUNT_SINGLE);
        long fieldId2 =
                ProtoStream.makeFieldId(fieldNumber2,
                    ProtoStream.FIELD_TYPE_FIXED64 | ProtoStream.FIELD_COUNT_SINGLE);
        long fieldId3 =
                ProtoStream.makeFieldId(fieldNumber3,
                    ProtoStream.FIELD_TYPE_BYTES | ProtoStream.FIELD_COUNT_SINGLE);
        long fieldId4 =
                ProtoStream.makeFieldId(fieldNumber4,
                    ProtoStream.FIELD_TYPE_FIXED32 | ProtoStream.FIELD_COUNT_SINGLE);

        protoOutputStream.writeInt64(fieldId1, 12345L);
        protoOutputStream.writeFixed64(fieldId2, 0x1234567890ABCDEFL);
        protoOutputStream.writeBytes(fieldId3, new byte[]{1, 2, 3, 4, 5});
        protoOutputStream.writeFixed32(fieldId4, 0xDEADBEEF);

        byte[] inputBytes = protoOutputStream.getBytes();
        ProtoInputStream protoInputStream =
                new ProtoInputStream(new ByteArrayInputStream(inputBytes));

        // Create a ProtoFieldFilter with a predicate that filters out field number 2
        ProtoFieldFilter filter = new ProtoFieldFilter(fieldNumber -> fieldNumber == fieldNumber2);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        filter.filter(protoInputStream, outputStream);

        byte[] outputBytes = outputStream.toByteArray();

        // Now, verify that outputBytes contains only fields 1, 3, 4
        ProtoInputStream outputProtoInputStream = new ProtoInputStream(outputBytes);

        boolean hasField1 = false;
        boolean hasField3 = false;
        boolean hasField4 = false;

        int fieldNumber;

        while ((fieldNumber = outputProtoInputStream.nextField())
                != ProtoInputStream.NO_MORE_FIELDS) {

            switch (fieldNumber) {
                case 1:
                    hasField1 = true;
                    long value1 = outputProtoInputStream.readLong(fieldId1);
                    assertEquals(12345L, value1);
                    break;
                case 2:
                    fail("Field 2 should be filtered out");
                    break;
                case 3:
                    hasField3 = true;
                    byte[] value3 = outputProtoInputStream.readBytes(fieldId3);
                    assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, value3);
                    break;
                case 4:
                    hasField4 = true;
                    int value4 = outputProtoInputStream.readInt(fieldId4);
                    assertEquals(0xDEADBEEF, value4);
                    break;
                default:
                    fail("Unexpected field number: " + fieldNumber
                            + " At byte" + outputProtoInputStream.dumpDebugData());
            }
        }

        assertTrue("Field 1 should be present", hasField1);
        assertTrue("Field 3 should be present", hasField3);
        assertTrue("Field 4 should be present", hasField4);
    }

    @Test
    public void testDifferentWireTypes() throws IOException {
        // Create a ProtoOutputStream and write fields of different wire types
        ProtoOutputStream protoOutputStream = new ProtoOutputStream();

        // Define field numbers and field IDs
        int fieldVarint = 1; // int64
        int fieldFixed64 = 2; // fixed64
        int fieldLengthDelimited = 3; // bytes
        int fieldMessage = 4; // message (nested)
        int fieldFixed32 = 5; // fixed32

        long fieldIdVarint =
                ProtoStream.makeFieldId(fieldVarint,
                    ProtoStream.FIELD_TYPE_INT64 | ProtoStream.FIELD_COUNT_SINGLE);
        long fieldIdFixed64 =
                ProtoStream.makeFieldId(fieldFixed64,
                    ProtoStream.FIELD_TYPE_FIXED64 | ProtoStream.FIELD_COUNT_SINGLE);
        long fieldIdLengthDelimited =
                ProtoStream.makeFieldId(fieldLengthDelimited,
                    ProtoStream.FIELD_TYPE_BYTES | ProtoStream.FIELD_COUNT_SINGLE);
        long fieldIdMessage =
                ProtoStream.makeFieldId(fieldMessage,
                    ProtoStream.FIELD_TYPE_MESSAGE | ProtoStream.FIELD_COUNT_SINGLE);
        long fieldIdFixed32 =
                ProtoStream.makeFieldId(fieldFixed32,
                    ProtoStream.FIELD_TYPE_FIXED32 | ProtoStream.FIELD_COUNT_SINGLE);

        protoOutputStream.writeInt64(fieldIdVarint, 12345L);
        protoOutputStream.writeFixed64(fieldIdFixed64, 0x1234567890ABCDEFL);
        protoOutputStream.writeBytes(fieldIdLengthDelimited, new byte[]{10, 20, 30});
        // Start a nested message
        long tokenGroup = protoOutputStream.start(fieldIdMessage);
        // Nested field
        int nestedFieldNumber = 1;
        long nestedFieldId =
                ProtoStream.makeFieldId(nestedFieldNumber,
                    ProtoStream.FIELD_TYPE_INT32 | ProtoStream.FIELD_COUNT_SINGLE);
        protoOutputStream.writeInt32(nestedFieldId, 42);
        protoOutputStream.end(tokenGroup);
        protoOutputStream.writeFixed32(fieldIdFixed32, 0xDEADBEEF);

        // Get the bytes
        byte[] inputBytes = protoOutputStream.getBytes();

        // Create a ProtoInputStream
        ProtoInputStream protoInputStream =
                new ProtoInputStream(new ByteArrayInputStream(inputBytes));

        // Create a ProtoFieldFilter that doesn't filter any fields
        ProtoFieldFilter filter = new ProtoFieldFilter(fieldNumber -> false);

        // Use ByteArrayOutputStream to collect output
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // Filter the proto
        filter.filter(protoInputStream, outputStream);

        // Get the output bytes
        byte[] outputBytes = outputStream.toByteArray();

        // Verify that all fields are present and correctly handled
        ProtoInputStream outputProtoInputStream =
                new ProtoInputStream(new ByteArrayInputStream(outputBytes));

        boolean hasVarint = false;
        boolean hasFixed64 = false;
        boolean hasLengthDelimited = false;
        boolean hasMessage = false;
        boolean hasFixed32 = false;

        int fieldNumber;
        while ((fieldNumber = outputProtoInputStream.nextField())
                != ProtoInputStream.NO_MORE_FIELDS) {
            switch (fieldNumber) {
                case 1:
                    hasVarint = true;
                    long varintValue = outputProtoInputStream.readLong(fieldIdVarint);
                    assertEquals(12345L, varintValue);
                    break;
                case 2:
                    hasFixed64 = true;
                    long fixed64Value = outputProtoInputStream.readLong(fieldIdFixed64);
                    assertEquals(0x1234567890ABCDEFL, fixed64Value);
                    break;
                case 3:
                    hasLengthDelimited = true;
                    byte[] bytesValue = outputProtoInputStream.readBytes(fieldIdLengthDelimited);
                    assertArrayEquals(new byte[]{10, 20, 30}, bytesValue);
                    break;
                case 4:
                    hasMessage = true;
                    long token = outputProtoInputStream.start(fieldIdMessage);
                    int groupFieldNumber;
                    boolean hasGroupField = false;
                    while ((groupFieldNumber = outputProtoInputStream.nextField())
                            != ProtoInputStream.NO_MORE_FIELDS) {
                        if (groupFieldNumber == 1) {
                            hasGroupField = true;
                            long groupFieldId =
                                    ProtoStream.makeFieldId(groupFieldNumber,
                                        ProtoStream.FIELD_TYPE_INT32
                                            | ProtoStream.FIELD_COUNT_SINGLE);
                            int groupValue = outputProtoInputStream.readInt(groupFieldId);
                            assertEquals(42, groupValue);
                        } else {
                            fail("Unexpected group field number: " + groupFieldNumber);
                        }
                    }
                    outputProtoInputStream.end(token);
                    assertTrue("Group field should be present", hasGroupField);
                    break;
                case 5:
                    hasFixed32 = true;
                    int fixed32Value = outputProtoInputStream.readInt(fieldIdFixed32);
                    assertEquals(0xDEADBEEF, fixed32Value);
                    break;
                default:
                    fail("Unexpected field number: " + fieldNumber);
            }
        }

        assertTrue("Varint field should be present", hasVarint);
        assertTrue("Fixed64 field should be present", hasFixed64);
        assertTrue("Length-delimited field should be present", hasLengthDelimited);
        assertTrue("Message field should be present", hasMessage);
        assertTrue("Fixed32 field should be present", hasFixed32);
    }
    @Test
    public void testNestedMessagesUnfiltered() throws IOException {
        ProtoOutputStream protoOutputStream = new ProtoOutputStream();

        int fieldNumber1 = 1; // int64
        int fieldNumber2 = 2; // message (nested)
        int nestedFieldNumber1 = 1; // int32 in nested message
        int nestedFieldNumber2 = 2; // fixed32 in nested message

        long fieldId1 =
                ProtoStream.makeFieldId(fieldNumber1,
                    ProtoStream.FIELD_TYPE_INT64 | ProtoStream.FIELD_COUNT_SINGLE);
        long fieldId2 =
                ProtoStream.makeFieldId(fieldNumber2,
                    ProtoStream.FIELD_TYPE_MESSAGE | ProtoStream.FIELD_COUNT_SINGLE);

        long nestedFieldId1 =
                ProtoStream.makeFieldId(nestedFieldNumber1,
                    ProtoStream.FIELD_TYPE_INT32 | ProtoStream.FIELD_COUNT_SINGLE);
        long nestedFieldId2 =
                ProtoStream.makeFieldId(nestedFieldNumber2,
                    ProtoStream.FIELD_TYPE_FIXED32 | ProtoStream.FIELD_COUNT_SINGLE);

        protoOutputStream.writeInt64(fieldId1, 12345L);

        // Start a nested message
        long token = protoOutputStream.start(fieldId2);
        protoOutputStream.writeInt32(nestedFieldId1, 6789);
        protoOutputStream.writeFixed32(nestedFieldId2, 0xCAFEBABE);
        protoOutputStream.end(token);

        byte[] inputBytes = protoOutputStream.getBytes();
        ProtoInputStream protoInputStream =
                new ProtoInputStream(new ByteArrayInputStream(inputBytes));

        // Create a ProtoFieldFilter that filters out field number 2 (the nested message)
        ProtoFieldFilter filter = new ProtoFieldFilter(fieldNumber -> fieldNumber == fieldNumber2);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        filter.filter(protoInputStream, outputStream);
        byte[] outputBytes = outputStream.toByteArray();

        // verify that outputBytes contains only fieldNumber1
        ProtoInputStream outputProtoInputStream =
                new ProtoInputStream(new ByteArrayInputStream(outputBytes));

        boolean hasField1 = false;
        boolean hasField2 = false;

        int fieldNumber;
        while ((fieldNumber = outputProtoInputStream.nextField())
                != ProtoInputStream.NO_MORE_FIELDS) {
            switch (fieldNumber) {
                case 1:
                    hasField1 = true;
                    long value1 = outputProtoInputStream.readLong(fieldId1);
                    assertEquals(12345L, value1);
                    break;
                case 2:
                    hasField2 = true;
                    outputProtoInputStream.skip();
                    break;
                default:
                    fail("Unexpected field number: " + fieldNumber);
            }
        }

        assertTrue("Field 1 should be present", hasField1);
        assertFalse("Field 2 should be filtered out", hasField2);
    }

    @Test
    public void testRepeatedFields() throws IOException {

        ProtoOutputStream protoOutputStream = new ProtoOutputStream();

        int fieldNumber1 = 1; // int32 repeated

        long fieldId1 =
                ProtoStream.makeFieldId(fieldNumber1,
                    ProtoStream.FIELD_TYPE_INT32 | ProtoStream.FIELD_COUNT_REPEATED);

        protoOutputStream.writeRepeatedInt32(fieldId1, 100);
        protoOutputStream.writeRepeatedInt32(fieldId1, 200);
        protoOutputStream.writeRepeatedInt32(fieldId1, 300);

        byte[] inputBytes = protoOutputStream.getBytes();
        ProtoInputStream protoInputStream =
                new ProtoInputStream(new ByteArrayInputStream(inputBytes));

        ProtoFieldFilter filter = new ProtoFieldFilter(fieldNumber -> false);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        filter.filter(protoInputStream, outputStream);

        byte[] outputBytes = outputStream.toByteArray();

        // Verify that outputBytes equals inputBytes
        assertArrayEquals("Output bytes should be the same as input bytes when no fields"
                + " are filtered", inputBytes, outputBytes);
    }

}
