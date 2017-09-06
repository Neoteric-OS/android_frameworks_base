/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.hardware;

/**
 * The Light class is a representation of a single light on a device, such as
 * player indicator lights on a gamepad or lights on a keyboard.
 */
public abstract class Light {

    /**
     * @hide
     */
    public Light() {

    }

    /**
     * Raw brightness representing the off state.
     */
    public static final int LIGHT_OFF = 0;

    /**
     * Set the brightness of the light.
     * <p> Note: setting the brightness to {@link Light#LIGHT_OFF} will cancel the
     * blinking of the light.
     *
     * @param brightness the brightness of the light between 0 and 1.  This is
     * mapped onto the underlying light's supported range.
     */
    public void setScaledBrightness(float brightness) {
        float b = Math.min(1.0f, Math.max(0.0f, brightness));
        setBrightness((int) Math.ceil(getMaximumBrightness() * b));
    }

    /**
     * Set the raw brightness of the light.
     * <p> Note: setting the brightness to {@link Light#LIGHT_OFF} will cancel the
     * blinking of the light.
     *
     * @param brightness the raw brightness of the light.  values greater than
     * the maximum brightness are automatically clamped.
     */
    public abstract void setBrightness(int brightness);

    /**
     * Get the raw brightness of the light.
     * @return the raw brightness of the light.
     */
    public abstract int getBrightness();

    /**
     * Get the maximum raw brightness of the light.
     * @return the maximum raw brightness of the light.
     */
    public abstract int getMaximumBrightness();

    /**
     * Get the name of the light.
     * @return the name of the light.
     */
    public abstract String getName();

    /**
     * Start blinking the light with a given interval.
     *
     * <p> If the light is off when startBlinking is called, the light will use
     * the last non-zero brightness it was set to
     * </p><p>
     * The blinking cycle will always start with the light being on.  If the
     * light is already blinking, the cycle will be reset and blink with the new
     * intervals.
     * </p>
     *
     * @param onInterval time in milliseconds the light is on per cycle
     * @param offInterval time in milliseconds the light is off per cycle
     */
    public abstract void startBlinking(int onInterval, int offInterval);

    /**
     * Stop blinking the light.
     */
    public abstract void stopBlinking();

    /**
     * Check whether the light is currently blinking.
     * @return True if the light is blinking, else false
     */
    public abstract boolean isBlinking();
}
