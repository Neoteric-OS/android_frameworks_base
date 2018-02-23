/**
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tests.pkgup;

import java.io.File;

public class PkgupPathUtil {

    public static File getPkgupRootDir() {
        String root = System.getenv("PKGUP_ROOT");
        if (root != null) {
            return new File(root);
        }
        String out = System.getenv("OUT");
        if (out != null) {
            return new File(out, "obj/PACKAGING/PackageUpdate_intermediates");
        }
        throw new RuntimeException("Please export root path of PackageUpdate to environment variable PKGUP_ROOT."
                + " This can be skipped if you run this test on android source tree directly.");
    }

    public static File getAbsoluteFile(File file) {
        if (file.isAbsolute()) {
            return file;
        }
        return new File(getPkgupRootDir(), file.getPath());
    }

    public static File getAbsoluteFile(String file) {
        return getAbsoluteFile(new File(file));
    }

    public static String getAbsolutePath(File file) {
        return getAbsoluteFile(file).getAbsolutePath();
    }

    public static String getAbsolutePath(String file) {
        return getAbsolutePath(new File(file));
    }


    private PkgupPathUtil() {
    }
}
