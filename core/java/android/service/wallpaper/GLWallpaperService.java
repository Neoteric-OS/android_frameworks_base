/*
 * Copyright (C) 2010 The Android Open Source Project
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
package android.service.wallpaper;

import javax.microedition.khronos.opengles.GL;

import android.opengl.EglHelper;
import android.opengl.GLRenderer;
import android.opengl.EglHelper.ComponentSizeChooser;
import android.opengl.EglHelper.DefaultContextFactory;
import android.opengl.EglHelper.DefaultWindowSurfaceFactory;
import android.opengl.EglHelper.EGLConfigChooser;
import android.opengl.EglHelper.EGLContextFactory;
import android.opengl.EglHelper.EGLWindowSurfaceFactory;
import android.opengl.EglHelper.GLThread;
import android.opengl.EglHelper.GLWrapper;
import android.opengl.EglHelper.SimpleEGLConfigChooser;
import android.view.SurfaceHolder;

/**
 * An implementation of WallpaperService that offers an Engine which uses a dedicated OpenGL Surface
 * <p>
 * A GLWallpaperService.GLEngine provides the following features:
 * <p>
 * <ul>
 * <li>Manages a surface, which is a special piece of memory that can be composited into the Android view system.
 * <li>Manages an EGL display, which enables OpenGL to render into a surface.
 * <li>Accepts a user-provided GLRenderer object that does the actual rendering.
 * <li>Renders on a dedicated thread to decouple rendering performance from the UI thread.
 * <li>Supports both on-demand and continuous rendering.
 * <li>Optionally wraps, traces, and/or error-checks the renderer's OpenGL calls.
 * </ul>
 * 
 * <h3>Using GLWallpaperService.GLEngine</h3>
 * <p>
 * Typically you use GLEngine by subclassing it and overriding onCreateEngine(). If your application does not need to
 * override event methods then GLEngine can be used as-is. For the most part GLEngine behavior is customized by calling
 * "set" methods rather than by subclassing. For example, unlike a regular Engine, drawing is delegated to a separate
 * GLRenderer object which is registered with the GLEngine using the {@link #setRenderer(GLRenderer)} call.
 * <p>
 * <h3>Initializing GLWallpaperService.GLEngine</h3>
 * All you have to do to initialize a GLEngine is call {@link #setRenderer(GLRenderer)}. However, if desired, you can
 * modify the default behavior of GLEngine by calling one or more of these methods before calling setRenderer:
 * <ul>
 * <li>{@link #setDebugFlags(int)}
 * <li>{@link #setEGLConfigChooser(boolean)}
 * <li>{@link #setEGLConfigChooser(EGLConfigChooser)}
 * <li>{@link #setEGLConfigChooser(int, int, int, int, int, int)}
 * <li>{@link #setGLWrapper(GLWrapper)}
 * </ul>
 * <p>
 * <h4>Choosing an EGL Configuration</h4>
 * A given Android device may support multiple possible types of drawing surfaces. The available surfaces may differ in
 * how may channels of data are present, as well as how many bits are allocated to each channel. Therefore, the first
 * thing GLEngine has to do when starting to render is choose what type of surface to use.
 * <p>
 * By default GLEngine chooses an available surface that's closest to a 16-bit R5G6B5 surface with a 16-bit depth buffer
 * and no stencil. If you would prefer a different surface (for example, if you do not need a depth buffer) you can
 * override the default behavior by calling one of the setEGLConfigChooser methods.
 * <p>
 * <h4>Debug Behavior</h4>
 * You can optionally modify the behavior of GLEngine by calling one or more of the debugging methods
 * {@link #setDebugFlags(int)}, and {@link #setGLWrapper}. These methods may be called before and/or after setRenderer,
 * but typically they are called before setRenderer so that they take effect immediately.
 * <p>
 * <h4>Setting a GLRenderer</h4>
 * Finally, you must call {@link #setRenderer} to register a {@link GLRenderer}. The renderer is responsible for doing
 * the actual OpenGL rendering.
 * <p>
 * <h3>Rendering Mode</h3>
 * Once the renderer is set, you can control whether the renderer draws continuously or on-demand by calling
 * {@link #setRenderMode}. The default is continuous rendering.
 * <p>
 * <h3>Activity Life-cycle</h3>
 * A GLEngine must be notified when the activity is paused and resumed. GLEngine clients are required to call
 * {@link #onPause()} when the activity pauses and {@link #onResume()} when the activity resumes. These calls allow
 * GLEngine to pause and resume the rendering thread, and also allow GLEngine to release and recreate the OpenGL
 * display.
 * <p>
 * <h3>Handling events</h3>
 * <p>
 * To handle an event you will typically subclass GLEngine and override the appropriate method, just as you would with
 * any other Engine. However, when handling the event, you may need to communicate with the GLRenderer object that's
 * running in the rendering thread. You can do this using any standard Java cross-thread communication mechanism. In
 * addition, one relatively easy way to communicate with your renderer is to call {@link #queueEvent(Runnable)}. For
 * example:
 * 
 * <pre class="prettyprint">
 * class MyGLWallpaperService extends GLWallpaperService {
 *     public Engine onCreateEngine() {
 *         return new MyGLEngine();
 *     }
 * 
 *     class MyGLEngine extends GLEngine {
 * 
 *         private MyRenderer mMyRenderer;
 * 
 *         public void start() {
 *         mMyRenderer = ...;
 *         setRenderer(mMyRenderer);
 *     }
 * 
 *         public boolean onKeyDown(int keyCode, KeyEvent event) {
 *             if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
 *                 queueEvent(new Runnable() {
 *                     // This method will be called on the rendering
 *                     // thread:
 *                     public void run() {
 *                         mMyRenderer.handleDpadCenter();
 *                     }
 *                 });
 *                 return true;
 *             }
 *             return super.onKeyDown(keyCode, event);
 *         }
 *     }
 * }
 * </pre>
 * 
 */
