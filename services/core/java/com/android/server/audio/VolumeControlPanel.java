/*
 * Copyright (C) 2006 The Android Open Source Project
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
 *
 */

package com.android.server.audio;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnKeyListener;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.util.Log;

import java.lang.reflect.Field;

/** This is a class to handle volume control panel. */
public class VolumeControlPanel implements OnKeyListener {

    private static final String LOGTAG = VolumeControlPanel.class.getSimpleName();

    private Context mContext = null;

    private Dialog mVolumePanel = null;

    private SeekBar mVolumeBar = null;

    private ImageView mIconView = null;

    private String mDeviceName = null;

    private int mMaxVolume = -1;

    private int mCurrentVolume = -1;

    private VolumeChangedListener mListener = null;

    private boolean mIsVolumeControlAvailable = false;

    /**
     * Constructor.
     *
     * @param context - context
     * @param listener - object of VolumeChangedListener
     */
    public VolumeControlPanel(Context context, VolumeChangedListener listener) {
        Log.d(LOGTAG, "VolumeControlPanel/in");
        mContext = context;
        mListener = listener;
        mVolumePanel = new Dialog(mContext);
        mVolumePanel.setOnKeyListener(this);
        mVolumePanel.setCanceledOnTouchOutside(true);
        mVolumePanel.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        init();
        Log.d(LOGTAG, "VolumeControlPanel/out");
    }

    /**
     * Dismiss volume panel.
     */
    public void dismiss() {
        Log.d(LOGTAG, "dismiss/in");
        if (mVolumePanel != null && mVolumePanel.isShowing()) {
            mVolumePanel.dismiss();
        }
        Log.d(LOGTAG, "dismiss/out");
    }

    /**
     * Deinit.
     */
    public void deinit() {
        Log.d(LOGTAG, "deinit/in");
        if (mVolumePanel != null) {
            mVolumePanel.setOnKeyListener(null);
            mVolumePanel = null;
        }
        Log.d(LOGTAG, "deinit/out");
    }

    /**
     * Set device name.
     *
     * @param deviceName - device Name
     */
    public void setDeviceName(String deviceName) {
        Log.d(LOGTAG, "setDeviceName/in[" + deviceName + "]");
        if (!TextUtils.isEmpty(deviceName)) {
            mDeviceName = deviceName;
            if (mVolumePanel != null) {
                ((TextView)mVolumePanel.findViewById(com.android.internal.R.id.device_name)).setText(deviceName);
            }
        }
        Log.d(LOGTAG, "setDeviceName/out");
    }

    /**
     * Set max volume.
     *
     * @param maxVolume - max volume
     */
    public void setMaxVolume(int maxVolume) {
        Log.d(LOGTAG, "setMaxVolume/in[" + maxVolume + "]");
        mMaxVolume = maxVolume;
        mIsVolumeControlAvailable = maxVolume > 0;
        if (mVolumeBar != null && mIsVolumeControlAvailable) {
            mVolumeBar.setMax(maxVolume);
        }
        Log.d(LOGTAG, "setMaxVolume/out");
    }

    /**
     * Set volume.
     *
     * @param volume - volume
     */
    public void setVolume(int volume) {
        Log.d(LOGTAG, "setVolume/in[" + volume + "]");
        mCurrentVolume = volume;
        if (mVolumeBar != null && volume > -1) {
            mVolumeBar.setProgress(volume);
        }
        if (mIconView != null) {
            String iconName = mVolumeBar.getProgress() == 0
                    ? "ic_volume_media_mute" : "ic_volume_media";
            //RemoteResources remoteResources = new RemoteResources(mContext, "com.android.systemui");
            //if (remoteResources.getDrawable(iconName) != null) {
                //int ResId = mContext.getResources().getIdentifier(iconName, "drawable", "com.android.systemui");
                mIconView.setImageDrawable(mContext.getResources().getDrawable(com.android.internal.R.drawable.ic_audio_media));
            //}

        }
        Log.d(LOGTAG, "setVolume/out");
    }

    /**
     * Show volume panel.
     *
     * @param volumeUp - indicates if volume should be up
     */
    public void show(boolean volumeUp) {
        Log.d(LOGTAG, "show/in[" + volumeUp + "]");
        init();
        updateVolume(volumeUp);
        if (mVolumePanel != null && !mVolumePanel.isShowing()) {
            mVolumePanel.show();
        }
        Log.d(LOGTAG, "show/out");
    }

