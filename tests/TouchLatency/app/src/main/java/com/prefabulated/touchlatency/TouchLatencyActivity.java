/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.prefabulated.touchlatency;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.os.Bundle;
import android.os.Trace;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Choreographer;
import android.view.Display;
import android.view.Display.Mode;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

class TouchLatencyView extends View implements View.OnTouchListener {
    private static final int BACKGROUND_COLOR = 0xFF400080;
    private static final int BALL_DIAMETER = 200;
    private static final int SEC_TO_NANOS = 1000000000;
    private static final float FPS_UPDATE_THRESHOLD = 20;
    private static final long BALL_VELOCITY = 420;

    public TouchLatencyView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Trace.beginSection("TouchLatencyView constructor");
        setOnTouchListener(this);
        setWillNotDraw(false);
        mBluePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBluePaint.setColor(0xFF0000FF);
        mBluePaint.setStyle(Paint.Style.FILL);
        mGreenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mGreenPaint.setColor(0xFF00FF00);
        mGreenPaint.setStyle(Paint.Style.FILL);
        mYellowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mYellowPaint.setColor(0xFFFFFF00);
        mYellowPaint.setStyle(Paint.Style.FILL);
        mRedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mRedPaint.setColor(0xFFFF0000);
        mRedPaint.setStyle(Paint.Style.FILL);
        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(0xFFFFFFFF);
        mTextPaint.setTextSize(60);
        mTextPaint.setTextAlign(Align.RIGHT);

        mLastDrawNano = 0;
        mFps = 0;
        mLastFpsUpdate = 0;
        mFrameCount = 0;
        mDisplayRate = 120;

        mDf = new DecimalFormat("Content Rate: #.00");
        mDf.setRoundingMode(RoundingMode.HALF_UP);

        Trace.endSection();
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        // do nothing
        return true;
    }

    // (75, -):  green
    // (45, 75]: yellow
    // (-, 45]:  red
    private Paint getBallColor() {
        if (mFps > 75)
            return mGreenPaint;
        else if (mFps > 45)
            return mYellowPaint;
        else
            return mRedPaint;
    }

    private void drawBall(Canvas canvas) {
        Trace.beginSection("TouchLatencyView drawBall");
        int width = canvas.getWidth();
        int height = canvas.getHeight();
        float fps = 0f;

        long t = System.nanoTime();
        long tDiff = t - mLastDrawNano;
        mLastDrawNano = t;
        mFrameCount++;

        if (tDiff < SEC_TO_NANOS) {
            fps = 1f * SEC_TO_NANOS / tDiff;
        }

        long fDiff = t - mLastFpsUpdate;
        if (Math.abs(mFps - fps) > FPS_UPDATE_THRESHOLD) {
            mFps = fps;
            mLastFpsUpdate = t;
            mFrameCount = 0;
        } else if (fDiff > SEC_TO_NANOS) {
            mFps = 1f * mFrameCount * SEC_TO_NANOS / fDiff;
            mLastFpsUpdate = t;
            mFrameCount = 0;
        }

        final long pos = t * BALL_VELOCITY / SEC_TO_NANOS;
        final long xMax = width - BALL_DIAMETER;
        final long yMax = height - BALL_DIAMETER;
        long xOffset = pos % xMax;
        long yOffset = pos % yMax;

        float left, right, top, bottom;

        if (((pos / xMax) & 1) == 0) {
            left = xMax - xOffset;
        } else {
            left = xOffset;
        }
        right = left + BALL_DIAMETER;

        if (((pos / yMax) & 1) == 0) {
            top = yMax - yOffset;
        } else {
            top = yOffset;
        }
        bottom = top + BALL_DIAMETER;

        // Draw the ball
        canvas.drawColor(BACKGROUND_COLOR);
        canvas.drawOval(left, top, right, bottom, getBallColor());
        // Panel refresh rate
        canvas.drawText("Display Refresh Rate: " + mDisplayRate, width, 100, mTextPaint);
        // Ball Drawing update frequency
        canvas.drawText(mDf.format(mFps), width, 180, mTextPaint);

        Trace.endSection();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Trace.beginSection("TouchLatencyView onDraw");
        drawBall(canvas);
        Trace.endSection();
    }

    public void setDisplayRate(int displayRate) {
        mDisplayRate = displayRate;
    }

    private final Paint mBluePaint, mGreenPaint, mYellowPaint, mRedPaint, mTextPaint;

    private long mLastDrawNano, mLastFpsUpdate, mFrameCount;
    private float mFps;
    private final DecimalFormat mDf;
    private int mDisplayRate;
}

public class TouchLatencyActivity extends Activity implements Choreographer.FrameCallback {
    private List<Mode> mDisplayModes;
    private List<Integer> mSupportedDisplayRates;
    private int mCurrentContentRateIndex, mCurrentModeIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Trace.beginSection("TouchLatencyActivity onCreate");
        setContentView(R.layout.activity_touch_latency);

