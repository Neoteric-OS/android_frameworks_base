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

package android.opengl;

import java.io.Writer;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGL11;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL;
import javax.microedition.khronos.opengles.GL10;

import android.util.Log;
import android.view.SurfaceHolder;

/**
 * An EGL helper class. Performance OpenGL ES Initialization, creating a surface that is immediately usable.
 */
public class EglHelper {

    /**
     * The renderer only renders when the surface is created, or when {@link #requestRender} is called.
     * 
     * @see #getRenderMode()
     * @see #setRenderMode(int)
     */
    public final static int RENDERMODE_WHEN_DIRTY = 0;
    /**
     * The renderer is called continuously to re-render the scene.
     * 
     * @see #getRenderMode()
     * @see #setRenderMode(int)
     * @see #requestRender()
     */
    public final static int RENDERMODE_CONTINUOUSLY = 1;

    /**
     * Check glError() after every GL call and throw an exception if glError indicates that an error has occurred. This
     * can be used to help track down which OpenGL ES call is causing an error.
     * 
     * @see #getDebugFlags
     * @see #setDebugFlags
     */
    public final static int DEBUG_CHECK_GL_ERROR = 1;

    /**
     * Log GL calls to the system log at "verbose" level with tag "GLSurfaceView".
     * 
     * @see #getDebugFlags
     * @see #setDebugFlags
     */
    public final static int DEBUG_LOG_GL_CALLS = 2;

    private EGL10 mEgl;
    private EGLDisplay mEglDisplay;
    private EGLSurface mEglSurface;
    private EGLContext mEglContext;
    private EGLConfig mEglConfig;

    private EGLConfigChooser mEGLConfigChooser;
    private EGLContextFactory mEGLContextFactory;
    private EGLWindowSurfaceFactory mEGLWindowSurfaceFactory;
    private GLWrapper mGLWrapper;
    private int mDebugFlags;

    /**
     * Standard Constructor.
     * 
     * @param chooser
     * @param contextFactory
     * @param surfaceFactory
     * @param wrapper
     */
    public EglHelper(EGLConfigChooser chooser, EGLContextFactory contextFactory,
            EGLWindowSurfaceFactory surfaceFactory, GLWrapper wrapper) {
        this.mEGLConfigChooser = chooser;
        this.mEGLContextFactory = contextFactory;
        this.mEGLWindowSurfaceFactory = surfaceFactory;
        this.mGLWrapper = wrapper;
    }

    /**
     * Initialize EGL for a given configuration spec.
     * 
     * @param configSpec
     */
    public void start() {
        /*
         * Get an EGL instance
         */
        mEgl = (EGL10) EGLContext.getEGL();

        /*
         * Get to the default display.
         */
        mEglDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

        /*
         * We can now initialize EGL for that display
         */
        int[] version = new int[2];
        mEgl.eglInitialize(mEglDisplay, version);
        mEglConfig = mEGLConfigChooser.chooseConfig(mEgl, mEglDisplay);

        /*
         * Create an OpenGL ES context. This must be done only once, an OpenGL context is a somewhat heavy object.
         */
        mEglContext = mEGLContextFactory.createContext(mEgl, mEglDisplay, mEglConfig);
        if (mEglContext == null || mEglContext == EGL10.EGL_NO_CONTEXT) {
            throw new RuntimeException("createContext failed");
        }

        mEglSurface = null;
    }

    /**
     * Get the current EGLConfig
     * 
     * @return
     */
    public EGLConfig getEGLConfig() {
        return mEglConfig;
    }

