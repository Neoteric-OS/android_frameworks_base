package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;


/**
 * Class that stores information specific to voice network registration.
 * @hide
 */
public class VoiceSpecificRegistrationStates implements Parcelable{
    // concurrent services support indicator. if
    // registered on a CDMA system.
    // false - Concurrent services not supported,
    // true - Concurrent services supported
    public final boolean mCssSupported;

    // TSB-58 Roaming Indicator if registered
    // on a CDMA or EVDO system or -1 if not.
    // Valid values are 0-255.
    public final int mRoamingIndicator;

    // indicates whether the current system is in the
    // PRL if registered on a CDMA or EVDO system or -1 if
    // not. 0=not in the PRL, 1=in the PRL
    public final int mSystemIsInPrl;

    // default Roaming Indicator from the PRL,
    // if registered on a CDMA or EVDO system or -1 if not.
    // Valid values are 0-255.
    public final int mDefaultRoamingIndicator;

    VoiceSpecificRegistrationStates(boolean cssSupported, int roamingIndicator, int systemIsInPrl,
            int defaultRoamingIndicator) {
        mCssSupported = cssSupported;
        mRoamingIndicator = roamingIndicator;
        mSystemIsInPrl = systemIsInPrl;
        mDefaultRoamingIndicator = defaultRoamingIndicator;
    }

    VoiceSpecificRegistrationStates(Parcel source) {
        mCssSupported = source.readBoolean();
        mRoamingIndicator = source.readInt();
        mSystemIsInPrl = source.readInt();
        mDefaultRoamingIndicator = source.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeBoolean(mCssSupported);
        dest.writeInt(mRoamingIndicator);
        dest.writeInt(mSystemIsInPrl);
        dest.writeInt(mDefaultRoamingIndicator);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "VoiceSpecificRegistrationStates {"
                + " mCssSupported=" + mCssSupported
                + " mRoamingIndicator=" + mRoamingIndicator
                + " mSystemIsInPrl=" + mSystemIsInPrl
                + " mDefaultRoamingIndicator=" + mDefaultRoamingIndicator + "}";
    }

    @Override
    public int hashCode() {
        return Objects.hash(mCssSupported, mRoamingIndicator, mSystemIsInPrl,
                mDefaultRoamingIndicator);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || !(o instanceof VoiceSpecificRegistrationStates)) {
            return false;
        }

        VoiceSpecificRegistrationStates other = (VoiceSpecificRegistrationStates) o;
        return mCssSupported == other.mCssSupported
                && mRoamingIndicator == other.mRoamingIndicator
                && mSystemIsInPrl == other.mSystemIsInPrl
                && mDefaultRoamingIndicator == other.mDefaultRoamingIndicator;
    }


    public static final Parcelable.Creator<VoiceSpecificRegistrationStates> CREATOR =
            new Parcelable.Creator<VoiceSpecificRegistrationStates>() {
                @Override
                public VoiceSpecificRegistrationStates createFromParcel(Parcel source) {
                    return new VoiceSpecificRegistrationStates(source);
                }

                @Override
                public VoiceSpecificRegistrationStates[] newArray(int size) {
                    return new VoiceSpecificRegistrationStates[size];
                }
            };
}