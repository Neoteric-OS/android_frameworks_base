/*
 * Copyright (C) 2011 The Android Open Source Project
 * Portions Copyright (C) 2012-2013 Motorola Mobility LLC All Rights Reserved.
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

package com.android.systemui.statusbar;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.systemui.statusbar.policy.NetworkController;

import com.android.systemui.R;

// Intimately tied to the design of res/layout/signal_cluster_view.xml
public class SignalClusterView
        extends LinearLayout
        implements NetworkController.SignalCluster {

    static final boolean DEBUG = false;
    static final String TAG = "SignalClusterView";
    private Context mContext;

    NetworkController mNC;

    private boolean mWifiVisible = false;
    private int mWifiStrengthId = 0, mWifiActivityId = 0;
    private boolean mMobileVisible = false;
    private int mMobileStrengthId = 0, mMobileActivityId = 0, mMobileTypeId = 0, mSimIconId = 0;
    private boolean mIsAirplaneMode = false;
    private int mAirplaneIconId = 0;
    private String mWifiDescription, mMobileDescription, mMobileTypeDescription, mSimDescription;

    ViewGroup mWifiGroup, mMobileGroup;
    ImageView mWifi, mMobile, mWifiActivity, mMobileActivity, mMobileType, mAirplane, mMobileSimSlot;
    private int  mMobileRoamingId;
    private String mMobileRoamingDescription;
    ImageView mMobileRoaming; //
    private boolean mConfig_show_both_wifi_and_mobile_network = false;
    View mSpacer;

    public SignalClusterView(Context context) {
        this(context, null);
    }

    public SignalClusterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SignalClusterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;

        mConfig_show_both_wifi_and_mobile_network =
            context.getResources().getBoolean(
                R.bool.config_show_both_wifi_and_mobile_network
            );
    }

    public void setNetworkController(NetworkController nc) {
        if (DEBUG) Slog.d(TAG, "NetworkController=" + nc);
        mNC = nc;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mWifiGroup      = (ViewGroup) findViewById(R.id.wifi_combo);
        mWifi           = (ImageView) findViewById(R.id.wifi_signal);
        mWifiActivity   = (ImageView) findViewById(R.id.wifi_inout);
        mMobileGroup    = (ViewGroup) findViewById(R.id.mobile_combo);
        mMobile         = (ImageView) findViewById(R.id.mobile_signal);
        mMobileActivity = (ImageView) findViewById(R.id.mobile_inout);
        mMobileType     = (ImageView) findViewById(R.id.mobile_type);
        mMobileSimSlot      = (ImageView) findViewById(R.id.mobile_sim);
        mMobileRoaming  = (ImageView) findViewById(R.id.mobile_roaming);
        mSpacer         =             findViewById(R.id.spacer);

        mAirplane       = (ImageView) findViewById(R.id.airplane);

        apply();
    }

    @Override
    protected void onDetachedFromWindow() {
        mWifiGroup      = null;
        mWifi           = null;
        mWifiActivity   = null;
        mMobileGroup    = null;
        mMobile         = null;
        mMobileRoaming  = null; // Added roaming layer: qhnf37
        mMobileActivity = null;
        mMobileType     = null;
        mMobileSimSlot  = null;
        mSpacer         = null;
        mAirplane       = null;

        super.onDetachedFromWindow();
    }

    @Override
    public void setWifiIndicators(boolean visible, int strengthIcon, int activityIcon,
            String contentDescription) {
        mWifiVisible = visible;
        mWifiStrengthId = strengthIcon;
        mWifiActivityId = activityIcon;
        mWifiDescription = contentDescription;

        apply();
    }

    @Override
    public void setMobileDataIndicators(
                                        boolean visible,
                                        int strengthIcon,
                                        int roamingIcon, // Added roaming layer: qhnf37
                                        int simIcon,               // Added sim layer: qhnf37
                                        int activityIcon,
                                        int typeIcon,
                                        String contentDescription,
                                        String typeContentDescription,
                                        String roamingDescription, // Added roaming layer: qhnf37
                                        String simDescription      // Added sim layer: qhnf37
    ) {
        mMobileVisible = visible;
        mMobileStrengthId = strengthIcon;
        mMobileRoamingId = roamingIcon;
        mMobileActivityId = activityIcon;
        mMobileTypeId = typeIcon;
        mMobileDescription = contentDescription;
        mMobileTypeDescription = typeContentDescription;
        mMobileRoamingDescription = roamingDescription;
        mSimIconId = simIcon;
        mSimDescription = simDescription;
        apply();
    }

    @Override
    public void setIsAirplaneMode(boolean is, int airplaneIconId) {
        mIsAirplaneMode = is;
        mAirplaneIconId = airplaneIconId;

        apply();
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        // Standard group layout onPopulateAccessibilityEvent() implementations
        // ignore content description, so populate manually
        if (mWifiVisible && mWifiGroup.getContentDescription() != null)
            event.getText().add(mWifiGroup.getContentDescription());
        if (mMobileVisible && mMobileGroup.getContentDescription() != null)
            event.getText().add(mMobileGroup.getContentDescription());
        return super.dispatchPopulateAccessibilityEvent(event);
    }

    // Run after each indicator change.
    private void apply() {
        if (mWifiGroup == null) return;

        if (mWifiVisible) {
            mWifiGroup.setVisibility(View.VISIBLE);
            mWifi.setImageResource(mWifiStrengthId);
            mWifiActivity.setImageResource(mWifiActivityId);
            mWifiGroup.setContentDescription(mWifiDescription);
        } else {
            mWifiGroup.setVisibility(View.GONE);
        }

        if (DEBUG) Slog.d(TAG,
                String.format("wifi: %s sig=%d act=%d",
                    (mWifiVisible ? "VISIBLE" : "GONE"),
                    mWifiStrengthId, mWifiActivityId));

        if (mMobileVisible && !mIsAirplaneMode) {
            mMobileGroup.setVisibility(View.VISIBLE);
            mMobile.setImageResource(mMobileStrengthId);
            mMobileRoaming.setImageResource(mMobileRoamingId); // Added roaming layer: qhnf37
            mMobileActivity.setImageResource(mMobileActivityId);
            mMobileType.setImageResource(mMobileTypeId);
            mMobileGroup.setContentDescription(mMobileTypeDescription + " " + mMobileDescription
                                               + " " + mMobileRoamingDescription // Added roaming layer: qhnf37
                                               + " " + mSimDescription // Added sim layer: qhnf37
            );
            mMobileSimSlot.setImageResource(mSimIconId);

            if( ! mConfig_show_both_wifi_and_mobile_network ) { // IKHSS6-1244 added if(...)
                mMobileType.setVisibility( !mWifiVisible ? View.VISIBLE : View.GONE );
                mMobileActivity.setVisibility( !mWifiVisible ? View.VISIBLE : View.GONE );
            }

        } else {
            mMobileGroup.setVisibility(View.GONE);
        }

        if (mIsAirplaneMode) {
            mAirplane.setVisibility(View.VISIBLE);
            mAirplane.setImageResource(mAirplaneIconId);
        } else {
            mAirplane.setVisibility(View.GONE);
        }

        if (DEBUG) Slog.d(TAG,
                String.format("mobile: %s sig=%d act=%d typ=%d roam=%d",
                    (mMobileVisible ? "VISIBLE" : "GONE"),
                    mMobileStrengthId, mMobileActivityId, mMobileTypeId, mMobileRoamingId)); // Added roaming: qhnf37


        if (
            (
                ( (mMobileType.getVisibility() == View.VISIBLE) && (mMobileTypeId != 0) )
                ||
                ( (mMobileRoaming.getVisibility() == View.VISIBLE) && (mMobileRoamingId != 0) )
                ||
                ((mIsAirplaneMode) || (mSimIconId != 0))
            )
            &&
            mWifiVisible
        ) {
            mSpacer.setVisibility(View.INVISIBLE); // Occupy the space, but it is an invisible spacer.
        } else {
            mSpacer.setVisibility(View.GONE); // Release the space
        }

        if (DEBUG) {
            Slog.d(TAG,
                "Showing:"
                +"\n mWifiVisible="+mWifiVisible
                +"\n   mWifiGroup.getVisibility()="+(
                    (mWifiGroup.getVisibility() == View.VISIBLE)
                    ?"visible"
                    :(
                        (mWifiGroup.getVisibility() == View.INVISIBLE)
                        ?"invisible"
                        :((mWifiGroup.getVisibility() == View.GONE)?"gone":"unknown")
                    )
                )
                +"\n     mWifi.getVisibility()="+(
                    (mWifi.getVisibility() == View.VISIBLE)
                    ?"visible"
                    :(
                        (mWifi.getVisibility() == View.INVISIBLE)
                        ?"invisible"
                        :((mWifi.getVisibility() == View.GONE)?"gone":"unknown")
                    )
                )
                +"\n       mWifiStrengthId="+mWifiStrengthId+"/"+getResourceName(mWifiStrengthId)
                +"\n     mWifiActivity.getVisibility()="+(
                    (mWifiActivity.getVisibility() == View.VISIBLE)
                    ?"visible"
                    :(
                        (mWifiActivity.getVisibility() == View.INVISIBLE)
                        ?"invisible"
                        :((mWifiActivity.getVisibility() == View.GONE)?"gone":"unknown")
                    )
                )
                +"\n       mWifiActivityId="+mWifiActivityId+"/"+getResourceName(mWifiActivityId)
                +"\n mMobileVisible="+mMobileVisible
                +"\n   mMobileGroup.getVisibility()="+(
                    (mMobileGroup.getVisibility() == View.VISIBLE)
                    ?"visible"
                    :(
                        (mMobileGroup.getVisibility() == View.INVISIBLE)
                        ?"invisible"
                        :((mMobileGroup.getVisibility() == View.GONE)?"gone":"unknown")
                    )
                )
                +"\n     mMobile.getVisibility()="+(
                    (mMobile.getVisibility() == View.VISIBLE)
                    ?"visible"
                    :(
                        (mMobile.getVisibility() == View.INVISIBLE)
                        ?"invisible"
                        :((mMobile.getVisibility() == View.GONE)?"gone":"unknown")
                    )
                )
                +"\n       mMobileStrengthId="+mMobileStrengthId+"/"+getResourceName(mMobileStrengthId)
                +"\n     mMobileActivity.getVisibility()="+(
                    (mMobileActivity.getVisibility() == View.VISIBLE)
                    ?"visible"
                    :(
                        (mMobileActivity.getVisibility() == View.INVISIBLE)
                        ?"invisible"
                        :((mMobileActivity.getVisibility() == View.GONE)?"gone":"unknown")
                    )
                )
                +"\n       mMobileActivityId="+mMobileActivityId+"/"+getResourceName(mMobileActivityId)
                +"\n     mMobileRoaming.getVisibility()="+(
                    (mMobileRoaming.getVisibility() == View.VISIBLE)
                    ?"visible"
                    :(
                        (mMobileRoaming.getVisibility() == View.INVISIBLE)
                        ?"invisible"
                        :((mMobileRoaming.getVisibility() == View.GONE)?"gone":"unknown")
                    )
                )
                +"\n       mMobileRoamingId="+mMobileRoamingId+"/"+getResourceName(mMobileRoamingId)
                +"\n     mMobileSimSlot.getVisibility()="+(
                    (mMobileSimSlot.getVisibility() == View.VISIBLE)
                    ?"visible"
                    :(
                        (mMobileSimSlot.getVisibility() == View.INVISIBLE)
                        ?"invisible"
                        :((mMobileSimSlot.getVisibility() == View.GONE)?"gone":"unknown")
                    )
                )
                +"\n       mSimIconId="+mSimIconId+"/"+getResourceName(mSimIconId)
                +"\n     mMobileType.getVisibility()="+(
                    (mMobileType.getVisibility() == View.VISIBLE)
                    ?"visible"
                    :(
                        (mMobileType.getVisibility() == View.INVISIBLE)
                        ?"invisible"
                        :((mMobileType.getVisibility() == View.GONE)?"gone":"unknown")
                    )
                )
                +"\n       mMobileTypeId="+mMobileTypeId+"/"+getResourceName(mMobileTypeId)
           );
        }

    }

    private String getResourceName(int resId) {
        if (resId != 0) {
            final Resources res = mContext.getResources();
            try {
                return res.getResourceName(resId);
            } catch (android.content.res.Resources.NotFoundException ex) {
                return "(unknown)";
            }
        } else {
            return "(null)";
        }
    }
}

