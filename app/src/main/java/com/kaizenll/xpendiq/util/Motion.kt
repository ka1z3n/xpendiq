package com.kaizenll.xpendiq.util

import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import android.view.animation.PathInterpolator
import android.widget.TextView
import com.kaizenll.xpendiq.R

/**
 * Shared motion constants + helpers. Every animation is gated on [enabled] so that users who
 * turn off animations (or enable reduced-motion) get instant, static UI.
 */
object Motion {

    /** Material 3 "emphasized" easing — cubic-bezier(.2, 0, 0, 1). */
    val emphasized = PathInterpolator(0.2f, 0f, 0f, 1f)

    /** Ease-out cubic, for value count-ups. */
    val easeOutCubic = PathInterpolator(0.33f, 1f, 0.68f, 1f)

    const val EMPHASIZED_MS = 800L

    /** False when the OS animator scale is 0 (animations off / reduced motion). */
    fun enabled(context: Context): Boolean =
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) != 0f

    /** Roll a rupee figure from 0 up to [toPaise]. Falls back to a static value when disabled. */
    fun countUpRupees(tv: TextView, toPaise: Long, whole: Boolean) {
        val fmt: (Long) -> String = { p ->
            if (whole) CurrencyFormat.paiseToInrWhole(p) else CurrencyFormat.paiseToInr(p)
        }
        (tv.getTag(R.id.motion_count_animator) as? ValueAnimator)?.cancel()
        if (!enabled(tv.context) || toPaise <= 0L) {
            tv.text = fmt(toPaise)
            return
        }
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900L
            interpolator = easeOutCubic
            addUpdateListener { tv.text = fmt((toPaise * (it.animatedValue as Float)).toLong()) }
        }
        tv.setTag(R.id.motion_count_animator, anim)
        anim.start()
    }
}
