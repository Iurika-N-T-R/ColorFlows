package com.iurika.ColorFlows

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private val Context.d get() = resources.displayMetrics.density

/** A harmony as a tiny dashed wheel with its points joined, like a constellation; [on] draws it solid. */
class HarmonyIcon(c: Context, val harmony: Harmony) : View(c) {
    var on = false; set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    override fun onDraw(c: Canvas) {
        val d = context.d
        val cx = width / 2f; val cy = height / 2f; val r = min(width, height) / 2f - 3 * d
        p.color = if (on) Color.WHITE else 0xff8a8a8e.toInt()
        p.strokeWidth = (if (on) 2f else 1.5f) * d
        if (on) c.drawCircle(cx, cy, r + 1 * d, p.apply { strokeWidth = 1.5f * d })
        // the rim in dashes
        p.strokeWidth = 3 * d
        val box = RectF(cx - r + 5 * d, cy - r + 5 * d, cx + r - 5 * d, cy + r - 5 * d)
        for (i in 0 until 12) c.drawArc(box, i * 30f + 4, 20f, false, p)
        val rr = r - 5 * d
        val pts = if (harmony == Harmony.MONO) listOf(0f to .1f, 0f to .5f, 0f to .9f)
        else harmony.turns.map { (90f + it) to 1f } // the base at 12 o'clock
        p.strokeWidth = 1.5f * d
        val xy = pts.map { (a, f) -> val t = Math.toRadians(if (harmony == Harmony.MONO) 90.0 else a.toDouble()); (cx + rr * f * cos(t)).toFloat() to (cy - rr * f * sin(t)).toFloat() }
        if (harmony == Harmony.MONO) c.drawLine(xy[0].first, xy[0].second, xy[2].first, xy[2].second, p)
        else for (i in xy.indices) if (xy.size > 2 || i == 0) {
            val (a, b) = xy[i]; val (e, f) = xy[(i + 1) % xy.size]
            c.drawLine(a, b, e, f, p)
        }
        p.style = Paint.Style.FILL
        for ((x, y) in xy) c.drawCircle(x, y, 2.5f * d, p)
        p.style = Paint.Style.STROKE
    }
}

/**
 * A lit ball of one color, as painters study it: the cooler shadow below, the warmer light above.
 * Touching it picks the shade under the finger.
 */
class SphereView(c: Context) : View(c) {
    var color = 0xffd2643c.toInt(); set(v) { field = v; bmp = null; picked = v; invalidate() }
    var picked = color; private set
    var onPick: (Int) -> Unit = {}
    private var bmp: Bitmap? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bp = Paint(Paint.FILTER_BITMAP_FLAG)
    private var px = -1f; private var py = -1f

    override fun onMeasure(w: Int, h: Int) { val s = MeasureSpec.getSize(w); setMeasuredDimension(s, s) }

    private fun render(n: Int): Bitmap {
        val (shadow, mid, light) = shading(color)
        val out = IntArray(n * n)
        val r = n / 2f
        // light from the top left, a little in front
        val lx = -.5f; val ly = -.6f; val lz = .4f
        val ll = sqrt(lx * lx + ly * ly + lz * lz)
        for (y in 0 until n) for (x in 0 until n) {
            val nx = (x - r + .5f) / r; val ny = (y - r + .5f) / r
            val q = nx * nx + ny * ny
            if (q > 1) continue
            val nz = sqrt(1 - q)
            val lam = ((nx * lx + ny * ly + nz * lz) / ll).coerceIn(-1f, 1f)
            val t = (lam + 1) / 2 // 0 = turned away, 1 = facing the light
            var c = if (t < .5f) mix(shadow, mid, (t / .5f).pow(.8f)) else mix(mid, light, ((t - .5f) / .5f).pow(1.6f))
            // light bouncing back on the rim of the shadow side
            val rim = (1 - nz).pow(3) * (1 - t)
            c = mix(c, mid, rim * .35f)
            // the soft edge, so the ball isn't jagged
            val a = ((1 - sqrt(q)) * r).coerceIn(0f, 1f)
            out[y * n + x] = (c and 0xffffff) or ((a * 255).toInt() shl 24)
        }
        return Bitmap.createBitmap(out, n, n, Bitmap.Config.ARGB_8888)
    }

