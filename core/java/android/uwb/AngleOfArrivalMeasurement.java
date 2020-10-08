/*
 * Copyright 2020 The Android Open Source Project
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

package android.uwb;

import android.annotation.FloatRange;

/**
 * Represents an angle of arrival measurement between two devices using Ultra Wideband
 *
 * @hide
 */
public class AngleOfArrivalMeasurement {
    /**
     * Azimuth angle measurement in radians
     * <p>Azimuth angle measurement of remote device in radians from -pi to +pi
     * in horizontal coordinate system, this is the angle clockwise from the meridian
     * when viewing above the north pole
     *
     * <p>See: https://en.wikipedia.org/wiki/Horizontal_coordinate_system
     *
     * <p>On an Android device, azimuth north is defined as the angle perpendicular away
     * from the back of the device when holding it in portrait mode upright.
     *
     * <p>Azimuth angle must be supported when Angle of Arrival is supported
     *
     * @return angle in radians
     */
    @FloatRange(from = -Math.PI, to = +Math.PI)
    public double azimuthRadian() {
        throw new UnsupportedOperationException();
    }

    /**
     * Error of azimuth angle measurement in radians
     *
     * <p>Must be a positive value
     *
     * @return angle measurement error in radians
     */
    public double azimuthErrorRadian() {
        throw new UnsupportedOperationException();
    }

    /**
     * Azimuth angle measurement confidence level expressed as a value between
     * 0.0 to 1.0.
     *
     * <p>A value of 0.0 indicates there is no confidence in the measurement. A value of 1.0
     * indicates there is maximum confidence in the measurement.
     *
     * @return the confidence level of the azimuth angle measurement
     */
    @FloatRange(from = 0.0, to = 1.0)
    public double azimuthConfidenceLevel() {
        throw new UnsupportedOperationException();
    }

    /**
     * Altitude angle measurement in radians
     * <p>Altitude angle measurement of remote device in radians from -pi to +pi
     * in horizontal coordinate system, this is the angle above the equator when
     * the north pole is up
     *
     * <p>See: https://en.wikipedia.org/wiki/Horizontal_coordinate_system
     *
     * <p>On an Android device, altitude is defined as the angle vertical from ground
     * when holding the device in portrait mode upright
     *
     * @return the angle measurement in radians or NaN when this is not available
     */
    @FloatRange(from = -Math.PI, to = +Math.PI)
    public double altitudeRadian() {
        throw new UnsupportedOperationException();
    }

    /**
     * Error in altitude angle measurement in radians
     *
     * <p>Must be a positive value
     *
     * @return angle measurement error in radians
     */
    public double altitudeErrorRadian() {
        throw new UnsupportedOperationException();
    }

    /**
     * Altitude angle measurement confidence level expressed as a value between
     * 0.0 to 1.0.
     *
     * <p>A value of 0.0 indicates there is no confidence in the measurement. A value of 1.0
     * indicates there is maximum confidence in the measurement.
     *
     * @return the confidence level of the altitude angle measurement
     */
    @FloatRange(from = 0.0, to = 1.0)
    public double altitudeConfidenceLevel() {
        throw new UnsupportedOperationException();
    }
}
