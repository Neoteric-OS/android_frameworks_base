
package android.os;

import android.os.Parcelable;

/**
 * Many devices with "vibration" or "rumble" capabilities (such as gamepads)
 * support variable magnitude vibrations.  VibratorEvents provide a mechanism
 * by which {@link Vibrator} can support variable magnitude vibrations.
 */
public final class VibratorEvent implements android.os.Parcelable {

    /**
     * @hide
     * Android default vibration magnitude
     */
    public static final short DEFAULT_MAGNITUDE = (short) 0xc000;

    /**
     * Duration in milliseconds of the vibration event
     */
    public long duration;

    /**
     * Strong magnitude of the vibration event.  This is generally a heavy,
     * low frequency vibration.
     */
    public short strongMagnitude;

    /**
     * Weak magnitude of the vibration event.  This is generally a light,
     * high frequency vibration.
     */
    public short weakMagnitude;

    /**
     * Create a vibrator event with the specified duration.
     * <p> Note: intended as a functional equivalent for
     * {@link Vibrator#vibrate(long)} via
     * {@link Vibrator#vibrateEvent(VibratorEvent)}
     *
     * @param duration Duration of the event in milliseconds
     */
    public VibratorEvent(long duration)
    {
        this.duration = duration;
        this.strongMagnitude = DEFAULT_MAGNITUDE;
        this.weakMagnitude = DEFAULT_MAGNITUDE;
    }

    /**
     * Create a vibrator event with the specified duration and magnitude.
     *
     * @param duration Duration of the event in milliseconds
     * @param magnitude Magnitude of the vibration
     */
    public VibratorEvent(long duration, short magnitude)
    {
        this.duration = duration;
        this.strongMagnitude = magnitude;
        this.weakMagnitude = magnitude;
    }

    /**
     * Create a vibrator event with the specified duration with separated strong
     * and weak magnitudes.
     * <p> Many gamepad style input devices have two rumble motors, one for
     * course, slow rumbling and another fine, fast rumbling. The settings for
     * each are known as "strong" and "weak" magnitudes respectively.
     *
     * @param duration Duration of the event in milliseconds
     * @param strongMagnitude Magnitude of the strong vibration
     * @param weakMagnitude Magnitude of the weak vibration
     */
    public VibratorEvent(long duration, short strongMagnitude, short weakMagnitude)
    {
        this.duration = duration;
        this.strongMagnitude = strongMagnitude;
        this.weakMagnitude = weakMagnitude;
    }

    /**
     * @hide
     */
    public static final Parcelable.Creator<VibratorEvent> CREATOR = new Parcelable.Creator() {
        public VibratorEvent[] newArray(int size) { return new VibratorEvent[size]; }
        public VibratorEvent createFromParcel(Parcel in) {
            long duration = in.readLong();
            short strongMagnitude = (short) in.readInt();
            short weakMagnitude = (short) in.readInt();
            return new VibratorEvent(duration, strongMagnitude, weakMagnitude);
        }
    };

    /**
     * @hide
     */
    public int describeContents() {
        return 0;
    }

    /**
     * @hide
     */
    public void writeToParcel(Parcel out, int flags) {
        out.writeLong(duration);
        out.writeInt(strongMagnitude);
        out.writeInt(weakMagnitude);
    }

    @Override
    public String toString() {
        return "VibratorEvent(duration=" + duration + " ms"
                + ", strong magnitude=" + strongMagnitude
                + ", weak magnitude=" + weakMagnitude
                + ")";
    }

    /**
     * Checks whether the event causes a vibration
     * @return True if duration greater than zero and either magnitude is
     * non-zero, else false.
     */
    public boolean vibrates() {
        return ((strongMagnitude != 0) || (weakMagnitude != 0)) && (duration > 0);
    }

    /**
     * Checks whether an event sequence is a valid pattern.  All events must
     * be non-null and have non-zero positive durations.
     *
     * @param events sequence of vibration events
     * @return True if events is a valid pattern, else false.
     */
    public static boolean isValidPattern(VibratorEvent[] events) {
        if (events == null || events.length == 0) {
            return false;
        }

        for (VibratorEvent event : events) {
            if (event == null || event.duration <= 0) {
                return false;
            }
        }

        return true;
    }
};