public class GLWallpaperService extends WallpaperService {
    private static final String TAG = "GLWallpaperService";

    @Override
    public Engine onCreateEngine() {
        return new GLEngine();
    }

    /**
     * An Engine which uses OpenGL as its primary drawing device. See the GLWallpaperService documentation for details.
     * 
     */
    public class GLEngine extends Engine {
        /**
         * The renderer only renders when the surface is created, or when {@link #requestRender} is called.
         * 
         * @see #getRenderMode()
         * @see #setRenderMode(int)
         */
        public final static int RENDERMODE_WHEN_DIRTY = EglHelper.RENDERMODE_WHEN_DIRTY;
        /**
         * The renderer is called continuously to re-render the scene.
         * 
         * @see #getRenderMode()
         * @see #setRenderMode(int)
         * @see #requestRender()
         */
        public final static int RENDERMODE_CONTINUOUSLY = EglHelper.RENDERMODE_CONTINUOUSLY;

        /**
         * Check glError() after every GL call and throw an exception if glError indicates that an error has occurred.
         * This can be used to help track down which OpenGL ES call is causing an error.
         * 
         * @see #getDebugFlags
         * @see #setDebugFlags
         */
        public final static int DEBUG_CHECK_GL_ERROR = EglHelper.DEBUG_CHECK_GL_ERROR;

        /**
         * Log GL calls to the system log at "verbose" level with tag "EglHelper".
         * 
         * @see #getDebugFlags
         * @see #setDebugFlags
         */
        public final static int DEBUG_LOG_GL_CALLS = EglHelper.DEBUG_LOG_GL_CALLS;

        private GLThread mGLThread;
        private EGLConfigChooser mEGLConfigChooser;
        private EGLContextFactory mEGLContextFactory;
        private EGLWindowSurfaceFactory mEGLWindowSurfaceFactory;
        private GLWrapper mGLWrapper;
        private int mDebugFlags;

        /**
         * Standard Engine constructor. In order to render something, you must call {@link #setRenderer} to register a
         * renderer.
         */
        public GLEngine() {
            super();
        }

        /**
         * If you override this, you must call super.onVisibilityChanged(visible) or your GLRenderer will not start and
         * stop correctly.
         */
        @Override
        public void onVisibilityChanged(boolean visible) {
            if (visible) {
                onResume();
            } else {
                onPause();
            }
            super.onVisibilityChanged(visible);
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            mGLThread.onWindowResize(width, height);
            super.onSurfaceChanged(holder, format, width, height);
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            mGLThread.surfaceCreated(holder);
            super.onSurfaceCreated(holder);
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            mGLThread.surfaceDestroyed();
            super.onSurfaceDestroyed(holder);
        }

