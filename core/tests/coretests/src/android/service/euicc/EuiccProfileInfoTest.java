/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.service.euicc;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.telephony.UiccAccessRule;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class EuiccProfileInfoTest {
    @Test
    public void testWriteToParcel() {
        EuiccProfileInfo p =
                new EuiccProfileInfo.Builder()
                        .setIccid("21430000000000006587")
                        .setNickname("profile nickname")
                        .setProfileName("profile name")
                        .setServiceProviderName("service provider")
                        .setOperatorId(
                                new OperatorId(
                                        new byte[] {0x23, 0x45, 0x67},
                                        new byte[] {1, 2, 3},
                                        new byte[] {4, 5}))
                        .setState(EuiccProfileInfo.ProfileState.ENABLED)
                        .setProfileClass(EuiccProfileInfo.ProfileClass.OPERATIONAL)
                        .setPolicyRules(EuiccProfileInfo.PolicyRule.DO_NOT_DELETE)
                        .setUiccAccessRule(
                                new UiccAccessRule[] {
                                        new UiccAccessRule(new byte[] {}, "package", 12345L)
                                })
                        .build();

        Parcel parcel = Parcel.obtain();
        assertTrue(parcel != null);
        p.writeToParcel(parcel, 0);

        parcel.setDataPosition(0);
        EuiccProfileInfo fromParcel = EuiccProfileInfo.CREATOR.createFromParcel(parcel);

        assertEquals(p, fromParcel);
    }

    @Test
    public void testBuilderAndGetters() {
        EuiccProfileInfo p =
                new EuiccProfileInfo.Builder()
                        .setIccid("21430000000000006587")
                        .setNickname("profile nickname")
                        .setProfileName("profile name")
                        .setServiceProviderName("service provider")
                        .setOperatorId(
                                new OperatorId(
                                        new byte[] {0x23, 0x45, 0x67},
                                        new byte[] {1, 2, 3},
                                        new byte[] {4, 5}))
                        .setState(EuiccProfileInfo.ProfileState.ENABLED)
                        .setProfileClass(EuiccProfileInfo.ProfileClass.OPERATIONAL)
                        .setPolicyRules(EuiccProfileInfo.PolicyRule.DO_NOT_DELETE)
                        .setUiccAccessRule(
                                new UiccAccessRule[] {
                                        new UiccAccessRule(new byte[0], null, 0)
                                })
                        .build();

        assertEquals("21430000000000006587", p.getIccid());
        assertEquals("profile nickname", p.getNickname());
        assertEquals("profile name", p.getProfileName());
        assertEquals("service provider", p.getServiceProviderName());
        assertEquals("325", p.getOperatorId().getMcc());
        assertEquals("764", p.getOperatorId().getMnc());
        assertArrayEquals(new byte[] {1, 2, 3}, p.getOperatorId().getGid1());
        assertArrayEquals(new byte[] {4, 5}, p.getOperatorId().getGid2());
        assertEquals(EuiccProfileInfo.ProfileState.ENABLED, p.getState());
        assertEquals(EuiccProfileInfo.ProfileClass.OPERATIONAL, p.getProfileClass());
        assertEquals(EuiccProfileInfo.PolicyRule.DO_NOT_DELETE, p.getPolicyRules());
        assertTrue(p.hasPolicyRules());
        assertTrue(p.hasPolicyRule(EuiccProfileInfo.PolicyRule.DO_NOT_DELETE));
        assertFalse(p.hasPolicyRule(EuiccProfileInfo.PolicyRule.DO_NOT_DISABLE));
        assertArrayEquals(
                new UiccAccessRule[] {new UiccAccessRule(new byte[0], null, 0)},
                p.getUiccAccessRules());
    }

    @Test
    public void testBuilder_BasedOnAnotherProfile() {
        EuiccProfileInfo p =
                new EuiccProfileInfo.Builder()
                        .setIccid("21430000000000006587")
                        .setNickname("profile nickname")
                        .setProfileName("profile name")
                        .setServiceProviderName("service provider")
                        .setOperatorId(
                                new OperatorId(
                                        new byte[] {0x23, 0x45, 0x67},
                                        new byte[] {1, 2, 3},
                                        new byte[] {4, 5}))
                        .setState(EuiccProfileInfo.ProfileState.ENABLED)
                        .setProfileClass(EuiccProfileInfo.ProfileClass.OPERATIONAL)
                        .setPolicyRules(EuiccProfileInfo.PolicyRule.DO_NOT_DELETE)
                        .setUiccAccessRule(
                                new UiccAccessRule[] {
                                        new UiccAccessRule(new byte[0], null, 0)
                                })
                        .build();

        EuiccProfileInfo copied = new EuiccProfileInfo.Builder(p).build();

        assertEquals(p, copied);
        assertEquals(p.hashCode(), copied.hashCode());
    }

    @Test
    public void testEqualsHashCode() {
        EuiccProfileInfo p =
                new EuiccProfileInfo.Builder()
                        .setIccid("21430000000000006587")
                        .setNickname("profile nickname")
                        .setProfileName("profile name")
                        .setServiceProviderName("service provider")
                        .setOperatorId(
                                new OperatorId(
                                        new byte[] {0x23, 0x45, 0x67},
                                        new byte[] {1, 2, 3},
                                        new byte[] {4, 5}))
                        .setState(EuiccProfileInfo.ProfileState.ENABLED)
                        .setProfileClass(EuiccProfileInfo.ProfileClass.OPERATIONAL)
                        .setPolicyRules(EuiccProfileInfo.PolicyRule.DO_NOT_DELETE)
                        .setUiccAccessRule(
                                new UiccAccessRule[] {
                                        new UiccAccessRule(new byte[0], null, 0)
                                })
                        .build();

        assertTrue(p.equals(p));
        assertFalse(p.equals(new Object()));

        EuiccProfileInfo t = null;
        assertFalse(p.equals(t));

        t = new EuiccProfileInfo.Builder(p).setIccid("21").build();
        assertFalse(p.equals(t));
        assertNotEquals(p.hashCode(), t.hashCode());

        t = new EuiccProfileInfo.Builder(p).setNickname(null).build();
        assertFalse(p.equals(t));
        assertNotEquals(p.hashCode(), t.hashCode());

        t = new EuiccProfileInfo.Builder(p).setProfileName(null).build();
        assertFalse(p.equals(t));
        assertNotEquals(p.hashCode(), t.hashCode());

        t = new EuiccProfileInfo.Builder(p).setServiceProviderName(null).build();
        assertFalse(p.equals(t));
        assertNotEquals(p.hashCode(), t.hashCode());

        t = new EuiccProfileInfo.Builder(p).setOperatorId(null).build();
        assertFalse(p.equals(t));
        assertNotEquals(p.hashCode(), t.hashCode());

        t = new EuiccProfileInfo.Builder(p)
                .setState(EuiccProfileInfo.ProfileState.DISABLED).build();
        assertFalse(p.equals(t));
        assertNotEquals(p.hashCode(), t.hashCode());

        t = new EuiccProfileInfo.Builder(p)
                .setProfileClass(EuiccProfileInfo.ProfileClass.TESTING).build();
        assertFalse(p.equals(t));
        assertNotEquals(p.hashCode(), t.hashCode());

        t = new EuiccProfileInfo.Builder(p).setPolicyRules(0).build();
        assertFalse(p.equals(t));
        assertNotEquals(p.hashCode(), t.hashCode());

        t = new EuiccProfileInfo.Builder(p).setUiccAccessRule(null).build();
        assertFalse(p.equals(t));
        assertNotEquals(p.hashCode(), t.hashCode());
    }

    @Test(expected = IllegalStateException.class)
    public void testBuilderBuild_NoIccid() {
        new EuiccProfileInfo.Builder().build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderSetOperatorMccMnc_Illegal() {
        new EuiccProfileInfo.Builder()
                .setOperatorId(new OperatorId(new byte[] {1, 2, 3, 4}, null, null));
    }

    @Test
    public void testCreatorNewArray() {
        EuiccProfileInfo[] profiles = EuiccProfileInfo.CREATOR.newArray(123);
        assertEquals(123, profiles.length);
    }
}
