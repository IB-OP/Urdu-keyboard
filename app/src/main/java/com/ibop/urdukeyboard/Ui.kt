package com.ibop.urdukeyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable

object Palette {
    val BG: Int = Color.parseColor("#121316")
    val KEY: Int = Color.parseColor("#2C2F36")
    val KEY_PRESSED: Int = Color.parseColor("#4A4F5A")
    val KEY_FN: Int = Color.parseColor("#1D1F24")
    val ACCENT: Int = Color.parseColor("#3B82F6")
    val TEXT: Int = Color.WHITE
    val MUTED: Int = Color.parseColor("#9AA0AA")
    val GREEN: Int = Color.parseColor("#22C55E")
    val AMBER: Int = Color.parseColor("#F59E0B")
    val RED: Int = Color.parseColor("#EF4444")
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun roundedRect(context: Context, color: Int, radiusDp: Int): GradientDrawable {
    val g = GradientDrawable()
    g.shape = GradientDrawable.RECTANGLE
    g.cornerRadius = context.dp(radiusDp).toFloat()
    g.setColor(color)
    return g
}

fun pressableBg(context: Context, normal: Int, pressed: Int, radiusDp: Int = 8): StateListDrawable {
    val s = StateListDrawable()
    s.addState(intArrayOf(android.R.attr.state_pressed), roundedRect(context, pressed, radiusDp))
    s.addState(intArrayOf(), roundedRect(context, normal, radiusDp))
    return s
}

/** Text shown on a key for a given Unicode code point. */
object Labels {
    fun forCodePoint(cp: Int): String {
        if (cp == 0) return ""
        when (cp) {
            0x200C -> return "ZWNJ"
            0x200D -> return "ZWJ"
            0x200E -> return "LRM"
            0x200F -> return "RLM"
        }
        val type = Character.getType(cp)
        val glyph = String(Character.toChars(cp))
        return when {
            // Combining marks (zabar, zer, shadda ...) are shown on a dotted circle so they are visible.
            type == Character.NON_SPACING_MARK.toInt() || type == Character.ENCLOSING_MARK.toInt() -> "◌" + glyph
            // Invisible format characters (e.g. U+0600 number sign) get their code instead of a glyph.
            type == Character.FORMAT.toInt() || type == Character.CONTROL.toInt() -> "U+" + "%04X".format(cp)
            else -> glyph
        }
    }
}
