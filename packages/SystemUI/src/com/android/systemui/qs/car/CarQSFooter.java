/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.qs.car;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.qs.QSFooter;
import com.android.systemui.qs.QSPanel;
import com.android.systemui.statusbar.phone.MultiUserSwitch;
import com.android.systemui.statusbar.phone.SystemUIDialog;
import com.android.systemui.statusbar.policy.DeviceProvisionedController;
import com.android.systemui.statusbar.policy.SecurityController;
import com.android.systemui.statusbar.policy.UserInfoController;
import android.content.DialogInterface;

/**
 * The footer view that displays below the status bar in the auto use-case. This view shows the
 * user switcher and access to settings.
 */
public class CarQSFooter extends RelativeLayout implements QSFooter,
        UserInfoController.OnUserInfoChangedListener, DialogInterface.OnClickListener {
    private static final String TAG = "CarQSFooter";

    private UserInfoController mUserInfoController;

    private MultiUserSwitch mMultiUserSwitch;
    private TextView mUserName;
    private ImageView mMultiUserAvatar;
    private CarQSFragment.UserSwitchCallback mUserSwitchCallback;
    private final SecurityController mSecurityController;
    private AlertDialog mDialog;
    private final ActivityStarter mActivityStarter;

    public CarQSFooter(Context context, AttributeSet attrs) {
        super(context, attrs);
        mSecurityController = Dependency.get(SecurityController.class);
        mActivityStarter = Dependency.get(ActivityStarter.class);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mMultiUserSwitch = findViewById(R.id.multi_user_switch);
        mMultiUserAvatar = mMultiUserSwitch.findViewById(R.id.multi_user_avatar);
        mUserName = findViewById(R.id.user_name);

        mUserInfoController = Dependency.get(UserInfoController.class);

        mMultiUserSwitch.setOnClickListener(v -> {
            if (mSecurityController.isDeviceManaged()) {
                createDialog();
                return;
            }
            if (mUserSwitchCallback == null) {
                Log.e(TAG, "CarQSFooter not properly set up; cannot display user switcher.");
                return;
            }

            if (!mUserSwitchCallback.isShowing()) {
                mUserSwitchCallback.show();
            } else {
                mUserSwitchCallback.hide();
            }
        });

        findViewById(R.id.settings_button).setOnClickListener(v -> {
            ActivityStarter activityStarter = Dependency.get(ActivityStarter.class);

            if (!Dependency.get(DeviceProvisionedController.class).isCurrentUserSetup()) {
                // If user isn't setup just unlock the device and dump them back at SUW.
                activityStarter.postQSRunnableDismissingKeyguard(() -> { });
                return;
            }

            activityStarter.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS),
                    true /* dismissShade */);
        });
    }

    @Override
    public void onUserInfoChanged(String name, Drawable picture, String userAccount) {
        mMultiUserAvatar.setImageDrawable(picture);
        mUserName.setText(name);
    }

    @Override
    public void setQSPanel(@Nullable QSPanel panel) {
        if (panel != null) {
            mMultiUserSwitch.setQsPanel(panel);
        }
    }

    public void setUserSwitchCallback(CarQSFragment.UserSwitchCallback callback) {
        mUserSwitchCallback = callback;
    }

    @Override
    public void setListening(boolean listening) {
        if (listening) {
            mUserInfoController.addCallback(this);
        } else {
            mUserInfoController.removeCallback(this);
        }
    }

    @Override
    public void setExpandClickListener(OnClickListener onClickListener) {
        // No view that should expand/collapse the quick settings.
    }

    @Override
    public void setExpanded(boolean expanded) {
        // Do nothing because the quick settings cannot be expanded.
    }

    @Override
    public void setExpansion(float expansion) {
        // Do nothing because the quick settings cannot be expanded.
    }

    @Override
    public void setKeyguardShowing(boolean keyguardShowing) {
        // Do nothing because the footer will not be shown when the keyguard is up.
    }

    private String getSettingsButton() {
        return mContext.getString(R.string.monitoring_button_view_policies);
    }

    private String getPositiveButton() {
        return mContext.getString(R.string.ok);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_NEGATIVE) {
            final Intent intent = new Intent(Settings.ACTION_ENTERPRISE_PRIVACY_SETTINGS);
            mDialog.dismiss();
            mActivityStarter.postStartActivityDismissingKeyguard(intent, 0);
        }
    }

    protected CharSequence getManagementMessage(boolean isDeviceManaged,
                                                CharSequence organizationName) {
        if (!isDeviceManaged) return null;
        if (organizationName != null)
            return mContext.getString(
                    R.string.monitoring_description_named_management, organizationName);
        return mContext.getString(R.string.monitoring_description_management);
    }

    private void createDialog() {
        final boolean isDeviceManaged = mSecurityController.isDeviceManaged();
        final CharSequence deviceOwnerOrganization =
                mSecurityController.getDeviceOwnerOrganizationName();
        mDialog = new SystemUIDialog(mContext);
        mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View dialogView = LayoutInflater.from(
                new ContextThemeWrapper(mContext, R.style.Theme_SystemUI_Dialog))
                .inflate(R.layout.quick_settings_footer_dialog, null, false);
        mDialog.setView(dialogView);
        mDialog.setButton(DialogInterface.BUTTON_POSITIVE, getPositiveButton(), this);

        // device management section
        CharSequence managementMessage = getManagementMessage(isDeviceManaged,
                deviceOwnerOrganization);
        if (managementMessage == null) {
            dialogView.findViewById(R.id.device_management_disclosures).setVisibility(View.GONE);
        } else {
            dialogView.findViewById(R.id.device_management_disclosures).setVisibility(View.VISIBLE);
            TextView deviceManagementWarning =
                    (TextView) dialogView.findViewById(R.id.device_management_warning);
            deviceManagementWarning.setText(managementMessage);
            mDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getSettingsButton(), this);
        }
        dialogView.findViewById(R.id.ca_certs_disclosures).setVisibility(View.GONE);
        dialogView.findViewById(R.id.network_logging_disclosures).setVisibility(View.GONE);
        dialogView.findViewById(R.id.vpn_disclosures).setVisibility(View.GONE);
        mDialog.show();
        mDialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
