package com.iurika.ColorFlows

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Palette(val id: Long, var name: String, var colors: List<Int>, var weights: List<Float> = colors.map { 1f }) {
    fun json(): JSONObject = JSONObject().put("id", id).put("name", name)
        .put("colors", JSONArray(colors)).put("weights", JSONArray(weights.map { it.toDouble() }))

    companion object {
        fun of(o: JSONObject): Palette {
            val cs = o.getJSONArray("colors").let { a -> List(a.length()) { a.getInt(it) } }
            val ws = o.optJSONArray("weights")?.let { a -> List(a.length()) { a.getDouble(it).toFloat() } }?.takeIf { it.size == cs.size }
            return Palette(o.getLong("id"), o.optString("name"), cs, ws ?: cs.map { 1f })
        }
    }
}

/** The saved palettes, newest first, in one JSON file; and the wheel's last state in prefs. */
object Store {
    private lateinit var file: AtomicFile
    private lateinit var ctx: Context
    val palettes = ArrayList<Palette>()

    fun init(c: Context) {
        ctx = c.applicationContext
        file = AtomicFile(File(c.filesDir, "palettes.json"))
        palettes.clear()
        runCatching { JSONArray(String(file.readFully())) }.getOrNull()?.let { a -> for (i in 0 until a.length()) palettes += Palette.of(a.getJSONObject(i)) }
    }

    fun save() {
        val out = file.startWrite()
        runCatching { out.write(JSONArray(palettes.map { it.json() }).toString().toByteArray()); file.finishWrite(out) }.onFailure { file.failWrite(out) }
    }

    fun put(p: Palette) { palettes.removeAll { it.id == p.id }; palettes.add(0, p); save() }

    fun remove(p: Palette) { palettes.removeAll { it.id == p.id }; save() }

    val prefs get() = ctx.getSharedPreferences("state", Context.MODE_PRIVATE)
}
