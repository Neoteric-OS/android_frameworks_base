/*
 * Copyright (C) 2022 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.util.nameless.display;

import static android.provider.Settings.System.MIN_REFRESH_RATE;
import static android.provider.Settings.System.PEAK_REFRESH_RATE;

import android.content.Context;
import android.provider.Settings;
import android.view.Display;

import com.android.internal.R;

import java.util.ArrayList;
import java.util.Comparator;

public class DisplayRefreshRateHelper {

    private static final float DEFAULT_REFRESH_RATE = 60f;

    private static ArrayList<Integer> sRefreshRateList;

    private static void initialize(Context context) {
        sRefreshRateList = new ArrayList<>();
        Display.Mode mode = context.getDisplay().getMode();
        Display.Mode[] modes = context.getDisplay().getSupportedModes();
        for (Display.Mode m : modes) {
            if (m.getPhysicalWidth() == mode.getPhysicalWidth() &&
                    m.getPhysicalHeight() == mode.getPhysicalHeight()) {
                sRefreshRateList.add((int) m.getRefreshRate());
            }
        }
        sRefreshRateList.sort(Comparator.naturalOrder());
    }

    public static ArrayList<Integer> getSupportedRefreshRateList(Context context) {
        if (sRefreshRateList == null) {
            initialize(context);
        }
        return sRefreshRateList;
    }

    public static int getMinimumRefreshRate(Context context) {
        final int refreshRate = context.getResources().getInteger(
                R.integer.config_defaultRefreshRate);
        final float defaultRefreshRate = refreshRate != 0 ? (float) refreshRate : DEFAULT_REFRESH_RATE;
        return (int) Settings.System.getFloat(context.getContentResolver(),
                MIN_REFRESH_RATE, defaultRefreshRate);
    }

    public static int getPeakRefreshRate(Context context) {
        final int refreshRate = context.getResources().getInteger(
                R.integer.config_defaultPeakRefreshRate);
        final float defaultPeakRefreshRate = refreshRate != 0 ? (float) refreshRate : DEFAULT_REFRESH_RATE;
        return (int) Settings.System.getFloat(context.getContentResolver(),
                PEAK_REFRESH_RATE, defaultPeakRefreshRate);
    }

    public static ArrayList<Integer> getRefreshRate(Context context) {
        final ArrayList<Integer> ret = new ArrayList<>();
        ret.add(getMinimumRefreshRate(context));
        ret.add(getPeakRefreshRate(context));
        return ret;
    }

    public static void setMinimumRefreshRate(Context context, int refreshRate) {
        Settings.System.putFloat(context.getContentResolver(),
                MIN_REFRESH_RATE, (float) refreshRate);
    }

    public static void setPeakRefreshRate(Context context, int refreshRate) {
        Settings.System.putFloat(context.getContentResolver(),
                PEAK_REFRESH_RATE, (float) refreshRate);
    }

    public static void setRefreshRate(Context context, int minRefreshRate, int peakRefreshRate) {
        setMinimumRefreshRate(context, minRefreshRate);
        setPeakRefreshRate(context, peakRefreshRate);
    }

    public static boolean isRefreshRateValid(Context context, int refreshRate) {
        if (sRefreshRateList == null) {
            initialize(context);
        }
        return sRefreshRateList.contains(refreshRate);
    }
}
