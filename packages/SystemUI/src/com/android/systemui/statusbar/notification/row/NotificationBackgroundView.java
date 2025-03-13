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
 * limitations under the License
 */

package com.android.systemui.statusbar.notification.row;

import static com.android.systemui.Flags.notificationRowTransparency;
import static com.android.systemui.util.ColorUtilKt.hexColorString;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.graphics.ColorUtils;
import com.android.internal.util.ContrastColorUtil;
import com.android.systemui.Dumpable;
import com.android.systemui.common.shared.colors.SurfaceEffectColors;
import com.android.systemui.res.R;
import com.android.systemui.statusbar.notification.shared.NotificationAddXOnHoverToDismiss;
import com.android.systemui.util.DrawableDumpKt;

import java.io.PrintWriter;
import java.util.Arrays;

/**
 * A view that can be used for both the dimmed and normal background of an notification.
 */
public class NotificationBackgroundView extends View implements Dumpable,
        ExpandableNotificationRow.DismissButtonTargetVisibilityListener {

    private static final int MAX_ALPHA = 0xFF;
    private final boolean mDontModifyCorners;
    private Drawable mBackground;
    private int mClipTopAmount;
    private int mClipBottomAmount;
    private int mTintColor;
    @Nullable private Integer mRippleColor;
    private final float[] mCornerRadii = new float[8];
    private final float[] mFocusOverlayCornerRadii = new float[8];
    private float mFocusOverlayStroke = 0;
    private boolean mBottomIsRounded;
    private boolean mBottomAmountClips = true;
    private int mActualHeight = -1;
    private int mActualWidth = -1;
    private boolean mExpandAnimationRunning;
    private int mExpandAnimationWidth = -1;
    private int mExpandAnimationHeight = -1;
    private int mDrawableAlpha = 255;
    private final ColorStateList mLightColoredStatefulColors;
    private final ColorStateList mDarkColoredStatefulColors;
    private int mNormalColor;
    private boolean mBgIsColorized = false;
    private boolean mForceOpaque = false;
    private final int convexR = 9;
    private final int concaveR = 22;

    // True only if the dismiss button is visible.
    private boolean mDrawDismissButtonCutout = false;

    public NotificationBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDontModifyCorners = getResources().getBoolean(R.bool.config_clipNotificationsToOutline);
        mLightColoredStatefulColors = getResources().getColorStateList(
                R.color.notification_state_color_light);
        mDarkColoredStatefulColors = getResources().getColorStateList(
                R.color.notification_state_color_dark);
        mFocusOverlayStroke = getResources().getDimension(R.dimen.notification_focus_stroke_width);
    }

    public void setNormalColor(int color) {
        mNormalColor = color;
    }

    @Override
    public void onTargetVisibilityChanged(boolean targetVisible) {
        if (NotificationAddXOnHoverToDismiss.isUnexpectedlyInLegacyMode()) {
            return;
        }

        if (mDrawDismissButtonCutout != targetVisible) {
            mDrawDismissButtonCutout = targetVisible;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mClipTopAmount + mClipBottomAmount < getActualHeight() || mExpandAnimationRunning) {
            canvas.save();
            if (!mExpandAnimationRunning) {
                canvas.clipRect(0, mClipTopAmount, getWidth(),
                        getActualHeight() - mClipBottomAmount);
            }

            if (!NotificationAddXOnHoverToDismiss.isEnabled()) {
                draw(canvas, mBackground);
                canvas.restore();
                return;
            }

            Rect backgroundBounds = null;
            if (mBackground != null || mDrawDismissButtonCutout) {
                backgroundBounds = calculateBackgroundBounds();
            }

            if (mDrawDismissButtonCutout) {
                canvas.clipPath(calculateDismissButtonCutoutPath(backgroundBounds));
            }

            if (mBackground != null) {
                mBackground.setBounds(backgroundBounds);
                mBackground.draw(canvas);
            }

            canvas.restore();
        }
    }

    /**
     * A way to tell whether the background has been colorized.
     */
    public boolean isColorized() {
        return mBgIsColorized;
    }

    /**
     * A way to inform this class whether the background has been colorized.
     * We need to know this, in order to *not* override that color.
     */
    public void setBgIsColorized(boolean b) {
        mBgIsColorized = b;
    }

    /** Sets if the background should be opaque. */
    public void setForceOpaque(boolean forceOpaque) {
        mForceOpaque = forceOpaque;
        if (notificationRowTransparency()) {
            updateBaseLayerColor();
        }
    }

    private Path calculateDismissButtonCutoutPath(Rect backgroundBounds) {
        // TODO(b/365585705): Adapt to RTL after the UX design is finalized.

        NotificationAddXOnHoverToDismiss.isUnexpectedlyInLegacyMode();

        Path path = new Path();

        final int left = backgroundBounds.left;
        final int right = backgroundBounds.right;
        final int top = backgroundBounds.top;
        final int bottom = backgroundBounds.bottom;

        // Generate the path clockwise from the left-top corner.
        path.moveTo(left, top);
        path.lineTo(right - 2 * convexR - concaveR, top);
        path.quadTo(right - convexR - concaveR, top, right - convexR - concaveR,
                top + convexR);
        path.quadTo(right - convexR - concaveR, top + convexR + concaveR, right - convexR,
                top + convexR + concaveR);
        path.quadTo(right, top + convexR + concaveR, right, top + 2 * convexR + concaveR);
        path.lineTo(right, bottom);
        path.lineTo(left, bottom);
        path.lineTo(left, top);

        return path;
    }

    private Rect calculateBackgroundBounds() {
        NotificationAddXOnHoverToDismiss.isUnexpectedlyInLegacyMode();

        int top = 0;
        int bottom = getActualHeight();
        if (mBottomIsRounded
                && mBottomAmountClips
                && !mExpandAnimationRunning) {
            bottom -= mClipBottomAmount;
        }
        final boolean alignedToRight = isAlignedToRight();
        final int width = getWidth();
        final int actualWidth = getActualWidth();

        int left = alignedToRight ? width - actualWidth : 0;
        int right = alignedToRight ? width : actualWidth;

        if (mExpandAnimationRunning) {
            // Horizontally center this background view inside of the container
            left = (int) ((width - actualWidth) / 2.0f);
            right = (int) (left + actualWidth);
        }

        return new Rect(left, top, right, bottom);
    }

    /**
     * @return Whether the background view should be right-aligned. This only matters if the
     * actualWidth is different than the full (measured) width. In other words, this is used to
     * define the short-shelf alignment.
     */
    protected boolean isAlignedToRight() {
        return isLayoutRtl();
    }

    private void draw(Canvas canvas, Drawable drawable) {
        NotificationAddXOnHoverToDismiss.assertInLegacyMode();

        if (drawable != null) {
            int top = 0;
            int bottom = getActualHeight();
            if (mBottomIsRounded
                    && mBottomAmountClips
                    && !mExpandAnimationRunning) {
                bottom -= mClipBottomAmount;
            }

            final boolean alignedToRight = isAlignedToRight();
            final int width = getWidth();
            final int actualWidth = getActualWidth();

            int left = alignedToRight ? width - actualWidth : 0;
            int right = alignedToRight ? width : actualWidth;

            if (mExpandAnimationRunning) {
                // Horizontally center this background view inside of the container
                left = (int) ((width - actualWidth) / 2.0f);
                right = (int) (left + actualWidth);
            }
            drawable.setBounds(left, top, right, bottom);
            drawable.draw(canvas);
        }
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || who == mBackground;
    }

    @Override
    protected void drawableStateChanged() {
        setState(getDrawableState());
    }

    @Override
    public void drawableHotspotChanged(float x, float y) {
        if (mBackground != null) {
            mBackground.setHotspot(x, y);
        }
    }

    /**
     * Stateful colors are colors that will overlay on the notification original color when one of
     * hover states, pressed states or other similar states is activated.
     */
    private void setStatefulColors() {
        if (mTintColor != mNormalColor) {
            ColorStateList newColor = ContrastColorUtil.isColorDark(mTintColor)
                    ? mDarkColoredStatefulColors : mLightColoredStatefulColors;
            ((GradientDrawable) getStatefulBackgroundLayer().mutate()).setColor(newColor);
        }
    }

    /**
     * Sets a background drawable. As we need to change our bounds independently of layout, we need
     * the notion of a background independently of the regular View background..
     */
    public void setCustomBackground(Drawable background) {
        if (mBackground != null) {
            mBackground.setCallback(null);
            unscheduleDrawable(mBackground);
        }
        mBackground = background;
        mRippleColor = null;
        mBackground.mutate();
        if (mBackground != null) {
            mBackground.setCallback(this);
            setTint(mTintColor);
        }
        if (mBackground instanceof RippleDrawable) {
            ((RippleDrawable) mBackground).setForceSoftware(true);
        }
        updateBackgroundRadii();
        invalidate();
    }

    public void setCustomBackground(int drawableResId) {
        final Drawable d = mContext.getDrawable(drawableResId);
        setCustomBackground(d);
    }

    public Drawable getBaseBackgroundLayer() {
        return ((LayerDrawable) mBackground).getDrawable(0);
    }

    private Drawable getStatefulBackgroundLayer() {
        return ((LayerDrawable) mBackground).getDrawable(1);
    }

    private void updateBaseLayerColor() {
        // BG base layer being a drawable, there isn't a method like setColor() to color it.
        // Instead, we set a color filter that essentially replaces every pixel of the drawable.
        // For non-colorized notifications, this function specifies a new color token.
        // For colorized notifications, this uses a color that matches the tint color at 90% alpha.
        int color = isColorized()
                ? ColorUtils.setAlphaComponent(mTintColor, (int) (MAX_ALPHA * 0.9f))
                : SurfaceEffectColors.surfaceEffect1(getContext());
        if (mForceOpaque) {
            color = ColorUtils.setAlphaComponent(color, MAX_ALPHA);
        }
        getBaseBackgroundLayer().setColorFilter(
                new PorterDuffColorFilter(
                        color,
                        PorterDuff.Mode.SRC)); // SRC operator discards the drawable's color+alpha
    }

    public void setTint(int tintColor) {
        Drawable baseLayer = getBaseBackgroundLayer();
        baseLayer.mutate().setTintMode(PorterDuff.Mode.SRC_ATOP);
        baseLayer.setTint(tintColor);
        mTintColor = tintColor;
        if (notificationRowTransparency()) {
            updateBaseLayerColor();
        }
        setStatefulColors();
        invalidate();
    }

    public void setActualHeight(int actualHeight) {
        if (mExpandAnimationRunning) {
            return;
        }
        mActualHeight = actualHeight;
        invalidate();
    }

    private int getActualHeight() {
        if (mExpandAnimationRunning && mExpandAnimationHeight > -1) {
            return mExpandAnimationHeight;
        } else if (mActualHeight > -1) {
            return mActualHeight;
        }
        return getHeight();
    }

    public void setActualWidth(int actualWidth) {
        mActualWidth = actualWidth;
    }

    private int getActualWidth() {
        if (mExpandAnimationRunning && mExpandAnimationWidth > -1) {
            return mExpandAnimationWidth;
        } else if (mActualWidth > -1) {
            return mActualWidth;
        }
        return getWidth();
    }

    public void setClipTopAmount(int clipTopAmount) {
        mClipTopAmount = clipTopAmount;
        invalidate();
    }

    public void setClipBottomAmount(int clipBottomAmount) {
        mClipBottomAmount = clipBottomAmount;
        invalidate();
    }

    @Override
    public boolean hasOverlappingRendering() {

        // Prevents this view from creating a layer when alpha is animating.
        return false;
    }

    public void setState(int[] drawableState) {
        if (mBackground != null && mBackground.isStateful()) {
            mBackground.setState(drawableState);
        }
    }

    public void setRippleColor(int color) {
        if (mBackground instanceof RippleDrawable) {
            RippleDrawable ripple = (RippleDrawable) mBackground;
            ripple.setColor(ColorStateList.valueOf(color));
            mRippleColor = color;
        } else {
            mRippleColor = null;
        }
    }

    public void setDrawableAlpha(int drawableAlpha) {
        mDrawableAlpha = drawableAlpha;
        if (mExpandAnimationRunning) {
            return;
        }
        mBackground.setAlpha(drawableAlpha);
    }

    /**
     * Sets the current top and bottom radius for this background.
     */
    public void setRadius(float topRoundness, float bottomRoundness) {
        if (topRoundness == mCornerRadii[0] && bottomRoundness == mCornerRadii[4]) {
            return;
        }
        mBottomIsRounded = bottomRoundness != 0.0f;
        mCornerRadii[0] = topRoundness;
        mCornerRadii[1] = topRoundness;
        mCornerRadii[2] = topRoundness;
        mCornerRadii[3] = topRoundness;
        mCornerRadii[4] = bottomRoundness;
        mCornerRadii[5] = bottomRoundness;
        mCornerRadii[6] = bottomRoundness;
        mCornerRadii[7] = bottomRoundness;
        updateBackgroundRadii();
    }

    public void setBottomAmountClips(boolean clips) {
        if (clips != mBottomAmountClips) {
            mBottomAmountClips = clips;
            invalidate();
        }
    }

    private void updateBackgroundRadii() {
        if (mDontModifyCorners) {
            return;
        }
        if (mBackground instanceof LayerDrawable layerDrawable) {
            int numberOfLayers = layerDrawable.getNumberOfLayers();
            for (int i = 0; i < numberOfLayers; i++) {
                GradientDrawable gradientDrawable = (GradientDrawable) layerDrawable.getDrawable(i);
                gradientDrawable.setCornerRadii(mCornerRadii);
            }
            updateFocusOverlayRadii(layerDrawable);
        }
    }

    private void updateFocusOverlayRadii(LayerDrawable background) {
        GradientDrawable overlay =
                (GradientDrawable) background.findDrawableByLayerId(
                        R.id.notification_focus_overlay);
        for (int i = 0; i < mCornerRadii.length; i++) {
            // in theory subtracting mFocusOverlayStroke/2 should be enough but notification
            // background is still peeking a bit from below - probably due to antialiasing or
            // overlay uneven scaling. So let's subtract full mFocusOverlayStroke to make sure the
            // radius is a bit smaller and covers background corners fully
            mFocusOverlayCornerRadii[i] = Math.max(0, mCornerRadii[i] - mFocusOverlayStroke);
        }
        overlay.setCornerRadii(mFocusOverlayCornerRadii);
    }

    /** Set the current expand animation size. */
    public void setExpandAnimationSize(int width, int height) {
        mExpandAnimationHeight = height;
        mExpandAnimationWidth = width;
        invalidate();
    }

    public void setExpandAnimationRunning(boolean running) {
        mExpandAnimationRunning = running;
        if (mBackground instanceof LayerDrawable) {
            GradientDrawable gradientDrawable =
                    (GradientDrawable) ((LayerDrawable) mBackground).getDrawable(0);
            // Speed optimization: disable AA if transfer mode is not SRC_OVER. AA is not easy to
            // spot during animation anyways.
            gradientDrawable.setAntiAlias(!running);
        }
        if (!mExpandAnimationRunning) {
            setDrawableAlpha(mDrawableAlpha);
        }
        invalidate();
    }

    @Override
    public void dump(PrintWriter pw, @NonNull String[] args) {
        pw.println("mDontModifyCorners: " + mDontModifyCorners);
        pw.println("mClipTopAmount: " + mClipTopAmount);
        pw.println("mClipBottomAmount: " + mClipBottomAmount);
        pw.println("mCornerRadii: " + Arrays.toString(mCornerRadii));
        pw.println("mBottomIsRounded: " + mBottomIsRounded);
        pw.println("mBottomAmountClips: " + mBottomAmountClips);
        pw.println("mActualWidth: " + mActualWidth);
        pw.println("mActualHeight: " + mActualHeight);
        pw.println("mTintColor: " + hexColorString(mTintColor));
        pw.println("mRippleColor: " + hexColorString(mRippleColor));
        pw.println("mBackground: " + DrawableDumpKt.dumpToString(mBackground));
    }

    /** create a concise dump of this view's colors */
    public String toDumpString() {
        return "<NotificationBackgroundView"
                + " tintColor=" + hexColorString(mTintColor)
                + " rippleColor=" + hexColorString(mRippleColor)
                + " bgColor=" + DrawableDumpKt.getSolidColor(mBackground)
                + ">";

    }
}
