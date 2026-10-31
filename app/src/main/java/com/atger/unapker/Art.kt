package com.atger.unapker

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.AttributeSet
import android.view.View

fun Context.dp(value: Float) = value * resources.displayMetrics.density

fun Context.dp(value: Int) = dp(value.toFloat()).toInt()

fun Context.tileBackground(color: Int, radiusDp: Float, shadowDp: Int): Drawable {
    val radius = dp(radiusDp)
    val shadow = GradientDrawable().apply {
        setColor(getColor(R.color.tile_shadow))
        cornerRadius = radius
    }
    val face = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }
    return LayerDrawable(arrayOf(shadow, face)).apply {
        setLayerInset(0, 0, dp(shadowDp), 0, 0)
        setLayerInset(1, 0, 0, 0, dp(shadowDp))
    }
}

class MatDrawable(context: Context) : Drawable() {
    private val step = context.dp(26f)
    private val ground = context.getColor(R.color.ground)
    private val line = Paint().apply {
        color = 0x0EFFFFFF
        strokeWidth = context.dp(1f)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawColor(ground)
        var x = 0f
        while (x < bounds.width()) {
            canvas.drawLine(x, 0f, x, bounds.height().toFloat(), line)
            x += step
        }
        var y = 0f
        while (y < bounds.height()) {
            canvas.drawLine(0f, y, bounds.width().toFloat(), y, line)
            y += step
        }
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}

// Drawn in the design's 342x360 coordinate space and scaled to the view.
class BoxFlapsView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val flap = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.box_flap) }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.box_rim) }
    private val left = Path().apply { moveTo(70f, 262f); lineTo(36f, 214f); lineTo(150f, 214f); lineTo(171f, 262f); close() }
    private val right = Path().apply { moveTo(272f, 262f); lineTo(306f, 214f); lineTo(192f, 214f); lineTo(171f, 262f); close() }
    private val rimRect = RectF(70f, 252f, 272f, 268f)

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.scale(width / 342f, height / 360f)
        canvas.drawPath(left, flap)
        canvas.drawPath(right, flap)
        canvas.drawRoundRect(rimRect, 4f, 4f, rim)
        canvas.restore()
    }
}

class RingView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val stroke = context.dp(7f)
    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        color = 0xFFFFFFFF.toInt()
    }
    private val sun = Paint(white).apply { color = context.getColor(R.color.sun) }
    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        val inset = stroke / 2
        oval.set(inset, inset, width - inset, height - inset)
        canvas.drawArc(oval, 0f, 62f, false, white)
        canvas.drawArc(oval, 180f, 22f, false, sun)
    }
}

class BarView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.track) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.sun) }
    private val rect = RectF()

    var progress = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val radius = height / 2f
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, radius, radius, track)
        if (progress <= 0f) return
        rect.right = (width * progress).coerceAtLeast(height.toFloat())
        canvas.drawRoundRect(rect, radius, radius, fill)
    }
}
