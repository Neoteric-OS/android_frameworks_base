/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.server.display;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManagerInternal;
import android.hardware.display.DisplayManagerInternal.DisplayTransactionListener;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLES11Ext;
import android.util.Slog;
import android.view.DisplayInfo;
import android.view.Surface.OutOfResourcesException;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceSession;

import libcore.io.Streams;

import com.android.server.LocalServices;

/**
 * <p>
 * The abstract class to animates a screen transition from on to off or off to on.
 * </p><p>
 * This component must only be created or accessed by the {@link Looper} thread
 * that belongs to the {@link DisplayPowerController}.
 * </p>
 */
abstract class ColorFade {
    private static final String TAG = "ColorFade";

    private static final boolean DEBUG = true;

    // The layer for the electron beam surface.
    // This is currently hardcoded to be one layer above the boot animation.
    protected static final int COLOR_FADE_LAYER = 0x40000001;

    private final int mDisplayId;

    // Set to true when the resources has been created.
    protected boolean mCreatedResources;
    protected boolean mPrepared;
    protected int mMode;

    protected final DisplayManagerInternal mDisplayManagerInternal;
    protected int mDisplayLayerStack; // layer stack associated with primary display
    protected int mDisplayWidth;      // real width, not rotated
    protected int mDisplayHeight;     // real height, not rotated
    protected SurfaceSession mSurfaceSession;
    protected SurfaceControl mSurfaceControl;
    protected Surface mSurface;
    protected NaturalSurfaceLayout mSurfaceLayout;
    protected EGLDisplay mEglDisplay;
    protected EGLConfig mEglConfig;
    protected EGLContext mEglContext;
    protected EGLSurface mEglSurface;
    protected boolean mSurfaceVisible;
    protected float mSurfaceAlpha;

    /**
     * Animates an color fade warming up.
     */
    public static final int MODE_WARM_UP = 0;

    /**
     * Animates an color fade shutting off.
     */
    public static final int MODE_COOL_DOWN = 1;

    /**
     * Animates a simple dim layer to fade the contents of the screen in or out progressively.
     */
    public static final int MODE_FADE = 2;

    public ColorFade(int displayId) {
        mDisplayId = displayId;
        mDisplayManagerInternal = LocalServices.getService(DisplayManagerInternal.class);
    }

    /**
     * Warms up the color fade in preparation for turning on or off.
     * This method prepares a GL context.
     *
     * @param mode The desired mode for the upcoming animation.
     * @return True if the color fade is ready, false if it is uncontrollable.
     */
    public boolean prepare(Context context, int mode)
    {
        if (DEBUG) {
            Slog.d(TAG, "prepare: mode=" + mode);
        }

        mMode = mode;

        // Get the display size and layer stack.
        // This is not expected to change while the color fade surface is showing.
        DisplayInfo displayInfo = mDisplayManagerInternal.getDisplayInfo(mDisplayId);
        mDisplayLayerStack = displayInfo.layerStack;
        mDisplayWidth = displayInfo.getNaturalWidth();
        mDisplayHeight = displayInfo.getNaturalHeight();

        // Prepare the surface for drawing.
        if (!(createSurface() && createEglContext() && createEglSurface())) {
            dismiss();
            return false;
        }

        mCreatedResources = true;
        mPrepared = true;

        return true;
    }

    /**
     * Dismisses the color fade animation resources.
     *
     * This function destroys the resources that are created for the color fade
     * animation but does not clean up the surface.
     */
    public void dismissResources()
    {
        if (DEBUG) {
            Slog.d(TAG, "dismissResources");
        }

        if (mCreatedResources) {
            attachEglContext();
            try {
                destroyEglSurface();
            } finally {
                detachEglContext();
            }
        }
        // This is being called with no active context so shouldn't be
        // needed but is safer to not change for now.
        GLES20.glFlush();
        mCreatedResources = false;
    }
    /**
     * Dismisses the color fade animation surface and cleans up.
     *
     * To prevent stray photons from leaking out after the color fade has been
     * turned off, it is a good idea to defer dismissing the animation until the
     * color fade has been turned back on fully.
     */
    public void dismiss() {
        if (DEBUG) {
            Slog.d(TAG, "dismiss");
        }

        if (mPrepared)
        {
          dismissResources();
          destroySurface();
          mPrepared = false;
        }
    }

