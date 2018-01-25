package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;


/**
 * Class that stores information specific to data network registration.
 * @hide
 */
public class DataSpecificRegistrationStates implements Parcelable{
    // The maximum number of simultaneous Data Calls that
    // must be established using setupDataCall().
    public final int mMaxDataCalls;

    DataSpecificRegistrationStates(int maxDataCalls) {
        mMaxDataCalls = maxDataCalls;
    }

    DataSpecificRegistrationStates(Parcel source) {
        mMaxDataCalls = source.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mMaxDataCalls);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "DataSpecificRegistrationStates {" + " mMaxDataCalls=" + mMaxDataCalls + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || !(o instanceof DataSpecificRegistrationStates)) {
            return false;
        }

        DataSpecificRegistrationStates other = (DataSpecificRegistrationStates) o;
        return mMaxDataCalls == other.mMaxDataCalls;
    }


    public static final Parcelable.Creator<DataSpecificRegistrationStates> CREATOR =
            new Parcelable.Creator<DataSpecificRegistrationStates>() {
                @Override
                public DataSpecificRegistrationStates createFromParcel(Parcel source) {
                    return new DataSpecificRegistrationStates(source);
                }

                @Override
                public DataSpecificRegistrationStates[] newArray(int size) {
                    return new DataSpecificRegistrationStates[size];
                }
            };
}