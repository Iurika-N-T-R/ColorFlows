package com.iurika.ColorFlows

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * The color wheel with the harmony's markers on it. RGB: a disc, hue around, saturation out from the white center.
 * RYB: a ring of the painter's 12 hues. Hue h sits at angle h counterclockwise from the right, red at 3 o'clock.
 * Dragging any marker turns the whole harmony; on the disc it also sets the saturation.
 * Monochrome: the markers share the hue, each keeps its own saturation (its distance from the center) and value;
 * the one touched last is [selected], the one the value slider changes.
 */
class WheelView(c: Context) : View(c) {
    var wheel = Wheel.RGB; set(v) { field = v; invalidate() }
    var harmony = Harmony.COMPLEMENT; set(v) { field = v; invalidate() }
    var base = Hsv(140f, .75f, 1f); set(v) { field = v; invalidate() }
    var mono = monoOf(base); set(v) { field = v; invalidate() }
    var selected = 0; set(v) { field = v; invalidate() }
    var onChange: () -> Unit = {}

    fun points() = harmony.points(base, mono)

    private val d = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var cx = 0f; private var cy = 0f; private var r = 0f
    private var drag = -1

    // The ring's thickness, and the circle its markers run on
    private val ring get() = r * .16f
    private val track get() = if (wheel == Wheel.RYB) r - ring / 2 else r

    override fun onMeasure(w: Int, h: Int) {
        val s = MeasureSpec.getSize(w)
        setMeasuredDimension(s, s)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        cx = w / 2f; cy = h / 2f; r = min(w, h) / 2f - 14 * d
    }

    private fun at(h: Float, frac: Float): Pair<Float, Float> {
        val a = Math.toRadians(h.toDouble())
        return cx + (track * frac * cos(a)).toFloat() to cy - (track * frac * sin(a)).toFloat()
    }

    private fun frac(p: Hsv) = if (wheel == Wheel.RYB && harmony != Harmony.MONO) 1f else p.s

    override fun onDraw(c: Canvas) {
        if (wheel == Wheel.RGB) {
            // the sweep runs clockwise on screen, so the hues go in backwards to come out counterclockwise
            val hues = IntArray(13) { Color.HSVToColor(floatArrayOf((360f - it * 30f) % 360, 1f, 1f)) }
            fill.shader = SweepGradient(cx, cy, hues, null)
            c.drawCircle(cx, cy, r, fill)
            fill.shader = RadialGradient(cx, cy, r, Color.WHITE, 0x00ffffff, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, fill)
            fill.shader = null
            val v = points().getOrElse(selected) { base }.v
            fill.color = Color.argb(((1 - v) * 255).toInt(), 0, 0, 0)
            c.drawCircle(cx, cy, r, fill)
        } else {
            val box = RectF(cx - r + ring / 2, cy - r + ring / 2, cx + r - ring / 2, cy + r - ring / 2)
            line.strokeWidth = ring
            line.strokeCap = Paint.Cap.BUTT
            for (i in 0 until 12) {
                line.color = wheel.color(Hsv(i * 30f, base.s, base.v))
                // drawArc goes clockwise: segment i covers the hues i*30 ± 15
                c.drawArc(box, -(i * 30f) - 15f, 30.5f, false, line)
            }
        }
        // the lines from the center to each color, then the markers, base last so it is on top
        val pts = points()
        line.strokeWidth = 1.5f * d
        line.color = 0x66ffffff
        for (p in pts) at(p.h, frac(p)).let { (x, y) -> c.drawLine(cx, cy, x, y, line) }
        for (i in pts.indices.sortedBy { it == selected }) {
            val p = pts[i]
            val (x, y) = at(p.h, frac(p))
            val col = wheel.color(p)
            val rad = 13 * d
            if (i == selected) {
                // the one being moved: an open ring, the color shows through
                line.strokeWidth = 3 * d; line.color = Color.WHITE
                c.drawCircle(x, y, rad, line)
                line.strokeWidth = 1.5f * d; line.color = 0x88000000.toInt()
                c.drawCircle(x, y, rad + 2 * d, line)
            } else {
                fill.color = col
                c.drawCircle(x, y, rad, fill)
                line.strokeWidth = 2 * d; line.color = 0xddffffff.toInt()
                c.drawCircle(x, y, rad, line)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val dx = e.x - cx; val dy = cy - e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val pts = points()
                // the nearest marker under the finger, else the base jumps where it touched
                drag = pts.indices.minByOrNull { i -> at(pts[i].h, frac(pts[i])).let { (x, y) -> hypot(x - e.x, y - e.y) } }
                    ?.takeIf { i -> at(pts[i].h, frac(pts[i])).let { (x, y) -> hypot(x - e.x, y - e.y) } < 36 * d } ?: 0
                parent.requestDisallowInterceptTouchEvent(true)
                move(dx, dy)
            }
            MotionEvent.ACTION_MOVE -> move(dx, dy)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> drag = -1
        }
        return true
    }

    private fun move(dx: Float, dy: Float) {
        val a = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val far = (hypot(dx, dy) / track).coerceIn(0f, 1f)
        if (harmony == Harmony.MONO) {
            // one hue for all; the touched one alone moves in or out
            val h = (a % 360 + 360) % 360
            selected = drag
            if (drag == 0) base = Hsv(h, far, base.v)
            else { base = base.copy(h = h); mono = mono.toMutableList().also { it[drag - 1] = it[drag - 1].copy(s = far) } }
        } else {
            val h = ((a - harmony.turns.getOrElse(drag) { 0f }) % 360 + 360) % 360
            // only the base's own distance sets the saturation, the others keep their turn from it
            base = Hsv(h, if (drag == 0 && wheel == Wheel.RGB) far else base.s, base.v)
        }
        onChange()
    }
}
