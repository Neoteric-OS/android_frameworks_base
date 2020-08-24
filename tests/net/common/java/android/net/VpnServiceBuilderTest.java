/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.net;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@RunWith(Parameterized.class)
public class VpnServiceBuilderTest {
    @Parameterized.Parameter
    public boolean isIPv4Route;

    public boolean isIPv6Route;

    @Parameterized.Parameter(1)
    public String routeAddress;

    @Parameterized.Parameter(2)
    public int routePrefixLength;

    @Parameterized.Parameters
    public static Collection<Object[]> testData() {
        return Arrays.asList(new Object[][]{
                {true /* isIPv4Route */, "192.0.2.0" /* routeAddress */, 24
                        /* routePrefixLength */},
                {false, "2001:db8::", 32},
        });
    }

    private VpnService.Builder mBuilder;
    private List<RouteInfo> mRoutes;

    private void addRoute() {
        mBuilder.addRoute(routeAddress, routePrefixLength);
    }

    private void excludeRoute() {
        mBuilder.excludeRoute(routeAddress, routePrefixLength);
    }

    @Before
    public void setUp() {
        mBuilder = new VpnService().new Builder();
        mRoutes = mBuilder.getRoutes();
        isIPv6Route = !isIPv4Route;
    }

    @Test
    public void testExcludedRoute() {
        excludeRoute();

        assertEquals(1, mRoutes.size());
        assertIpFamilyAllowed(false /* allowIPv4 */, false /* allowIPv6 */);
        assertEquals(RouteInfo.RTN_THROW, mRoutes.get(0).getType());
    }

    @Test
    public void testIncludedRoute() {
        addRoute();

        assertEquals(1, mRoutes.size());
        assertIpFamilyAllowed(isIPv4Route, isIPv6Route);
        assertEquals(RouteInfo.RTN_UNICAST, mRoutes.get(0).getType());
    }

    @Test
    public void testDuplicatedRoute() {
        excludeRoute();
        addRoute();

        assertEquals(1, mRoutes.size());
        assertIpFamilyAllowed(isIPv4Route, isIPv6Route);
        assertEquals(RouteInfo.RTN_UNICAST, mRoutes.get(0).getType());

        excludeRoute();

        assertEquals(1, mRoutes.size());
        assertIpFamilyAllowed(isIPv4Route, isIPv6Route);
        assertEquals(RouteInfo.RTN_THROW, mRoutes.get(0).getType());
    }

    private void assertIpFamilyAllowed(boolean allowIPv4, boolean allowIPv6) {
        assertEquals(allowIPv4, mBuilder.mConfig.allowIPv4);
        assertEquals(allowIPv6, mBuilder.mConfig.allowIPv6);
    }
}