        /**
         * Set the glWrapper. If the glWrapper is not null, its {@link GLWrapper#wrap(GL)} method is called whenever a
         * surface is created. A GLWrapper can be used to wrap the GL object that's passed to the renderer. Wrapping a
         * GL object enables examining and modifying the behavior of the GL calls made by the renderer.
         * <p>
         * Wrapping is typically used for debugging purposes.
         * <p>
         * The default value is null.
         * 
         * @param glWrapper
         *            the new GLWrapper
         */
        public void setGLWrapper(GLWrapper glWrapper) {
            mGLWrapper = glWrapper;
        }

        /**
         * Set the debug flags to a new value. The value is constructed by OR-together zero or more of the DEBUG_CHECK_*
         * constants. The debug flags take effect whenever a surface is created. The default value is zero.
         * 
         * @param debugFlags
         *            the new debug flags
         * @see #DEBUG_CHECK_GL_ERROR
         * @see #DEBUG_LOG_GL_CALLS
         */
        public void setDebugFlags(int debugFlags) {
            mDebugFlags = debugFlags;
        }

        /**
         * Get the current value of the debug flags.
         * 
         * @return the current value of the debug flags.
         */
        public int getDebugFlags() {
            return mDebugFlags;
        }

        /**
         * Set the renderer associated with this view. Also starts the thread that will call the renderer, which in turn
         * causes the rendering to start.
         * <p>
         * This method should be called once and only once in the life-cycle of a GLSurfaceView.
         * <p>
         * The following GLEngine methods can only be called <em>before</em> setRenderer is called:
         * <ul>
         * <li>{@link #setEGLConfigChooser(boolean)}
         * <li>{@link #setEGLConfigChooser(EGLConfigChooser)}
         * <li>{@link #setEGLConfigChooser(int, int, int, int, int, int)}
         * </ul>
         * <p>
         * The following GLSurfaceView methods can only be called <em>after</em> setRenderer is called:
         * <ul>
         * <li>{@link #getRenderMode()}
         * <li>{@link #onPause()}
         * <li>{@link #onResume()}
         * <li>{@link #queueEvent(Runnable)}
         * <li>{@link #requestRender()}
         * <li>{@link #setRenderMode(int)}
         * </ul>
         * 
         * @param renderer
         *            the renderer to use to perform OpenGL drawing.
         */
        public void setRenderer(GLRenderer renderer) {
            checkRenderThreadState();
            if (mEGLConfigChooser == null) {
                mEGLConfigChooser = new SimpleEGLConfigChooser(true);
            }
            if (mEGLContextFactory == null) {
                mEGLContextFactory = new DefaultContextFactory();
            }
            if (mEGLWindowSurfaceFactory == null) {
                mEGLWindowSurfaceFactory = new DefaultWindowSurfaceFactory();
            }
            mGLThread = new GLThread(renderer, mEGLConfigChooser, mEGLContextFactory, mEGLWindowSurfaceFactory,
                    mGLWrapper);
            mGLThread.start();
        }

        /**
         * Install a custom EGLContextFactory.
         * <p>
         * If this method is called, it must be called before {@link #setRenderer(GLRenderer)} is called.
         * <p>
         * If this method is not called, then by default a context will be created with no shared context and with a
         * null attribute list.
         */
        public void setEGLContextFactory(EGLContextFactory factory) {
            checkRenderThreadState();
            mEGLContextFactory = factory;
        }

        /**
         * Install a custom EGLWindowSurfaceFactory.
         * <p>
         * If this method is called, it must be called before {@link #setRenderer(GLRenderer)} is called.
         * <p>
         * If this method is not called, then by default a window surface will be created with a null attribute list.
         */
        public void setEGLWindowSurfaceFactory(EGLWindowSurfaceFactory factory) {
            checkRenderThreadState();
            mEGLWindowSurfaceFactory = factory;
        }

