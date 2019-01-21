/*
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

package com.android.server.net.ipmemorystore;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.annotation.NonNull;
import android.content.Context;
import android.net.ipmemorystore.Blob;
import android.net.ipmemorystore.IOnBlobRetrievedListener;
import android.net.ipmemorystore.IOnNetworkAttributesRetrieved;
import android.net.ipmemorystore.IOnSameNetworkResponseListener;
import android.net.ipmemorystore.IOnStatusListener;
import android.net.ipmemorystore.NetworkAttributes;
import android.net.ipmemorystore.NetworkAttributesParcelable;
import android.net.ipmemorystore.SameL3NetworkResponse;
import android.net.ipmemorystore.SameL3NetworkResponseParcelable;
import android.net.ipmemorystore.Status;
import android.net.ipmemorystore.StatusParcelable;
import android.os.RemoteException;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Objects;

/** Unit tests for {@link IpMemoryStoreService}. */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class IpMemoryStoreServiceTest {
    private static final String TEST_CLIENT_ID = "testClientId";
    private static final String TEST_DATA_NAME = "testData";

    private static final String[] FAKE_KEYS = { "fakeKey1", "fakeKey2", "fakeKey3", "fakeKey4" };

    @Mock
    private Context mMockContext;
    private File mDbFile;

    private IpMemoryStoreService mService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        final Context context = InstrumentationRegistry.getContext();
        final File dir = context.getFilesDir();
        mDbFile = new File(dir, "test.db");
        doReturn(mDbFile).when(mMockContext).getDatabasePath(anyString());
        mService = new IpMemoryStoreService(mMockContext);
    }

    @After
    public void tearDown() {
        mService.shutdown();
        mDbFile.delete();
    }

    // Helpers to simplify matching
    private StatusParcelable statusThat(@NonNull final ArgumentMatcher<Status> matcher) {
        return argThat(parcelable -> matcher.matches(new Status(parcelable)));
    }
    private NetworkAttributesParcelable attributesThat(
            @NonNull final ArgumentMatcher<NetworkAttributes> matcher) {
        return argThat(parcelable -> matcher.matches(new NetworkAttributes(parcelable)));
    }
    private SameL3NetworkResponseParcelable sameNetworkThat(
            @NonNull final ArgumentMatcher<SameL3NetworkResponse> matcher) {
        return argThat(parcelable -> matcher.matches(new SameL3NetworkResponse(parcelable)));
    }

    private void storeAttributes(final String l2Key, final NetworkAttributes na)
            throws RemoteException {
        final IOnStatusListener listener = mock(IOnStatusListener.class);
        mService.storeNetworkAttributes(l2Key, na.toParcelable(), listener);
        verify(listener, timeout(5000).times(1)).onComplete(statusThat(s -> s.isSuccess()));
    }

    @Test
    public void testNetworkAttributes() throws RemoteException {
        final NetworkAttributes.Builder na = new NetworkAttributes.Builder();
        try {
            na.setAssignedV4Address(
                    (Inet4Address) Inet4Address.getByAddress(new byte[]{1, 2, 3, 4}));
        } catch (UnknownHostException e) { /* Can't happen */ }
        na.setGroupHint("hint1");
        na.setMtu(219);
        final String l2Key = FAKE_KEYS[0];
        NetworkAttributes attributes = na.build();
        storeAttributes(l2Key, attributes);

        final IOnNetworkAttributesRetrieved listener = mock(IOnNetworkAttributesRetrieved.class);
        mService.retrieveNetworkAttributes(l2Key, listener);
        verify(listener, timeout(5000).times(1)).onNetworkAttributesRetrieved(
                statusThat(s -> s.isSuccess()),
                eq(l2Key),
                attributesThat(a -> a.equals(attributes)));

        final NetworkAttributes.Builder na2 = new NetworkAttributes.Builder();
        try {
            na.setDnsAddresses(Arrays.asList(
                    new InetAddress[] {Inet6Address.getByName("0A1C:2E40:480A::1CA6")}));
        } catch (UnknownHostException e) { /* Still can't happen */ }
        final NetworkAttributes attributes2 = na2.build();
        storeAttributes(l2Key, attributes2);

        reset(listener);
        mService.retrieveNetworkAttributes(l2Key, listener);
        verify(listener, timeout(5000).times(1)).onNetworkAttributesRetrieved(
                statusThat(s -> s.isSuccess()),
                eq(l2Key),
                attributesThat(a -> Objects.equals(attributes.groupHint, a.groupHint)
                        && Objects.equals(attributes.assignedV4Address, a.assignedV4Address)
                        && Objects.equals(attributes.mtu, a.mtu)
                        && Objects.equals(attributes2.dnsAddresses, a.dnsAddresses)
                ));

        reset(listener);
        mService.retrieveNetworkAttributes(l2Key + "nonexistent", listener);
        verify(listener, timeout(5000).times(1)).onNetworkAttributesRetrieved(
                statusThat(s -> s.isSuccess()),
                eq(l2Key + "nonexistent"),
                isNull());

        // Verify that this test does not miss any new field added later.
        // If any field is added to NetworkAttributes it must be tested here for storing
        // and retrieving.
        assertEquals(4, Arrays.stream(NetworkAttributes.class.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers())).count());
    }

    @Test
    public void testInvalidAttributes() throws RemoteException {
        final IOnStatusListener listener = mock(IOnStatusListener.class);
        mService.storeNetworkAttributes("key", null, listener);
        verify(listener, timeout(5000).times(1)).onComplete(statusThat(s ->
                // Should fail on storing null attributes
                !s.isSuccess() && Status.ERROR_ILLEGAL_ARGUMENT == s.resultCode
        ));

        reset(listener);
        final NetworkAttributes na = new NetworkAttributes.Builder().setMtu(2).build();
        mService.storeNetworkAttributes(null, na.toParcelable(), listener);
        verify(listener, timeout(5000).times(1)).onComplete(statusThat(s ->
                // Should fail on storing attributes for a null key
                !s.isSuccess() && Status.ERROR_ILLEGAL_ARGUMENT == s.resultCode
        ));

        reset(listener);
        mService.storeNetworkAttributes(null, null, listener);
        verify(listener, timeout(5000).times(1)).onComplete(statusThat(s ->
            // Should fail on storing null attributes for a null key
                !s.isSuccess() && Status.ERROR_ILLEGAL_ARGUMENT == s.resultCode
        ));

        final IOnNetworkAttributesRetrieved listener2 = mock(IOnNetworkAttributesRetrieved.class);
        mService.retrieveNetworkAttributes(null, listener2);
        verify(listener2, timeout(5000).times(1)).onNetworkAttributesRetrieved(
                statusThat(s -> !s.isSuccess() && Status.ERROR_ILLEGAL_ARGUMENT == s.resultCode),
                isNull(), // key
                isNull()); // attr
    }

    @Test
    public void testPrivateData() throws RemoteException {
        final Blob blob = new Blob();
        blob.data = new byte[] { -3, 6, 8, -9, 12, -128, 0, 89, 112, 91, -34 };
        final String l2Key = FAKE_KEYS[0];
        final IOnStatusListener statusListener = mock(IOnStatusListener.class);
        mService.storeBlob(l2Key, TEST_CLIENT_ID, TEST_DATA_NAME, blob, statusListener);
        verify(statusListener, timeout(5000).times(1)).onComplete(statusThat(s -> s.isSuccess()));

        final IOnBlobRetrievedListener blobListener = mock(IOnBlobRetrievedListener.class);
        mService.retrieveBlob(l2Key, TEST_CLIENT_ID, TEST_DATA_NAME, blobListener);
        verify(blobListener, timeout(5000).times(1)).onBlobRetrieved(
                statusThat(s -> s.isSuccess()),
                eq(l2Key),
                eq(TEST_DATA_NAME),
                argThat(b -> Arrays.equals(b.data, blob.data)));

        reset(blobListener);
        mService.retrieveBlob(l2Key, TEST_CLIENT_ID, TEST_DATA_NAME + "2", blobListener);
        verify(blobListener, timeout(5000).times(1)).onBlobRetrieved(
                statusThat(s -> s.isSuccess()),
                eq(l2Key),
                eq(TEST_DATA_NAME + "2"),
                argThat(b -> null == b.data));
    }

    @Test
    public void testFindL2Key() {
        // TODO : implement this
    }

    private void assertNetworksSameness(final String key1, final String key2, final int sameness)
            throws RemoteException {
        final IOnSameNetworkResponseListener listener = mock(IOnSameNetworkResponseListener.class);
        mService.isSameNetwork(key1, key2, listener);
        verify(listener, timeout(5000).times(1)).onSameNetworkResponse(
                statusThat(s -> s.isSuccess()),
                argThat((s) -> sameness == new SameL3NetworkResponse(s).getNetworkSameness()));
    }

    @Test
    public void testIsSameNetwork() throws UnknownHostException, RemoteException {
        final NetworkAttributes.Builder na = new NetworkAttributes.Builder();
        na.setAssignedV4Address((Inet4Address) Inet4Address.getByAddress(new byte[]{1, 2, 3, 4}));
        na.setGroupHint("hint1");
        na.setMtu(219);
        na.setDnsAddresses(Arrays.asList(Inet6Address.getByName("0A1C:2E40:480A::1CA6")));

        storeAttributes(FAKE_KEYS[0], na.build());
        // 0 and 1 have identical attributes
        storeAttributes(FAKE_KEYS[1], na.build());

        // Hopefully only the MTU being different still means it's the same network
        na.setMtu(200);
        storeAttributes(FAKE_KEYS[2], na.build());

        // Hopefully different MTU, assigned V4 address and grouphint make a different network,
        // even with identical DNS addresses
        na.setAssignedV4Address(null);
        na.setGroupHint("hint2");
        storeAttributes(FAKE_KEYS[3], na.build());

        assertNetworksSameness(FAKE_KEYS[0], FAKE_KEYS[1], SameL3NetworkResponse.NETWORK_SAME);
        assertNetworksSameness(FAKE_KEYS[0], FAKE_KEYS[2], SameL3NetworkResponse.NETWORK_SAME);
        assertNetworksSameness(FAKE_KEYS[1], FAKE_KEYS[2], SameL3NetworkResponse.NETWORK_SAME);
        assertNetworksSameness(FAKE_KEYS[0], FAKE_KEYS[3], SameL3NetworkResponse.NETWORK_DIFFERENT);
        assertNetworksSameness(FAKE_KEYS[0], "neverInsertedKey",
                SameL3NetworkResponse.NETWORK_NEVER_CONNECTED);

        final IOnSameNetworkResponseListener listener = mock(IOnSameNetworkResponseListener.class);
        mService.isSameNetwork(null, null, listener);
        verify(listener, timeout(5000).times(1)).onSameNetworkResponse(
                statusThat(s -> !s.isSuccess() && Status.ERROR_ILLEGAL_ARGUMENT == s.resultCode),
                isNull());
    }
}
