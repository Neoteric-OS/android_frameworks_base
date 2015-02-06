/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.nfc.cardemulation;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;

/**
 * @hide
 */
public final class NfcFServiceInfo implements Parcelable {
    static final String TAG = "NfcFServiceInfo";

    /**
     * The service that implements this
     */
    final ResolveInfo mService;

    /**
     * Description of the service
     */
    final String mDescription;

    /**
     * Description of the service registered by API
     */
    String mDynamicDescription;

    /**
     * Localized Description of the service registered by API
     */
    HashMap<String, String> mDynamicDescriptionLocalization;

    /**
     * System Code of the service
     */
    final String mSystemCode;

    /**
     * System Code of the service registered by API
     */
    String mDynamicSystemCode;

    /**
     * NFCID2 of the service
     */
    final String mNfcid2;

    /**
     * NFCID2 of the service registered by API
     */
    String mDynamicNfcid2;

    /**
     * Whether this service should only be started when the device is unlocked.
     */
    final boolean mRequiresDeviceUnlock;

    /**
     * The id of the service banner specified in XML
     */
    final int mBannerResourceId;

    /**
     * The service banner registered by API
     */
    Drawable mDynamicBanner;

    /**
     * The uid of the package the service belongs to
     */
    final int mUid;

    /**
     * @hide
     */
    public NfcFServiceInfo(ResolveInfo info, String description,
            String dynamicDescription, HashMap<String, String> dynaicDescriptionLocalization,
            String systemCode, String dynamicSystemCode, String nfcid2, String dynamicNfcid2, 
            boolean requiresUnlock, int bannerResource, Drawable dynamicBanner, int uid) {
        this.mService = info;
        this.mDescription = description;
        this.mDynamicDescription = dynamicDescription;
        this.mDynamicDescriptionLocalization = dynaicDescriptionLocalization;
        this.mSystemCode = systemCode;
        this.mDynamicSystemCode = dynamicSystemCode;
        this.mNfcid2 = nfcid2;
        this.mDynamicNfcid2 = dynamicNfcid2;
        this.mRequiresDeviceUnlock = requiresUnlock;
        this.mBannerResourceId = bannerResource;
        this.mDynamicBanner = dynamicBanner;
        this.mUid = uid;
    }

