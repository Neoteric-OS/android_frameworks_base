package com.android.server.display;

import android.content.Context;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.util.Slog;

/**
 * <p>
 * An implementation to draw a black frame when the screen turns off. If GLES2 is supported,
 * please use {@link ColorFadeAnimation}.
 * </p><p>
 * This component must only be created or accessed by the {@link Looper} thread
 * that belongs to the {@link DisplayPowerController}.
 * </p>
 */
public class ColorFadeDrawBlack extends ColorFade {
    private static final String TAG = "ColorFadeDrawBlack";

    private static final boolean DEBUG = false;

    public ColorFadeDrawBlack(int displayId) {
        super(displayId);
    }

    /**
     * Draws a black frame. Doesn't have animation
     *
     * @param level The color fade level. (Not used)
     * @return True if successful.
     */
    public boolean draw(float level) {
        if (DEBUG) {
            Slog.d(TAG, "drawFrame: level=" + level);
        }

        if (!mPrepared) {
            return false;
        }

        if (mMode == MODE_FADE) {
            return showSurface(1.0f - level);
        }

        if (!attachEglContext()) {
            return false;
        }
        try {
            // Clear frame to solid black.
            GLES20.glClearColor(0f, 0f, 0f, 1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            if (checkGlErrors("drawFrame")) {
                return false;
            }
            EGL14.eglSwapBuffers(mEglDisplay, mEglSurface);
        } finally {
            detachEglContext();
        }
        return showSurface(1.0f);
    }

    /**
     * Generate an eglConfigAttribList with GLES1 format.
     */
    @Override
    protected int[] getConfigAttribList() {
      return new int[] {
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_NONE
      };
    }
}
