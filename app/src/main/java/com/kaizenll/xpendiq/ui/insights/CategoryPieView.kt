package com.kaizenll.xpendiq.ui.insights

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Minimal donut chart: takes a list of (color, amount) pairs, draws each slice as a stroked
 * arc on a single circle. Because we use `Paint.Style.STROKE` with `useCenter = false`, the
 * inside is naturally hollow — no clipping or XferMode tricks needed.
 *
 * No labels are drawn inside the donut; the surrounding legend rows handle that.
 */
class CategoryPieView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** color × fraction of total (0..1). */
    private var slices: List<Pair<Int, Float>> = emptyList()

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val rect = RectF()

    /** Outer / inner radius ratio. 0.6 gives a generous donut hole. */
    private val innerRadiusRatio = 0.62f

    fun setData(items: List<Pair<Int, Long>>) {
        val total = items.sumOf { it.second }
        slices = if (total > 0L) {
            items
                .filter { it.second > 0L }
                .map { (color, amount) -> color to (amount.toDouble() / total.toDouble()).toFloat() }
        } else {
            emptyList()
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (slices.isEmpty()) return

        val cx = width / 2f
        val cy = height / 2f
        val outerR = minOf(width, height) / 2f - 4f
        val innerR = outerR * innerRadiusRatio
        val midR = (outerR + innerR) / 2f
        val stroke = outerR - innerR
        arcPaint.strokeWidth = stroke
        rect.set(cx - midR, cy - midR, cx + midR, cy + midR)

        var startAngle = -90f  // start at 12 o'clock
        for ((color, fraction) in slices) {
            arcPaint.color = color
            val sweep = fraction * 360f
            canvas.drawArc(rect, startAngle, sweep, false, arcPaint)
            startAngle += sweep
        }
    }
}
