/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.hardware.input;

import android.Manifest;
import android.annotation.FloatRange;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.annotation.SystemService;
import android.annotation.TestApi;
import android.annotation.UserIdInt;
import android.app.ActivityThread;
import android.compat.annotation.ChangeId;
import android.compat.annotation.UnsupportedAppUsage;
import android.content.Context;
import android.hardware.BatteryState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.InputEventInjectionSync;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.Log;
import android.view.Display;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.InputMonitor;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.VerifiedInputEvent;
import android.view.WindowManager.LayoutParams;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodSubtype;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Provides information about input devices and available key layouts.
 */
@SystemService(Context.INPUT_SERVICE)
public final class InputManager {
    private static final String TAG = "InputManager";
    // To enable these logs, run: 'adb shell setprop log.tag.InputManager DEBUG' (requires restart)
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.P, trackingBug = 115609023)
    private final IInputManager mIm;

    /**
     * Whether a PointerIcon is shown for stylus pointers.
     * Obtain using {@link #isStylusPointerIconEnabled()}.
     */
    @Nullable
    private Boolean mIsStylusPointerIconEnabled = null;

    /**
     * Broadcast Action: Query available keyboard layouts.
     * <p>
     * The input manager service locates available keyboard layouts
     * by querying broadcast receivers that are registered for this action.
     * An application can offer additional keyboard layouts to the user
     * by declaring a suitable broadcast receiver in its manifest.
     * </p><p>
     * Here is an example broadcast receiver declaration that an application
     * might include in its AndroidManifest.xml to advertise keyboard layouts.
     * The meta-data specifies a resource that contains a description of each keyboard
     * layout that is provided by the application.
     * <pre><code>
     * &lt;receiver android:name=".InputDeviceReceiver"
     *         android:label="@string/keyboard_layouts_label">
     *     &lt;intent-filter>
     *         &lt;action android:name="android.hardware.input.action.QUERY_KEYBOARD_LAYOUTS" />
     *     &lt;/intent-filter>
     *     &lt;meta-data android:name="android.hardware.input.metadata.KEYBOARD_LAYOUTS"
     *             android:resource="@xml/keyboard_layouts" />
     * &lt;/receiver>
     * </code></pre>
     * </p><p>
     * In the above example, the <code>@xml/keyboard_layouts</code> resource refers to
     * an XML resource whose root element is <code>&lt;keyboard-layouts></code> that
     * contains zero or more <code>&lt;keyboard-layout></code> elements.
     * Each <code>&lt;keyboard-layout></code> element specifies the name, label, and location
     * of a key character map for a particular keyboard layout.  The label on the receiver
     * is used to name the collection of keyboard layouts provided by this receiver in the
     * keyboard layout settings.
     * <pre><code>
     * &lt;?xml version="1.0" encoding="utf-8"?>
     * &lt;keyboard-layouts xmlns:android="http://schemas.android.com/apk/res/android">
     *     &lt;keyboard-layout android:name="keyboard_layout_english_us"
     *             android:label="@string/keyboard_layout_english_us_label"
     *             android:keyboardLayout="@raw/keyboard_layout_english_us" />
     * &lt;/keyboard-layouts>
     * </pre></code>
     * </p><p>
     * The <code>android:name</code> attribute specifies an identifier by which
     * the keyboard layout will be known in the package.
     * The <code>android:label</code> attribute specifies a human-readable descriptive
     * label to describe the keyboard layout in the user interface, such as "English (US)".
     * The <code>android:keyboardLayout</code> attribute refers to a
     * <a href="https://source.android.com/docs/core/interaction/input/key-character-map-files">
     * key character map</a> resource that defines the keyboard layout.
     * The <code>android:keyboardLocale</code> attribute specifies a comma separated list of BCP 47
     * language tags depicting the locales supported by the keyboard layout. This attribute is
     * optional and will be used for auto layout selection for external physical keyboards.
     * The <code>android:keyboardLayoutType</code> attribute specifies the layoutType for the
     * keyboard layout. This can be either empty or one of the following supported layout types:
     * qwerty, qwertz, azerty, dvorak, colemak, workman, extended, turkish_q, turkish_f. This
     * attribute is optional and will be used for auto layout selection for external physical
     * keyboards.
     * </p>
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_QUERY_KEYBOARD_LAYOUTS =
            "android.hardware.input.action.QUERY_KEYBOARD_LAYOUTS";

    /**
     * Metadata Key: Keyboard layout metadata associated with
     * {@link #ACTION_QUERY_KEYBOARD_LAYOUTS}.
     * <p>
     * Specifies the resource id of a XML resource that describes the keyboard
     * layouts that are provided by the application.
     * </p>
     */
    public static final String META_DATA_KEYBOARD_LAYOUTS =
            "android.hardware.input.metadata.KEYBOARD_LAYOUTS";

    /**
     * Prevent touches from being consumed by apps if these touches passed through a non-trusted
     * window from a different UID and are considered unsafe.
     *
     * @hide
     */
    @TestApi
    @ChangeId
    public static final long BLOCK_UNTRUSTED_TOUCHES = 158002302L;

    /**
     * Input Event Injection Synchronization Mode: None.
     * Never blocks.  Injection is asynchronous and is assumed always to be successful.
     * @hide
     */
    public static final int INJECT_INPUT_EVENT_MODE_ASYNC = InputEventInjectionSync.NONE;

    /**
     * Input Event Injection Synchronization Mode: Wait for result.
     * Waits for previous events to be dispatched so that the input dispatcher can
     * determine whether input event injection will be permitted based on the current
     * input focus.  Does not wait for the input event to finish being handled
     * by the application.
     * @hide
     */
    public static final int INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT =
            InputEventInjectionSync.WAIT_FOR_RESULT;

    /**
     * Input Event Injection Synchronization Mode: Wait for finish.
     * Waits for the event to be delivered to the application and handled.
     * @hide
     */
    @UnsupportedAppUsage(trackingBug = 171972397)
    public static final int INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH =
            InputEventInjectionSync.WAIT_FOR_FINISHED;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = { "SWITCH_STATE_" }, value = {
            SWITCH_STATE_UNKNOWN,
            SWITCH_STATE_OFF,
            SWITCH_STATE_ON
    })
    public @interface SwitchState {}

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = { "REMAPPABLE_MODIFIER_KEY_" }, value = {
            RemappableModifierKey.REMAPPABLE_MODIFIER_KEY_CTRL_LEFT,
            RemappableModifierKey.REMAPPABLE_MODIFIER_KEY_CTRL_RIGHT,
            RemappableModifierKey.REMAPPABLE_MODIFIER_KEY_META_LEFT,
            RemappableModifierKey.REMAPPABLE_MODIFIER_KEY_META_RIGHT,
            RemappableModifierKey.REMAPPABLE_MODIFIER_KEY_ALT_LEFT,
            RemappableModifierKey.REMAPPABLE_MODIFIER_KEY_ALT_RIGHT,
            RemappableModifierKey.REMAPPABLE_MODIFIER_KEY_SHIFT_LEFT,
            RemappableModifierKey.REMAPPABLE_MODIFIER_KEY_SHIFT_RIGHT,
            RemappableModifierKey.REMAPPABLE_MODIFIER_KEY_CAPS_LOCK,
    })
    public @interface RemappableModifierKey {
        int REMAPPABLE_MODIFIER_KEY_CTRL_LEFT = KeyEvent.KEYCODE_CTRL_LEFT;
        int REMAPPABLE_MODIFIER_KEY_CTRL_RIGHT = KeyEvent.KEYCODE_CTRL_RIGHT;
        int REMAPPABLE_MODIFIER_KEY_META_LEFT = KeyEvent.KEYCODE_META_LEFT;
        int REMAPPABLE_MODIFIER_KEY_META_RIGHT = KeyEvent.KEYCODE_META_RIGHT;
        int REMAPPABLE_MODIFIER_KEY_ALT_LEFT = KeyEvent.KEYCODE_ALT_LEFT;
        int REMAPPABLE_MODIFIER_KEY_ALT_RIGHT = KeyEvent.KEYCODE_ALT_RIGHT;
        int REMAPPABLE_MODIFIER_KEY_SHIFT_LEFT = KeyEvent.KEYCODE_SHIFT_LEFT;
        int REMAPPABLE_MODIFIER_KEY_SHIFT_RIGHT = KeyEvent.KEYCODE_SHIFT_RIGHT;
        int REMAPPABLE_MODIFIER_KEY_CAPS_LOCK = KeyEvent.KEYCODE_CAPS_LOCK;
    }

    /**
     * Switch State: Unknown.
     *
     * The system has yet to report a valid value for the switch.
     * @hide
     */
    public static final int SWITCH_STATE_UNKNOWN = -1;

    /**
     * Switch State: Off.
     * @hide
     */
    public static final int SWITCH_STATE_OFF = 0;

    /**
     * Switch State: On.
     * @hide
     */
    public static final int SWITCH_STATE_ON = 1;

    private final InputManagerGlobal mGlobal;
    private final Context mContext;

    /** @hide */
    public InputManager(Context context) {
        mGlobal = InputManagerGlobal.getInstance();
        mIm = mGlobal.getInputManagerService();
        mContext = context;
    }

    /**
     * Gets an instance of the input manager.
     *
     *  Warning: The usage of this method is not supported!
     *
     *  @return The input manager instance.
     *  Use {@link Context#getSystemService(Class)}
     *  to obtain the InputManager instance.
     *
     * TODO (b/277717573): Soft remove this API in version V.
     * TODO (b/277039664): Migrate app usage off this API.
     *
     * @hide
     */
    @Deprecated
    @UnsupportedAppUsage
    public static InputManager getInstance() {
        return Objects.requireNonNull(ActivityThread.currentApplication())
                .getSystemService(InputManager.class);
    }

    /**
     * Get the current VelocityTracker strategy.
     * @hide
     */
    public String getVelocityTrackerStrategy() {
        return mGlobal.getVelocityTrackerStrategy();
    }

    /**
     * Gets information about the input device with the specified id.
     * @param id The device id.
     * @return The input device or null if not found.
     */
    @Nullable
    public InputDevice getInputDevice(int id) {
        return mGlobal.getInputDevice(id);
    }

    /**
     * Gets information about the input device with the specified descriptor.
     * @param descriptor The input device descriptor.
     * @return The input device or null if not found.
     * @hide
     */
    public InputDevice getInputDeviceByDescriptor(String descriptor) {
        return mGlobal.getInputDeviceByDescriptor(descriptor);
    }

    /**
     * Gets the ids of all input devices in the system.
     * @return The input device ids.
     */
    public int[] getInputDeviceIds() {
        return mGlobal.getInputDeviceIds();
    }

    /**
     * Returns true if an input device is enabled. Should return true for most
     * situations. Some system apps may disable an input device, for
     * example to prevent unwanted touch events.
     *
     * @param id The input device Id.
     *
     * @hide
     */
    public boolean isInputDeviceEnabled(int id) {
        return mGlobal.isInputDeviceEnabled(id);
    }

    /**
     * Enables an InputDevice.
     * <p>
     * Requires {@link android.Manifest.permission#DISABLE_INPUT_DEVICE}.
     * </p>
     *
     * @param id The input device Id.
     *
     * @hide
     */
    public void enableInputDevice(int id) {
        mGlobal.enableInputDevice(id);
    }

    /**
     * Disables an InputDevice.
     * <p>
     * Requires {@link android.Manifest.permission#DISABLE_INPUT_DEVICE}.
     * </p>
     *
     * @param id The input device Id.
     *
     * @hide
     */
    public void disableInputDevice(int id) {
        mGlobal.disableInputDevice(id);
    }

    /**
     * Registers an input device listener to receive notifications about when
     * input devices are added, removed or changed.
     *
     * @param listener The listener to register.
     * @param handler The handler on which the listener should be invoked, or null
     * if the listener should be invoked on the calling thread's looper.
     *
     * @see #unregisterInputDeviceListener
     */
    public void registerInputDeviceListener(InputDeviceListener listener, Handler handler) {
        mGlobal.registerInputDeviceListener(listener, handler);
    }

    /**
     * Unregisters an input device listener.
     *
     * @param listener The listener to unregister.
     *
     * @see #registerInputDeviceListener
     */
    public void unregisterInputDeviceListener(InputDeviceListener listener) {
        mGlobal.unregisterInputDeviceListener(listener);
    }

    /**
     * Queries whether the device is in tablet mode.
     *
     * @return The tablet switch state which is one of {@link #SWITCH_STATE_UNKNOWN},
     * {@link #SWITCH_STATE_OFF} or {@link #SWITCH_STATE_ON}.
     * @hide
     */
    @SwitchState
    public int isInTabletMode() {
        try {
            return mIm.isInTabletMode();
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    /**
     * Register a tablet mode changed listener.
     *
     * @param listener The listener to register.
     * @param handler The handler on which the listener should be invoked, or null
     * if the listener should be invoked on the calling thread's looper.
     * @hide
     */
    public void registerOnTabletModeChangedListener(
            OnTabletModeChangedListener listener, Handler handler) {
<<<<<<< PATCH SET (3e78d5 Bind an input device via descriptor)
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        synchronized (mTabletModeLock) {
            if (mOnTabletModeChangedListeners == null) {
                initializeTabletModeListenerLocked();
            }
            int idx = findOnTabletModeChangedListenerLocked(listener);
            if (idx < 0) {
                OnTabletModeChangedListenerDelegate d =
                        new OnTabletModeChangedListenerDelegate(listener, handler);
                mOnTabletModeChangedListeners.add(d);
            }
        }
=======
        mGlobal.registerOnTabletModeChangedListener(listener, handler);
>>>>>>> BASE      (da32cb Merge "Adjust with changes in libxml2 upgrade" into main)
    }

    /**
     * Unregister a tablet mode changed listener.
     *
     * @param listener The listener to unregister.
     * @hide
     */
    public void unregisterOnTabletModeChangedListener(OnTabletModeChangedListener listener) {
        mGlobal.unregisterOnTabletModeChangedListener(listener);
    }

    /**
     * Queries whether the device's microphone is muted
     *
     * @return The mic mute switch state which is one of {@link #SWITCH_STATE_UNKNOWN},
     * {@link #SWITCH_STATE_OFF} or {@link #SWITCH_STATE_ON}.
     * @hide
     */
    @SwitchState
    public int isMicMuted() {
        try {
            return mIm.isMicMuted();
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    /**
     * Gets information about all supported keyboard layouts.
     * <p>
     * The input manager consults the built-in keyboard layouts as well
     * as all keyboard layouts advertised by applications using a
     * {@link #ACTION_QUERY_KEYBOARD_LAYOUTS} broadcast receiver.
     * </p>
     *
     * @return A list of all supported keyboard layouts.
     *
     * @hide
     */
    public KeyboardLayout[] getKeyboardLayouts() {
        try {
            return mIm.getKeyboardLayouts();
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    /**
     * Returns the descriptors of all supported keyboard layouts appropriate for the specified
     * input device.
     * <p>
     * The input manager consults the built-in keyboard layouts as well as all keyboard layouts
     * advertised by applications using a {@link #ACTION_QUERY_KEYBOARD_LAYOUTS} broadcast receiver.
     * </p>
     *
     * @param device The input device to query.
     * @return The ids of all keyboard layouts which are supported by the specified input device.
     *
     * @hide
     */
    @TestApi
    @NonNull
    public List<String> getKeyboardLayoutDescriptorsForInputDevice(@NonNull InputDevice device) {
        KeyboardLayout[] layouts = getKeyboardLayoutsForInputDevice(device.getIdentifier());
        List<String> res = new ArrayList<>();
        for (KeyboardLayout kl : layouts) {
            res.add(kl.getDescriptor());
        }
        return res;
    }

    /**
     * Returns the layout type of the queried layout
     * <p>
     * The input manager consults the built-in keyboard layouts as well as all keyboard layouts
     * advertised by applications using a {@link #ACTION_QUERY_KEYBOARD_LAYOUTS} broadcast receiver.
     * </p>
     *
     * @param layoutDescriptor The layout descriptor of the queried layout
     * @return layout type of the queried layout
     *
     * @hide
     */
    @TestApi
    @NonNull
    public String getKeyboardLayoutTypeForLayoutDescriptor(@NonNull String layoutDescriptor) {
        KeyboardLayout[] layouts = getKeyboardLayouts();
        for (KeyboardLayout kl : layouts) {
            if (layoutDescriptor.equals(kl.getDescriptor())) {
                return kl.getLayoutType();
            }
        }
        return "";
    }

    /**
     * Gets information about all supported keyboard layouts appropriate
     * for a specific input device.
     * <p>
     * The input manager consults the built-in keyboard layouts as well
     * as all keyboard layouts advertised by applications using a
     * {@link #ACTION_QUERY_KEYBOARD_LAYOUTS} broadcast receiver.
     * </p>
     *
     * @return A list of all supported keyboard layouts for a specific
     * input device.
     *
     * @hide
     */
    @NonNull
    public KeyboardLayout[] getKeyboardLayoutsForInputDevice(
            @NonNull InputDeviceIdentifier identifier) {
        return mGlobal.getKeyboardLayoutsForInputDevice(identifier);
    }

    /**
     * Gets the keyboard layout with the specified descriptor.
     *
     * @param keyboardLayoutDescriptor The keyboard layout descriptor, as returned by
     * {@link KeyboardLayout#getDescriptor()}.
     * @return The keyboard layout, or null if it could not be loaded.
     *
     * @hide
     */
    public KeyboardLayout getKeyboardLayout(String keyboardLayoutDescriptor) {
        if (keyboardLayoutDescriptor == null) {
            throw new IllegalArgumentException("keyboardLayoutDescriptor must not be null");
        }

        try {
            return mIm.getKeyboardLayout(keyboardLayoutDescriptor);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    /**
     * Gets the current keyboard layout descriptor for the specified input device.
     *
     * @param identifier Identifier for the input device
     * @return The keyboard layout descriptor, or null if no keyboard layout has been set.
     *
     * @hide
     */
    @TestApi
    @Nullable
    public String getCurrentKeyboardLayoutForInputDevice(
            @NonNull InputDeviceIdentifier identifier) {
        try {
            return mIm.getCurrentKeyboardLayoutForInputDevice(identifier);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    /**
     * Sets the current keyboard layout descriptor for the specified input device.
     * <p>
     * This method may have the side-effect of causing the input device in question to be
     * reconfigured.
     * </p>
     *
     * @param identifier The identifier for the input device.
     * @param keyboardLayoutDescriptor The keyboard layout descriptor to use, must not be null.
     *
     * @hide
     */
    @TestApi
    @RequiresPermission(Manifest.permission.SET_KEYBOARD_LAYOUT)
    public void setCurrentKeyboardLayoutForInputDevice(@NonNull InputDeviceIdentifier identifier,
<<<<<<< PATCH SET (3e78d5 Bind an input device via descriptor)
                                                       @NonNull String keyboardLayoutDescriptor) {
        if (identifier == null) {
            throw new IllegalArgumentException("identifier must not be null");
        }
        if (keyboardLayoutDescriptor == null) {
            throw new IllegalArgumentException("keyboardLayoutDescriptor must not be null");
        }

        try {
            mIm.setCurrentKeyboardLayoutForInputDevice(identifier,
                    keyboardLayoutDescriptor);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
=======
            @NonNull String keyboardLayoutDescriptor) {
        mGlobal.setCurrentKeyboardLayoutForInputDevice(identifier,
                keyboardLayoutDescriptor);
>>>>>>> BASE      (da32cb Merge "Adjust with changes in libxml2 upgrade" into main)
    }

    /**
     * Gets all keyboard layout descriptors that are enabled for the specified input device.
     *
     * @param identifier The identifier for the input device.
     * @return The keyboard layout descriptors.
     *
     * @hide
     */
    public String[] getEnabledKeyboardLayoutsForInputDevice(InputDeviceIdentifier identifier) {
        if (identifier == null) {
            throw new IllegalArgumentException("inputDeviceDescriptor must not be null");
        }

        try {
            return mIm.getEnabledKeyboardLayoutsForInputDevice(identifier);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    /**
     * Adds the keyboard layout descriptor for the specified input device.
     * <p>
     * This method may have the side-effect of causing the input device in question to be
     * reconfigured.
     * </p>
     *
     * @param identifier The identifier for the input device.
     * @param keyboardLayoutDescriptor The descriptor of the keyboard layout to add.
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.SET_KEYBOARD_LAYOUT)
    public void addKeyboardLayoutForInputDevice(InputDeviceIdentifier identifier,
                                                String keyboardLayoutDescriptor) {
        if (identifier == null) {
            throw new IllegalArgumentException("inputDeviceDescriptor must not be null");
        }
        if (keyboardLayoutDescriptor == null) {
            throw new IllegalArgumentException("keyboardLayoutDescriptor must not be null");
        }

        try {
            mIm.addKeyboardLayoutForInputDevice(identifier, keyboardLayoutDescriptor);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    /**
     * Removes the keyboard layout descriptor for the specified input device.
     * <p>
     * This method may have the side-effect of causing the input device in question to be
     * reconfigured.
     * </p>
     *
     * @param identifier The identifier for the input device.
     * @param keyboardLayoutDescriptor The descriptor of the keyboard layout to remove.
     *
     * @hide
     */
    @TestApi
    @RequiresPermission(Manifest.permission.SET_KEYBOARD_LAYOUT)
    public void removeKeyboardLayoutForInputDevice(@NonNull InputDeviceIdentifier identifier,
                                                   @NonNull String keyboardLayoutDescriptor) {
        if (identifier == null) {
            throw new IllegalArgumentException("inputDeviceDescriptor must not be null");
        }
        if (keyboardLayoutDescriptor == null) {
            throw new IllegalArgumentException("keyboardLayoutDescriptor must not be null");
        }

        try {
            mIm.removeKeyboardLayoutForInputDevice(identifier, keyboardLayoutDescriptor);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    /**
     * Remaps modifier keys. Remapping a modifier key to itself will clear any previous remappings
     * for that key.
     *
     * @param fromKey The modifier key getting remapped.
     * @param toKey The modifier key that it is remapped to.
     *
     * @hide
     */
    @TestApi
    @RequiresPermission(Manifest.permission.REMAP_MODIFIER_KEYS)
    public void remapModifierKey(@RemappableModifierKey int fromKey,
            @RemappableModifierKey int toKey) {
        try {
            mIm.remapModifierKey(fromKey, toKey);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    /**
     * Clears all existing modifier key remappings
     *
     * @hide
     */
    @TestApi
    @RequiresPermission(Manifest.permission.REMAP_MODIFIER_KEYS)
    public void clearAllModifierKeyRemappings() {
        try {
            mIm.clearAllModifierKeyRemappings();
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    /**
     * Provides the current modifier key remapping
     *
     * @return a {fromKey, toKey} map that contains the existing modifier key remappings..
     * {@link RemappableModifierKey}
     *
     * @hide
     */
    @TestApi
    @NonNull
    @SuppressWarnings("unchecked")
    @RequiresPermission(Manifest.permission.REMAP_MODIFIER_KEYS)
    public Map<Integer, Integer> getModifierKeyRemapping() {
        try {
            return mIm.getModifierKeyRemapping();
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    /**
     * Gets the TouchCalibration applied to the specified input device's coordinates.
     *
     * @param inputDeviceDescriptor The input device descriptor.
     * @return The TouchCalibration currently assigned for use with the given
     * input device. If none is set, an identity TouchCalibration is returned.
     *
     * @hide
     */
    public TouchCalibration getTouchCalibration(String inputDeviceDescriptor, int surfaceRotation) {
        try {
            return mIm.getTouchCalibrationForInputDevice(inputDeviceDescriptor, surfaceRotation);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    /**
     * Sets the TouchCalibration to apply to the specified input device's coordinates.
     * <p>
     * This method may have the side-effect of causing the input device in question
     * to be reconfigured. Requires {@link android.Manifest.permission#SET_INPUT_CALIBRATION}.
     * </p>
     *
     * @param inputDeviceDescriptor The input device descriptor.
     * @param calibration The calibration to be applied
     *
     * @hide
     */
    public void setTouchCalibration(String inputDeviceDescriptor, int surfaceRotation,
                                    TouchCalibration calibration) {
        try {
            mIm.setTouchCalibrationForInputDevice(inputDeviceDescriptor, surfaceRotation, calibration);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    /**
     * Gets the keyboard layout descriptor for the specified input device, userId, imeInfo and
     * imeSubtype.
     *
     * @param identifier Identifier for the input device
     * @param userId user profile ID
     * @param imeInfo contains IME information like imeId, etc.
     * @param imeSubtype contains IME subtype information like input languageTag, layoutType, etc.
     * @return The keyboard layout descriptor, or null if no keyboard layout has been set.
     *
     * @hide
     */
    @Nullable
    public String getKeyboardLayoutForInputDevice(@NonNull InputDeviceIdentifier identifier,
            @UserIdInt int userId, @NonNull InputMethodInfo imeInfo,
            @Nullable InputMethodSubtype imeSubtype) {
        try {
            return mIm.getKeyboardLayoutForInputDevice(identifier, userId, imeInfo, imeSubtype);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    /**
     * Sets the keyboard layout descriptor for the specified input device, userId, imeInfo and
     * imeSubtype.
     *
     * <p>
     * This method may have the side-effect of causing the input device in question to be
     * reconfigured.
     * </p>
     *
     * @param identifier The identifier for the input device.
     * @param userId user profile ID
     * @param imeInfo contains IME information like imeId, etc.
     * @param imeSubtype contains IME subtype information like input languageTag, layoutType, etc.
     * @param keyboardLayoutDescriptor The keyboard layout descriptor to use, must not be null.
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.SET_KEYBOARD_LAYOUT)
    public void setKeyboardLayoutForInputDevice(@NonNull InputDeviceIdentifier identifier,
            @UserIdInt int userId, @NonNull InputMethodInfo imeInfo,
            @Nullable InputMethodSubtype imeSubtype, @NonNull String keyboardLayoutDescriptor) {
        if (identifier == null) {
            throw new IllegalArgumentException("identifier must not be null");
        }
        if (keyboardLayoutDescriptor == null) {
            throw new IllegalArgumentException("keyboardLayoutDescriptor must not be null");
        }

        try {
            mIm.setKeyboardLayoutForInputDevice(identifier, userId, imeInfo, imeSubtype,
                    keyboardLayoutDescriptor);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    /**
     * Gets all keyboard layouts that are enabled for the specified input device, userId, imeInfo
     * and imeSubtype.
     *
     * @param identifier The identifier for the input device.
     * @param userId user profile ID
     * @param imeInfo contains IME information like imeId, etc.
     * @param imeSubtype contains IME subtype information like input languageTag, layoutType, etc.
     * @return The keyboard layout descriptors.
     *
     * @hide
     */
    public KeyboardLayout[] getKeyboardLayoutListForInputDevice(InputDeviceIdentifier identifier,
            @UserIdInt int userId, @NonNull InputMethodInfo imeInfo,
            @Nullable InputMethodSubtype imeSubtype) {
        if (identifier == null) {
            throw new IllegalArgumentException("inputDeviceDescriptor must not be null");
        }

        try {
            return mIm.getKeyboardLayoutListForInputDevice(identifier, userId, imeInfo, imeSubtype);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    /**
     * Changes the mouse pointer speed temporarily, but does not save the setting.
     * <p>
     * Requires {@link android.Manifest.permission#SET_POINTER_SPEED}.
     * </p>
     *
     * @param speed The pointer speed as a value between {@link InputSettings#MIN_POINTER_SPEED} and
     * {@link InputSettings#MAX_POINTER_SPEED}, or the default value {@link InputSettings#DEFAULT_POINTER_SPEED}.
     *
     * @hide
     */
    public void tryPointerSpeed(int speed) {
        if (speed < InputSettings.MIN_POINTER_SPEED || speed > InputSettings.MAX_POINTER_SPEED) {
            throw new IllegalArgumentException("speed out of range");
        }

        try {
            mIm.tryPointerSpeed(speed);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    /**
     * Returns the maximum allowed obscuring opacity per UID to propagate touches.
     *
     * <p>For certain window types (eg. {@link LayoutParams#TYPE_APPLICATION_OVERLAY}), the decision
     * of honoring {@link LayoutParams#FLAG_NOT_TOUCHABLE} or not depends on the combined obscuring
     * opacity of the windows above the touch-consuming window, per UID. Check documentation of
     * {@link LayoutParams#FLAG_NOT_TOUCHABLE} for more details.
     *
     * <p>The value returned is between 0 (inclusive) and 1 (inclusive).
     *
     * @see LayoutParams#FLAG_NOT_TOUCHABLE
     */
    @FloatRange(from = 0, to = 1)
    public float getMaximumObscuringOpacityForTouch() {
<<<<<<< PATCH SET (3e78d5 Bind an input device via descriptor)
        Context context = ActivityThread.currentApplication();
        return Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.MAXIMUM_OBSCURING_OPACITY_FOR_TOUCH,
                DEFAULT_MAXIMUM_OBSCURING_OPACITY_FOR_TOUCH);
    }

    /**
     * Sets the maximum allowed obscuring opacity by UID to propagate touches.
     *
     * <p>For certain window types (eg. SAWs), the decision of honoring {@link LayoutParams
     * #FLAG_NOT_TOUCHABLE} or not depends on the combined obscuring opacity of the windows
     * above the touch-consuming window.
     *
     * <p>For a certain UID:
     * <ul>
     *     <li>If it's the same as the UID of the touch-consuming window, allow it to propagate
     *     the touch.
     *     <li>Otherwise take all its windows of eligible window types above the touch-consuming
     *     window, compute their combined obscuring opacity considering that {@code
     *     opacity(A, B) = 1 - (1 - opacity(A))*(1 - opacity(B))}. If the computed value is
     *     lesser than or equal to this setting and there are no other windows preventing the
     *     touch, allow the UID to propagate the touch.
     * </ul>
     *
     * <p>This value should be between 0 (inclusive) and 1 (inclusive).
     *
     * @see #getMaximumObscuringOpacityForTouch()
     *
     * @hide
     */
    @TestApi
    @RequiresPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
    public void setMaximumObscuringOpacityForTouch(@FloatRange(from = 0, to = 1) float opacity) {
        if (opacity < 0 || opacity > 1) {
            throw new IllegalArgumentException(
                    "Maximum obscuring opacity for touch should be >= 0 and <= 1");
        }
        Context context = ActivityThread.currentApplication();
        Settings.Global.putFloat(context.getContentResolver(),
                Settings.Global.MAXIMUM_OBSCURING_OPACITY_FOR_TOUCH, opacity);
    }

    /**
     * Returns the current mode of the block untrusted touches feature, one of:
     * <ul>
     *     <li>{@link BlockUntrustedTouchesMode#DISABLED}
     *     <li>{@link BlockUntrustedTouchesMode#PERMISSIVE}
     *     <li>{@link BlockUntrustedTouchesMode#BLOCK}
     * </ul>
     *
     * @hide
     */
    @TestApi
    @BlockUntrustedTouchesMode
    public int getBlockUntrustedTouchesMode(@NonNull Context context) {
        int mode = Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.BLOCK_UNTRUSTED_TOUCHES_MODE, DEFAULT_BLOCK_UNTRUSTED_TOUCHES_MODE);
        if (!ArrayUtils.contains(BLOCK_UNTRUSTED_TOUCHES_MODES, mode)) {
            Log.w(TAG, "Unknown block untrusted touches feature mode " + mode + ", using "
                    + "default " + DEFAULT_BLOCK_UNTRUSTED_TOUCHES_MODE);
            return DEFAULT_BLOCK_UNTRUSTED_TOUCHES_MODE;
        }
        return mode;
    }

    /**
     * Sets the mode of the block untrusted touches feature to one of:
     * <ul>
     *     <li>{@link BlockUntrustedTouchesMode#DISABLED}
     *     <li>{@link BlockUntrustedTouchesMode#PERMISSIVE}
     *     <li>{@link BlockUntrustedTouchesMode#BLOCK}
     * </ul>
     *
     * @hide
     */
    @TestApi
    @RequiresPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
    public void setBlockUntrustedTouchesMode(@NonNull Context context,
                                             @BlockUntrustedTouchesMode int mode) {
        if (!ArrayUtils.contains(BLOCK_UNTRUSTED_TOUCHES_MODES, mode)) {
            throw new IllegalArgumentException("Invalid feature mode " + mode);
        }
        Settings.Global.putInt(context.getContentResolver(),
                Settings.Global.BLOCK_UNTRUSTED_TOUCHES_MODE, mode);
=======
        return InputSettings.getMaximumObscuringOpacityForTouch(mContext);
>>>>>>> BASE      (da32cb Merge "Adjust with changes in libxml2 upgrade" into main)
    }

    /**
     * Queries the framework about whether any physical keys exist on any currently attached input
     * devices that are capable of producing the given array of key codes.
     *
     * @param keyCodes The array of key codes to query.
     * @return A new array of the same size as the key codes array whose elements
     * are set to true if at least one attached keyboard supports the corresponding key code
     * at the same index in the key codes array.
     *
     * @hide
     */
    public boolean[] deviceHasKeys(int[] keyCodes) {
        return deviceHasKeys(-1, keyCodes);
    }

    /**
     * Queries the framework about whether any physical keys exist on the specified input device
     * that are capable of producing the given array of key codes.
     *
     * @param id The id of the input device to query or -1 to consult all devices.
     * @param keyCodes The array of key codes to query.
     * @return A new array of the same size as the key codes array whose elements are set to true
     * if the given device could produce the corresponding key code at the same index in the key
     * codes array.
     *
     * @hide
     */
    public boolean[] deviceHasKeys(int id, int[] keyCodes) {
        return mGlobal.deviceHasKeys(id, keyCodes);
    }

    /**
     * Gets the {@link android.view.KeyEvent key code} produced by the given location on a reference
     * QWERTY keyboard layout.
     * <p>
     * This API is useful for querying the physical location of keys that change the character
     * produced based on the current locale and keyboard layout.
     * <p>
     * @see InputDevice#getKeyCodeForKeyLocation(int) for examples.
     *
     * @param locationKeyCode The location of a key specified as a key code on the QWERTY layout.
     * This provides a consistent way of referring to the physical location of a key independently
     * of the current keyboard layout. Also see the
     * <a href="https://www.w3.org/TR/2017/CR-uievents-code-20170601/#key-alphanumeric-writing-system">
     * hypothetical keyboard</a> provided by the W3C, which may be helpful for identifying the
     * physical location of a key.
     * @return The key code produced by the key at the specified location, given the current
     * keyboard layout. Returns {@link KeyEvent#KEYCODE_UNKNOWN} if the device does not specify
     * {@link InputDevice#SOURCE_KEYBOARD} or the requested mapping cannot be determined.
     *
     * @hide
     */
    public int getKeyCodeForKeyLocation(int deviceId, int locationKeyCode) {
        return mGlobal.getKeyCodeForKeyLocation(deviceId, locationKeyCode);
    }

    /**
     * Injects an input event into the event system, targeting windows owned by the provided uid.
     *
     * If a valid targetUid is provided, the system will only consider injecting the input event
     * into windows owned by the provided uid. If the input event is targeted at a window that is
     * not owned by the provided uid, input injection will fail and a RemoteException will be
     * thrown.
     *
     * The synchronization mode determines whether the method blocks while waiting for
     * input injection to proceed.
     * <p>
     * Requires the {@link android.Manifest.permission#INJECT_EVENTS} permission.
     * </p><p>
     * Make sure you correctly set the event time and input source of the event
     * before calling this method.
     * </p>
     *
     * @param event The event to inject.
     * @param mode The synchronization mode.  One of:
     * {@link android.os.InputEventInjectionSync#NONE},
     * {@link android.os.InputEventInjectionSync#WAIT_FOR_RESULT}, or
     * {@link android.os.InputEventInjectionSync#WAIT_FOR_FINISHED}.
     * @param targetUid The uid to target, or {@link android.os.Process#INVALID_UID} to target all
     *                 windows.
     * @return True if input event injection succeeded.
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.INJECT_EVENTS)
    public boolean injectInputEvent(InputEvent event, int mode, int targetUid) {
        return mGlobal.injectInputEvent(event, mode, targetUid);
    }

    /**
     * Injects an input event into the event system on behalf of an application.
     * The synchronization mode determines whether the method blocks while waiting for
     * input injection to proceed.
     * <p>
     * Requires the {@link android.Manifest.permission#INJECT_EVENTS} permission.
     * </p><p>
     * Make sure you correctly set the event time and input source of the event
     * before calling this method.
     * </p>
     *
     * @param event The event to inject.
     * @param mode The synchronization mode.  One of:
     * {@link android.os.InputEventInjectionSync#NONE},
     * {@link android.os.InputEventInjectionSync#WAIT_FOR_RESULT}, or
     * {@link android.os.InputEventInjectionSync#WAIT_FOR_FINISHED}.
     * @return True if input event injection succeeded.
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.INJECT_EVENTS)
    @UnsupportedAppUsage
    public boolean injectInputEvent(InputEvent event, int mode) {
        return mGlobal.injectInputEvent(event, mode);
    }

    /**
     * Verify the details of an {@link android.view.InputEvent} that came from the system.
     * If the event did not come from the system, or its details could not be verified, then this
     * will return {@code null}. Receiving {@code null} does not mean that the event did not
     * originate from the system, just that we were unable to verify it. This can
     * happen for a number of reasons during normal operation.
     *
     * @param event The {@link android.view.InputEvent} to check
     *
     * @return {@link android.view.VerifiedInputEvent}, which is a subset of the provided
     * {@link android.view.InputEvent}
     *         {@code null} if the event could not be verified.
     */
    @Nullable
    public VerifiedInputEvent verifyInputEvent(@NonNull InputEvent event) {
        try {
            return mIm.verifyInputEvent(event);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    /**
     * Changes the mouse pointer's icon shape into the specified id.
     *
     * @param iconId The id of the pointer graphic, as a value between
     * {@link PointerIcon#TYPE_ARROW} and {@link PointerIcon#TYPE_HANDWRITING}.
     *
     * @hide
     */
    @UnsupportedAppUsage
    public void setPointerIconType(int iconId) {
        mGlobal.setPointerIconType(iconId);
    }

    /** @hide */
    public void setCustomPointerIcon(PointerIcon icon) {
        mGlobal.setCustomPointerIcon(icon);
    }

    /**
     * Check if showing a {@link android.view.PointerIcon} for styluses is enabled.
     *
     * @return true if a pointer icon will be shown over the location of a
     * stylus pointer, false if there is no pointer icon shown for styluses.
     */
    public boolean isStylusPointerIconEnabled() {
        if (mIsStylusPointerIconEnabled == null) {
            mIsStylusPointerIconEnabled = InputSettings.isStylusPointerIconEnabled(mContext);
        }
        return mIsStylusPointerIconEnabled;
    }

    /**
     * Request or release pointer capture.
     * <p>
     * When in capturing mode, the pointer icon disappears and all mouse events are dispatched to
     * the window which has requested the capture. Relative position changes are available through
     * {@link MotionEvent#getX} and {@link MotionEvent#getY}.
     *
     * @param enable true when requesting pointer capture, false when releasing.
     *
     * @hide
     */
    public void requestPointerCapture(IBinder windowToken, boolean enable) {
        mGlobal.requestPointerCapture(windowToken, enable);
    }

    /**
     * Monitor input on the specified display for gestures.
     *
     * @hide
     */
    public InputMonitor monitorGestureInput(String name, int displayId) {
        return mGlobal.monitorGestureInput(name, displayId);
    }

    /**
     * Get sensors information as list.
     *
     * @hide
     */
    public InputSensorInfo[] getSensorList(int deviceId) {
        return mGlobal.getSensorList(deviceId);
    }

    /**
     * Enable input device sensor
     *
     * @hide
     */
    public boolean enableSensor(int deviceId, int sensorType, int samplingPeriodUs,
<<<<<<< PATCH SET (3e78d5 Bind an input device via descriptor)
                                int maxBatchReportLatencyUs) {
        try {
            return mIm.enableSensor(deviceId, sensorType, samplingPeriodUs,
                    maxBatchReportLatencyUs);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
=======
            int maxBatchReportLatencyUs) {
        return mGlobal.enableSensor(deviceId, sensorType, samplingPeriodUs,
                maxBatchReportLatencyUs);
>>>>>>> BASE      (da32cb Merge "Adjust with changes in libxml2 upgrade" into main)
    }

    /**
     * Enable input device sensor
     *
     * @hide
     */
    public void disableSensor(int deviceId, int sensorType) {
        mGlobal.disableSensor(deviceId, sensorType);
    }

    /**
     * Flush input device sensor
     *
     * @hide
     */
    public boolean flushSensor(int deviceId, int sensorType) {
        return mGlobal.flushSensor(deviceId, sensorType);
    }

    /**
     * Register input device sensor listener
     *
     * @hide
     */
    public boolean registerSensorListener(IInputSensorEventListener listener) {
        return mGlobal.registerSensorListener(listener);
    }

    /**
     * Unregister input device sensor listener
     *
     * @hide
     */
    public void unregisterSensorListener(IInputSensorEventListener listener) {
        mGlobal.unregisterSensorListener(listener);
    }

    /**
     * Add a runtime association between the input port and the display port. This overrides any
     * static associations.
     * @param inputPort The port of the input device.
     * @param displayPort The physical port of the associated display.
     * <p>
     * Requires {@link android.Manifest.permission#ASSOCIATE_INPUT_DEVICE_TO_DISPLAY}.
     * </p>
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.ASSOCIATE_INPUT_DEVICE_TO_DISPLAY)
    public void addPortAssociation(@NonNull String inputPort, int displayPort) {
        try {
            mIm.addPortAssociation(inputPort, displayPort);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    /**
     * Remove the runtime association between the input port and the display port. Any existing
     * static association for the cleared input port will be restored.
     * @param inputPort The port of the input device to be cleared.
     * <p>
     * Requires {@link android.Manifest.permission#ASSOCIATE_INPUT_DEVICE_TO_DISPLAY}.
     * </p>
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.ASSOCIATE_INPUT_DEVICE_TO_DISPLAY)
    public void removePortAssociation(@NonNull String inputPort) {
        try {
            mIm.removePortAssociation(inputPort);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    /**
     * Add a runtime association between the input port and display, by unique id. Input ports are
     * expected to be unique.
     * @param inputPort The port of the input device.
     * @param displayUniqueId The unique id of the associated display.
     * <p>
     * Requires {@link android.Manifest.permission#ASSOCIATE_INPUT_DEVICE_TO_DISPLAY}.
     * </p>
     * @hide
     */
<<<<<<< PATCH SET (3e78d5 Bind an input device via descriptor)
    @RequiresPermission(android.Manifest.permission.ASSOCIATE_INPUT_DEVICE_TO_DISPLAY)
    public void addUniqueIdAssociationByPort(@NonNull String inputPort,
                                             @NonNull String displayUniqueId) {
        try {
            mIm.addUniqueIdAssociationByPort(inputPort, displayUniqueId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
=======
    @TestApi
    public void addUniqueIdAssociation(@NonNull String inputPort,
            @NonNull String displayUniqueId) {
        mGlobal.addUniqueIdAssociation(inputPort, displayUniqueId);
>>>>>>> BASE      (da32cb Merge "Adjust with changes in libxml2 upgrade" into main)
    }

    /**
     * Removes a runtime association between the input device and display.
     * @param inputPort The port of the input device.
     * <p>
     * Requires {@link android.Manifest.permission#ASSOCIATE_INPUT_DEVICE_TO_DISPLAY}.
     * </p>
     * @hide
     */
<<<<<<< PATCH SET (3e78d5 Bind an input device via descriptor)
    @RequiresPermission(android.Manifest.permission.ASSOCIATE_INPUT_DEVICE_TO_DISPLAY)
    public void removeUniqueIdAssociationByPort(@NonNull String inputPort) {
        try {
            mIm.removeUniqueIdAssociationByPort(inputPort);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Add a runtime association between the input device name and display, by descriptor. Input
     * device descriptors are expected to be unique per physical device, though one physical
     * device can have multiple virtual input devices that possess the same descriptor.
     * E.g. a keyboard with built in trackpad will be 2 different input devices with the same
     * descriptor.
     * @param inputDeviceDescriptor The descriptor of the input device.
     * @param displayUniqueId The unique id of the associated display.
     * <p>
     * Requires {@link android.Manifest.permissions.ASSOCIATE_INPUT_DEVICE_TO_DISPLAY}.
     * </p>
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.ASSOCIATE_INPUT_DEVICE_TO_DISPLAY)
    public void addUniqueIdAssociationByDescriptor(@NonNull String inputDeviceDescriptor,
                                                   @NonNull String displayUniqueId) {
        try {
            mIm.addUniqueIdAssociationByDescriptor(inputDeviceDescriptor, displayUniqueId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Removes a runtime association between the input device and display.
     * @param inputDeviceDescriptor The descriptor of the input device.
     * <p>
     * Requires {@link android.Manifest.permissions.ASSOCIATE_INPUT_DEVICE_TO_DISPLAY}.
     * </p>
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.ASSOCIATE_INPUT_DEVICE_TO_DISPLAY)
    public void removeUniqueIdAssociationByDescriptor(@NonNull String inputDeviceDescriptor) {
        try {
            mIm.removeUniqueIdAssociationByDescriptor(inputDeviceDescriptor);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
=======
    @TestApi
    public void removeUniqueIdAssociation(@NonNull String inputPort) {
        mGlobal.removeUniqueIdAssociation(inputPort);
>>>>>>> BASE      (da32cb Merge "Adjust with changes in libxml2 upgrade" into main)
    }

    /**
     * Reports the version of the Universal Stylus Initiative (USI) protocol supported by the given
     * display, if any.
     *
     * @return the USI version supported by the display, or null if the device does not support USI
     * @see <a href="https://universalstylus.org">Universal Stylus Initiative</a>
     */
    @Nullable
    public HostUsiVersion getHostUsiVersion(@NonNull Display display) {
        return mGlobal.getHostUsiVersion(display);
    }

    /**
     * Returns the Bluetooth address of this input device, if known.
     *
     * The returned string is always null if this input device is not connected
     * via Bluetooth, or if the Bluetooth address of the device cannot be
     * determined. The returned address will look like: "11:22:33:44:55:66".
     * @hide
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH)
    @Nullable
    public String getInputDeviceBluetoothAddress(int deviceId) {
        return mGlobal.getInputDeviceBluetoothAddress(deviceId);
    }

    /**
     * Gets a vibrator service associated with an input device, always creates a new instance.
     * @return The vibrator, never null.
     * @hide
     */
    public Vibrator getInputDeviceVibrator(int deviceId, int vibratorId) {
        return new InputDeviceVibrator(deviceId, vibratorId);
    }

    /**
     * Cancel all ongoing pointer gestures on all displays.
     * @hide
     */
    public void cancelCurrentTouch() {
<<<<<<< PATCH SET (3e78d5 Bind an input device via descriptor)
        try {
            mIm.cancelCurrentTouch();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Adds a battery listener to be notified about {@link BatteryState} changes for an input
     * device. The same listener can be registered for multiple input devices.
     * The listener will be notified of the initial battery state of the device after it is
     * successfully registered.
     * @param deviceId the input device that should be monitored
     * @param executor an executor on which the callback will be called
     * @param listener the {@link InputDeviceBatteryListener}
     * @see #removeInputDeviceBatteryListener(int, InputDeviceBatteryListener)
     * @hide
     */
    public void addInputDeviceBatteryListener(int deviceId, @NonNull Executor executor,
                                              @NonNull InputDeviceBatteryListener listener) {
        Objects.requireNonNull(executor, "executor should not be null");
        Objects.requireNonNull(listener, "listener should not be null");

        synchronized (mBatteryListenersLock) {
            if (mBatteryListeners == null) {
                mBatteryListeners = new SparseArray<>();
                mInputDeviceBatteryListener = new LocalInputDeviceBatteryListener();
            }
            RegisteredBatteryListeners listenersForDevice = mBatteryListeners.get(deviceId);
            if (listenersForDevice == null) {
                // The deviceId is currently not being monitored for battery changes.
                // Start monitoring the device.
                listenersForDevice = new RegisteredBatteryListeners();
                mBatteryListeners.put(deviceId, listenersForDevice);
                try {
                    mIm.registerBatteryListener(deviceId, mInputDeviceBatteryListener);
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                }
            } else {
                // The deviceId is already being monitored for battery changes.
                // Ensure that the listener is not already registered.
                for (InputDeviceBatteryListenerDelegate delegate : listenersForDevice.mDelegates) {
                    if (Objects.equals(listener, delegate.mListener)) {
                        throw new IllegalArgumentException(
                                "Attempting to register an InputDeviceBatteryListener that has "
                                        + "already been registered for deviceId: "
                                        + deviceId);
                    }
                }
            }
            final InputDeviceBatteryListenerDelegate delegate =
                    new InputDeviceBatteryListenerDelegate(listener, executor);
            listenersForDevice.mDelegates.add(delegate);

            // Notify the listener immediately if we already have the latest battery state.
            if (listenersForDevice.mLatestBatteryState != null) {
                delegate.notifyBatteryStateChanged(listenersForDevice.mLatestBatteryState);
            }
        }
=======
        mGlobal.cancelCurrentTouch();
>>>>>>> BASE      (da32cb Merge "Adjust with changes in libxml2 upgrade" into main)
    }

    /**
     * Pilfer pointers from an input channel.
     *
     * Takes all the current pointer event streams that are currently being sent to the given
     * input channel and generates appropriate cancellations for all other windows that are
     * receiving these pointers.
     *
     * This API is intended to be used in conjunction with spy windows. When a spy window pilfers
     * pointers, the foreground windows and all other spy windows that are receiving any of the
     * pointers that are currently being dispatched to the pilfering window will have those pointers
     * canceled. Only the pilfering window will continue to receive events for the affected pointers
     * until the pointer is lifted.
     *
     * This method should be used with caution as unexpected pilfering can break fundamental user
     * interactions.
     *
     * @see android.os.InputConfig#SPY
     * @hide
     */
    @RequiresPermission(Manifest.permission.MONITOR_INPUT)
    public void pilferPointers(IBinder inputChannelToken) {
        mGlobal.pilferPointers(inputChannelToken);
    }

    /**
     * Adds a battery listener to be notified about {@link BatteryState} changes for an input
     * device. The same listener can be registered for multiple input devices.
     * The listener will be notified of the initial battery state of the device after it is
     * successfully registered.
     * @param deviceId the input device that should be monitored
     * @param executor an executor on which the callback will be called
     * @param listener the {@link InputDeviceBatteryListener}
     * @see #removeInputDeviceBatteryListener(int, InputDeviceBatteryListener)
     * @hide
     */
    public void addInputDeviceBatteryListener(int deviceId, @NonNull Executor executor,
            @NonNull InputDeviceBatteryListener listener) {
        mGlobal.addInputDeviceBatteryListener(deviceId, executor, listener);
    }

    /**
     * Removes a previously registered battery listener for an input device.
     * @see #addInputDeviceBatteryListener(int, Executor, InputDeviceBatteryListener)
     * @hide
     */
    public void removeInputDeviceBatteryListener(int deviceId,
<<<<<<< PATCH SET (3e78d5 Bind an input device via descriptor)
                                                 @NonNull InputDeviceBatteryListener listener) {
        Objects.requireNonNull(listener, "listener should not be null");

        synchronized (mBatteryListenersLock) {
            if (mBatteryListeners == null) {
                return;
            }
            RegisteredBatteryListeners listenersForDevice = mBatteryListeners.get(deviceId);
            if (listenersForDevice == null) {
                // The deviceId is not currently being monitored.
                return;
            }
            final List<InputDeviceBatteryListenerDelegate> delegates =
                    listenersForDevice.mDelegates;
            for (int i = 0; i < delegates.size();) {
                if (Objects.equals(listener, delegates.get(i).mListener)) {
                    delegates.remove(i);
                    continue;
                }
                i++;
            }
            if (!delegates.isEmpty()) {
                return;
            }

            // There are no more battery listeners for this deviceId. Stop monitoring this device.
            mBatteryListeners.remove(deviceId);
            try {
                mIm.unregisterBatteryListener(deviceId, mInputDeviceBatteryListener);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
            if (mBatteryListeners.size() == 0) {
                // There are no more devices being monitored, so the registered
                // IInputDeviceBatteryListener will be automatically dropped by the server.
                mBatteryListeners = null;
                mInputDeviceBatteryListener = null;
            }
        }
=======
            @NonNull InputDeviceBatteryListener listener) {
        mGlobal.removeInputDeviceBatteryListener(deviceId, listener);
>>>>>>> BASE      (da32cb Merge "Adjust with changes in libxml2 upgrade" into main)
    }

    /**
     * Whether there is a gesture-compatible touchpad connected to the device.
     * @hide
     */
<<<<<<< PATCH SET (3e78d5 Bind an input device via descriptor)
    public boolean isStylusEverUsed(@NonNull Context context) {
        return Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.STYLUS_EVER_USED, 0) == 1;
=======
    public boolean areTouchpadGesturesAvailable(@NonNull Context context) {
        // TODO: implement the right logic
        return true;
>>>>>>> BASE      (da32cb Merge "Adjust with changes in libxml2 upgrade" into main)
    }

    /**
     * Registers a Keyboard backlight change listener to be notified about {@link
     * KeyboardBacklightState} changes for connected keyboard devices.
     *
     * @param executor an executor on which the callback will be called
     * @param listener the {@link KeyboardBacklightListener}
     * @hide
     * @see #unregisterKeyboardBacklightListener(KeyboardBacklightListener)
     * @throws IllegalArgumentException if {@code listener} has already been registered previously.
     * @throws NullPointerException if {@code listener} or {@code executor} is null.
     */
    @RequiresPermission(Manifest.permission.MONITOR_KEYBOARD_BACKLIGHT)
    public void registerKeyboardBacklightListener(@NonNull Executor executor,
            @NonNull KeyboardBacklightListener listener) throws IllegalArgumentException {
        mGlobal.registerKeyboardBacklightListener(executor, listener);
    }

    /**
     * Unregisters a previously added Keyboard backlight change listener.
     *
     * @param listener the {@link KeyboardBacklightListener}
     * @see #registerKeyboardBacklightListener(Executor, KeyboardBacklightListener)
     * @hide
     */
    @RequiresPermission(Manifest.permission.MONITOR_KEYBOARD_BACKLIGHT)
    public void unregisterKeyboardBacklightListener(
            @NonNull KeyboardBacklightListener listener) {
        mGlobal.unregisterKeyboardBacklightListener(listener);
    }

    /**
     * A callback used to be notified about battery state changes for an input device. The
     * {@link #onBatteryStateChanged(int, long, BatteryState)} method will be called once after the
     * listener is successfully registered to provide the initial battery state of the device.
     * @see InputDevice#getBatteryState()
     * @see #addInputDeviceBatteryListener(int, Executor, InputDeviceBatteryListener)
     * @see #removeInputDeviceBatteryListener(int, InputDeviceBatteryListener)
     * @hide
     */
    public interface InputDeviceBatteryListener {
        /**
         * Called when the battery state of an input device changes.
         * @param deviceId the input device for which the battery changed.
         * @param eventTimeMillis the time (in ms) when the battery change took place.
         *        This timestamp is in the {@link SystemClock#uptimeMillis()} time base.
         * @param batteryState the new battery state, never null.
         */
        void onBatteryStateChanged(
                int deviceId, long eventTimeMillis, @NonNull BatteryState batteryState);
    }

    /**
     * Listens for changes in input devices.
     */
    public interface InputDeviceListener {
        /**
         * Called whenever an input device has been added to the system.
         * Use {@link InputManagerGlobal#getInputDevice} to get more information about the device.
         *
         * @param deviceId The id of the input device that was added.
         */
        void onInputDeviceAdded(int deviceId);

        /**
         * Called whenever an input device has been removed from the system.
         *
         * @param deviceId The id of the input device that was removed.
         */
        void onInputDeviceRemoved(int deviceId);

        /**
         * Called whenever the properties of an input device have changed since they
         * were last queried.  Use {@link InputManager#getInputDevice} to get
         * a fresh {@link InputDevice} object with the new properties.
         *
         * @param deviceId The id of the input device that changed.
         */
        void onInputDeviceChanged(int deviceId);
    }

    /** @hide */
    public interface OnTabletModeChangedListener {
        /**
         * Called whenever the device goes into or comes out of tablet mode.
         *
         * @param whenNanos The time at which the device transitioned into or
         * out of tablet mode. This is given in nanoseconds in the
         * {@link SystemClock#uptimeMillis} time base.
         */
        void onTabletModeChanged(long whenNanos, boolean inTabletMode);
    }

<<<<<<< PATCH SET (3e78d5 Bind an input device via descriptor)
    private final class TabletModeChangedListener extends ITabletModeChangedListener.Stub {
        @Override
        public void onTabletModeChanged(long whenNanos, boolean inTabletMode) {
            InputManager.this.onTabletModeChanged(whenNanos, inTabletMode);
        }
    }

    private static final class OnTabletModeChangedListenerDelegate extends Handler {
        private static final int MSG_TABLET_MODE_CHANGED = 0;

        public final OnTabletModeChangedListener mListener;

        public OnTabletModeChangedListenerDelegate(
                OnTabletModeChangedListener listener, Handler handler) {
            super(handler != null ? handler.getLooper() : Looper.myLooper());
            mListener = listener;
        }

        public void sendTabletModeChanged(long whenNanos, boolean inTabletMode) {
            SomeArgs args = SomeArgs.obtain();
            args.argi1 = (int) (whenNanos & 0xFFFFFFFF);
            args.argi2 = (int) (whenNanos >> 32);
            args.arg1 = (Boolean) inTabletMode;
            obtainMessage(MSG_TABLET_MODE_CHANGED, args).sendToTarget();
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_TABLET_MODE_CHANGED:
                    SomeArgs args = (SomeArgs) msg.obj;
                    long whenNanos = (args.argi1 & 0xFFFFFFFFl) | ((long) args.argi2 << 32);
                    boolean inTabletMode = (boolean) args.arg1;
                    mListener.onTabletModeChanged(whenNanos, inTabletMode);
                    break;
            }
        }
    }

    private static final class LocalBatteryState extends BatteryState {
        final int mDeviceId;
        final boolean mIsPresent;
        final int mStatus;
        final float mCapacity;
        final long mEventTime;

        LocalBatteryState(int deviceId, boolean isPresent, int status, float capacity,
                          long eventTime) {
            mDeviceId = deviceId;
            mIsPresent = isPresent;
            mStatus = status;
            mCapacity = capacity;
            mEventTime = eventTime;
        }

        @Override
        public boolean isPresent() {
            return mIsPresent;
        }

        @Override
        public int getStatus() {
            return mStatus;
        }

        @Override
        public float getCapacity() {
            return mCapacity;
        }
    }

    private static final class RegisteredBatteryListeners {
        final List<InputDeviceBatteryListenerDelegate> mDelegates = new ArrayList<>();
        LocalBatteryState mLatestBatteryState;
    }

    private static final class InputDeviceBatteryListenerDelegate {
        final InputDeviceBatteryListener mListener;
        final Executor mExecutor;

        InputDeviceBatteryListenerDelegate(InputDeviceBatteryListener listener, Executor executor) {
            mListener = listener;
            mExecutor = executor;
        }

        void notifyBatteryStateChanged(LocalBatteryState batteryState) {
            mExecutor.execute(() ->
                    mListener.onBatteryStateChanged(batteryState.mDeviceId, batteryState.mEventTime,
                            batteryState));
        }
    }

    private class LocalInputDeviceBatteryListener extends IInputDeviceBatteryListener.Stub {
        @Override
        public void onBatteryStateChanged(int deviceId, boolean isBatteryPresent, int status,
                                          float capacity, long eventTime) {
            synchronized (mBatteryListenersLock) {
                if (mBatteryListeners == null) return;
                final RegisteredBatteryListeners entry = mBatteryListeners.get(deviceId);
                if (entry == null) return;

                entry.mLatestBatteryState =
                        new LocalBatteryState(
                                deviceId, isBatteryPresent, status, capacity, eventTime);
                for (InputDeviceBatteryListenerDelegate delegate : entry.mDelegates) {
                    delegate.notifyBatteryStateChanged(entry.mLatestBatteryState);
                }
            }
        }
=======
    /**
     * A callback used to be notified about keyboard backlight state changes for keyboard device.
     * The {@link #onKeyboardBacklightChanged(int, KeyboardBacklightState, boolean)} method
     * will be called once after the listener is successfully registered to provide the initial
     * keyboard backlight state of the device.
     * @see #registerKeyboardBacklightListener(Executor, KeyboardBacklightListener)
     * @see #unregisterKeyboardBacklightListener(KeyboardBacklightListener)
     * @hide
     */
    public interface KeyboardBacklightListener {
        /**
         * Called when the keyboard backlight brightness level changes.
         * @param deviceId the keyboard for which the backlight brightness changed.
         * @param state the new keyboard backlight state, never null.
         * @param isTriggeredByKeyPress whether brightness change was triggered by the user
         *                              pressing up/down key on the keyboard.
         */
        void onKeyboardBacklightChanged(
                int deviceId, @NonNull KeyboardBacklightState state, boolean isTriggeredByKeyPress);
>>>>>>> BASE      (da32cb Merge "Adjust with changes in libxml2 upgrade" into main)
    }
}
