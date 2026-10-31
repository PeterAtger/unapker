package com.peter.unapker

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator

object Motion {
    private val settle = PathInterpolator(0.2f, 0.9f, 0.3f, 1f)
    private val swoop = PathInterpolator(0.6f, 0f, 0.3f, 1f)
    private val fling = PathInterpolator(0.1f, 0.8f, 0.3f, 1f)

    fun enter(view: View) {
        view.alpha = 0f
        view.translationY = view.context.dp(18f)
        view.animate().alpha(1f).translationY(0f).setDuration(500).setInterpolator(settle).start()
    }

    fun rise(view: View, delay: Long) {
        view.alpha = 0f
        view.translationY = view.context.dp(24f)
        view.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(550).setInterpolator(settle).start()
    }

    fun bob(view: View, rotation: Float, phaseMs: Long): Animator {
        val lift = -view.context.dp(14f)
        return ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, lift),
            PropertyValuesHolder.ofFloat(View.ROTATION, rotation, rotation + 3f),
        ).apply {
            duration = 1900
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
            currentPlayTime = phaseMs
        }
    }

    fun drop(view: View, rotation: Float) {
        val fall = -view.context.dp(480f)
        view.pivotY = view.height.toFloat().takeIf { it > 0 } ?: view.context.dp(64f)
        val fallIn = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, fall, 0f),
            PropertyValuesHolder.ofFloat(View.ROTATION, -14f, rotation),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f, 1f),
        ).apply {
            duration = 480
            interpolator = PathInterpolator(0.5f, 0f, 0.9f, 0.6f)
        }
        val squash = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1.05f, 0.98f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.86f, 1.04f, 1f),
        ).apply {
            duration = 300
            interpolator = settle
        }
        AnimatorSet().apply { playSequentially(fallIn, squash) }.start()
    }

    fun gather(view: View, fromX: Float, fromY: Float, fromRotation: Float, delay: Long) {
        view.translationX = fromX
        view.translationY = fromY
        view.rotation = fromRotation
        view.scaleX = 1f
        view.scaleY = 1f
        view.alpha = 1f
        view.animate()
            .translationX(0f).translationY(0f).rotation(0f).scaleX(0.2f).scaleY(0.2f).alpha(0f)
            .setStartDelay(delay).setDuration(950).setInterpolator(swoop).start()
    }

    fun pop(view: View, delay: Long) {
        view.scaleX = 0.2f
        view.scaleY = 0.2f
        view.alpha = 0f
        view.animate().scaleX(1f).scaleY(1f).alpha(1f)
            .setStartDelay(delay).setDuration(650).setInterpolator(OvershootInterpolator(2.2f)).start()
    }

    fun spin(view: View): Animator =
        ObjectAnimator.ofFloat(view, View.ROTATION, 0f, 360f).apply {
            duration = 1100
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

    fun halo(view: View): Animator =
        ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.2f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.2f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.4f, 0.08f),
        ).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

    fun hop(view: View, delay: Long): Animator =
        ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, -view.context.dp(6f)),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.15f, 1f),
        ).apply {
            duration = 600
            startDelay = delay
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

    fun float(view: View, distanceDp: Float, durationMs: Long, delay: Long = 0): Animator =
        ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, -view.context.dp(distanceDp)).apply {
            duration = durationMs
            startDelay = delay
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

    fun burst(view: View, toX: Float, toY: Float, toRotation: Float): Animator {
        view.translationX = 0f
        view.translationY = 0f
        view.scaleX = 0.3f
        view.scaleY = 0.3f
        view.alpha = 0f
        val out = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, toX),
            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, toY),
            PropertyValuesHolder.ofFloat(View.ROTATION, 0f, toRotation),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.3f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.3f, 1f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f, 1f, 1f, 1f, 1f),
        ).apply {
            duration = 1150
            interpolator = fling
        }
        val drift = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, toY, toY - view.context.dp(9f)),
            PropertyValuesHolder.ofFloat(View.ROTATION, toRotation, toRotation + 14f),
        ).apply {
            duration = 1600
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        return AnimatorSet().apply {
            playSequentially(out, drift)
            start()
        }
    }

    fun shake(view: View): Animator =
        ObjectAnimator.ofFloat(view, View.ROTATION, -6f, -6f, -6f, -6f, -6f, -6f, -6f, 8f, -12f, 10f, -6f, -6f).apply {
            duration = 2400
            repeatCount = ValueAnimator.INFINITE
            start()
        }
}