    override fun onDraw(c: Canvas) {
        val n = min(width, 360)
        val b = bmp ?: render(n).also { bmp = it }
        c.drawBitmap(b, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), bp)
        // the picked shade as a disc in the middle, or where the finger is
        val d = context.d
        val x = if (px < 0) width / 2f else px; val y = if (py < 0) height / 2f else py
        p.style = Paint.Style.FILL; p.color = picked
        c.drawCircle(x, y, 22 * d, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2 * d; p.color = 0x55ffffff
        c.drawCircle(x, y, 22 * d, p)
        p.style = Paint.Style.FILL
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val b = bmp ?: return true
        val r = width / 2f
        val dx = e.x - r; val dy = e.y - r
        val k = min(1f, (r - 2) / max(1f, hypot(dx, dy))) // kept inside the ball
        px = r + dx * k; py = r + dy * k
        val c = b.getPixel((px / width * b.width).toInt().coerceIn(0, b.width - 1), (py / height * b.height).toInt().coerceIn(0, b.height - 1))
        picked = c or 0xff000000.toInt()
        parent.requestDisallowInterceptTouchEvent(true)
        if (e.actionMasked == MotionEvent.ACTION_UP) onPick(picked)
        invalidate()
        return true
    }
}

/** The picture of the IMAGE tab: touching it reads the color under the finger, shown in a loupe ring. */
class PickImageView(c: Context) : ImageView(c) {
    var bitmap: Bitmap? = null; set(v) { field = v; setImageBitmap(v); lx = -1f }
    var onPick: (Int, Boolean) -> Unit = { _, _ -> } // color, finger lifted
    private var lx = -1f; private var ly = -1f; private var col = 0
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init { scaleType = ScaleType.FIT_CENTER }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val b = bitmap ?: return false
        val inv = Matrix().also { imageMatrix.invert(it) }
        val pt = floatArrayOf(e.x - paddingLeft, e.y - paddingTop).also(inv::mapPoints)
        val x = pt[0].toInt(); val y = pt[1].toInt()
        if (x !in 0 until b.width || y !in 0 until b.height) return true
        parent.requestDisallowInterceptTouchEvent(true)
        lx = e.x; ly = e.y; col = b.getPixel(x, y) or 0xff000000.toInt()
        onPick(col, e.actionMasked == MotionEvent.ACTION_UP)
        invalidate()
        return true
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (lx < 0) return
        val d = context.d
        // above the finger, so it isn't hidden by it
        val y = max(30 * d, ly - 56 * d)
        p.style = Paint.Style.FILL; p.color = col
        c.drawCircle(lx, y, 26 * d, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 4 * d; p.color = Color.WHITE
        c.drawCircle(lx, y, 26 * d, p)
        p.strokeWidth = 1.5f * d
        c.drawCircle(lx, ly, 6 * d, p)
    }
}

/** Picks an index with the chances given by [w]. */
fun Random.weighted(w: List<Float>): Int {
    var x = nextFloat() * w.sum()
    for (i in w.indices) { x -= w[i]; if (x <= 0) return i }
    return w.lastIndex
}

enum class Pattern { DOTS, SHAPES, WAVES, GRADIENT, FLAT }

/** The palette tried on a pattern; [weights] says how much of each color, [seed] (the dice) reshuffles it. */
class PatternView(c: Context, val kind: Pattern) : View(c) {
    var colors = listOf<Int>(); set(v) { field = v; invalidate() }
    var weights = listOf<Float>(); set(v) { field = v; invalidate() }
    var seed = 1; set(v) { field = v; invalidate() }
    var selected = 0; set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private fun w() = if (weights.size == colors.size) weights else colors.map { 1f }

