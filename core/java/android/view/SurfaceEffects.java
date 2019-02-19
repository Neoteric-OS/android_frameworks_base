/**
 * Copyright (C) 2016 Samsung Electronics Co., Ltd. All rights reserved.
 *
 * Mobile Communications Business,
 * IT & Mobile Communications, Samsung Electronics Co., Ltd.
 *
 * This software and its documentation are confidential and proprietary
 * information of Samsung Electronics Co., Ltd.  No part of the software and
 * documents may be copied, reproduced, transmitted, translated, or reduced to
 * any electronic medium or machine-readable form without the prior written
 * consent of Samsung Electronics.
 *
 * Samsung Electronics makes no representations with respect to the contents,
 * and assumes no responsibility for any errors that might appear in the
 * software and documents. This publication and the contents hereof are subject
 * to change without notice.
 */

/**
 * SurfaceEffects helps to control SurfaceFlinger effects.
 *
 * In order to apply a new effect for a window, you should create a new instance of
 * Effect.Builder class using Builder builder = newBuilder() and set all required parameters.
 * Once an effect is configured, call Effect effect = builder.build() method to create an effect
 * object, which stores all effect parameters.
 *
 * You can also call all these functions in a chain, for example
 * newBuilder().setPixelEffectType(PixEffectType.BLUR).start(windowToken)
 */

package android.view;

import android.graphics.Rect;

import java.util.Vector;

/**
 * @hide
 */
public class SurfaceEffects {

    private static final long FRAME_LENGTH_NANOS = 16666666L;

    /**
     * Animation interpolation mode. Defines how animated value is interpolated among
     * different key frames
     */
    public enum InterpMode {
        HOLD(0),
        LINEAR(1),
        SMOOTH(4),
        SMOOTH_OUT(2),
        SMOOTH_IN(3);

        public final int id;
        InterpMode(int id) {
            this.id = id;
        }
    }

    /**
     * Effect animation mode defines the animation behaviour.
     *
     * STATIC - effect is static.
     * LOOP - effect animation runs cyclically
     * ONCE_STAY_START - effect animation runs only once and then it becomes static and keep the
     *                   initial state
     * ONCE_STAY_END - effect animation runs only once and then it becomes static and keep the
     *                 latest state
     * ONCE_DESTROYED - effect animation runs only once and then the effect is destroyed.
     *
     * Values must correspond to constants defined in
     * frameworks/native/services/surfaceflinger/Effects/Animator.h
     */
    public enum AnimationMode {

        STATIC(0),
        LOOP(1),
        ONCE_STAY_START(2),
        ONCE_STAY_END(3),
        ONCE_DESTROY(4);

        public final int id;
        AnimationMode(int id) {
            this.id = id;
        }
    }

    /**
     * Effect animated parameters. Can be used to control effects
     *
     * Values must correspond to constants defined in
     * frameworks/native/services/surfaceflinger/Effects/Animator.h
     */
    public enum AnimParam {

        ALPHA(12),
        BLUR_RADIUS(13),
        BLUR_SATURATION(14),
        BLUR_ALPHA(15),
        REGION_SIZE_X(20),
        REGION_SIZE_Y(21),
        REGION_CORNER_SIZE(22),
        REGION_POS_X(23),
        REGION_POS_Y(24),
        REGION_FACTOR_X(27),
        REGION_FACTOR_Y(28),
        REGION_OFFSET_X(31),
        REGION_OFFSET_Y(32);

        public final int id;
        AnimParam(int id) {
            this.id = id;
        }
    }

    /**
     * Effect target defines which windows are affected by the effect
     *
     * BEHIND - all windows which are behind current window are affected
     * SELF_AND_BEHIND - the same as BEHIND plus the effect window content itself is affected
     *
     * Values must correspond to constants defined in
     * frameworks/native/services/surfaceflinger/Effects/Effect.h
     */
    public enum EffectTarget {

        SELF(1),
        BEHIND(2),
        SELF_AND_BEHIND(3);

        public final int id;
        EffectTarget(int id) {
            this.id = id;
        }
    }

    /**
     * Pixel effect type allows to choose one of the predefined effects.
     *
     * Values must correspond to constants defined in
     * frameworks/native/services/surfaceflinger/Effects/PixelEffect.h
     */
    public enum PixEffectType {
        NONE(0),
        BLUR(1);

        public final int id;
        PixEffectType(int id) {
            this.id = id;
        }
    }

    public static final Effect EMPTY_EFFECT = newBuilder().build();

    public static class Effect {

        /**
         * Class to build argument lists to send to compositor
         */
        public static class Builder {
            private AnimationMode mAnimationMode = AnimationMode.STATIC;
            private EffectTarget mEffectTarget = EffectTarget.BEHIND;
            private PixEffectType mPixelEffectType = PixEffectType.NONE;
            private final Vector<Rect> mEffectRegion = new Vector<Rect>();
            private final Vector<AnimKeyFrame> mPixelAnimationVector = new Vector<AnimKeyFrame>();
            private final Vector<AnimKeyFrame> mGeometryAnimationVector =
                    new Vector<AnimKeyFrame>();

            private Builder() {}

            /**
             * Get currently set animation mode
             *
             * @return
             */
            public AnimationMode getAnimationMode() {
                return mAnimationMode;
            }

            /**
             * Set animation mode
             *
             * @param mAnimationMode desired animation mode in effect
             * @return self
             */
            public Builder setAnimationMode(AnimationMode mAnimationMode) {
                this.mAnimationMode = mAnimationMode;
                return this;
            }

