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

package android.app.timezone;

import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Description of the state of time zone rules.
 *
 * <p>The following properties are provided:
 * <ul>
 *     <li>
 *         systemRulesVersion - the IANA rules version that shipped with the OS (always present).
 *         e.g. "2017a".
 *     </li>
 *     <li>
 *         bundleFormatVersionSupported - the bundle format version supported by this device. In the
 *         form major.minor, where versions are three character decimal values in ASCII like
 *         "001.001". Major versions differences are not guaranteed compatible - e.g. 002.001 is not
 *         compatible with 001.001 or 003.001 devices. Minor versions should be backwards
 *         compatible, i.e. 001.002 will be compatible with 001.001 devices but not 001.003 devices.
 *     </li>
 *     <li>
 *         operationInProgress - {@code true} if there is an install / uninstall operation currently
 *         happening. The fields below will be left unset in this case.
 *     </li>
 *     <li>
 *         bundleInstalled - {@code true} if there is a supplementary bundle installed,
 *         {@code false} otherwise. Installed bundles currently require a reboot to become active.
 *     </li>
 *     <li>
 *         bundleRulesVersion - [present if bundleInstalled == true], the IANA rules version from
 *         the installed bundle. e.g. "2017a"
 *     </li>
 *     <li>
 *         androidRevision - [present if bundleInstalled == true], the Android revision for this
 *         bundle. a three character decimal value in ASCII like "001". Allows there to be several
 *         revisions for a given IANA rules release. Numerically higher is better.
 *     </li>
 * </ul>
 *
 * @hide
 */
@SystemApi
public final class RulesState implements Parcelable {
    private final String systemRulesVersion;
    private final String bundleFormatVersionSupported;
    private final boolean operationInProgress;
    private final boolean bundleInstalled;
    private final String bundleRulesVersion;
    private final String bundleAndroidRevision;

    public RulesState(String systemRulesVersion, String bundleFormatVersionSupported,
            boolean operationInProgress, boolean bundleInstalled, String bundleRulesVersion,
            String androidRevision) {
        this.systemRulesVersion = systemRulesVersion;
        this.bundleFormatVersionSupported = bundleFormatVersionSupported;
        this.operationInProgress = operationInProgress;
        this.bundleInstalled = bundleInstalled;
        this.bundleRulesVersion = bundleRulesVersion;
        this.bundleAndroidRevision = androidRevision;
    }

    public String getSystemRulesVersion() {
        return systemRulesVersion;
    }

    public String getBundleFormatVersionSupported() {
        return bundleFormatVersionSupported;
    }

    public boolean isOperationInProgress() {
        return operationInProgress;
    }

    public boolean isBundleInstalled() {
        return bundleInstalled;
    }

    public String getBundleRulesVersion() {
        return bundleRulesVersion;
    }

    public String getBundleAndroidRevision() {
        return bundleAndroidRevision;
    }

    public static final Parcelable.Creator<RulesState> CREATOR
            = new Parcelable.Creator<RulesState>() {
        public RulesState createFromParcel(Parcel in) {
            return new RulesState(in);
        }

        public RulesState[] newArray(int size) {
            return new RulesState[size];
        }
    };

    private RulesState(Parcel in) {
        systemRulesVersion = in.readString();
        bundleFormatVersionSupported = in.readString();
        operationInProgress = in.readInt() != 0;
        bundleInstalled = in.readInt() != 0;
        if (bundleInstalled) {
            bundleRulesVersion = in.readString();
            bundleAndroidRevision = in.readString();
        } else {
            bundleRulesVersion = null;
            bundleAndroidRevision = null;
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(systemRulesVersion);
        out.writeString(bundleFormatVersionSupported);
        out.writeInt(operationInProgress ? 1 : 0);
        out.writeInt(bundleInstalled ? 1 : 0);
        if (bundleInstalled) {
            out.writeString(bundleRulesVersion);
            out.writeString(bundleAndroidRevision);
        }
    }

    @Override
    public String toString() {
        return "RulesState{" +
                "systemRulesVersion='" + systemRulesVersion + '\'' +
                ", bundleFormatVersionSupported='" + bundleFormatVersionSupported + '\'' +
                ", operationInProgress=" + operationInProgress +
                ", bundleInstalled=" + bundleInstalled +
                ", bundleRulesVersion='" + bundleRulesVersion + '\'' +
                ", bundleAndroidRevision='" + bundleAndroidRevision + '\'' +
                '}';
    }
}