    override fun onDraw(c: Canvas) {
        if (colors.isEmpty()) return
        val rnd = Random(seed * 31 + kind.ordinal)
        val pick = { colors[rnd.weighted(w())] }
        val d = context.d
        val W = width.toFloat(); val H = height.toFloat()
        p.style = Paint.Style.FILL; p.shader = null; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        when (kind) {
            Pattern.DOTS -> {
                val cols = 6
                val cw = W / cols
                val rad = cw * .4f
                for (i in 0 until cols) {
                    // every other column half a step down
                    var y = if (i % 2 == 0) rad + cw * .1f else 0f
                    while (y - rad < H) { p.color = pick(); c.drawCircle(cw * i + cw / 2, y, rad, p); y += cw * 1.05f }
                }
            }
            Pattern.SHAPES -> {
                val cell = 92 * d
                val nx = max(1, (W / cell).toInt()); val ny = max(1, (H / cell).toInt())
                val cwid = W / nx; val chei = H / ny
                for (gy in 0 until ny) for (gx in 0 until nx) {
                    val x = cwid * gx + cwid * (.3f + rnd.nextFloat() * .4f)
                    val y = chei * gy + chei * (.3f + rnd.nextFloat() * .4f)
                    val s = min(cwid, chei) * (.25f + rnd.nextFloat() * .2f)
                    shape(c, rnd.nextInt(8), x, y, s, rnd.nextFloat() * 360, pick(), pick(), d)
                }
            }
            Pattern.WAVES -> {
                val bands = 7
                val step = H / (bands - 1)
                // from the top down: each band fills from its wavy top edge to the bottom, over the ones above
                for (i in -1..bands) { // -1: behind the first wave's dips
                    val top = step * (i - .5f)
                    val amp = step * (.25f + rnd.nextFloat() * .35f)
                    val ph = rnd.nextFloat() * 6.28f
                    val f = (1.2f + rnd.nextFloat()) * 6.28f / W
                    path.reset(); path.moveTo(0f, H)
                    var x = 0f
                    while (x <= W + 4) { path.lineTo(x, top + amp * sin(x * f + ph)); x += 4 }
                    path.lineTo(W, H); path.close()
                    p.color = colors[Math.floorMod(i + seed, colors.size)]
                    c.drawPath(path, p)
                }
            }
            Pattern.GRADIENT -> {
                val g = 8 * d
                val stripH = H * .16f
                val arr = (if (colors.size == 1) colors + colors else colors).toIntArray()
                p.shader = LinearGradient(0f, 0f, W, 0f, arr, null, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, W, stripH, p)
                p.shader = LinearGradient(0f, stripH + g, 0f, H, arr, null, Shader.TileMode.CLAMP)
                c.drawRect(0f, stripH + g, W, H, p)
                p.shader = null
            }
            Pattern.FLAT -> c.drawColor(colors[selected.coerceIn(0, colors.lastIndex)])
        }
    }

    // ponytail: 8 hand-made shapes; more kinds is just more branches
    private fun shape(c: Canvas, k: Int, x: Float, y: Float, s: Float, rot: Float, a: Int, b: Int, d: Float) {
        c.save(); c.rotate(rot, x, y)
        p.style = Paint.Style.FILL
        when (k) {
            0 -> { p.color = a; c.drawCircle(x, y, s * .7f, p) }
            1 -> { p.style = Paint.Style.STROKE; p.strokeWidth = s * .3f; p.color = a; c.drawCircle(x, y, s * .6f, p) }
            2 -> { // a triangle in a triangle
                for ((i, f) in listOf(1f, .65f, .32f).withIndex()) {
                    p.color = if (i % 2 == 0) a else b
                    path.reset(); path.moveTo(x, y - s * f); path.lineTo(x + s * f * .9f, y + s * f * .6f); path.lineTo(x - s * f * .9f, y + s * f * .6f); path.close()
                    c.drawPath(path, p)
                }
            }
            3 -> { // a square in a square
                for ((i, f) in listOf(.8f, .55f, .3f).withIndex()) { p.color = if (i % 2 == 0) a else b; c.drawRect(x - s * f, y - s * f, x + s * f, y + s * f, p) }
            }
            4 -> { // pills side by side
                p.style = Paint.Style.STROKE; p.strokeWidth = s * .28f
                for (i in 0..2) { p.color = if (i % 2 == 0) a else b; val o = (i - 1) * s * .32f; c.drawLine(x - s, y + o, x + s, y + o, p) }
            }
            5 -> { // zigzag
                p.style = Paint.Style.STROKE; p.strokeWidth = s * .2f; p.color = a
                path.reset(); path.moveTo(x - s, y)
                for (i in 1..4) path.lineTo(x - s + i * s / 2, if (i % 2 == 1) y - s * .4f else y + s * .4f)
                c.drawPath(path, p)
            }
            6 -> { p.style = Paint.Style.STROKE; p.strokeWidth = 3 * d; p.color = a; c.drawArc(x - s * .7f, y - s * .7f, x + s * .7f, y + s * .7f, 0f, 180f, false, p) }
            else -> { p.color = a; path.reset(); path.moveTo(x, y - s * .7f); path.lineTo(x + s * .7f, y + s * .5f); path.lineTo(x - s * .7f, y + s * .5f); path.close(); c.drawPath(path, p) }
        }
        p.style = Paint.Style.FILL
        c.restore()
    }
}

