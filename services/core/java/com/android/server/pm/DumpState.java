package com.android.server.pm;

public final class DumpState {
    public static final int DUMP_LIBS = 1 << 0;
    public static final int DUMP_FEATURES = 1 << 1;
    public static final int DUMP_ACTIVITY_RESOLVERS = 1 << 2;
    public static final int DUMP_SERVICE_RESOLVERS = 1 << 3;
    public static final int DUMP_RECEIVER_RESOLVERS = 1 << 4;
    public static final int DUMP_CONTENT_RESOLVERS = 1 << 5;
    public static final int DUMP_PERMISSIONS = 1 << 6;
    public static final int DUMP_PACKAGES = 1 << 7;
    public static final int DUMP_SHARED_USERS = 1 << 8;
    public static final int DUMP_MESSAGES = 1 << 9;
    public static final int DUMP_PROVIDERS = 1 << 10;
    public static final int DUMP_VERIFIERS = 1 << 11;
    public static final int DUMP_PREFERRED = 1 << 12;
    public static final int DUMP_PREFERRED_XML = 1 << 13;
    public static final int DUMP_KEYSETS = 1 << 14;
    public static final int DUMP_VERSION = 1 << 15;
    public static final int DUMP_INSTALLS = 1 << 16;
    public static final int DUMP_INTENT_FILTER_VERIFIERS = 1 << 17;
    public static final int DUMP_DOMAIN_PREFERRED = 1 << 18;
    public static final int DUMP_FROZEN = 1 << 19;
    public static final int DUMP_DEXOPT = 1 << 20;
    public static final int DUMP_COMPILER_STATS = 1 << 21;
    public static final int DUMP_CHANGES = 1 << 22;

    public static final int OPTION_SHOW_FILTERS = 1 << 0;

    private int mTypes;

    private int mOptions;

    private boolean mTitlePrinted;

    private SharedUserSetting mSharedUser;

    public boolean isDumping(int type) {
        if (mTypes == 0 && type != DUMP_PREFERRED_XML) {
            return true;
        }

        return (mTypes & type) != 0;
    }

    public void setDump(int type) {
        mTypes |= type;
    }

    public boolean isOptionEnabled(int option) {
        return (mOptions & option) != 0;
    }

    public void setOptionEnabled(int option) {
        mOptions |= option;
    }

    public boolean onTitlePrinted() {
        final boolean printed = mTitlePrinted;
        mTitlePrinted = true;
        return printed;
    }

    public boolean getTitlePrinted() {
        return mTitlePrinted;
    }

    public void setTitlePrinted(boolean enabled) {
        mTitlePrinted = enabled;
    }

    public SharedUserSetting getSharedUser() {
        return mSharedUser;
    }

    public void setSharedUser(SharedUserSetting user) {
        mSharedUser = user;
    }
}