    /**
     * Draws an animation frame showing the color fade activated at the
     * specified level.
     *
     * @param level The color fade level.
     * @return True if successful.
     */
    abstract public boolean draw(float level);
    
    /**
     * Generate an eglConfigAttribList. Should be implemented differently when
     * using GLES1 and GLES 2.
     */
    abstract protected int[] getConfigAttribList();

    private boolean createEglContext() {
        if (mEglDisplay == null) {
            mEglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (mEglDisplay == EGL14.EGL_NO_DISPLAY) {
                logEglError("eglGetDisplay");
                return false;
            }

            int[] version = new int[2];
            if (!EGL14.eglInitialize(mEglDisplay, version, 0, version, 1)) {
                mEglDisplay = null;
                logEglError("eglInitialize");
                return false;
            }
        }

        if (mEglConfig == null) {
            int[] eglConfigAttribList = getConfigAttribList();
            int[] numEglConfigs = new int[1];
            EGLConfig[] eglConfigs = new EGLConfig[1];
            if (!EGL14.eglChooseConfig(mEglDisplay, eglConfigAttribList, 0,
                    eglConfigs, 0, eglConfigs.length, numEglConfigs, 0)) {
                logEglError("eglChooseConfig");
                return false;
            }
            mEglConfig = eglConfigs[0];
        }

        if (mEglContext == null) {
            int[] eglContextAttribList = new int[] {
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                    EGL14.EGL_NONE
            };
            mEglContext = EGL14.eglCreateContext(mEglDisplay, mEglConfig,
                    EGL14.EGL_NO_CONTEXT, eglContextAttribList, 0);
            if (mEglContext == null) {
                logEglError("eglCreateContext");
                return false;
            }
        }
        return true;
    }

    private boolean createSurface() {
        if (mSurfaceSession == null) {
            mSurfaceSession = new SurfaceSession();
        }

        SurfaceControl.openTransaction();
        try {
            if (mSurfaceControl == null) {
                try {
                    int flags;
                    if (mMode == MODE_FADE) {
                        flags = SurfaceControl.FX_SURFACE_DIM | SurfaceControl.HIDDEN;
                    } else {
                        flags = SurfaceControl.OPAQUE | SurfaceControl.HIDDEN;
                    }
                    mSurfaceControl = new SurfaceControl(mSurfaceSession,
                            "ColorFade", mDisplayWidth, mDisplayHeight,
                            PixelFormat.OPAQUE, flags);
                } catch (OutOfResourcesException ex) {
                    Slog.e(TAG, "Unable to create surface.", ex);
                    return false;
                }
            }

            mSurfaceControl.setLayerStack(mDisplayLayerStack);
            mSurfaceControl.setSize(mDisplayWidth, mDisplayHeight);
            mSurface = new Surface();
            mSurface.copyFrom(mSurfaceControl);

            mSurfaceLayout = new NaturalSurfaceLayout(mDisplayManagerInternal,
                    mDisplayId, mSurfaceControl);
            mSurfaceLayout.onDisplayTransaction();
        } finally {
            SurfaceControl.closeTransaction();
        }
        return true;
    }

    private boolean createEglSurface() {
        if (mEglSurface == null) {
            int[] eglSurfaceAttribList = new int[] {
                    EGL14.EGL_NONE
            };
            // turn our SurfaceControl into a Surface
            mEglSurface = EGL14.eglCreateWindowSurface(mEglDisplay, mEglConfig, mSurface,
                    eglSurfaceAttribList, 0);
            if (mEglSurface == null) {
                logEglError("eglCreateWindowSurface");
                return false;
            }
        }
        return true;
    }

    private void destroyEglSurface() {
        if (mEglSurface != null) {
            if (!EGL14.eglDestroySurface(mEglDisplay, mEglSurface)) {
                logEglError("eglDestroySurface");
            }
            mEglSurface = null;
        }
    }

    private void destroySurface() {
        if (mSurfaceControl != null) {
            mSurfaceLayout.dispose();
            mSurfaceLayout = null;
            SurfaceControl.openTransaction();
            try {
                mSurfaceControl.destroy();
                mSurface.release();
            } finally {
                SurfaceControl.closeTransaction();
            }
            mSurfaceControl = null;
            mSurfaceVisible = false;
            mSurfaceAlpha = 0f;
        }
    }

