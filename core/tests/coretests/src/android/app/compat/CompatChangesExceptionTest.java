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

package android.app.compat;

import static junit.framework.Assert.assertEquals;

import org.testng.annotations.Test;

/**
 * {@link CompatChangesException} tests.
 */
public class CompatChangesExceptionTest {

    private static void assertMessageAndCause(String msg, Throwable cause, Exception e) {
        assertEquals(msg, e.getMessage());
        assertEquals(cause, e.getCause());
    }

    @Test
    public void testConstructors() {
        String msg = "Test exception message";
        Throwable cause = new Throwable();
        assertMessageAndCause(null, null, new CompatChangesException());
        assertMessageAndCause(null, null, new CompatChangesException((String) null));
        assertMessageAndCause(null, null, new CompatChangesException((Throwable) null));
        assertMessageAndCause(null, null, new CompatChangesException(null, null));
        assertMessageAndCause(msg, null, new CompatChangesException(msg));
        assertMessageAndCause(msg, null, new CompatChangesException(msg, null));
        assertMessageAndCause(msg, cause, new CompatChangesException(msg, cause));
    }
}