/**
 * How much of each color: one segment per color, as wide as its share. Dragging a border trades
 * between its two neighbours; a tap picks a segment.
 */
class ShareBar(c: Context) : View(c) {
    var colors = listOf<Int>(); set(v) { field = v; invalidate() }
    var weights = listOf<Float>(); set(v) { field = v; invalidate() }
    var selected = -1; set(v) { field = v; invalidate() }
    var onChange: (List<Float>) -> Unit = {}
    var onSelect: (Int) -> Unit = {}
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var border = -1
    private var downX = 0f

    private fun edges(): List<Float> {
        val tot = weights.sum().takeIf { it > 0 } ?: 1f
        var acc = 0f
        return weights.map { acc += it; acc / tot * width }
    }

    override fun onDraw(c: Canvas) {
        if (colors.isEmpty() || weights.size != colors.size) return
        val r = height / 2f
        val path = Path().apply { addRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), r, r, Path.Direction.CW) }
        c.save(); c.clipPath(path)
        var x0 = 0f
        for ((i, x1) in edges().withIndex()) {
            p.color = colors[i]; c.drawRect(x0, 0f, x1, height.toFloat(), p)
            if (i == selected) { p.color = onColor(colors[i]); c.drawCircle((x0 + x1) / 2, r, r * .35f, p) }
            x0 = x1
        }
        c.restore()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val es = edges()
        val d = context.d
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                border = (0 until es.lastIndex).minByOrNull { abs(es[it] - e.x) }?.takeIf { abs(es[it] - e.x) < 18 * d } ?: -1
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> if (border >= 0) {
                val tot = weights.sum()
                val left = if (border == 0) 0f else es[border - 1]
                val right = es[border + 1]
                val min = 10 * d
                val x = e.x.coerceIn(left + min, right - min)
                val pair = (weights[border] + weights[border + 1])
                val wl = (x - left) / (right - left) * pair
                weights = weights.toMutableList().also { it[border] = wl; it[border + 1] = pair - wl }.map { it / tot * weights.size }
                onChange(weights)
            }
            MotionEvent.ACTION_UP -> if (border < 0 && abs(e.x - downX) < 12 * d) {
                val i = es.indexOfFirst { e.x <= it }.takeIf { it >= 0 } ?: es.lastIndex
                onSelect(i)
            }
        }
        return true
    }
}

/** A slider whose track shows what it does (black to the color, the hues…); [value] 0..1. */
class GradientSlider(c: Context) : View(c) {
    var colors = intArrayOf(Color.BLACK, Color.WHITE); set(v) { field = v; invalidate() }
    var value = 1f; set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var onChange: (Float) -> Unit = {}
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), (32 * context.d).toInt())

    private val pad get() = 14 * context.d

    override fun onDraw(c: Canvas) {
        val d = context.d
        val y = height / 2f
        p.shader = LinearGradient(pad, 0f, width - pad, 0f, colors, null, Shader.TileMode.CLAMP)
        c.drawRoundRect(pad, y - 2.5f * d, width - pad, y + 2.5f * d, 3 * d, 3 * d, p)
        p.shader = null
        val x = pad + value * (width - 2 * pad)
        p.color = 0x55000000; c.drawCircle(x, y + 1 * d, 11 * d, p)
        p.color = Color.WHITE; c.drawCircle(x, y, 10 * d, p)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent.requestDisallowInterceptTouchEvent(true)
        value = (e.x - pad) / (width - 2 * pad)
        onChange(value)
        return true
    }
}