        /**
         * Install a custom EGLConfigChooser.
         * <p>
         * If this method is called, it must be called before {@link #setRenderer(GLRenderer)} is called.
         * <p>
         * If no setEGLConfigChooser method is called, then by default the view will choose a config as close to 16-bit
         * RGB as possible, with a depth buffer as close to 16 bits as possible.
         * 
         * @param configChooser
         */
        public void setEGLConfigChooser(EGLConfigChooser configChooser) {
            checkRenderThreadState();
            mEGLConfigChooser = configChooser;
        }

        /**
         * Install a config chooser which will choose a config as close to 16-bit RGB as possible, with or without an
         * optional depth buffer as close to 16-bits as possible.
         * <p>
         * If this method is called, it must be called before {@link #setRenderer(GLRenderer)} is called.
         * <p>
         * If no setEGLConfigChooser method is called, then by default the view will choose a config as close to 16-bit
         * RGB as possible, with a depth buffer as close to 16 bits as possible.
         * 
         * @param needDepth
         */
        public void setEGLConfigChooser(boolean needDepth) {
            setEGLConfigChooser(new SimpleEGLConfigChooser(needDepth));
        }

        /**
         * Install a config chooser which will choose a config with at least the specified component sizes, and as close
         * to the specified component sizes as possible.
         * <p>
         * If this method is called, it must be called before {@link #setRenderer(GLRenderer)} is called.
         * <p>
         * If no setEGLConfigChooser method is called, then by default the view will choose a config as close to 16-bit
         * RGB as possible, with a depth buffer as close to 16 bits as possible.
         * 
         */
        public void setEGLConfigChooser(int redSize, int greenSize, int blueSize, int alphaSize, int depthSize,
                int stencilSize) {
            setEGLConfigChooser(new ComponentSizeChooser(redSize, greenSize, blueSize, alphaSize, depthSize,
                    stencilSize));
        }

        /**
         * Set the rendering mode. When renderMode is RENDERMODE_CONTINUOUSLY, the renderer is called repeatedly to
         * re-render the scene. When renderMode is RENDERMODE_WHEN_DIRTY, the renderer only rendered when the surface is
         * created, or when {@link #requestRender} is called. Defaults to RENDERMODE_CONTINUOUSLY.
         * <p>
         * Using RENDERMODE_WHEN_DIRTY can improve battery life and overall system performance by allowing the GPU and
         * CPU to idle when the view does not need to be updated.
         * <p>
         * This method can only be called after {@link #setRenderer(GLRenderer)}
         * 
         * @param renderMode
         *            one of the RENDERMODE_X constants
         * @see #RENDERMODE_CONTINUOUSLY
         * @see #RENDERMODE_WHEN_DIRTY
         */
        public void setRenderMode(int renderMode) {
            mGLThread.setRenderMode(renderMode);
        }

        /**
         * Get the current rendering mode. May be called from any thread. Must not be called before a renderer has been
         * set.
         * 
         * @return the current rendering mode.
         * @see #RENDERMODE_CONTINUOUSLY
         * @see #RENDERMODE_WHEN_DIRTY
         */
        public int getRenderMode() {
            return mGLThread.getRenderMode();
        }

        /**
         * Request that the renderer render a frame. This method is typically used when the render mode has been set to
         * {@link #RENDERMODE_WHEN_DIRTY}, so that frames are only rendered on demand. May be called from any thread.
         * Must not be called before a renderer has been set.
         */
        public void requestRender() {
            mGLThread.requestRender();
        }

        /**
         * Inform the renderer that the engine is not visible. Calling this method will pause the rendering thread. Must
         * not be called before a renderer has been set.
         */
        public void onPause() {
            mGLThread.onPause();
        }

        /**
         * Inform the renderer that the engine is visible. Calling this method will recreate the OpenGL display and
         * resume the rendering thread. Must not be called before a renderer has been set.
         */
        public void onResume() {
            mGLThread.onResume();
        }

        /**
         * Queue a runnable to be run on the GL rendering thread. This can be used to communicate with the Renderer on
         * the rendering thread. Must not be called before a renderer has been set.
         * 
         * @param r
         *            the runnable to be run on the GL rendering thread.
         */
        public void queueEvent(Runnable r) {
            mGLThread.queueEvent(r);
        }

        private void checkRenderThreadState() {
            if (mGLThread != null) {
                throw new IllegalStateException("setRenderer has already been called for this instance.");
            }
        }
    }
}
