package com.iurika.colorflows

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Hue, saturation, value as Android's Color.HSVToColor wants them: h 0..360, s and v 0..1. */
data class Hsv(val h: Float, val s: Float, val v: Float) {
    fun color() = Color.HSVToColor(floatArrayOf(((h % 360) + 360) % 360, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))

    companion object {
        fun of(c: Int) = FloatArray(3).also { Color.colorToHSV(c, it) }.let { Hsv(it[0], it[1], it[2]) }
    }
}

/**
 * The painter's wheel (red, yellow, blue primaries) against the screen's (red, green, blue):
 * the same angle points to the "artist" hue, so the complement of red is green, not cyan.
 */
object Ryb {
    // ponytail: piecewise-linear Itten mapping, close enough for picking; a real RYB cube interpolation if someone needs exact pigments
    private val ryb = floatArrayOf(0f, 60f, 120f, 180f, 240f, 300f, 360f)
    private val rgb = floatArrayOf(0f, 35f, 60f, 120f, 240f, 285f, 360f)

    private fun map(h: Float, from: FloatArray, to: FloatArray): Float {
        val x = ((h % 360) + 360) % 360
        val i = (1 until from.size).first { x <= from[it] }
        return to[i - 1] + (x - from[i - 1]) / (from[i] - from[i - 1]) * (to[i] - to[i - 1])
    }

    fun toRgb(h: Float) = map(h, ryb, rgb)
    fun fromRgb(h: Float) = map(h, rgb, ryb)
}

/** A rule placing the other colors from the base one: hue turns, or saturation/value steps for the monochrome one. */
enum class Harmony(val label: String, val turns: List<Float>) {
    COMPLEMENT("Complémentaire", listOf(0f, 180f)),
    MONO("Monochrome", listOf(0f, 0f, 0f)),
    ANALOG("Analogue", listOf(0f, 30f, -30f)),
    ACCENT("Analogue + accent", listOf(0f, 30f, -30f, 180f)),
    SPLIT("Complémentaire divisée", listOf(0f, 150f, 210f)),
    TRIAD("Triade", listOf(0f, 120f, 240f)),
    SQUARE("Carré", listOf(0f, 90f, 180f, 270f)),
    RECT("Rectangle", listOf(0f, 60f, 180f, 240f)),
    COMPOUND("Composée", listOf(0f, 30f, 180f, 210f));

    /** The colors in wheel space (hue = the wheel's own angle), base first. */
    fun points(base: Hsv): List<Hsv> = if (this == MONO) listOf(
        base,
        Hsv(base.h, base.s * .45f, min(1f, base.v * 1.1f + .1f)),
        Hsv(base.h, min(1f, base.s * 1.15f + .1f), base.v * .55f),
    ) else turns.map { base.copy(h = base.h + it) }
}

enum class Wheel { RGB, RYB }

/** Wheel-space point to screen color: the RYB wheel turns its angle into the matching RGB hue first. */
fun Wheel.color(p: Hsv) = if (this == Wheel.RYB) p.copy(h = Ryb.toRgb(p.h)).color() else p.color()

fun Wheel.point(c: Int) = Hsv.of(c).let { if (this == Wheel.RYB) it.copy(h = Ryb.fromRgb(it.h)) else it }

fun hex(c: Int) = "#%06X".format(c and 0xffffff)

/** "#abc", "abc", "#aabbcc", "aabbcc" → color, or null. */
fun parseHex(s: String): Int? {
    var t = s.trim().removePrefix("#")
    if (t.length == 3) t = t.map { "$it$it" }.joinToString("")
    if (t.length != 6) return null
    return t.toIntOrNull(16)?.let { it or 0xff000000.toInt() }
}

fun rgbText(c: Int) = "${Color.red(c)}, ${Color.green(c)}, ${Color.blue(c)}"

/** Dark or light text that stays readable on [c]. */
fun onColor(c: Int) = if (luminance(c) > .5f) 0xff111111.toInt() else Color.WHITE

fun luminance(c: Int) = (.299f * Color.red(c) + .587f * Color.green(c) + .114f * Color.blue(c)) / 255f

fun mix(a: Int, b: Int, t: Float) = Color.rgb(
    (Color.red(a) + (Color.red(b) - Color.red(a)) * t).roundToInt(),
    (Color.green(a) + (Color.green(b) - Color.green(a)) * t).roundToInt(),
    (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).roundToInt(),
)

private fun dist(a: Int, b: Int) = abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b))

/**
 * The main colors of a picture: pixels of a 96 px copy counted in 4-bit-per-channel boxes,
 * each box averaged, the most common first, near-duplicates dropped.
 */
fun extract(src: Bitmap, count: Int = 24): List<Int> {
    val s = 96f / max(src.width, src.height)
    val b = if (s < 1) Bitmap.createScaledBitmap(src, max(1, (src.width * s).toInt()), max(1, (src.height * s).toInt()), true) else src
    val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
    val n = HashMap<Int, IntArray>() // box → [count, r, g, b]
    for (p in px) {
        if (Color.alpha(p) < 128) continue
        val k = (Color.red(p) shr 4 shl 8) or (Color.green(p) shr 4 shl 4) or (Color.blue(p) shr 4)
        val a = n.getOrPut(k) { IntArray(4) }
        a[0]++; a[1] += Color.red(p); a[2] += Color.green(p); a[3] += Color.blue(p)
    }
    val out = ArrayList<Int>()
    for (a in n.values.sortedByDescending { it[0] }) {
        val c = Color.rgb(a[1] / a[0], a[2] / a[0], a[3] / a[0])
        if (out.none { dist(it, c) < 48 }) out += c
        if (out.size == count) break
    }
    return out
}

/** Shadow, midtone and light of [c] as a painter would mix them: the shadow cooler, the light warmer. */
fun shading(c: Int): List<Int> {
    val h = Hsv.of(c)
    // turns toward blue (240) for the shadow and toward yellow (60) for the light, a little
    fun toward(target: Float, by: Float): Float {
        val d = ((target - h.h + 540) % 360) - 180
        return h.h + d * by
    }
    return listOf(
        Hsv(toward(240f, .12f), min(1f, h.s * 1.1f + .05f), h.v * .45f).color(),
        c,
        Hsv(toward(60f, .12f), h.s * .55f, min(1f, h.v * 1.15f + .15f)).color(),
    )
}
