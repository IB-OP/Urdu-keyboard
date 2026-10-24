package com.ibop.urdukeyboard

/** What one physical key types at each level, as Unicode code points (0 = nothing at that level). */
class KeyDef(val base: Int, val shift: Int, val altGr: Int)

/**
 * An Urdu keyboard layout that is installed on the PC. The phone only ever sends physical key
 * presses (HID usage IDs); this table is used to show the right Urdu letters on the phone's keys.
 */
class UrduLayout(
    val id: String,
    val title: String,
    val note: String,
    private val keys: Map<Int, KeyDef>
) {
    val hasAltGr: Boolean = keys.values.any { it.altGr != 0 }

    fun codePoint(usage: Int, shift: Boolean, altGr: Boolean): Int {
        val k = keys[usage] ?: return 0
        return if (altGr) k.altGr else if (shift) k.shift else k.base
    }
}

object Layouts {
    val windowsUrdu = UrduLayout(
        id = "windows_urdu",
        title = "Windows \"Urdu\" (built-in)",
        note = "The Urdu keyboard that comes with Windows (Urdu - Pakistan). Choose this if you added Urdu from Windows language settings.",
        keys = LayoutData.windowsUrdu
    )

    val crulpPhonetic = UrduLayout(
        id = "crulp_phonetic",
        title = "CRULP Urdu Phonetic",
        note = "The phonetic layout (A = ا, B = ب ...). Has Shift and AltGr levels for diacritics.",
        keys = LayoutData.crulpPhonetic
    )

    val all: List<UrduLayout> = listOf(windowsUrdu, crulpPhonetic)

    fun byId(id: String?): UrduLayout = all.firstOrNull { it.id == id } ?: windowsUrdu
}