    public NfcFServiceInfo(PackageManager pm, ResolveInfo info)
            throws XmlPullParserException, IOException {
        ServiceInfo si = info.serviceInfo;
        XmlResourceParser parser = null;
        try {
            parser = si.loadXmlMetaData(pm, HostNfcFService.SERVICE_META_DATA);
            if (parser == null) {
                throw new XmlPullParserException("No " + HostNfcFService.SERVICE_META_DATA +
                        " meta-data");
            }

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.START_TAG &&
                    eventType != XmlPullParser.END_DOCUMENT) {
                eventType = parser.next();
            }

            String tagName = parser.getName();
            if (!"host-nfcf-service".equals(tagName)) {
                throw new XmlPullParserException(
                        "Meta-data does not start with <host-nfcf-service> tag");
            }

            Resources res = pm.getResourcesForApplication(si.applicationInfo);
            AttributeSet attrs = Xml.asAttributeSet(parser);
            TypedArray sa = res.obtainAttributes(attrs,
                    com.android.internal.R.styleable.HostNfcFService);
            mService = info;
            mDescription = sa.getString(
                    com.android.internal.R.styleable.HostNfcFService_description);
            mDynamicDescription = null;
            mDynamicDescriptionLocalization = null;
            mDynamicSystemCode = null;
            mDynamicNfcid2 = null;
            mRequiresDeviceUnlock = sa.getBoolean(
                    com.android.internal.R.styleable.HostNfcFService_requireDeviceUnlock,
                    false);
            mBannerResourceId = sa.getResourceId(
                    com.android.internal.R.styleable.HostNfcFService_nfcFServiceBanner, -1);
            sa.recycle();
            mDynamicBanner = null;

            String systemCode = null;
            String nfcid2 = null;
            final int depth = parser.getDepth();

            while (((eventType = parser.next()) != XmlPullParser.END_TAG ||
                    parser.getDepth() > depth) && eventType != XmlPullParser.END_DOCUMENT) {
                tagName = parser.getName();
                if (eventType == XmlPullParser.START_TAG &&
                        "system-code-filter".equals(tagName) && systemCode == null) {
                    final TypedArray a = res.obtainAttributes(attrs,
                            com.android.internal.R.styleable.SystemCodeFilter);
                    systemCode = a.getString(
                            com.android.internal.R.styleable.SystemCodeFilter_name).toUpperCase();
                    if (!CardEmulation.isValidSystemCode(systemCode) &&
                            !systemCode.equals("null")) {
                        Log.e(TAG, "Invalid System Code: " + systemCode);
                        systemCode = null;
                    }
                    a.recycle();
                } else if (eventType == XmlPullParser.START_TAG &&
                        "nfcid2-filter".equals(tagName) && nfcid2 == null) {
                    final TypedArray a = res.obtainAttributes(attrs,
                            com.android.internal.R.styleable.Nfcid2Filter);
                    nfcid2 = a.getString(
                            com.android.internal.R.styleable.Nfcid2Filter_name).toUpperCase();
                    if (!CardEmulation.isValidNfcid2(nfcid2) && !nfcid2.equals("random") &&
                            !nfcid2.equals("null")) {
                        Log.e(TAG, "Invalid NFCID2: " + nfcid2);
                        nfcid2 = null;
                    }
                    a.recycle();
                }
            }
            mSystemCode = (systemCode == null ? "null" : systemCode);
            mNfcid2 = (nfcid2 == null ? "null" : nfcid2);
        } catch (NameNotFoundException e) {
            throw new XmlPullParserException("Unable to create context for: " + si.packageName);
        } finally {
            if (parser != null) parser.close();
        }
        // Set uid
        mUid = si.applicationInfo.uid;
    }

    public ComponentName getComponent() {
        return new ComponentName(mService.serviceInfo.packageName,
                mService.serviceInfo.name);
    }

    public String getSystemCode() {
        return mDynamicSystemCode == null ? mSystemCode : mDynamicSystemCode;
    }

    public void setOrReplaceDynamicSystemCode(String systemCode) {
        mDynamicSystemCode = systemCode;
    }

    public String getNfcid2() {
        return mDynamicNfcid2 == null ? mNfcid2 : mDynamicNfcid2;
    }

    public void setOrReplaceDynamicNfcid2(String nfcid2) {
        mDynamicNfcid2 = nfcid2;
    }

    public boolean requiresUnlock() {
        return mRequiresDeviceUnlock;
    }

    public String getDescription() {
        String language = "en"; // TODO: get current language from the system
        String description = mDescription;
        if (mDynamicDescription != null) {
            description = mDynamicDescription;
            if (mDynamicDescriptionLocalization != null) {
                if (mDynamicDescriptionLocalization.get(language) != null) {
                    description = mDynamicDescriptionLocalization.get(language);
                }
            }
        }
        return description;
    }

    public void setOrReplaceDynamicDynamicDescription(
            String description, HashMap<String, String>descriptionLocalization) {
        mDynamicDescription = description;
        mDynamicDescriptionLocalization = descriptionLocalization;
    }

    public int getUid() {
        return mUid;
    }

    public CharSequence loadLabel(PackageManager pm) {
        return mService.loadLabel(pm);
    }

    public Drawable loadIcon(PackageManager pm) {
        return mService.loadIcon(pm);
    }

    public Drawable loadBanner(PackageManager pm) {
        Resources res;
        try {
            res = pm.getResourcesForApplication(mService.serviceInfo.packageName);
            Drawable banner;
            if (mDynamicBanner == null) {
                banner = res.getDrawable(mBannerResourceId);
            } else {
                banner = mDynamicBanner;
            }
            return banner;
        } catch (NotFoundException e) {
            Log.e(TAG, "Could not load banner.");
            return null;
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Could not load banner.");
            return null;
        }
    }

    public void setOrReplaceDynamicBanner(Drawable banner) {
        mDynamicBanner = banner;
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder("NfcFService: ");
        out.append(getComponent());
        out.append(", description: " + mDescription);
        if (mDynamicDescription != null) {
            out.append(", dynamic description: " + mDynamicDescription);
        }
        out.append(", System Code: " + mSystemCode);
        if (mDynamicSystemCode != null) {
            out.append(", dynamic System Code: " + mDynamicSystemCode);
        }
        out.append(", NFCID2: " + mNfcid2);
        if (mDynamicNfcid2 != null) {
            out.append(", dynamic NFCID2: " + mDynamicNfcid2);
        }
        return out.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NfcFServiceInfo)) return false;
        NfcFServiceInfo thatService = (NfcFServiceInfo) o;

        return thatService.getComponent().equals(this.getComponent());
    }

    @Override
    public int hashCode() {
        return getComponent().hashCode();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        mService.writeToParcel(dest, flags);
        dest.writeString(mDescription);
        dest.writeInt(mDynamicDescription != null ? 1 : 0);
        if (mDynamicDescription != null) {
            dest.writeString(mDynamicDescription);
        }
        dest.writeInt(mDynamicDescriptionLocalization != null ? 1 : 0);
        if (mDynamicDescriptionLocalization != null) {
            dest.writeInt(mDynamicDescriptionLocalization.size());
            for (String key : mDynamicDescriptionLocalization.keySet()) {
                dest.writeString(key);
                dest.writeString(mDynamicDescriptionLocalization.get(key));
            }
        }
        dest.writeString(mSystemCode);
        dest.writeInt(mDynamicSystemCode != null ? 1 : 0);
        if (mDynamicSystemCode != null) {
            dest.writeString(mDynamicSystemCode);
        }
        dest.writeString(mNfcid2);
        dest.writeInt(mDynamicNfcid2 != null ? 1 : 0);
        if (mDynamicNfcid2 != null) {
            dest.writeString(mDynamicNfcid2);
        }
        dest.writeInt(mRequiresDeviceUnlock ? 1 : 0);
        dest.writeInt(mBannerResourceId);
        // TODO: save mDynamicBanner
        dest.writeInt(mUid);
    };

    public static final Parcelable.Creator<NfcFServiceInfo> CREATOR =
            new Parcelable.Creator<NfcFServiceInfo>() {
        @Override
        public NfcFServiceInfo createFromParcel(Parcel source) {
            ResolveInfo info = ResolveInfo.CREATOR.createFromParcel(source);
            String description = source.readString();
            String dynamicDescription = null;
            if (source.readInt() != 0) {
                dynamicDescription = source.readString();
            }
            HashMap<String, String> dynamicDescriptionLocalization = null;
            if (source.readInt() != 0) {
                dynamicDescriptionLocalization = new HashMap<String, String>();
                int size = source.readInt();
                for (int i = 0; i < size; i++) {
                    String key = source.readString();
                    String value = source.readString();
                    dynamicDescriptionLocalization.put(key,value);
                }
            }
            String systemCode = source.readString();
            String dynamicSystemCode = null;
            if (source.readInt() != 0) {
                dynamicSystemCode = source.readString();
            }
            String nfcid2 = source.readString();
            String dynamicNfcid2 = null;
            if (source.readInt() != 0) {
                dynamicNfcid2 = source.readString();
            }
            boolean requiresUnlock = (source.readInt() != 0) ? true : false;
            int bannerResourceId = source.readInt();
            // create dynamicBanner
            Drawable dynamicBanner = null;
            int uid = source.readInt();
            NfcFServiceInfo service = new NfcFServiceInfo(info,
                    description, dynamicDescription, dynamicDescriptionLocalization,
                    systemCode, dynamicSystemCode, nfcid2, dynamicNfcid2,
                    requiresUnlock, bannerResourceId, dynamicBanner, uid);
            return service;
        }

        @Override
        public NfcFServiceInfo[] newArray(int size) {
            return new NfcFServiceInfo[size];
        }
    };

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("    " + getComponent() +
                " (Description: " + getDescription() + ")");
        pw.println("    System Code: " + getSystemCode());
        pw.println("    NFCID2: " + getNfcid2());
    }
}