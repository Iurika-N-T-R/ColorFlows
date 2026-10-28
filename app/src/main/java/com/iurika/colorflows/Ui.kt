package com.iurika.colorflows

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

const val BG = 0xff161618.toInt()
const val SURF = 0xff222226.toInt()
const val SURF2 = 0xff2e2e34.toInt()
const val TEXT = 0xffffffff.toInt()
const val MU = 0xff9a9aa2.toInt()

fun Activity.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

fun Activity.rounded(color: Int, radius: Int, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
    cornerRadius = dp(radius).toFloat(); setColor(color); if (stroke > 0) setStroke(dp(stroke), strokeColor)
}

fun Activity.ripple(radius: Int) = RippleDrawable(ColorStateList.valueOf(0x33ffffff), null, rounded(TEXT, radius))

fun Activity.tv(text: CharSequence, size: Float = 15f, color: Int = TEXT, bold: Boolean = false) = TextView(this).apply {
    this.text = text; textSize = size; setTextColor(color); if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
}

fun Activity.vbox(vararg v: View) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; v.forEach(::addView) }

fun Activity.hbox(vararg v: View) = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; v.forEach(::addView) }

/** Width/height raw (-1, -2 or already px), margins in dp. */
fun <T : View> T.lp(w: Int = -1, h: Int = -2, weight: Float = 0f, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0): T = apply {
    val k = resources.displayMetrics.density
    layoutParams = LinearLayout.LayoutParams(w, h, weight).apply { setMargins((l * k).toInt(), (t * k).toInt(), (r * k).toInt(), (b * k).toInt()) }
}

fun Activity.iconButton(res: Int, desc: String, size: Int = 44, onClick: () -> Unit) = ImageView(this).apply {
    setImageResource(res); contentDescription = desc
    val pad = dp((size - 24) / 2); setPadding(pad, pad, pad, pad)
    background = ripple(size / 2); setOnClickListener { onClick() }
}.lp(dp(size), dp(size))

/** A rounded outline drawn with the palette's colors, left to right (the big buttons). */
class Outline(var colors: IntArray, private val r: Float, private val w: Float) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = w }
    override fun draw(c: Canvas) {
        val b = bounds
        p.shader = if (colors.size > 1) LinearGradient(b.left.toFloat(), 0f, b.right.toFloat(), 0f, colors, null, Shader.TileMode.CLAMP) else null
        if (colors.size == 1) p.color = colors[0]
        c.drawRoundRect(b.left + w / 2, b.top + w / 2, b.right - w / 2, b.bottom - w / 2, r, r, p)
    }
    override fun setAlpha(a: Int) { p.alpha = a }
    override fun setColorFilter(f: ColorFilter?) { p.colorFilter = f }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** The big rounded buttons under the wheel, outlined in the current colors. */
fun Activity.bigButton(label: String, onClick: () -> Unit) = tv(label, 16f).apply {
    gravity = Gravity.CENTER; minHeight = dp(52)
    background = Outline(intArrayOf(MU), dp(18).toFloat(), dp(1.5f))
    foreground = ripple(18)
    setOnClickListener { onClick() }
}

fun Activity.dp(v: Float) = v * resources.displayMetrics.density

fun TextView.outline(colors: List<Int>) {
    (background as? Outline)?.let { it.colors = (colors.ifEmpty { listOf(MU) }).toIntArray(); it.invalidateSelf() }
}
