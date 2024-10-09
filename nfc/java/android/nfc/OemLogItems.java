package com.android.nfc;

import android.nfc.Tag;

public class OemLogItems {

    public static final int REPORT_DATA_CHANGED = 0X01;
    public static final int REPORT_CALLING_API = 0x0201;
    public static final int REPORT_HOST_ROUTING_STATUS = 0x0204;
    public static final int REPORT_CHECK_RLD_STATUS = 0x0206;
    public static final int REPORT_TAG_DETECTED = 0x03;

    public static class Builder {
        private OemLogItems mItem;

        public Builder(int type) {
            mItem = new OemLogItems();
            mItem.mAction = type;
        }

        public OemLogItems.Builder setAction(int action) {
            mItem.mAction = action;
            return this;
        }

        public OemLogItems.Builder setCallingApi(String api) {
            mItem.mCallingApi = api;
            return this;
        }

        public OemLogItems.Builder setCallingPid(int pid) {
            mItem.mCallingPid = pid;
            return this;
        }

        public OemLogItems.Builder setApduCommand(byte[] apdus) {
            mItem.mCommandApdus = apdus;
            return this;
        }

        public OemLogItems.Builder setRfFieldOnTime(long time) {
            mItem.mRfFieldOnTime = time;
            return this;
        }

        public OemLogItems.Builder setSimRouteTime(long time) {
            mItem.mSimRouteTime = time;
            return this;
        }

        public OemLogItems.Builder setApduResponse(byte[] apdus) {
            mItem.mResponseApdus = apdus;
            return this;
        }

        public OemLogItems.Builder setTag(Tag tag) {
            mItem.mTag = tag;
            return this;
        }

        public OemLogItems build() {
            return mItem;
        }
    }

    public Integer getAction() {
        return mAction;
    }
    public String getCallingApi() {
        return mCallingApi;
    }
    public int getCallingPid() {
        return mCallingPid;
    }

    public byte[] getCommandApdus() {
        return mCommandApdus;
    }

    public byte[] getResponsApdus() {
        return mResponseApdus;
    }
    public Long getRfFieldOnTime() {
        return mRfFieldOnTime;
    }

    public Long getSimRouteTime() {
        return mSimRouteTime;
    }

    public Tag getTag() { return mTag; }

    private String byteToHex(byte[] bytes){
        char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public String toString() {
        return new StringBuilder().append("[mCommandApdus: " + ((mCommandApdus != null) ? byteToHex(mCommandApdus): "null"))
                .append("[mResponseApdus: " + ((mResponseApdus != null) ? byteToHex(mResponseApdus): "null"))
                .append(", mCallingApi= " + mCallingApi)
                .append(", mAction= " + mAction)
                .append(", mCallingPId = " + mCallingPid)
                .append(", mRfFieldOnTime= " + mRfFieldOnTime)
                .append(", mSimRouteTime= " + mSimRouteTime + "]").toString();
    }

    private Integer mAction;
    private String mCallingApi;
    private Integer mCallingPid;
    private byte[] mCommandApdus;
    private byte[] mResponseApdus;
    private Long mRfFieldOnTime;
    private Long mSimRouteTime;
    private Tag mTag;
}