    protected boolean showSurface(float alpha) {
        if (!mSurfaceVisible || mSurfaceAlpha != alpha) {
            SurfaceControl.openTransaction();
            try {
                mSurfaceControl.setLayer(COLOR_FADE_LAYER);
                mSurfaceControl.setAlpha(alpha);
                mSurfaceControl.show();
            } finally {
                SurfaceControl.closeTransaction();
            }
            mSurfaceVisible = true;
            mSurfaceAlpha = alpha;
        }
        return true;
    }

    protected boolean attachEglContext() {
        if (mEglSurface == null) {
            return false;
        }
        if (!EGL14.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext)) {
            logEglError("eglMakeCurrent");
            return false;
        }
        return true;
    }

    protected void detachEglContext() {
        if (mEglDisplay != null) {
            EGL14.eglMakeCurrent(mEglDisplay,
                    EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
        }
    }

    private static void logEglError(String func) {
        Slog.e(TAG, func + " failed: error " + EGL14.eglGetError(), new Throwable());
    }

    protected static boolean checkGlErrors(String func) {
        return checkGlErrors(func, true);
    }

    private static boolean checkGlErrors(String func, boolean log) {
        boolean hadError = false;
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            if (log) {
                Slog.e(TAG, func + " failed: error " + error, new Throwable());
            }
            hadError = true;
        }
        return hadError;
    }

    protected void dump(PrintWriter pw) {
        pw.println();
        pw.println("Color Fade State:");
        pw.println("  mPrepared=" + mPrepared);
        pw.println("  mMode=" + mMode);
        pw.println("  mDisplayLayerStack=" + mDisplayLayerStack);
        pw.println("  mDisplayWidth=" + mDisplayWidth);
        pw.println("  mDisplayHeight=" + mDisplayHeight);
        pw.println("  mSurfaceVisible=" + mSurfaceVisible);
        pw.println("  mSurfaceAlpha=" + mSurfaceAlpha);
    }

    /**
     * Keeps a surface aligned with the natural orientation of the device.
     * Updates the position and transformation of the matrix whenever the display
     * is rotated.  This is a little tricky because the display transaction
     * callback can be invoked on any thread, not necessarily the thread that
     * owns the color fade.
     */
    private static final class NaturalSurfaceLayout implements DisplayTransactionListener {
        private final DisplayManagerInternal mDisplayManagerInternal;
        private final int mDisplayId;
        private SurfaceControl mSurfaceControl;

        public NaturalSurfaceLayout(DisplayManagerInternal displayManagerInternal,
                int displayId, SurfaceControl surfaceControl) {
            mDisplayManagerInternal = displayManagerInternal;
            mDisplayId = displayId;
            mSurfaceControl = surfaceControl;
            mDisplayManagerInternal.registerDisplayTransactionListener(this);
        }

        public void dispose() {
            synchronized (this) {
                mSurfaceControl = null;
            }
            mDisplayManagerInternal.unregisterDisplayTransactionListener(this);
        }

        @Override
        public void onDisplayTransaction() {
            synchronized (this) {
                if (mSurfaceControl == null) {
                    return;
                }

                DisplayInfo displayInfo = mDisplayManagerInternal.getDisplayInfo(mDisplayId);
                switch (displayInfo.rotation) {
                    case Surface.ROTATION_0:
                        mSurfaceControl.setPosition(0, 0);
                        mSurfaceControl.setMatrix(1, 0, 0, 1);
                        break;
                    case Surface.ROTATION_90:
                        mSurfaceControl.setPosition(0, displayInfo.logicalHeight);
                        mSurfaceControl.setMatrix(0, -1, 1, 0);
                        break;
                    case Surface.ROTATION_180:
                        mSurfaceControl.setPosition(displayInfo.logicalWidth,
                                displayInfo.logicalHeight);
                        mSurfaceControl.setMatrix(-1, 0, 0, -1);
                        break;
                    case Surface.ROTATION_270:
                        mSurfaceControl.setPosition(displayInfo.logicalWidth, 0);
                        mSurfaceControl.setMatrix(0, 1, -1, 0);
                        break;
                }
            }
        }
    }
}