    /*
     * React to the creation of a new surface by creating and returning an OpenGL interface that renders to that
     * surface.
     */
    public GL createSurface(SurfaceHolder holder) {
        /*
         * The window size has changed, so we need to create a new surface.
         */
        if (mEglSurface != null && mEglSurface != EGL10.EGL_NO_SURFACE) {

            /*
             * Unbind and destroy the old EGL surface, if there is one.
             */
            mEgl.eglMakeCurrent(mEglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
            mEGLWindowSurfaceFactory.destroySurface(mEgl, mEglDisplay, mEglSurface);
        }

        /*
         * Create an EGL surface we can render into.
         */
        mEglSurface = mEGLWindowSurfaceFactory.createWindowSurface(mEgl, mEglDisplay, mEglConfig, holder);

        if (mEglSurface == null || mEglSurface == EGL10.EGL_NO_SURFACE) {
            throw new RuntimeException("createWindowSurface failed");
        }

        /*
         * Before we can issue GL commands, we need to make sure the context is current and bound to a surface.
         */
        if (!mEgl.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext)) {
            throw new RuntimeException("eglMakeCurrent failed.");
        }

        GL gl = mEglContext.getGL();
        if (mGLWrapper != null) {
            gl = mGLWrapper.wrap(gl);
        }

        if ((mDebugFlags & (DEBUG_CHECK_GL_ERROR | DEBUG_LOG_GL_CALLS)) != 0) {
            int configFlags = 0;
            Writer log = null;
            if ((mDebugFlags & DEBUG_CHECK_GL_ERROR) != 0) {
                configFlags |= GLDebugHelper.CONFIG_CHECK_GL_ERROR;
            }
            if ((mDebugFlags & DEBUG_LOG_GL_CALLS) != 0) {
                log = new LogWriter();
            }
            gl = GLDebugHelper.wrap(gl, configFlags, log);
        }

        return gl;
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
     * Display the current render surface.
     * 
     * @return false if the context has been lost.
     */
    public boolean swap() {
        mEgl.eglSwapBuffers(mEglDisplay, mEglSurface);

        /*
         * Always check for EGL_CONTEXT_LOST, which means the context and all associated data were lost (For instance
         * because the device went to sleep). We need to sleep until we get a new surface.
         */
        return mEgl.eglGetError() != EGL11.EGL_CONTEXT_LOST;
    }

    /**
     * Destroys the current GL Surface
     */
    public void destroySurface() {
        if (mEglSurface != null && mEglSurface != EGL10.EGL_NO_SURFACE) {
            mEgl.eglMakeCurrent(mEglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
            mEGLWindowSurfaceFactory.destroySurface(mEgl, mEglDisplay, mEglSurface);
            mEglSurface = null;
        }
    }

    /**
     * Cleans up after the EglHelper
     */
    public void finish() {
        if (mEglContext != null) {
            mEGLContextFactory.destroyContext(mEgl, mEglDisplay, mEglContext);
            mEglContext = null;
        }
        if (mEglDisplay != null) {
            mEgl.eglTerminate(mEglDisplay);
            mEglDisplay = null;
        }
    }

    // ----------------------------------------------------------------------------

    /**
     * A generic GL Thread. Takes care of initializing EGL and GL. Delegates to a Renderer instance to do the actual
     * drawing. Can be configured to render continuously or on request.
     * 
     * All potentially blocking synchronization is done through the sGLThreadManager object. This avoids multiple-lock
     * ordering issues.
     * 
     */
    public static class GLThread extends Thread {
        private final static boolean LOG_THREADS = false;

        public final static int DEBUG_CHECK_GL_ERROR = 1;
        public final static int DEBUG_LOG_GL_CALLS = 2;

        private final GLThreadManager sGLThreadManager = new GLThreadManager();
        private GLThread mEglOwner;

        private EGLConfigChooser mEGLConfigChooser;
        private EGLContextFactory mEGLContextFactory;
        private EGLWindowSurfaceFactory mEGLWindowSurfaceFactory;
        private GLWrapper mGLWrapper;

        public SurfaceHolder mHolder;
        private boolean mSizeChanged = true;

        // Once the thread is started, all accesses to the following member
        // variables are protected by the sGLThreadManager monitor
        public boolean mDone;
        private boolean mPaused;
        private boolean mHasSurface;
        private boolean mWaitingForSurface;
        private boolean mHaveEgl;
        private int mWidth;
        private int mHeight;
        private int mRenderMode;
        private boolean mRequestRender;
        private boolean mEventsWaiting;
        // End of member variables protected by the sGLThreadManager monitor.

        private GLRenderer mRenderer;
        private ArrayList<Runnable> mEventQueue = new ArrayList<Runnable>();
        private EglHelper mEglHelper;

        /**
         * Standard GLThread constructor
         * 
         * @param renderer
         * @param chooser
         * @param contextFactory
         * @param surfaceFactory
         * @param wrapper
         */
        public GLThread(GLRenderer renderer, EGLConfigChooser chooser, EGLContextFactory contextFactory,
                EGLWindowSurfaceFactory surfaceFactory, GLWrapper wrapper) {
            super();
            mDone = false;
            mWidth = 0;
            mHeight = 0;
            mRequestRender = true;
            mRenderMode = RENDERMODE_CONTINUOUSLY;
            mRenderer = renderer;
            this.mEGLConfigChooser = chooser;
            this.mEGLContextFactory = contextFactory;
            this.mEGLWindowSurfaceFactory = surfaceFactory;
            this.mGLWrapper = wrapper;
        }

        @Override
        public void run() {
            setName("GLThread " + getId());
            if (LOG_THREADS) {
                Log.i("GLThread", "starting tid=" + getId());
            }

            try {
                guardedRun();
            } catch (InterruptedException e) {
                // fall thru and exit normally
            } finally {
                sGLThreadManager.threadExiting(this);
            }
        }

        /*
         * This private method should only be called inside a synchronized(sGLThreadManager) block.
         */
        private void stopEglLocked() {
            if (mHaveEgl) {
                mHaveEgl = false;
                mEglHelper.destroySurface();
                mEglHelper.finish();
                sGLThreadManager.releaseEglSurface(this);
            }
        }

        private void guardedRun() throws InterruptedException {
            mEglHelper = new EglHelper(mEGLConfigChooser, mEGLContextFactory, mEGLWindowSurfaceFactory, mGLWrapper);
            try {
                GL10 gl = null;
                boolean tellRendererSurfaceCreated = true;
                boolean tellRendererSurfaceChanged = true;

                /*
                 * This is our main activity thread's loop, we go until asked to quit.
                 */
                while (!isDone()) {
                    /*
                     * Update the asynchronous state (window size)
                     */
                    int w = 0;
                    int h = 0;
                    boolean changed = false;
                    boolean needStart = false;
                    boolean eventsWaiting = false;

                    synchronized (sGLThreadManager) {
                        while (true) {
                            // Manage acquiring and releasing the SurfaceView
                            // surface and the EGL surface.
                            if (mPaused) {
                                stopEglLocked();
                            }
                            if (!mHasSurface) {
                                if (!mWaitingForSurface) {
                                    stopEglLocked();
                                    mWaitingForSurface = true;
                                    sGLThreadManager.notifyAll();
                                }
                            } else {
                                if (!mHaveEgl) {
                                    if (sGLThreadManager.tryAcquireEglSurface(this)) {
                                        mHaveEgl = true;
                                        mEglHelper.start();
                                        mRequestRender = true;
                                        needStart = true;
                                    }
                                }
                            }

                            // Check if we need to wait. If not, update any state
                            // that needs to be updated, copy any state that
                            // needs to be copied, and use "break" to exit the
                            // wait loop.

                            if (mDone) {
                                return;
                            }

                            if (mEventsWaiting) {
                                eventsWaiting = true;
                                mEventsWaiting = false;
                                break;
                            }

                            if ((!mPaused) && mHasSurface && mHaveEgl && (mWidth > 0) && (mHeight > 0)
                                    && (mRequestRender || (mRenderMode == RENDERMODE_CONTINUOUSLY))) {
                                changed = mSizeChanged;
                                w = mWidth;
                                h = mHeight;
                                mSizeChanged = false;
                                mRequestRender = false;
                                if (mHasSurface && mWaitingForSurface) {
                                    changed = true;
                                    mWaitingForSurface = false;
                                    sGLThreadManager.notifyAll();
                                }
                                break;
                            }

                            // By design, this is the only place where we wait().

                            if (LOG_THREADS) {
                                Log.i("GLThread", "waiting tid=" + getId());
                            }
                            sGLThreadManager.wait();
                        }
                    } // end of synchronized(sGLThreadManager)

                    /*
                     * Handle queued events
                     */
                    if (eventsWaiting) {
                        Runnable r;
                        while ((r = getEvent()) != null) {
                            r.run();
                            if (isDone()) {
                                return;
                            }
                        }
                        // Go back and see if we need to wait to render.
                        continue;
                    }

                    if (needStart) {
                        tellRendererSurfaceCreated = true;
                        changed = true;
                    }
                    if (changed) {
                        gl = (GL10) mEglHelper.createSurface(mHolder);
                        tellRendererSurfaceChanged = true;
                    }
                    if (tellRendererSurfaceCreated) {
                        mRenderer.onSurfaceCreated(gl, mEglHelper.getEGLConfig());
                        tellRendererSurfaceCreated = false;
                    }
                    if (tellRendererSurfaceChanged) {
                        mRenderer.onSurfaceChanged(gl, w, h);
                        tellRendererSurfaceChanged = false;
                    }
                    if ((w > 0) && (h > 0)) {
                        /* draw a frame here */
                        mRenderer.onDrawFrame(gl);

                        /*
                         * Once we're done with GL, we need to call swapBuffers() to instruct the system to display the
                         * rendered frame
                         */
                        mEglHelper.swap();
                    }
                }
            } finally {
                /*
                 * clean-up everything...
                 */
                synchronized (sGLThreadManager) {
                    stopEglLocked();
                }
            }
        }

        private boolean isDone() {
            synchronized (sGLThreadManager) {
                return mDone;
            }
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
            if (!((RENDERMODE_WHEN_DIRTY <= renderMode) && (renderMode <= RENDERMODE_CONTINUOUSLY))) {
                throw new IllegalArgumentException("renderMode");
            }
            synchronized (sGLThreadManager) {
                mRenderMode = renderMode;
                if (renderMode == RENDERMODE_CONTINUOUSLY) {
                    sGLThreadManager.notifyAll();
                }
            }
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
            synchronized (sGLThreadManager) {
                return mRenderMode;
            }
        }

        /**
         * Request that the renderer render a frame. This method is typically used when the render mode has been set to
         * {@link #RENDERMODE_WHEN_DIRTY}, so that frames are only rendered on demand. May be called from any thread.
         * Must not be called before a renderer has been set.
         */
        public void requestRender() {
            synchronized (sGLThreadManager) {
                mRequestRender = true;
                sGLThreadManager.notifyAll();
            }
        }

        /**
         * Informs the EglHelper that the surface has been created
         * 
         * @param holder
         *            the SurfaceHolder of the surface
         */
        public void surfaceCreated(SurfaceHolder holder) {
            mHolder = holder;
            synchronized (sGLThreadManager) {
                if (LOG_THREADS) {
                    Log.i("GLThread", "surfaceCreated tid=" + getId());
                }
                mHasSurface = true;
                sGLThreadManager.notifyAll();
            }
        }

        /**
         * Informs the EglHelper that the surface has been destroyed
         */
        public void surfaceDestroyed() {
            synchronized (sGLThreadManager) {
                if (LOG_THREADS) {
                    Log.i("GLThread", "surfaceDestroyed tid=" + getId());
                }
                mHasSurface = false;
                sGLThreadManager.notifyAll();
                while (!mWaitingForSurface && isAlive() && !mDone) {
                    try {
                        sGLThreadManager.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        /**
         * Pauses the rendering thread
         */
        public void onPause() {
            synchronized (sGLThreadManager) {
                mPaused = true;
                sGLThreadManager.notifyAll();
            }
        }

        /**
         * Resumes the rendering thread
         */
        public void onResume() {
            synchronized (sGLThreadManager) {
                mPaused = false;
                mRequestRender = true;
                sGLThreadManager.notifyAll();
            }
        }

        /**
         * Informs the EglHelper that the window has been resized
         * 
         * @param w
         *            new window width
         * @param h
         *            new window height
         */
        public void onWindowResize(int w, int h) {
            synchronized (sGLThreadManager) {
                mWidth = w;
                mHeight = h;
                mSizeChanged = true;
                sGLThreadManager.notifyAll();
            }
        }

        /**
         * Shuts down the current thread
         */
        public void requestExitAndWait() {
            // don't call this from GLThread thread or it is a guaranteed
            // deadlock!
            synchronized (sGLThreadManager) {
                mDone = true;
                sGLThreadManager.notifyAll();
            }
            try {
                join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        /**
         * Queue an "event" to be run on the GL rendering thread.
         * 
         * @param r
         *            the runnable to be run on the GL rendering thread.
         */
        public void queueEvent(Runnable r) {
            synchronized (this) {
                mEventQueue.add(r);
                synchronized (sGLThreadManager) {
                    mEventsWaiting = true;
                    sGLThreadManager.notifyAll();
                }
            }
        }

        private Runnable getEvent() {
            synchronized (this) {
                if (mEventQueue.size() > 0) {
                    return mEventQueue.remove(0);
                }

            }
            return null;
        }

        private class GLThreadManager {

            public synchronized void threadExiting(GLThread thread) {
                if (LOG_THREADS) {
                    Log.i("GLThread", "exiting tid=" + thread.getId());
                }
                thread.mDone = true;
                if (mEglOwner == thread) {
                    mEglOwner = null;
                }
                notifyAll();
            }

            /*
             * Tries once to acquire the right to use an EGL surface. Does not block.
             * 
             * @return true if the right to use an EGL surface was acquired.
             */
            public synchronized boolean tryAcquireEglSurface(GLThread thread) {
                if (mEglOwner == thread || mEglOwner == null) {
                    mEglOwner = thread;
                    notifyAll();
                    return true;
                }
                return false;
            }

            public synchronized void releaseEglSurface(GLThread thread) {
                if (mEglOwner == thread) {
                    mEglOwner = null;
                }
                notifyAll();
            }
        }

        class LogWriter extends Writer {
            private StringBuilder mBuilder = new StringBuilder();

            @Override
            public void close() {
                flushBuilder();
            }

            @Override
            public void flush() {
                flushBuilder();
            }

            @Override
            public void write(char[] buf, int offset, int count) {
                for (int i = 0; i < count; i++) {
                    char c = buf[offset + i];
                    if (c == '\n') {
                        flushBuilder();
                    } else {
                        mBuilder.append(c);
                    }
                }
            }

            private void flushBuilder() {
                if (mBuilder.length() > 0) {
                    Log.v("GLSurfaceView", mBuilder.toString());
                    mBuilder.delete(0, mBuilder.length());
                }
            }
        }

    }

    // ----------------------------------------------------------------------

    /**
     * An interface for customizing the eglCreateContext and eglDestroyContext calls.
     * <p>
     * This interface must be implemented by clients wishing to call
     * {@link GLWallpaperService#setEGLContextFactory(EGLContextFactory)}
     */
    public interface EGLContextFactory {
        EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig eglConfig);

        void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context);
    }

    public static class DefaultContextFactory implements EGLContextFactory {

        public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig config) {
            return egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, null);
        }

        public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
            egl.eglDestroyContext(display, context);
        }
    }

    /**
     * An interface for customizing the eglCreateWindowSurface and eglDestroySurface calls.
     * <p>
     * This interface must be implemented by clients wishing to call
     * {@link GLWallpaperService#setEGLWindowSurfaceFactory(EGLWindowSurfaceFactory)}
     */
    public interface EGLWindowSurfaceFactory {
        EGLSurface createWindowSurface(EGL10 egl, EGLDisplay display, EGLConfig config, Object nativeWindow);

        void destroySurface(EGL10 egl, EGLDisplay display, EGLSurface surface);
    }

    public static class DefaultWindowSurfaceFactory implements EGLWindowSurfaceFactory {

        public EGLSurface createWindowSurface(EGL10 egl, EGLDisplay display, EGLConfig config, Object nativeWindow) {
            return egl.eglCreateWindowSurface(display, config, nativeWindow, null);
        }

        public void destroySurface(EGL10 egl, EGLDisplay display, EGLSurface surface) {
            egl.eglDestroySurface(display, surface);
        }
    }

    /**
     * An interface used to wrap a GL interface.
     * <p>
     * Typically used for implementing debugging and tracing on top of the default GL interface. You would typically use
     * this by creating your own class that implemented all the GL methods by delegating to another GL instance. Then
     * you could add your own behavior before or after calling the delegate. All the GLWrapper would do was instantiate
     * and return the wrapper GL instance:
     * 
     * <pre class="prettyprint">
     * class MyGLWrapper implements GLWrapper {
     *     GL wrap(GL gl) {
     *         return new MyGLImplementation(gl);
     *     }
     *     static class MyGLImplementation implements GL,GL10,GL11,... {
     *         ...
     *     }
     * }
     * </pre>
     * 
     * @see #setGLWrapper(GLWrapper)
     */
    public interface GLWrapper {
        /**
         * Wraps a gl interface in another gl interface.
         * 
         * @param gl
         *            a GL interface that is to be wrapped.
         * @return either the input argument or another GL object that wraps the input argument.
         */
        GL wrap(GL gl);
    }

    /**
     * An interface for choosing an EGLConfig configuration from a list of potential configurations.
     * <p>
     * This interface must be implemented by clients wishing to call
     * {@link GLSurfaceView#setEGLConfigChooser(EGLConfigChooser)}
     */
    public interface EGLConfigChooser {
        /**
         * Choose a configuration from the list. Implementors typically implement this method by calling
         * {@link EGL10#eglChooseConfig} and iterating through the results. Please consult the EGL specification
         * available from The Khronos Group to learn how to call eglChooseConfig.
         * 
         * @param egl
         *            the EGL10 for the current display.
         * @param display
         *            the current display.
         * @return the chosen configuration.
         */
        EGLConfig chooseConfig(EGL10 egl, EGLDisplay display);
    }

    abstract static class BaseConfigChooser implements EGLConfigChooser {
        public BaseConfigChooser(int[] configSpec) {
            mConfigSpec = configSpec;
        }

        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
            int[] num_config = new int[1];
            egl.eglChooseConfig(display, mConfigSpec, null, 0, num_config);

            int numConfigs = num_config[0];

            if (numConfigs <= 0) {
                throw new IllegalArgumentException("No configs match configSpec");
            }

            EGLConfig[] configs = new EGLConfig[numConfigs];
            egl.eglChooseConfig(display, mConfigSpec, configs, numConfigs, num_config);
            EGLConfig config = chooseConfig(egl, display, configs);
            if (config == null) {
                throw new IllegalArgumentException("No config chosen");
            }
            return config;
        }

        abstract EGLConfig chooseConfig(EGL10 egl, EGLDisplay display, EGLConfig[] configs);

        protected int[] mConfigSpec;
    }

    /**
     * A ConfigChooser that uses closest component sizes to determine the target config
     * 
     */
    public static class ComponentSizeChooser extends BaseConfigChooser {
        public ComponentSizeChooser(int redSize, int greenSize, int blueSize, int alphaSize, int depthSize,
                int stencilSize) {
            super(new int[] { EGL10.EGL_RED_SIZE, redSize, EGL10.EGL_GREEN_SIZE, greenSize, EGL10.EGL_BLUE_SIZE,
                    blueSize, EGL10.EGL_ALPHA_SIZE, alphaSize, EGL10.EGL_DEPTH_SIZE, depthSize, EGL10.EGL_STENCIL_SIZE,
                    stencilSize, EGL10.EGL_NONE });
            mValue = new int[1];
            mRedSize = redSize;
            mGreenSize = greenSize;
            mBlueSize = blueSize;
            mAlphaSize = alphaSize;
            mDepthSize = depthSize;
            mStencilSize = stencilSize;
        }

        @Override
        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display, EGLConfig[] configs) {
            EGLConfig closestConfig = null;
            int closestDistance = 1000;
            for (EGLConfig config : configs) {
                int d = findConfigAttrib(egl, display, config, EGL10.EGL_DEPTH_SIZE, 0);
                int s = findConfigAttrib(egl, display, config, EGL10.EGL_STENCIL_SIZE, 0);
                if (d >= mDepthSize && s >= mStencilSize) {
                    int r = findConfigAttrib(egl, display, config, EGL10.EGL_RED_SIZE, 0);
                    int g = findConfigAttrib(egl, display, config, EGL10.EGL_GREEN_SIZE, 0);
                    int b = findConfigAttrib(egl, display, config, EGL10.EGL_BLUE_SIZE, 0);
                    int a = findConfigAttrib(egl, display, config, EGL10.EGL_ALPHA_SIZE, 0);
                    int distance = Math.abs(r - mRedSize) + Math.abs(g - mGreenSize) + Math.abs(b - mBlueSize)
                            + Math.abs(a - mAlphaSize);
                    if (distance < closestDistance) {
                        closestDistance = distance;
                        closestConfig = config;
                    }
                }
            }
            return closestConfig;
        }

        private int findConfigAttrib(EGL10 egl, EGLDisplay display, EGLConfig config, int attribute, int defaultValue) {

            if (egl.eglGetConfigAttrib(display, config, attribute, mValue)) {
                return mValue[0];
            }
            return defaultValue;
        }

        private int[] mValue;
        // Subclasses can adjust these values:
        protected int mRedSize;
        protected int mGreenSize;
        protected int mBlueSize;
        protected int mAlphaSize;
        protected int mDepthSize;
        protected int mStencilSize;
    }

    /**
     * This class will choose a supported surface as close to RGB565 as possible, with or without a depth buffer.
     * 
     */
    public static class SimpleEGLConfigChooser extends ComponentSizeChooser {
        public SimpleEGLConfigChooser(boolean withDepthBuffer) {
            super(4, 4, 4, 0, withDepthBuffer ? 16 : 0, 0);
            // Adjust target values. This way we'll accept a 4444 or
            // 555 buffer if there's no 565 buffer available.
            mRedSize = 5;
            mGreenSize = 6;
            mBlueSize = 5;
        }
    }

    /**
     * An efficient writer for logging OpenGL debug info when debug flags are enabled
     * 
     */
    static class LogWriter extends Writer {

        @Override
        public void close() {
            flushBuilder();
        }

        @Override
        public void flush() {
            flushBuilder();
        }

        @Override
        public void write(char[] buf, int offset, int count) {
            for (int i = 0; i < count; i++) {
                char c = buf[offset + i];
                if (c == '\n') {
                    flushBuilder();
                } else {
                    mBuilder.append(c);
                }
            }
        }

        private void flushBuilder() {
            if (mBuilder.length() > 0) {
                Log.v("EglHelper", mBuilder.toString());
                mBuilder.delete(0, mBuilder.length());
            }
        }

        private StringBuilder mBuilder = new StringBuilder();
    }

}
