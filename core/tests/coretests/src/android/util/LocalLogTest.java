/*
 * Copyright (C) 2016 The Android Open Source Project
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

import androidx.test.filters.LargeTest;

import junit.framework.TestCase;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@LargeTest
public class LocalLogTest extends TestCase {

    public void testLog_underCapacity_localTimestamps() {
        boolean localTimestamps = true;
        doLog_underCapacityWithNoOverflow(localTimestamps);
    }

    public void testLog_underCapacity_nonLocalTimestamps() {
        boolean localTimestamps = false;
        doLog_underCapacityWithNoOverflow(localTimestamps);
    }

    private void doLog_underCapacityWithNoOverflow(boolean localTimestamps) {
        String[] lines = {
                "foo",
                "bar",
                "baz"
        };
        String[] want = lines;
        checkLogDumpAndDumpReverse(new LocalLog(10, localTimestamps), lines, want);
    }

    public void testLog_atCapacityWithNoOverflow_localTimestamps() {
        boolean localTimestamps = true;
        doLog_atCapacityWithNoOverflow(localTimestamps);
    }

    public void testLog_atCapacityWithNoOverflow_nonLocalTimestamps() {
        boolean localTimestamps = false;
        doLog_atCapacityWithNoOverflow(localTimestamps);
    }

    private void doLog_atCapacityWithNoOverflow(boolean localTimestamps) {
        String[] lines = {
                "foo",
                "bar",
                "baz"
        };
        String[] want = lines;
        checkLogDumpAndDumpReverse(new LocalLog(3, localTimestamps), lines, want);
    }

    public void testMaxLinesZero() {
        String[] lines = {
            "foo",
            "bar",
            "baz"
        };
        String[] want = {};
        checkLogDumpAndDumpReverse(new LocalLog(0), lines, want);
    }

    public void testEmpty() {
        String[] lines = {};
        String[] want = {};
        checkLogDumpAndDumpReverse(new LocalLog(10), lines, want);
    }

    public void testLog_overCapacity() {
        String[] lines = {
            "dropped",
            "dropped",
            "dropped",
            "dropped",
            "dropped",
            "dropped",
            "foo",
            "bar",
            "baz",
        };
        String[] want = {
            "foo",
            "bar",
            "baz",
        };
        checkLogDumpAndDumpReverse(new LocalLog(3), lines, want);
    }

    void checkLogDumpAndDumpReverse(LocalLog logger, String[] input, String[] want) {
        for (String l : input) {
            logger.log(l);
        }
        verifyAllLines(want, dump(logger).split("\n"));
        verifyAllLines(reverse(want), reverseDump(logger).split("\n"));
    }

    public void testLogObject() {
        Object[] lines = {
                1,
                2,
                3.0,
                4.0,
                "Five",
                "dropped",
                createStringWrapper("foo"),
                createStringWrapper("bar"),
                createStringWrapper("baz"),
        };
        String[] want = {
                "foo",
                "bar",
                "baz",
        };
        checkLogObjectDumpAndDumpReverse(new LocalLog(3), lines, want);
    }

    private Object createStringWrapper(String toWrap) {
        // AtomicReference is a class where toString() is delegated to the held value, so it is not
        // a string, but has the expected toString() behavior.
        return new AtomicReference<>(toWrap);
    }

    void checkLogObjectDumpAndDumpReverse(LocalLog logger, Object[] input, String[] want) {
        for (Object l : input) {
            logger.logObject(l);
        }
        verifyAllLines(want, dump(logger).split("\n"));
        verifyAllLines(reverse(want), reverseDump(logger).split("\n"));
    }

    void verifyAllLines(String[] wantLines, String[] toLog) {
        for (int i = 0; i < wantLines.length; i++) {
            String want = wantLines[i];
            String got = toLog[i];
            String msg = String.format("%s did not contain %s", quote(got), quote(want));
            assertTrue(msg, got.contains(want));
        }
    }

    static String dump(LocalLog logger) {
        StringWriter buffer = new StringWriter();
        PrintWriter writer = new PrintWriter(buffer);
        logger.dump(null, writer, new String[0]);
        return buffer.toString();
    }

    static String reverseDump(LocalLog logger) {
        StringWriter buffer = new StringWriter();
        PrintWriter writer = new PrintWriter(buffer);
        logger.reverseDump(null, writer, new String[0]);
        return buffer.toString();
    }

    static String quote(String s) {
        return '"' + s + '"';
    }

    static String[] reverse(String[] ary) {
        List<String> ls = Arrays.asList(ary);
        Collections.reverse(ls);
        return  ls.toArray(new String[ary.length]);
    }
}
