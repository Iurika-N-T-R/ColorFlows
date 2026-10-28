package com.iurika.ColorFlows

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorsTest {
    @Test fun rybWheelRoundTrips() {
        // the painter's primaries land on the screen's hues: yellow 60, blue 240
        assertEquals(60f, Ryb.toRgb(120f), .01f)
        assertEquals(240f, Ryb.toRgb(240f), .01f)
        for (h in 0..359 step 7) assertEquals(h.toFloat(), Ryb.fromRgb(Ryb.toRgb(h.toFloat())), .01f)
    }

    @Test fun hexParsing() {
        assertEquals(0xffaabbcc.toInt(), parseHex("#abc".replace("abc", "aabbcc")))
        assertEquals(0xffaabbcc.toInt(), parseHex("abc"))
        assertEquals(null, parseHex("#12345"))
    }
}
