/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.rrotests;

/**
 * Tests for runtime resource overlay of various resource configurations.
 *
 * The naming convention textMatrixABCDEF refers to in which packages and which
 * configurations a resource is defined (1 if the resource is defined). If
 * defined, a slot is always given the same value.
 *
 * SLOT  PACKAGE           CONFIGURATION  VALUE
 * A     target package    (default)      100
 * B     target package    -sv            200
 * C     OverlayAppFirst   (default)      300
 * D     OverlayAppFirst   -sv            400
 * E     OverlayAppSecond  (default)      500
 * F     OverlayAppSecond  -sv            600
 *
 * Example: in testMatrix101110, the base package defines the
 * R.integer.matrix101110 resource for the default configuration (value 100),
 * app-overlay-1 defines it for both default and Swedish configurations (values
 * 300 and 400, respectively), and app-overlay-2 defines it for the default
 * configuration (value 500). If both overlays are loaded, the expected value
 * after setting the language to Swedish is 400.
 *
 * These tests require the file system be prepared before execution: use
 * frameworks/base/core/tests/rro_tests/run.sh for this.
 */
public class MatrixTests extends BaseTestCase {
    public void testMatrix100000() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_100000, new int[]{100, 100, 100});
    }

    public void testMatrix100001() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_100001, new int[]{100, 100, 600});
    }

    public void testMatrix100010() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_100010, new int[]{100, 100, 500});
    }

    public void testMatrix100011() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_100011, new int[]{100, 100, 600});
    }

    public void testMatrix100100() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_100100, new int[]{100, 400, 400});
    }

    public void testMatrix100101() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_100101, new int[]{100, 400, 600});
    }

    public void testMatrix100110() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_100110, new int[]{100, 400, 400});
    }

    public void testMatrix100111() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_100111, new int[]{100, 400, 600});
    }

    public void testMatrix101000() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_101000, new int[]{100, 300, 300});
    }

    public void testMatrix101001() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_101001, new int[]{100, 300, 600});
    }

    public void testMatrix101010() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_101010, new int[]{100, 300, 500});
    }

    public void testMatrix101011() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_101011, new int[]{100, 300, 600});
    }

    public void testMatrix101100() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_101100, new int[]{100, 400, 400});
    }

    public void testMatrix101101() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_101101, new int[]{100, 400, 600});
    }

    public void testMatrix101110() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_101110, new int[]{100, 400, 400});
    }

    public void testMatrix101111() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_101111, new int[]{100, 400, 600});
    }

    public void testMatrix110000() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_110000, new int[]{200, 200, 200});
    }

    public void testMatrix110001() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_110001, new int[]{200, 200, 600});
    }

    public void testMatrix110010() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_110010, new int[]{200, 200, 200});
    }

    public void testMatrix110011() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_110011, new int[]{200, 200, 600});
    }

    public void testMatrix110100() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_110100, new int[]{200, 400, 400});
    }

    public void testMatrix110101() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_110101, new int[]{200, 400, 600});
    }

    public void testMatrix110110() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_110110, new int[]{200, 400, 400});
    }

    public void testMatrix110111() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_110111, new int[]{200, 400, 600});
    }

    public void testMatrix111000() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_111000, new int[]{200, 200, 200});
    }

    public void testMatrix111001() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_111001, new int[]{200, 200, 600});
    }

    public void testMatrix111010() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_111010, new int[]{200, 200, 200});
    }

    public void testMatrix111011() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_111011, new int[]{200, 200, 600});
    }

    public void testMatrix111100() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_111100, new int[]{200, 400, 400});
    }

    public void testMatrix111101() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_111101, new int[]{200, 400, 600});
    }

    public void testMatrix111110() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_111110, new int[]{200, 400, 400});
    }

    public void testMatrix111111() throws Throwable {
        setSwedishLocale();
        assertResource(R.integer.matrix_111111, new int[]{200, 400, 600});
    }
}