            /**
             * Get currently set effect target
             *
             * @return
             */
            public EffectTarget getEffectTarget() {
                return mEffectTarget;
            }

            /**
             * Set effect target
             *
             * @param mEffectTarget desired effect target
             * @return self
             */
            public Builder setEffectTarget(EffectTarget mEffectTarget) {
                this.mEffectTarget = mEffectTarget;
                return this;
            }

            /**
             * Get currently set pixel effect
             *
             * @return
             */
            public PixEffectType getPixelEffectType() {
                return mPixelEffectType;
            }

            /**
             * Set pixel effect
             *
             * @param mPixelEffectType desired pixel effect
             * @return self
             */
            public Builder setPixelEffectType(PixEffectType mPixelEffectType) {
                this.mPixelEffectType = mPixelEffectType;
                return this;
            }

            /**
             * Add pixel effect based animation keyframe.
             *
             * @param animParam desired animation parameters
             * @param timeMs time in milliseconds this keyframe will be displayed
             * @param value effect value
             * @param interpolation interpolation method for non-keyframe animation parts
             * @return self
             */
            public Builder addPixAnimation(AnimParam animParam, int timeMs, float value,
                    InterpMode interpolation) {
                mPixelAnimationVector.add(
                        new AnimKeyFrame(animParam, timeMs, value, interpolation)
                );
                return this;
            }

            /**
             * Return all animation keyframes as a vector
             *
             * @return
             */
            public Vector<AnimKeyFrame> getPixelAnimationVector() {
                return mPixelAnimationVector;
            }

            /**
             * Add region on which effect will be applied
             *
             * @param rect Rectange where the animation will be located
             * @return self
             */
            public Builder addEffectRegionRect(Rect rect) {
                mEffectRegion.add(new Rect(rect));
                return this;
            }

            /**
             * Remove all region information and apply effect fulscreen
             *
             * @return self
             */
            public Builder makeFullscreen() {
                mEffectRegion.clear();
                mEffectRegion.add(new Rect(0, 0, 10000, 10000));
                return this;
            }

            /**
             * Return region on which effect will be applied
             *
             * @return
             */
            public Vector<Rect> getEffectRegion() {
                return mEffectRegion;
            }

            /**
             * Serialize current parameters into an Effect
             *
             * @return Effect class with current Builder parameters
             */
            public Effect build() {
                return new Effect(this);
            }
        }

        private String mBytes;

        private Effect(Builder builder) {
            mBytes = serializeEffect(builder);
        }

        public Effect(Effect o) {
            mBytes = o.mBytes;
        }

        public String getBytes() {
            return mBytes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Effect effect = (Effect) o;
            return mBytes.equals(effect.mBytes);
        }

        /**
         * Get integer to uniquely identify this Effect
         *
         * @return
         */
        @Override
        public int hashCode() {
            return mBytes.hashCode();
        }

        /**
         * Serialize Effect into string for communication with
         * SurfaceFlinger
         *
         * @param builder Effect.Builder with the desired effect parameters
         * @return a string interpretable by SurfaceFlinger
         */
        public static String serializeEffect(Builder builder) {
            String newArgsList = "";
            newArgsList += Float.toString(builder.mAnimationMode.id); newArgsList += " ";
            newArgsList += Float.toString(builder.mEffectTarget.id); newArgsList += " ";
            newArgsList += Float.toString(builder.mPixelEffectType.id); newArgsList += " ";

            newArgsList += Float.toString(builder.getEffectRegion().size());
            newArgsList += " ";
            for (Rect r : builder.mEffectRegion) {
                newArgsList += Float.toString(r.left); newArgsList += " ";
                newArgsList += Float.toString(r.top); newArgsList += " ";
                newArgsList += Float.toString(r.right); newArgsList += " ";
                newArgsList += Float.toString(r.bottom); newArgsList += " ";
            }

            newArgsList += Float.toString(builder.mPixelAnimationVector.size());
            newArgsList += " ";
            for (AnimKeyFrame anim : builder.mPixelAnimationVector) {
                newArgsList += Float.toString(anim.animParam.id); newArgsList += " ";
                newArgsList += Float.toString(anim.timeMs); newArgsList += " ";
                newArgsList += Float.toString(anim.value); newArgsList += " ";
                newArgsList += Float.toString(anim.interp.id); newArgsList += " ";
            }

            newArgsList += Float.toString(builder.mGeometryAnimationVector.size());
            newArgsList += " ";
            for (AnimKeyFrame anim : builder.mGeometryAnimationVector) {
                newArgsList += Float.toString(anim.animParam.id); newArgsList += " ";
                newArgsList += Float.toString(anim.timeMs); newArgsList += " ";
                newArgsList += Float.toString(anim.value); newArgsList += " ";
                newArgsList += Float.toString(anim.interp.id); newArgsList += " ";
            }

            return newArgsList;
        }
    }

    /**
     * Create a new instance of effect builder class
     *
     * @return
     */
    public static Effect.Builder newBuilder() {
        return new Effect.Builder();
    }

    // Implementation details

    private static class AnimKeyFrame {
        public final AnimParam animParam;
        public final int timeMs;
        public final float value;
        public final InterpMode interp;

        AnimKeyFrame(AnimParam animParam, int timeMs, float value, InterpMode interp) {
            this.animParam = animParam;
            this.timeMs = timeMs;
            this.value = value;
            this.interp = interp;
        }
    }

}
