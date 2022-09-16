package com.android.systemui.statusbar.policy;

import static com.android.systemui.statusbar.StatusBarIconView.STATE_DOT;
import static com.android.systemui.statusbar.StatusBarIconView.STATE_HIDDEN;
import static com.android.systemui.statusbar.StatusBarIconView.STATE_ICON;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.statusbar.StatusIconDisplayable;

import java.util.ArrayList;

public class NetworkTrafficSB extends NetworkTraffic implements StatusIconDisplayable {

    public static final String SLOT = "networktraffic";

    private int mVisibleState = -1;
    private boolean mSystemIconVisible = true;

    public NetworkTrafficSB(Context context) {
        this(context, null);
    }

    public NetworkTrafficSB(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NetworkTrafficSB(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    @Override
    public void onDarkChanged(ArrayList<Rect> areas, float darkIntensity, int tint) {
        if (!mIsEnabled) return;
        mTintColor = DarkIconDispatcher.getTint(areas, this, tint);
        setTextColor(mTintColor);
        updateTrafficDrawable();
    }

    @Override
    public String getSlot() {
        return SLOT;
    }

    @Override
    public boolean isIconVisible() {
        return mIsEnabled;
    }

    @Override
    public int getVisibleState() {
        return mVisibleState;
    }

    @Override
    public void setVisibleState(int state, boolean animate) {
        if (state == mVisibleState || !mIsEnabled || !mAttached) {
            return;
        }
        mVisibleState = state;

        switch (state) {
            case STATE_ICON:
                mSystemIconVisible = true;
                break;
            case STATE_DOT:
            case STATE_HIDDEN:
            default:
                mSystemIconVisible = false;
                break;
        }
    }

    @Override
    public void setStaticDrawableColor(int color) {
        mTintColor = color;
        setTextColor(mTintColor);
        updateTrafficDrawable();
    }

    @Override
    public void setDecorColor(int color) {
        mTintColor = color;
        updateTrafficDrawable();
    }

    private void maybeRestoreVisibility() {
        if (mSystemIconVisible && getVisibility() == View.GONE && isIconVisible() && restoreViewQuickly()) {
            setVisibility(View.VISIBLE);
            update();
        }
    }

    @Override
    boolean isDisabled() {
        return !mIsEnabled || !mSystemIconVisible;
    }
}