        mTouchView = findViewById(R.id.canvasView);

        mButton = findViewById(R.id.next_mode);
        mButton.setOnClickListener(v -> nextDisplayMode());
        Display display = mTouchView.getContext().getDisplay();
        Mode[] allDisplayModes = display.getSupportedModes();

        Mode currentMode = display.getMode();
        Log.d("onCreate: Current Mode: ", currentMode.toString());

        // get the supported set of refresh rate for the device
        HashSet<Integer> supportedDisplayRateSet = new HashSet<>();
        mDisplayModes = new ArrayList<>();
        // make sure only switch to display modes with the same resolution
        for (Mode displayMode: allDisplayModes) {
            if (displayMode.getPhysicalHeight() == currentMode.getPhysicalHeight()
                    && displayMode.getPhysicalWidth() == currentMode.getPhysicalWidth()) {
                mDisplayModes.add(displayMode);
                supportedDisplayRateSet.add((int) displayMode.getRefreshRate());
            }
            if (currentMode.getModeId() == displayMode.getModeId()) {
                mCurrentModeIndex = mDisplayModes.size() - 1;
            }
        }
        mSupportedDisplayRates = supportedDisplayRateSet
                .stream().sorted().collect(Collectors.toList());
        Log.d("onCreate: mSupportedDisplayRates: ", mSupportedDisplayRates.toString());
        Log.d("onCreate: mDisplayModes.size ", String.valueOf(mDisplayModes.size()));
        Log.d("onCreate: mCurrentModeIndex =  ", String.valueOf(mCurrentModeIndex));

        mCurrentContentRateIndex = 0;
        mTouchView.setDisplayRate((int) currentMode.getRefreshRate());

        // for phones that supports VRR it's important to set the preferred display mode,
        // otherwise it might switch to a different display mode while going through
        // different content render rates, which will cause weird intermediate display modes.
        // eg. in 10fps the display will automatically switch to 24Hz, causing the content
        // render rate to drop from 10 -> 6 -> 2. 10/2 = 120/24
        Window w = getWindow();
        WindowManager.LayoutParams params = w.getAttributes();
        params.preferredDisplayModeId = currentMode.getModeId();
        w.setAttributes(params);

        Choreographer.getInstance().postFrameCallback(this);

        Trace.endSection();
    }

    private void nextDisplayMode() {
        Log.d("nextDisplayMode: previous display mode = ",
                mDisplayModes.get(mCurrentModeIndex).toString());
        // first decide if it's time to change panel refresh rate
        int displayRate = (int) mDisplayModes.get(mCurrentModeIndex).getRefreshRate();
        int contentRate = mSupportedDisplayRates.get(mCurrentContentRateIndex);
        if (displayRate == contentRate) {
            Window w = getWindow();
            WindowManager.LayoutParams params = w.getAttributes();

            int modeIndex = (mCurrentModeIndex + 1) % mDisplayModes.size();
            params.preferredDisplayModeId = mDisplayModes.get(modeIndex).getModeId();
            w.setAttributes(params);
            mCurrentModeIndex = modeIndex;
            mCurrentContentRateIndex = -1;
        }
        Log.d("nextDisplayMode: current display mode = ",
                mDisplayModes.get(mCurrentModeIndex).toString());

        displayRate = (int) mDisplayModes.get(mCurrentModeIndex).getRefreshRate();
        mTouchView.setDisplayRate(displayRate);
        // find the next in the supported display rate that is a divisor for the current frame rate
        do {
            mCurrentContentRateIndex =
                    (mCurrentContentRateIndex + 1) % mSupportedDisplayRates.size();
            contentRate = mSupportedDisplayRates.get(mCurrentContentRateIndex);
        }
        while (displayRate % contentRate != 0);

        Log.d("nextDisplayMode: display rate = ", String.valueOf(displayRate));
        Log.d("nextDisplayMode: content rate = ", String.valueOf(contentRate));
    }

    @Override
    public void doFrame(long l) {
        mFrameCount++;
        Choreographer.getInstance().postFrameCallback(this);

        int displayRate = (int) mDisplayModes.get(mCurrentModeIndex).getRefreshRate();
        int contentRate = mSupportedDisplayRates.get(mCurrentContentRateIndex);
        int divisor = displayRate / contentRate;
        Log.d("DoFrame: display rate = ", String.valueOf(displayRate));
        Log.d("DoFrame: content rate = ", String.valueOf(contentRate));
        Log.d("DoFrame: divisor = ", String.valueOf(divisor));
        if (divisor == 1 || mFrameCount % divisor == 0) {
            mTouchView.invalidate();
        }
    }

    private long mFrameCount = 0;
    private TouchLatencyView mTouchView;
    private Button mButton;
}