    /**
     * Init.
     */
    private void init() {
        Log.d(LOGTAG, "init/in");
        if (mVolumePanel == null) {
            return;
        }
        // Almost same as general volume panel.
        // See frameworks/base/packages/SystemUI/
        // src/com/android/systemui/volume/VolumeDialogImpl.java
        final Window window = mVolumePanel.getWindow();
        View view = mVolumePanel.getLayoutInflater().inflate(
                com.android.internal.R.layout.volume_control_panel, null);
        mVolumeBar = (SeekBar)view.findViewById(com.android.internal.R.id.seekbar);
        mVolumeBar.setMax(100); // Initialize just in case.
        mIconView = (ImageView)view.findViewById(com.android.internal.R.id.icon);


        final LayoutParams lp = window.getAttributes();
        lp.token = null;
        lp.height = LayoutParams.WRAP_CONTENT;
        lp.type = LayoutParams.TYPE_VOLUME_OVERLAY;
        lp.format = PixelFormat.TRANSLUCENT;

        //RemoteResources remoteResources = new RemoteResources(mContext, "com.android.systemui");
        //int ResId = mContext.getResources().getIdentifier("notification_panel_width", "dimen", "com.android.systemui");
        lp.width = mContext.getResources().getDimensionPixelSize(com.android.internal.R.dimen.search_view_preferred_width);
        lp.windowAnimations = -1;
        lp.gravity = Gravity.TOP;

        // Use not to show this panel on sink.
        try {
            Field field = lp.getClass().getDeclaredField("extensionFlags");
            field.setAccessible(true);
            field.setInt(lp, 0x00000001);
        } catch (NoSuchFieldException e) {
        } catch (NullPointerException e) {
        } catch (IllegalArgumentException e) {
        } catch (IllegalAccessException e) {
        }
        mVolumePanel.getWindow().setAttributes(lp);
        window.setBackgroundDrawable(new ColorDrawable(0x00000000));
        window.clearFlags(LayoutParams.FLAG_DIM_BEHIND);
        // We don't add LayoutParams.FLAG_NOT_FOCUSABLE bacause key event must be handled.
        window.addFlags(LayoutParams.FLAG_NOT_TOUCH_MODAL
                | LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | LayoutParams.FLAG_HARDWARE_ACCELERATED);

        mVolumePanel.setContentView(view);
        setDeviceName(mDeviceName);
        setMaxVolume(mMaxVolume);
        setVolume(mCurrentVolume);
        Log.d(LOGTAG, "init/out");
    }

    /**
     * Update volume.
     *
     * @param volumeUp - indicates if volume should be up
     */
    private void updateVolume(boolean volumeUp) {
        Log.d(LOGTAG, "updateVolume/in[" + mIsVolumeControlAvailable + "][" + volumeUp + "]");
        if (mIsVolumeControlAvailable) {
            int newVolume = 0;
            if (volumeUp) {
                newVolume = mVolumeBar.getProgress() + 1;
            } else {
                newVolume = mVolumeBar.getProgress() - 1;
            }
            Log.d(LOGTAG, "newVolume[" + newVolume + "]");
            if (0 <= newVolume && newVolume <= mVolumeBar.getMax()) {
                mVolumeBar.setProgress(newVolume);
                if (mListener != null) {
                    mListener.onVolumeChanged(newVolume);
                }
            }
        }
        Log.d(LOGTAG, "updateVolume/out");
    }

    /**
     * (non-Javadoc)
     *
     * @see android.content.DialogInterface.OnKeyListener
     *      #onKey(android.content.DialogInterface, int, android.view.KeyEvent)
     */
    @Override
    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
        Log.d(LOGTAG, "onKey/in");
        boolean ret = false;
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP
                || event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                updateVolume(event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP);
            }
            ret = true;
        }
        Log.d(LOGTAG, "onKey/out[" + ret + "]");
        return ret;
    }

    /**
     * This is interface of listener.
     */
    public interface VolumeChangedListener {

        /**
         * Notify volume is changed.
         *
         * @param volume - volume
         */
        /* package */void onVolumeChanged(int volume);
    }

}
