
package android.telephony;

import java.util.Arrays;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * @hide
 * ApnTypeInfo used by network state trackers.
 *
 */
public class ApnTypeInfo implements Parcelable {

    int mDataState;
    String mInterfaceName;
    String[] mIpAddressList;
    String mGateWay;
    String mApn;

    /**
     * Empty constructor
     */
    public ApnTypeInfo() {
    }

    /**
     * Copy constructors
     */
    public ApnTypeInfo(ApnTypeInfo s) {
        copyFrom(s);
    }

    public static ApnTypeInfo newFromBundle(Bundle m) {
        ApnTypeInfo ret;
        ret = new ApnTypeInfo();
        ret.setFromNotifierBundle(m);
        return ret;
    }

    protected void copyFrom(ApnTypeInfo s) {
        mDataState = s.mDataState;
        mInterfaceName = s.mInterfaceName;
        if (s.mIpAddressList != null) {
            String[] result = new String[s.mIpAddressList.length];
            System.arraycopy(s.mIpAddressList, 0, result, 0, s.mIpAddressList.length);
            mIpAddressList = result;
        }
        mGateWay = s.mGateWay;
        mApn = s.mApn;
    }

    public ApnTypeInfo(Parcel in) {
        mDataState = in.readInt();
        mInterfaceName = in.readString();
        mIpAddressList = in.readStringArray();
        mGateWay = in.readString();
        mApn = in.readString();
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mDataState);
        out.writeString(mInterfaceName);
        out.writeStringArray(mIpAddressList);
        out.writeString(mGateWay);
        out.writeString(mApn);

    }

    public void fillInNotifierBundle(Bundle m) {
        m.putInt("data-state", mDataState);
        m.putString("interface-name", mInterfaceName);
        m.putStringArray("ip-address-list", mIpAddressList);
        m.putString("gateway", mGateWay);
        m.putString("apn", mApn);
    }

    private void setFromNotifierBundle(Bundle m) {
        mDataState = m.getInt("data-state");
        mInterfaceName = m.getString("interface-name");
        mIpAddressList = m.getStringArray("ip-address-list");
        mGateWay = m.getString("gateway");
        mApn = m.getString("apn");
    }

    public int describeContents() {
        return 0;
    }

    public int getDataState() {
        return mDataState;
    }

    public void setDataState(int mDataState) {
        this.mDataState = mDataState;
    }

    public String getInterfaceName() {
        return mInterfaceName;
    }

    public void setInterfaceName(String mInterfaceName) {
        this.mInterfaceName = mInterfaceName;
    }

    public String[] getIpAddressList() {
        return mIpAddressList;
    }

    public void setIpAddressList(String[] mIpAddressList) {
        this.mIpAddressList = mIpAddressList;
    }

    public String getGateWay() {
        return mGateWay;
    }

    public void setGateWay(String mGateWay) {
        this.mGateWay = mGateWay;
    }

    public String getApn() {
        return mApn;
    }

    public void setApn(String mApn) {
        this.mApn = mApn;
    }

    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append("state=").append(mDataState);
        s.append(",iface=").append(mInterfaceName);
        s.append(",ip=").append(Arrays.toString(mIpAddressList));
        s.append(",gw=").append(mGateWay);
        s.append(",apn=").append(mApn);
        return s.toString();
    }

    public static final Parcelable.Creator<ApnTypeInfo> CREATOR
                                                        = new Parcelable.Creator<ApnTypeInfo>() {
        public ApnTypeInfo createFromParcel(Parcel in) {
            return new ApnTypeInfo(in);
        }

        public ApnTypeInfo[] newArray(int size) {
            return new ApnTypeInfo[size];
        }
    };
}
