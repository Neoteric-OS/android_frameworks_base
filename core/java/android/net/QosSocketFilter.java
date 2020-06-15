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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Objects;

/**
 * Filters a {@link QosSession} according to the binding on the provided {@link Socket}.
 *
 * @hide
 */
@SystemApi
public final class QosSocketFilter extends QosFilter implements Parcelable {

    private static final String TAG = QosSocketFilter.class.getSimpleName();

    @Nullable
    private ParcelFileDescriptor mParcelFileDescriptor;

    @NonNull
    private final Network mNetwork;

    /* We are parcelling the file descriptor and not the address since the file descriptor
     * is the real source of truth. */
    @Nullable
    private InetSocketAddress mLocalAddress;

    QosSocketFilter(@NonNull final Network network, @NonNull final Socket socket) {
        mNetwork = Objects.requireNonNull(network, "network cannot be null");
        Objects.requireNonNull(socket, "socket cannot be null");
        try {
            mParcelFileDescriptor = ParcelFileDescriptor.dup(socket.getFileDescriptor$());
        } catch (IOException e) {
            //This occurs when the socket is not bound.  For testing purposes, we allow this
            //state when TRANSPORT_TEST is used.  If the transport on the network is not set to
            //TRANSPORT_TEST though, ConnectivityService will error.
            mParcelFileDescriptor = null;
            Log.w(TAG, "ParcelFileDescriptor is invalid.");
        }
    }

    /**
     * The local address of the socket's binding
     *
     * @return the local address
     */
    @Nullable
    public InetSocketAddress getLocalAddress() {
        if (mLocalAddress == null) {
            mLocalAddress = getAddressFromFileDescriptor();
        }
        return mLocalAddress;
    }

    /**
     * The parcel file descriptor wrapped around the socket's file descriptor.
     *
     * @return the parcel file descriptor of the socket.
     */
    @Nullable
    public ParcelFileDescriptor getParcelFileDescriptor() {
        return mParcelFileDescriptor;
    }

    /**
     * The network used with this filter.
     * @return the registered {@link Network}
     */
    @NonNull
    @Override
    public Network getNetwork() {
        return mNetwork;
    }

    @Nullable
    private InetSocketAddress getAddressFromFileDescriptor() {
        try {
            if (mParcelFileDescriptor == null) {
                return null;
            }

            FileDescriptor fd = mParcelFileDescriptor.getFileDescriptor();
            if (fd == null) {
                return null;
            }

            SocketAddress address = Os.getsockname(fd);
            if (address == null) {
                Log.w(TAG, "getAddressFromFileDescriptor: socket address should not be null!");
                return null;
            } else if (address instanceof InetSocketAddress) {
                return (InetSocketAddress) address;
            } else {
                Log.d(TAG, "getAddressFromFileDescriptor: address is of different type: "
                        + address.toString());
                return null;
            }
        } catch (ErrnoException e) {
            Log.e(TAG, "getAddressFromFileDescriptor: getLocalAddress exception", e);
            return null;
        }
    }

    /* Parcelable methods */
    QosSocketFilter(final Parcel in) {
        mNetwork = Objects.requireNonNull(Network.CREATOR.createFromParcel(in));
        if (in.readInt() == 1) {
            mParcelFileDescriptor =
                    Objects.requireNonNull(ParcelFileDescriptor.CREATOR.createFromParcel(in));
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest, final int flags) {
        mNetwork.writeToParcel(dest, 0);
        if (mParcelFileDescriptor != null && mParcelFileDescriptor.getFileDescriptor() != null) {
            dest.writeInt(1);
            mParcelFileDescriptor.writeToParcel(dest, 0);
        } else {
            dest.writeInt(2);
        }
    }

    @NonNull
    public static final Creator<QosSocketFilter> CREATOR = new Creator<QosSocketFilter>() {
        @NonNull
        @Override
        public QosSocketFilter createFromParcel(final Parcel in) {
            return new QosSocketFilter(in);
        }

        @NonNull
        @Override
        public QosSocketFilter[] newArray(int size) {
            return new QosSocketFilter[size];
        }
    };

    @Override
    public String toString() {
        return "QosSocketFilter{"
                + "mNetwork=" + mNetwork
                //Not calling #getLocalAddress() since since that method lazy loads the value
                + ", mLocalAddress=" + mLocalAddress
                + '}';
    }

    /**
     * Sent through {@link QosCallback#onError(QosCallbackException)} when the socket is not bound.
     */
    public static class SocketNotBoundException extends Exception {

        public SocketNotBoundException() {
            super();
        }

        public @QosCallbackException.ExceptionType int getExceptionType() {
            return QosCallbackException.EX_TYPE_FILTER_SOCKET_NOT_BOUND;
        }

        @NonNull
        @Override
        public String getMessage() {
            return "The socket is unbound";
        }
    }
}
