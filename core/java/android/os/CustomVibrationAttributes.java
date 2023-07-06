/*
 * Copyright (C) 2022 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package android.os;

public class CustomVibrationAttributes {

    private CustomVibrationAttributes() {}

    public static final VibrationAttributes VIBRATION_ATTRIBUTES_QS_TILE =
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_CUSTOM_QS_TILE);
    public static final VibrationAttributes VIBRATION_ATTRIBUTES_FACE =
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_CUSTOM_FACE);
    public static final VibrationAttributes VIBRATION_ATTRIBUTES_FINGERPRINT =
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_CUSTOM_FINGERPRINT);
    public static final VibrationAttributes VIBRATION_ATTRIBUTES_FINGERPRINT_ERROR =
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_CUSTOM_FINGERPRINT_ERROR);
}
