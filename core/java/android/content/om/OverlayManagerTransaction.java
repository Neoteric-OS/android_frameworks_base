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

package android.content.om;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Container for a batch of requests to the OverlayManagerService.
 *
 * Transactions are created using a builder interface. Example usage:
 *
 * final OverlayManager om = (OverlayManager)ctx.getSystemService(Context.OVERLAY_SERVICE);
 * final OverlayManagerTransaction t = new OverlayManagerTransaction.Builder()
 *     .setEnabled(...)
 *     .setHighestPriority(...)
 *     .build();
 * om.commit(t);
 *
 * @hide
 */
public class OverlayManagerTransaction
        implements Iterable<OverlayManagerTransaction.Request>, Parcelable {
    // TODO: remove @hide from this class when IOverlayManager is added to the
    // SDK, but keep OverlayManagerTransaction.Request @hidden
    private final List<Request> mRequests;

    OverlayManagerTransaction(@NonNull final List<Request> requests) {
        checkNotNull(requests);
        if (requests.contains(null)) {
            throw new IllegalArgumentException("null request");
        }
        mRequests = requests;
    }

    private OverlayManagerTransaction(@NonNull final Parcel source) {
        final int size = source.readInt();
        mRequests = new ArrayList<Request>(size);
        for (int i = 0; i < size; i++) {
            final int request = source.readInt();
            final String packageName = source.readString();
            final int userId = source.readInt();
            final String extra = source.readString();
            mRequests.add(new Request(request, packageName, userId, extra));
        }
    }

    @Override
    public Iterator<Request> iterator() {
        return mRequests.iterator();
    }

    @Override
    public String toString() {
        return String.format("OverlayManagerTransaction { mRequests = %s }", mRequests);
    }

    /**
     * A single unit of the transaction, such as a request to enable an overlay.
     *
     * @hide
     */
    public static class Request {
        public static final int TYPE_SET_ENABLED = 0;
        public static final int TYPE_SET_DISABLED = 1;
        public static final int TYPE_SET_PRIORITY = 2;
        public static final int TYPE_SET_LOWEST_PRIORITY = 3;
        public static final int TYPE_SET_HIGHEST_PRIORITY = 4;

        public final int type;
        public final String packageName;
        public final int userId;
        public final String extra;

        public Request(final int type, @NonNull final String packageName, final int userId) {
            this(type, packageName, userId, null);
        }

        public Request(final int type, @NonNull final String packageName, final int userId,
                @Nullable final String extra) {
            this.type = type;
            this.packageName = packageName;
            this.userId = userId;
            this.extra = extra;
        }

        @Override
        public String toString() {
            return String.format("Request { type=0x%02x (%s) packageName=%s userId=%d extra=%s }",
                    type, typeToString(), packageName, userId, extra);
        }

        /**
         * Translate the request type into a human readable string. Only
         * intended for debugging.
         *
         * @hide
         */
        public String typeToString() {
            switch (type) {
                case TYPE_SET_ENABLED: return "TYPE_SET_ENABLED";
                case TYPE_SET_DISABLED: return "TYPE_SET_DISABLED";
                case TYPE_SET_PRIORITY: return "TYPE_SET_PRIORITY";
                case TYPE_SET_LOWEST_PRIORITY: return "TYPE_SET_LOWEST_PRIORITY";
                case TYPE_SET_HIGHEST_PRIORITY: return "TYPE_SET_HIGHEST_PRIORITY";
                default: return String.format("TYPE_UNKNOWN (0x%02x)", type);
            }
        }
    }

    /**
     * Builder class for OverlayManagerTransaction objects.
     *
     * @hide
     */
    public static class Builder {
        private final List<Request> mRequests = new ArrayList<>();

        /**
         * Request that an overlay package be enabled or disabled.
         *
         * It is always possible to disable an overlay, but due to technical and
         * security reasons it may not always be possible to enable an overlay. An
         * example of the latter is when the related target package is not
         * installed. If the technical obstacle is later overcome, the overlay is
         * automatically enabled at that point in time.
         *
         * An enabled overlay is a part of target package's resources, i.e. it will
         * be part of any lookups performed via {@link android.content.res.Resources}
         * and {@link android.content.res.AssetManager}. A disabled overlay will no
         * longer affect the resources of the target package. If the target is
         * currently running, its outdated resources will be replaced by new ones.
         * This happens the same way as when an application enters or exits split
         * window mode.
         *
         * @param packageName The name of the overlay package.
         * @param enable true to enable the overlay, false to disable it.
         * @param userId The user for which to change the overlay.
         * @return this Builder object, so you can chain additional requests
         */
        public Builder setEnabled(@NonNull String packageName, boolean enable, int userId) {
            checkNotNull(packageName);
            final int r = enable ? Request.TYPE_SET_ENABLED : Request.TYPE_SET_DISABLED;
            mRequests.add(new Request(r, packageName, userId));
            return this;
        }

        /**
         * Request that the priority of the given overlay is set to just higher
         * than the overlay with package name newParentPackageName. Both
         * overlay packages must have the same target and user.
         *
         * @param packageName The name of the overlay package whose priority should
         *        be adjusted.
         * @param newParentPackageName The name of the overlay package the newly
         *        adjusted overlay package should just outrank.
         * @param userId The user for which to change the overlay.
         * @return this Builder object, so you can chain additional requests
         */
        public Builder setPriority(@NonNull String packageName,
                @NonNull String newParentPackageName, int userId) {
            checkNotNull(packageName);
            checkNotNull(newParentPackageName);
            mRequests.add(new Request(Request.TYPE_SET_PRIORITY, packageName, userId,
                        newParentPackageName));
            return this;
        }

        /**
         * Request that the priority of the given overlay is set to the highest
         * priority relative to the other overlays with the same target and
         * user.
         *
         * @param packageName The name of the overlay package whose priority
         *        should be adjusted.
         * @param userId The user for which to change the overlay.
         * @return this Builder object, so you can chain additional requests
         */
        public Builder setHighestPriority(@NonNull String packageName, int userId) {
            checkNotNull(packageName);
            mRequests.add(new Request(Request.TYPE_SET_HIGHEST_PRIORITY, packageName, userId));
            return this;
        }

        /**
         * Request that the priority of the given overlay is set to the lowest
         * priority relative to the other overlays for the same target and
         * user.
         *
         * @see getOverlayInfosForTarget
         *
         * @param packageName The name of the overlay package whose priority
         *        should be adjusted.
         * @param userId The user for which to change the overlay.
         * @return this Builder object, so you can chain additional requests
         */
        public Builder setLowestPriority(@NonNull String packageName, int userId) {
            checkNotNull(packageName);
            mRequests.add(new Request(Request.TYPE_SET_LOWEST_PRIORITY, packageName, userId));
            return this;
        }

        /**
         * Create a new transaction out of the requests added so far. Execute
         * the transaction by calling OverlayManager#commit.
         *
         * @see OverlayManager#commit
         * @return a new transaction
         */
        public OverlayManagerTransaction build() {
            return new OverlayManagerTransaction(mRequests);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        final int size = mRequests.size();
        dest.writeInt(size);
        for (int i = 0; i < size; i++) {
            final Request req = mRequests.get(i);
            dest.writeInt(req.type);
            dest.writeString(req.packageName);
            dest.writeInt(req.userId);
            dest.writeString(req.extra);
        }
    }

    public static final Parcelable.Creator<OverlayManagerTransaction> CREATOR =
            new Parcelable.Creator<OverlayManagerTransaction>() {

        @Override
        public OverlayManagerTransaction createFromParcel(Parcel source) {
            return new OverlayManagerTransaction(source);
        }

        @Override
        public OverlayManagerTransaction[] newArray(int size) {
            return new OverlayManagerTransaction[size];
        }
    };
}
