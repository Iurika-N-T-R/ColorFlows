package com.iurika.ColorFlows

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

class MainActivity : Activity() {
    // ---- State, kept in prefs between launches ----
    private var tab = 0 // 0 RVB, 1 RYB, 2 IMAGE, 3 SPHÈRE
    private var harmony = Harmony.COMPLEMENT
    private val bases = arrayOf(Hsv(140f, .75f, 1f), Hsv(20f, 1f, 1f)) // the RGB wheel's, the RYB wheel's
    private val monos = arrayOf(monoOf(bases[0]), monoOf(bases[1])) // their monochrome's other two colors
    private val slots = arrayOf(ArrayList<Int>(), ArrayList<Int>()) // colors gathered in IMAGE, in SPHÈRE
    private var picked = -1 // the slot the next color replaces
    private val tones = shading(0xffd2643c.toInt()).toMutableList() // the sphere's shadow, midtone, light
    private var tone = 1 // the one its sliders change
    private var image: Bitmap? = null
    private var found = listOf<Int>() // the picture's main colors
    private var byName = false

    private lateinit var root: FrameLayout
    private val screens = ArrayList<() -> View>()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        load()
        root = FrameLayout(this)
        // drawn under the bars on every version, padded by them, so it looks the same everywhere
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        else @Suppress("DEPRECATION") { root.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION }
        root.setOnApplyWindowInsetsListener { v, ins ->
            @Suppress("DEPRECATION") v.setPadding(ins.systemWindowInsetLeft, ins.systemWindowInsetTop, ins.systemWindowInsetRight, ins.systemWindowInsetBottom)
            ins
        }
        root.setBackgroundColor(BG)
        setContentView(root)
        open { home() }
        intent?.let(::shared)
    }

    override fun onNewIntent(i: Intent) { super.onNewIntent(i); shared(i) }

    /** An image shared to the app opens in the IMAGE tab. */
    private fun shared(i: Intent) {
        if (i.action != Intent.ACTION_SEND) return
        @Suppress("DEPRECATION") val uri = i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return
        screens.clear(); tab = 2; setImage(uri); open { home() }
    }

    override fun onPause() { super.onPause(); save() }

    private fun load() {
        val p = Store.prefs
        tab = p.getInt("tab", 0)
        harmony = Harmony.entries.getOrElse(p.getInt("harmony", 0)) { Harmony.COMPLEMENT }
        fun hsv(s: String?) = s?.split(',')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 3 }?.let { Hsv(it[0], it[1], it[2]) }
        for (i in 0..1) {
            hsv(p.getString("base$i", null))?.let { bases[i] = it }
            monos[i] = p.getString("mono$i", null)?.split(';')?.mapNotNull(::hsv)?.takeIf { it.size == 2 } ?: monoOf(bases[i])
        }
        for (i in 0..1) p.getString("slots$i", null)?.let { s -> runCatching { JSONArray(s) }.getOrNull()?.let { a -> slots[i].clear(); for (k in 0 until a.length()) slots[i] += a.getInt(k) } }
        p.getString("tones", null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 3 }?.forEachIndexed { i, c -> tones[i] = c }
        byName = p.getBoolean("byName", false)
        if (tab == 2) tab = 0 // the picture isn't kept
    }

    private fun save() = Store.prefs.edit().putInt("tab", tab).putInt("harmony", harmony.ordinal)
        .apply { for (i in 0..1) { putString("base$i", bases[i].let { "${it.h},${it.s},${it.v}" }); putString("slots$i", JSONArray(slots[i]).toString())
            putString("mono$i", monos[i].joinToString(";") { "${it.h},${it.s},${it.v}" }) } }
        .putString("tones", tones.joinToString(",")).putBoolean("byName", byName).apply()

    // ---- Navigation: a stack of screens, back pops ----

    private fun open(build: () -> View) { screens += build; show() }

    private fun show() { val build: () -> View = screens[screens.lastIndex]; root.removeAllViews(); root.addView(build()) }

    private fun refresh() { if (screens.isNotEmpty()) show() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (screens.size > 1) { screens.removeAt(screens.lastIndex); show() } else @Suppress("DEPRECATION") super.onBackPressed()
    }

    private fun back() = @Suppress("DEPRECATION") onBackPressed()

    // ---- Home ----

    private val wheel get() = if (tab == 1) Wheel.RYB else Wheel.RGB

    /** The colors on screen: the harmony's on the wheels, the gathered ones in IMAGE and SPHÈRE. */
    private fun colors(): List<Int> = when (tab) {
        0, 1 -> harmony.points(bases[tab], monos[tab]).map { wheel.color(it) }
        2 -> slots[0]
        else -> slots[1]
    }

    private fun topBar(title: String, onBack: (() -> Unit)?, vararg actions: View) = hbox().apply {
        setPadding(dp(4), dp(6), dp(4), dp(6)); minimumHeight = dp(60)
        if (onBack != null) addView(iconButton(R.drawable.ic_back, "Retour") { onBack() })
        addView(tv(title, 21f, bold = true).lp(0, -2, 1f, l = if (onBack == null) 14 else 6))
        actions.forEach(::addView)
    }

    private fun home(): View {
        val buttons = mutableListOf<TextView>()
        val swatches = hbox().apply { gravity = Gravity.CENTER }
        val updates = ArrayList<() -> Unit>() // what follows the colors as they change
        fun changed() {
            val cs = colors()
            swatchRow(swatches, cs)
            buttons.forEach { it.outline(cs); it.isEnabled = cs.isNotEmpty(); it.alpha = if (cs.isEmpty()) .35f else 1f }
            updates.forEach { it() }
        }

        val tabs = hbox().apply {
            background = rounded(SURF, 12); setPadding(dp(3), dp(3), dp(3), dp(3))
            listOf("RVB", "RYB", "IMAGE", "SPHÈRE").forEachIndexed { i, s ->
                addView(tv(s, 13f, if (i == tab) TEXT else MU, bold = true).apply {
                    gravity = Gravity.CENTER; setPadding(0, dp(9), 0, dp(9))
                    if (i == tab) background = rounded(SURF2, 10)
                    setOnClickListener { if (tab != i) { tab = i; picked = -1; refresh() } }
                }.lp(0, -2, 1f))
            }
        }

        val body = vbox(tabs.lp(-1, -2, l = 16, t = 4, r = 16, b = 12))
        when (tab) {
            0, 1 -> wheelTab(body, ::changed, updates)
            2 -> imageTab(body, ::changed, updates)
            else -> sphereTab(body, ::changed, updates)
        }
        // IMAGE and SPHÈRE show their colors in the slots already
        if (tab < 2) body.addView(swatches.lp(-1, -2, t = 16, b = 8))

        val edit = bigButton("Éditer") { colors().takeIf { it.isNotEmpty() }?.let { editor(null, it) } }
        val keep = bigButton("Enregistrer") { colors().takeIf { it.isNotEmpty() }?.let { cs -> askName("") { n -> Store.put(Palette(System.currentTimeMillis(), n, cs)); toast("Palette enregistrée") } } }
        buttons += edit; buttons += keep
        changed()

        val more = iconButton(R.drawable.ic_more, "Plus") {}
        more.setOnClickListener { menu(more) }
        val palettes = hbox(
            ImageView(this).apply { setImageResource(R.drawable.ic_swatches) }.lp(dp(20), dp(20)),
            tv("Palettes", 14f, bold = true).lp(l = 8),
        ).apply { setPadding(dp(12), dp(8), dp(14), dp(8)); background = ripple(18); setOnClickListener { open { palettes() } } }

        return vbox(
            topBar("ColorFlows", null, palettes, more),
            ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(body) }.lp(-1, 0, 1f),
            hbox(edit.lp(0, -2, 1f, r = 6), keep.lp(0, -2, 1f, l = 6)).apply { setPadding(dp(16), dp(8), dp(16), dp(12)) },
        )
    }

    private fun menu(anchor: View) = PopupMenu(this, anchor).apply {
        menu.add("Copier les HEX"); menu.add("Partager la palette"); menu.add("Couleur au hasard"); menu.add("À propos")
        setOnMenuItemClickListener {
            val cs = colors()
            when (it.title) {
                "Copier les HEX" -> copy(cs.joinToString(" ") { c -> hex(c) })
                "Partager la palette" -> share(cs.joinToString("\n") { c -> hex(c) })
                "Couleur au hasard" -> {
                    if (tab > 1) tab = 0
                    bases[tab] = Hsv(Random.nextFloat() * 360, .45f + Random.nextFloat() * .5f, if (tab == 0) .75f + Random.nextFloat() * .25f else bases[tab].v)
                    refresh()
                }
                else -> AlertDialog.Builder(this@MainActivity, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("ColorFlows ${packageManager.getPackageInfo(packageName, 0).versionName}")
                    .setMessage("Harmonies de couleurs, palettes d'images et ombrages.\nLibre et open source (MIT).\ngithub.com/Iurika-N-T-R/ColorFlows")
                    .setPositiveButton("OK", null).show()
            }
            true
        }
        show()
    }

    /** One square per color with its hex under it; a tap shows its values. */
    private fun swatchRow(row: LinearLayout, cs: List<Int>, onTap: (Int) -> Unit = { info(cs[it]) }) {
        row.removeAllViews()
        val size = if (cs.size > 4) 48 else 60
        cs.forEachIndexed { i, c ->
            row.addView(vbox(
                View(this).apply { background = rounded(c, 16) }.lp(dp(size), dp(size)),
                tv(hex(c), 10f, bold = true).apply { background = rounded(SURF, 8); setPadding(dp(6), dp(2), dp(6), dp(2)); gravity = Gravity.CENTER }.lp(-2, -2, t = 6),
            ).apply { gravity = Gravity.CENTER_HORIZONTAL; setOnClickListener { onTap(i) } }.lp(-2, -2, l = 5, r = 5))
        }
    }

    private fun wheelTab(body: LinearLayout, changed: () -> Unit, updates: MutableList<() -> Unit>) {
        val w = WheelView(this).apply { wheel = this@MainActivity.wheel; harmony = this@MainActivity.harmony; base = bases[tab]; mono = monos[tab] }
        val icons = Harmony.entries.map { h -> HarmonyIcon(this, h).apply { on = h == this@MainActivity.harmony; contentDescription = h.label } }
        val name = tv(harmony.label, 13f, MU).apply { gravity = Gravity.CENTER }
        val slider = GradientSlider(this)
        val ryb = tab == 1
        val isMono = { harmony == Harmony.MONO }
        // the color the slider and the pipette work on: the base, or in monochrome the marker touched last
        fun cur() = w.points().getOrElse(w.selected) { bases[tab] }
        fun put(p: Hsv) {
            if (w.selected == 0) bases[tab] = p else monos[tab] = monos[tab].toMutableList().also { it[w.selected - 1] = p }
            w.base = bases[tab]; w.mono = monos[tab]
        }
        // RYB: left darkens toward black, right lightens toward white, the pure hue in the middle.
        // Monochrome sets the saturation by the marker's place, so its slider is the value alone.
        val shades = { ryb && !isMono() }
        fun pos(b: Hsv) = if (!shades()) b.v else if (b.v < 1f) (b.v - .15f) / .85f * .5f else .5f + (1 - b.s) / .85f * .5f
        fun from(x: Float, b: Hsv) = if (!shades()) b.copy(v = x) else if (x < .5f) b.copy(s = 1f, v = .15f + .85f * x / .5f) else b.copy(s = 1f - .85f * (x - .5f) / .5f, v = 1f)
        fun track() {
            val b = cur()
            slider.colors = if (shades()) intArrayOf(Color.BLACK, wheel.color(Hsv(b.h, 1f, 1f)), Color.WHITE) else intArrayOf(Color.BLACK, wheel.color(b.copy(v = 1f)))
            slider.value = pos(b)
        }
        updates += ::track
        slider.onChange = { x -> put(from(x, cur())); changed() }
        w.onChange = { bases[tab] = w.base; monos[tab] = w.mono; changed() }
        icons.forEach { ic ->
            ic.setOnClickListener {
                harmony = ic.harmony; w.harmony = harmony; w.selected = 0; name.text = harmony.label
                icons.forEach { it.on = it === ic }; changed()
            }
        }
        body.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(hbox(*icons.map { it.lp(dp(46), dp(46), l = 4, r = 4) }.toTypedArray()).apply { setPadding(dp(12), 0, dp(12), 0) })
        })
        body.addView(name.lp(-1, -2, t = 6))

        body.addView(hbox(
            iconButton(R.drawable.ic_swap, "Couleur suivante") {
                val pts = w.points()
                if (isMono()) { w.selected = (w.selected + 1) % pts.size; changed() }
                // the next color of the harmony becomes the one moved by the finger
                else if (pts.size > 1) { bases[tab] = pts[1].let { it.copy(h = ((it.h % 360) + 360) % 360) }; w.base = bases[tab]; changed() }
            },
            View(this).lp(0, 1, 1f),
            iconButton(R.drawable.ic_pipette, "Saisir une couleur") {
                hexDialog(wheel.color(cur())) { c ->
                    val p = wheel.point(c)
                    // in monochrome a typed color sets the hue of all three
                    if (isMono()) { bases[tab] = bases[tab].copy(h = p.h); w.base = bases[tab] }
                    put(p); changed()
                }
            },
        ).apply { setPadding(dp(12), dp(8), dp(12), 0) })
        body.addView(w.lp(-1, -2, l = 12, r = 12))
        if (ryb) body.addView(hbox(
            ImageView(this).apply { setImageResource(R.drawable.ic_drop); setColorFilter(MU) }.lp(dp(16), dp(16)),
            slider.lp(0, -2, 1f),
            ImageView(this).apply { setImageResource(R.drawable.ic_drop); setColorFilter(TEXT) }.lp(dp(16), dp(16)),
        ).lp(-1, -2, l = 20, t = 10, r = 20))
        else body.addView(slider.lp(-1, -2, l = 20, t = 10, r = 20))
    }

    /**
     * IMAGE and SPHÈRE gather colors in slots, as many as wanted: rows of 6, the next empty one dashed.
     * A filled slot tapped is the one the next color replaces.
     */
    private fun slotRow(list: ArrayList<Int>, changed: () -> Unit): LinearLayout {
        val box = vbox()
        fun fill() {
            box.removeAllViews()
            (0..list.size).chunked(6).forEach { idx ->
                val row = hbox()
                for (k in 0 until 6) {
                    val i = idx.getOrNull(k)
                    val c = i?.let { list.getOrNull(it) }
                    row.addView(tv(c?.let(::hex)?.drop(1) ?: if (i != null) "+" else "", if (c == null) 16f else 9f, c?.let(::onColor) ?: MU, bold = true).apply {
                        gravity = Gravity.CENTER
                        if (i != null) background = if (c == null) GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setStroke(dp(1), MU, dp(4).toFloat(), dp(3).toFloat()) }
                        else rounded(c, 12, if (i == picked) 3 else 0, TEXT)
                        if (c != null) {
                            setOnClickListener { picked = if (picked == i) -1 else i!!; fill() }
                            setOnLongClickListener { info(c); true }
                        }
                    }.lp(0, dp(46), 1f, l = 4, t = 4, r = 4, b = 4))
                }
                box.addView(row)
            }
        }
        box.tag = Runnable(::fill)
        fill()
        return box
    }

    private fun gather(list: ArrayList<Int>, c: Int, row: LinearLayout, changed: () -> Unit) {
        if (picked in list.indices) { list[picked] = c; picked = -1 } else list += c
        (row.tag as Runnable).run(); changed()
    }

    /** Empties the slot picked, else the last one. */
    private fun unslot(list: ArrayList<Int>, row: LinearLayout, changed: () -> Unit) {
        if (list.isEmpty()) return
        list.removeAt(if (picked in list.indices) picked else list.lastIndex); picked = -1
        (row.tag as Runnable).run(); changed()
    }

    private val pickImage = 1

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(req, res, data)
        if (req == pickImage && res == RESULT_OK) data?.data?.let { setImage(it); refresh() }
    }

    private fun setImage(uri: Uri) {
        // about 1600 px on the long side: plenty to point at, light on memory
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) } }
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= 1600) s *= 2
        image = runCatching { contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) } }.getOrNull()
        if (image == null) { toast("Image illisible"); return }
        found = extract(image!!)
    }

    private fun chooseImage() {
        @Suppress("DEPRECATION") startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), pickImage)
    }

    private fun imageTab(body: LinearLayout, changed: () -> Unit, updates: MutableList<() -> Unit>) {
        val list = slots[0]
        val slotsRow = slotRow(list, changed)
        val img = image
        if (img == null) {
            body.addView(vbox(
                ImageView(this).apply { setImageResource(R.drawable.ic_image); setColorFilter(MU) }.lp(dp(40), dp(40)),
                tv("Choisir une image", 16f, bold = true).lp(-2, -2, t = 12),
                tv("Touche l'image pour prendre une couleur, ses couleurs principales s'affichent dessous", 13f, MU).apply { gravity = Gravity.CENTER }.lp(-2, -2, t = 6),
            ).apply {
                gravity = Gravity.CENTER; setPadding(dp(24), dp(48), dp(24), dp(48))
                background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(SURF); setStroke(dp(1), SURF2, dp(6).toFloat(), dp(4).toFloat()) }
                foreground = ripple(18); setOnClickListener { chooseImage() }
            }.lp(-1, -2, l = 16, t = 8, r = 16))
        } else {
            val view = PickImageView(this).apply { bitmap = img }
            view.onPick = { c, done -> if (done) gather(list, c, slotsRow, changed) }
            body.addView(FrameLayout(this).apply {
                addView(view, FrameLayout.LayoutParams(-1, dp(320)))
                addView(iconButton(R.drawable.ic_close, "Fermer l'image") { image = null; found = emptyList(); refresh() }.apply { background = rounded(0x99000000.toInt(), 22) },
                    FrameLayout.LayoutParams(dp(44), dp(44), Gravity.END).apply { setMargins(0, dp(4), dp(4), 0) })
            }.lp(-1, -2, l = 16, r = 16))
            body.addView(hbox(
                iconButton(R.drawable.ic_delete, "Retirer la couleur") { unslot(list, slotsRow, changed) },
                iconButton(R.drawable.ic_shuffle, "Palette de l'image") {
                    // the picture's main colors, spread: one every few
                    list.clear(); list += found.take(6); picked = -1
                    (slotsRow.tag as Runnable).run(); changed()
                },
                View(this).lp(0, 1, 1f),
                iconButton(R.drawable.ic_image, "Autre image") { chooseImage() },
            ).apply { setPadding(dp(8), dp(6), dp(8), 0) })
            body.addView(grid(found) { gather(list, it, slotsRow, changed) }.lp(-1, -2, l = 16, r = 16))
        }
        body.addView(slotsRow.lp(-1, -2, l = 12, t = 14, r = 12))
        body.addView(tv("Touche une case pour la remplacer, appui long pour ses valeurs", 12f, MU).apply { gravity = Gravity.CENTER }.lp(-1, -2, t = 8))
    }

    /** Colors in rows of 8, a tap uses one. */
    private fun grid(cs: List<Int>, onTap: (Int) -> Unit) = vbox().apply {
        cs.chunked(8).forEach { row ->
            addView(hbox().apply {
                for (i in 0 until 8) addView((row.getOrNull(i)?.let { c ->
                    View(this@MainActivity).apply { background = rounded(c, 8, 1, 0x22ffffff); setOnClickListener { onTap(c) } }
                } ?: View(this@MainActivity)).lp(0, dp(34), 1f, l = 3, t = 3, r = 3, b = 3))
            })
        }
    }

    /**
     * The painter's sphere: shadow, midtone and light are three colors of their own; the one tapped is
     * changed with the sliders, and the ball is painted with all three. Touching the ball picks a shade,
     * "Ajouter" keeps it.
     */
    private fun sphereTab(body: LinearLayout, changed: () -> Unit, updates: MutableList<() -> Unit>) {
        val list = slots[1]
        val slotsRow = slotRow(list, changed)
        val ball = SphereView(this).apply { tones = this@MainActivity.tones.toList() }
        // before / now: the last kept color, the one under the finger
        val before = View(this)
        val now = View(this)
        fun bar() {
            before.background = rounded(list.lastOrNull() ?: ball.picked, 0)
            now.background = rounded(ball.picked, 0)
        }
        bar()
        ball.onPick = { bar() }
        val trio = hbox().apply { gravity = Gravity.CENTER }
        val hue = GradientSlider(this).apply { colors = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)) } }
        val sat = GradientSlider(this)
        val vel = GradientSlider(this)
        // the hue kept apart: a grey has none, and turning the saturation back up must find it again
        var hh = Hsv.of(tones[tone]).h
        fun tracks() {
            val k = Hsv.of(tones[tone])
            hue.value = hh / 360
            sat.colors = intArrayOf(Hsv(hh, 0f, k.v).color(), Hsv(hh, 1f, k.v).color()); sat.value = k.s
            vel.colors = intArrayOf(Color.BLACK, Hsv(hh, k.s, 1f).color()); vel.value = k.v
        }
        fun fillTrio() {
            trio.removeAllViews()
            listOf(R.drawable.ic_moon to "Ombre", R.drawable.ic_drop to "Ton moyen", R.drawable.ic_sun to "Lumière").forEachIndexed { i, (icon, n) ->
                val on = i == tone
                trio.addView(hbox(
                    ImageView(this).apply { setImageResource(icon); setColorFilter(if (on) TEXT else MU); contentDescription = n }.lp(dp(18), dp(18)),
                    View(this).apply { background = rounded(tones[i], 14, if (on) 3 else 1, if (on) TEXT else 0x44ffffff) }.lp(dp(if (on) 30 else 24), dp(if (on) 30 else 24), l = 8),
                ).apply {
                    gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(8)); background = ripple(14)
                    setOnClickListener { tone = i; hh = Hsv.of(tones[i]).h; fillTrio(); tracks() }
                    setOnLongClickListener { gather(list, tones[i], slotsRow, changed); bar(); true }
                }.lp(0, -2, 1f))
            }
        }
        fun set(k: Hsv) { tones[tone] = k.color(); ball.tones = tones.toList(); fillTrio(); tracks(); bar() }
        hue.onChange = { hh = it * 360; set(Hsv(hh, sat.value, vel.value)) }
        sat.onChange = { set(Hsv(hh, it, vel.value)) }
        vel.onChange = { set(Hsv(hh, sat.value, it)) }
        fillTrio(); tracks()

        body.addView(hbox(
            iconButton(R.drawable.ic_delete, "Retirer la couleur") { unslot(list, slotsRow, changed); bar() },
            hbox(before.lp(0, -1, 1f), now.lp(0, -1, 1f)).apply { background = rounded(SURF2, 8); clipToOutline = true }.lp(0, dp(28), 1f, l = 8, r = 8),
            iconButton(R.drawable.ic_pipette, "Saisir le ton moyen") {
                // a typed color becomes the midtone, its shadow and light worked out from it
                hexDialog(tones[1]) { c -> shading(c).forEachIndexed { i, t -> tones[i] = t }; ball.tones = tones.toList(); hh = Hsv.of(tones[tone]).h; fillTrio(); tracks(); bar() }
            },
        ).apply { setPadding(dp(12), 0, dp(12), dp(8)) })
        body.addView(ball.lp(dp(260), -2).apply { (layoutParams as LinearLayout.LayoutParams).gravity = Gravity.CENTER_HORIZONTAL })
        body.addView(hbox(
            tv("Auto", 13f, MU, bold = true).apply {
                setPadding(dp(14), dp(8), dp(14), dp(8)); background = rounded(SURF, 16); contentDescription = "Ombre et lumière depuis le ton moyen"
                setOnClickListener { shading(tones[1]).forEachIndexed { i, t -> tones[i] = t }; ball.tones = tones.toList(); hh = Hsv.of(tones[tone]).h; fillTrio(); tracks(); bar() }
            },
            View(this).lp(0, 1, 1f),
            hbox(ImageView(this).apply { setImageResource(R.drawable.ic_plus) }.lp(dp(18), dp(18)), tv("Ajouter", 14f, bold = true).lp(l = 6)).apply {
                setPadding(dp(14), dp(8), dp(16), dp(8)); background = rounded(SURF2, 16); foreground = ripple(16)
                setOnClickListener { gather(list, ball.picked, slotsRow, changed); bar() }
            },
        ).lp(-1, -2, l = 16, t = 8, r = 16))
        body.addView(trio.lp(-1, -2, l = 8, t = 6, r = 8))
        body.addView(vbox(hue, sat, vel).apply { background = rounded(SURF, 14); setPadding(dp(6), dp(6), dp(6), dp(6)) }.lp(-1, -2, l = 16, t = 6, r = 16))
        body.addView(slotsRow.lp(-1, -2, l = 12, t = 14, r = 12))
        body.addView(tv("Touche un ton pour le régler, appui long pour l'ajouter.\nTouche la sphère pour choisir une nuance.", 12f, MU).apply { gravity = Gravity.CENTER }.lp(-1, -2, t = 8))
    }

    // ---- "Modifier les couleurs": the palette tried on patterns ----

    private fun editor(saved: Palette?, start: List<Int>, startWeights: List<Float>? = null) {
        val cs = start.toMutableList()
        var ws = (startWeights ?: saved?.weights)?.takeIf { it.size == cs.size } ?: cs.map { 1f }
        var seed = 1
        var page = 0
        var sel = 0
        open {
            val pages = Pattern.entries.map { PatternView(this, it) }
            val frame = FrameLayout(this)
            val dots = hbox().apply { gravity = Gravity.CENTER }
            val bar = ShareBar(this)
            val swatches = hbox().apply { gravity = Gravity.CENTER }
            val reset = bigButton("Réinitialiser") {}
            val keep = bigButton("Enregistrer") {}
            val expand = iconButton(R.drawable.ic_expand, "Plein écran") { fullscreen(cs[sel]) }.apply { background = rounded(0x55000000, 10) }

            fun sync() {
                pages.forEach { it.colors = cs.toList(); it.weights = ws; it.seed = seed; it.selected = sel }
                bar.colors = cs.toList(); bar.weights = ws
                bar.selected = if (page == Pattern.FLAT.ordinal) sel else -1
                bar.visibility = if (page == Pattern.WAVES.ordinal || page == Pattern.GRADIENT.ordinal) View.INVISIBLE else View.VISIBLE
                expand.visibility = if (page == Pattern.FLAT.ordinal) View.VISIBLE else View.GONE
                val dirty = cs != start || ws != (startWeights ?: saved?.weights ?: start.map { 1f })
                reset.isEnabled = dirty; reset.alpha = if (dirty) 1f else .35f
                reset.outline(cs); keep.outline(cs)
                swatchRow(swatches, cs) { i -> hexDialog(cs[i]) { c -> cs[i] = c; sync() } }
                // the pipette over each square: they are tapped to change
                for (i in 0 until swatches.childCount) ((swatches.getChildAt(i) as LinearLayout).getChildAt(0)).foreground =
                    getDrawable(R.drawable.ic_pipette)!!.mutate().apply { setTint(onColor(cs[i])); alpha = 170 }.let { d -> android.graphics.drawable.InsetDrawable(d, dp(16)) }
                dots.removeAllViews()
                for (i in pages.indices) dots.addView(View(this).apply { background = rounded(if (i == page) TEXT else 0xff5a5a60.toInt(), 4) }.lp(dp(if (i == page) 8 else 6), dp(if (i == page) 8 else 6), l = 5, r = 5))
            }
            fun go(p: Int) {
                page = p.coerceIn(0, pages.lastIndex)
                frame.removeAllViews()
                frame.addView(pages[page], FrameLayout.LayoutParams(-1, -1))
                frame.addView(expand, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.END).apply { setMargins(0, dp(8), dp(8), 0) })
                sync()
            }
            bar.onChange = { ws = it; pages.forEach { p -> p.weights = ws } }
            bar.onSelect = { sel = it; if (page != Pattern.FLAT.ordinal) go(Pattern.FLAT.ordinal) else sync() }
            reset.setOnClickListener { cs.clear(); cs += start; ws = (startWeights ?: saved?.weights)?.takeIf { it.size == cs.size } ?: cs.map { 1f }; sync() }
            keep.setOnClickListener {
                if (saved != null) { saved.colors = cs.toList(); saved.weights = ws; Store.put(saved); toast("Palette mise à jour") }
                else askName("") { n -> Store.put(Palette(System.currentTimeMillis(), n, cs.toList(), ws)); toast("Palette enregistrée") }
            }
            // swipes between the patterns
            val swipe = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent) = true
                override fun onFling(a: MotionEvent?, b: MotionEvent, vx: Float, vy: Float): Boolean {
                    if (abs(vx) < abs(vy)) return false
                    go(if (vx < 0) page + 1 else page - 1); return true
                }
            })
            frame.setOnTouchListener { _, e -> swipe.onTouchEvent(e) }
            go(page)

            vbox(
                topBar("Modifier les couleurs", ::back,
                    iconButton(R.drawable.ic_list, "Valeurs") { values(cs) },
                    iconButton(R.drawable.ic_swatches, "Palettes") { open { palettes() } },
                    iconButton(R.drawable.ic_dice, "Mélanger") {
                        seed = Random.nextInt(1, 1 shl 20)
                        if (page == Pattern.FLAT.ordinal) cs.shuffle()
                        sync()
                    }),
                // many colors scroll sideways
                HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; isFillViewport = true; addView(swatches.apply { setPadding(dp(11), 0, dp(11), 0) }) }.lp(-1, -2, t = 4, b = 14),
                frame.apply { background = rounded(SURF, 4); clipToOutline = true }.lp(-1, 0, 1f, l = 16, r = 16),
                bar.lp(-1, dp(22), l = 16, t = 12, r = 16),
                dots.lp(-1, -2, t = 12),
                hbox(reset.lp(0, -2, 1f, r = 6), keep.lp(0, -2, 1f, l = 6)).apply { setPadding(dp(16), dp(12), dp(16), dp(12)) },
            )
        }
    }

    private fun fullscreen(c: Int) = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
        setContentView(View(this@MainActivity).apply { setBackgroundColor(c); setOnClickListener { dismiss() } })
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        show()
    }

    // ---- Palettes ----

    private fun palettes(): View {
        val list = vbox().apply { setPadding(dp(12), 0, dp(12), dp(16)) }
        val all = if (byName) Store.palettes.sortedBy { it.name.lowercase() } else Store.palettes
        if (all.isEmpty()) list.addView(tv("Aucune palette.\nEnregistre-en une depuis la roue, une image ou la sphère.", 14f, MU).apply { gravity = Gravity.CENTER }.lp(-1, -2, t = 80))
        all.forEach { p ->
            val circles = hbox()
            p.colors.forEach { c -> circles.addView(View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c); setStroke(dp(1), 0x33ffffff) } }.lp(dp(34), dp(34), r = 8)) }
            val card = vbox(
                tv(p.name.ifEmpty { "Sans nom" }, 15f, bold = true),
                HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(circles) }.lp(-1, -2, t = 10),
            ).apply {
                setPadding(dp(18), dp(14), dp(18), dp(16))
                background = Outline(p.colors.toIntArray(), dp(16).toFloat(), dp(1.2f)); foreground = ripple(16)
                setOnClickListener { editor(p, p.colors) }
            }
            card.setOnLongClickListener { paletteMenu(card, p); true }
            list.addView(card.lp(-1, -2, b = 10))
        }
        return vbox(
            topBar("Palettes", ::back, iconButton(R.drawable.ic_sort, "Trier") {
                byName = !byName; toast(if (byName) "Triées par nom" else "Les plus récentes d'abord"); refresh()
            }),
            ScrollView(this).apply { addView(list) }.lp(-1, 0, 1f),
        )
    }

    private fun paletteMenu(anchor: View, p: Palette) = PopupMenu(this, anchor).apply {
        listOf("Renommer", "Copier les HEX", "Partager", "Supprimer").forEach { menu.add(it) }
        setOnMenuItemClickListener {
            when (it.title) {
                "Renommer" -> askName(p.name) { n -> p.name = n; Store.put(p); refresh() }
                "Copier les HEX" -> copy(p.colors.joinToString(" ") { c -> hex(c) })
                "Partager" -> share("${p.name}\n" + p.colors.joinToString("\n") { c -> hex(c) })
                else -> AlertDialog.Builder(this@MainActivity, android.R.style.Theme_Material_Dialog_Alert)
                    .setMessage("Supprimer « ${p.name.ifEmpty { "Sans nom" }} » ?")
                    .setPositiveButton("Supprimer") { _, _ -> Store.remove(p); refresh() }
                    .setNegativeButton("Annuler", null).show()
            }
            true
        }
        show()
    }

    // ---- Dialogs ----

    private fun dialog(view: View, title: String? = null): AlertDialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
        .apply { if (title != null) setTitle(title) }.setView(view).create()

    private fun field(hint: String, text: String, digits: Boolean = false) = EditText(this).apply {
        this.hint = hint; setText(text); setSingleLine(); setTextColor(TEXT); setHintTextColor(MU)
        if (digits) { inputType = InputType.TYPE_CLASS_NUMBER; filters = arrayOf(InputFilter.LengthFilter(3)) }
        setSelectAllOnFocus(true)
    }

    private fun askName(current: String, onName: (String) -> Unit) {
        val f = field("Nom de la palette", current)
        val d = dialog(vbox(f.lp(-1, -2)).apply { setPadding(dp(20), dp(8), dp(20), 0) }, "Nom")
        d.setButton(AlertDialog.BUTTON_POSITIVE, "OK") { _, _ -> onName(f.text.toString().trim()) }
        d.setButton(AlertDialog.BUTTON_NEGATIVE, "Annuler") { _, _ -> }
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        d.show(); f.requestFocus()
    }

    /** Type a color: HEX, or red/green/blue 0-255. */
    private fun hexDialog(start: Int, onApply: (Int) -> Unit) {
        var rgb = false
        val hexF = field("HEX", hex(start))
        val r = field("R", Color.red(start).toString(), true)
        val g = field("V", Color.green(start).toString(), true)
        val b = field("B", Color.blue(start).toString(), true)
        val rgbRow = hbox(r.lp(0, -2, 1f), g.lp(0, -2, 1f, l = 8), b.lp(0, -2, 1f, l = 8)).apply { visibility = View.GONE }
        val preview = View(this).apply { background = rounded(start, 10) }
        fun value(): Int? = if (rgb) listOf(r, g, b).map { it.text.toString().toIntOrNull()?.coerceIn(0, 255) ?: return null }.let { Color.rgb(it[0], it[1], it[2]) } else parseHex(hexF.text.toString())
        val watcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { value()?.let { (preview.background as GradientDrawable).setColor(it) } }
            override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
        }
        listOf(hexF, r, g, b).forEach { it.addTextChangedListener(watcher) }
        val seg = hbox().apply { background = rounded(SURF2, 10); setPadding(dp(3), dp(3), dp(3), dp(3)) }
        fun segs() {
            seg.removeAllViews()
            listOf("HEX", "RVB").forEachIndexed { i, s ->
                seg.addView(tv(s, 13f, if ((i == 1) == rgb) TEXT else MU, bold = true).apply {
                    gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(8)); if ((i == 1) == rgb) background = rounded(0xff45454c.toInt(), 8)
                    setOnClickListener {
                        // carries the color over to the other way of typing it
                        value()?.let { c -> hexF.setText(hex(c)); r.setText("${Color.red(c)}"); g.setText("${Color.green(c)}"); b.setText("${Color.blue(c)}") }
                        rgb = i == 1; hexF.visibility = if (rgb) View.GONE else View.VISIBLE; rgbRow.visibility = if (rgb) View.VISIBLE else View.GONE; segs()
                    }
                }.lp(0, -2, 1f))
            }
        }
        segs()
        val d = dialog(vbox(hbox(seg.lp(0, -2, 1f), preview.lp(dp(40), dp(40), l = 12)), hexF.lp(-1, -2, t = 8), rgbRow.lp(-1, -2, t = 8)).apply { setPadding(dp(20), dp(16), dp(20), 0) })
        d.setButton(AlertDialog.BUTTON_POSITIVE, "Appliquer") { _, _ -> value()?.let(onApply) ?: toast("Couleur invalide") }
        d.setButton(AlertDialog.BUTTON_NEGATIVE, "Annuler") { _, _ -> }
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        d.show(); hexF.requestFocus()
    }

    /** A color's values, each copied with a tap. */
    private fun info(c: Int) {
        val h = Hsv.of(c)
        val rows = listOf(
            "HEX" to hex(c),
            "RVB" to rgbText(c),
            "TSV" to "${h.h.roundToInt()}°, ${(h.s * 100).roundToInt()} %, ${(h.v * 100).roundToInt()} %",
        )
        val v = vbox(View(this).apply { background = rounded(c, 14) }.lp(-1, dp(84), b = 8)).apply { setPadding(dp(20), dp(20), dp(20), 0) }
        rows.forEach { (k, s) ->
            v.addView(hbox(tv(k, 13f, MU).lp(dp(48), -2), tv(s, 16f, bold = true).lp(0, -2, 1f), ImageView(this).apply { setImageResource(R.drawable.ic_copy); setColorFilter(MU) }.lp(dp(18), dp(18))).apply {
                setPadding(dp(4), dp(12), dp(4), dp(12)); background = ripple(10); setOnClickListener { copy(s) }
            })
        }
        dialog(v).apply { setButton(AlertDialog.BUTTON_POSITIVE, "Fermer") { _, _ -> } }.show()
    }

    private fun values(cs: List<Int>) {
        val v = vbox().apply { setPadding(dp(20), dp(12), dp(20), 0) }
        cs.forEach { c ->
            v.addView(hbox(View(this).apply { background = rounded(c, 8) }.lp(dp(32), dp(32)), vbox(tv(hex(c), 15f, bold = true), tv("RVB ${rgbText(c)}", 12f, MU)).lp(0, -2, 1f, l = 12)).apply {
                setPadding(0, dp(8), 0, dp(8)); background = ripple(8); setOnClickListener { copy(hex(c)) }
            })
        }
        dialog(ScrollView(this).apply { addView(v) }, "Valeurs").apply {
            setButton(AlertDialog.BUTTON_POSITIVE, "Copier tout") { _, _ -> copy(cs.joinToString(" ") { hex(it) }) }
            setButton(AlertDialog.BUTTON_NEGATIVE, "Fermer") { _, _ -> }
        }.show()
    }

    // ---- Small things ----

    private fun copy(s: String) {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("couleurs", s))
        // Android 13+ shows its own "copied" bubble
        if (Build.VERSION.SDK_INT < 33) toast("Copié : $s")
    }

    private fun share(s: String) = startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, s), null))

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

}
