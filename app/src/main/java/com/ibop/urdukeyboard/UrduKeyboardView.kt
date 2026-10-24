package com.ibop.urdukeyboard

import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** Where the keyboard sends key presses (the Bluetooth link to the PC). */
interface KeySink {
    fun tap(modifiers: Int, usage: Int)
    fun play(steps: List<Pair<Int, Int>>)
}

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * The on-screen keyboard. Keys are laid out like a QWERTY keyboard, but each key is labelled with the
 * Urdu letter the PC's layout will produce for it. Pressing a key sends the *physical* key to the PC.
 */
class UrduKeyboardView(
    context: Context,
    private val layout: UrduLayout,
    private val sink: KeySink
) : LinearLayout(context) {

    private enum class ShiftState { OFF, ONCE, LOCK }

    private class LetterKey(val view: TextView, val usage: Int, val forceShift: Boolean)

    private val ui = Handler(Looper.getMainLooper())
    private val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private val keyH = context.dp(if (landscape) 38 else 52)
    private val toolH = context.dp(if (landscape) 34 else 46)

    private var shiftState = ShiftState.OFF
    private var altGr = false
    private var lastShiftTap = 0L

    private val preview = StringBuilder()
    private val previewView = TextView(context)
    private val pages = ArrayList<View>()
    private val tabs = ArrayList<TextView>()
    private val letterKeys = ArrayList<LetterKey>()
    private val shiftButtons = ArrayList<TextView>()
    private val altGrButtons = ArrayList<TextView>()

    init {
        orientation = LinearLayout.VERTICAL
        build()
    }

    // ------------------------------------------------------------------ building

    private fun build() {
        previewView.textSize = 18f
        previewView.gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
        previewView.textDirection = View.TEXT_DIRECTION_RTL
        previewView.maxLines = 1
        previewView.setPadding(context.dp(10), 0, context.dp(10), 0)
        previewView.background = roundedRect(context, Palette.KEY_FN, 8)
        val pl = LinearLayout.LayoutParams(MATCH, context.dp(if (landscape) 32 else 42))
        pl.setMargins(context.dp(4), context.dp(2), context.dp(4), context.dp(4))
        addView(previewView, pl)
        renderPreview()

        addView(buildTabs(), LinearLayout.LayoutParams(MATCH, context.dp(34)))

        pages.add(buildKeysPage())
        pages.add(buildSymbolsPage())
        pages.add(buildToolsPage())
        for (p in pages) addView(p, LinearLayout.LayoutParams(MATCH, WRAP))

        showPage(0)
        refreshLabels()
    }

    private fun buildTabs(): LinearLayout {
        val names = arrayOf("Keys", "123", "Word tools")
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        for (i in names.indices) {
            val t = TextView(context)
            t.text = names[i]
            t.textSize = 13f
            t.gravity = Gravity.CENTER
            t.setOnClickListener { showPage(i) }
            tabs.add(t)
            row.addView(t, LinearLayout.LayoutParams(0, MATCH, 1f))
        }
        return row
    }

    private fun showPage(index: Int) {
        for (i in pages.indices) {
            pages[i].visibility = if (i == index) View.VISIBLE else View.GONE
        }
        for (i in tabs.indices) {
            val on = i == index
            tabs[i].setTextColor(if (on) Palette.TEXT else Palette.MUTED)
            tabs[i].background = if (on) roundedRect(context, Palette.KEY, 8) else null
        }
    }

    private fun vbox(): LinearLayout {
        val b = LinearLayout(context)
        b.orientation = LinearLayout.VERTICAL
        return b
    }

    private fun rowOf(height: Int, vararg views: View): LinearLayout {
        val r = LinearLayout(context)
        r.orientation = LinearLayout.HORIZONTAL
        for (v in views) r.addView(v)
        r.layoutParams = LinearLayout.LayoutParams(MATCH, height)
        return r
    }

    private fun baseKey(text: String, weight: Float, function: Boolean, textSp: Float): TextView {
        val tv = TextView(context)
        tv.text = text
        tv.textSize = textSp
        tv.gravity = Gravity.CENTER
        tv.setTextColor(Palette.TEXT)
        tv.background = pressableBg(context, if (function) Palette.KEY_FN else Palette.KEY, Palette.KEY_PRESSED)
        val lp = LinearLayout.LayoutParams(0, MATCH, weight)
        lp.setMargins(context.dp(2), context.dp(3), context.dp(2), context.dp(3))
        tv.layoutParams = lp
        return tv
    }

    /** A key that types whatever the PC layout maps to this physical key. */
    private fun letterKey(usage: Int, forceShift: Boolean = false, weight: Float = 1f): TextView {
        val tv = baseKey("", weight, false, 20f)
        val lk = LetterKey(tv, usage, forceShift)
        letterKeys.add(lk)
        bind(tv, false) { pressLetter(lk) }
        return tv
    }

    private fun fnKey(
        label: String,
        weight: Float,
        repeat: Boolean = false,
        textSp: Float = 16f,
        action: () -> Unit
    ): TextView {
        val tv = baseKey(label, weight, true, textSp)
        bind(tv, repeat, action)
        return tv
    }

    /** Keys fire on touch-down (feels like a real keyboard); some repeat while held. */
    private fun bind(v: View, repeat: Boolean, action: () -> Unit) {
        var repeater: Runnable? = null
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    action()
                    if (repeat) {
                        val r = object : Runnable {
                            override fun run() {
                                action()
                                ui.postDelayed(this, 55)
                            }
                        }
                        repeater = r
                        ui.postDelayed(r, 420)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    val r = repeater
                    if (r != null) ui.removeCallbacks(r)
                    repeater = null
                }
            }
            true
        }
    }

    private fun shiftKey(weight: Float): TextView {
        val s = fnKey("⇧", weight, false, 20f) { onShift() }
        shiftButtons.add(s)
        return s
    }

    private fun altGrKey(weight: Float): TextView {
        val s = fnKey("AltGr", weight, false, 12f) {
            altGr = !altGr
            refreshLabels()
        }
        altGrButtons.add(s)
        return s
    }

    private fun backspaceKey(weight: Float): TextView =
        fnKey("⌫", weight, true, 20f) {
            sink.tap(0, Usage.BACKSPACE)
            backspacePreview()
        }

    private fun enterKey(weight: Float): TextView =
        fnKey("⏎", weight, false, 20f) {
            sink.tap(0, Usage.ENTER)
            appendPreview('\n'.code)
        }

    private fun spaceKey(weight: Float): TextView {
        val tv = baseKey("space", weight, false, 13f)
        tv.setTextColor(Palette.MUTED)
        bind(tv, true) {
            sink.tap(0, Usage.SPACE)
            appendPreview(' '.code)
        }
        return tv
    }

    private fun langKey(weight: Float): TextView =
        fnKey("🌐", weight, false, 18f) { sink.play(WIN_SPACE) }

    private fun buildKeysPage(): LinearLayout {
        val page = vbox()

        val row1 = ArrayList<View>()
        for (c in "qwertyuiop") row1.add(letterKey(Usage.letter(c)))
        page.addView(rowOf(keyH, *row1.toTypedArray()))

        val row2 = ArrayList<View>()
        for (c in "asdfghjkl") row2.add(letterKey(Usage.letter(c)))
        page.addView(rowOf(keyH, *row2.toTypedArray()))

        val row3 = ArrayList<View>()
        row3.add(shiftKey(1.5f))
        for (c in "zxcvbnm") row3.add(letterKey(Usage.letter(c)))
        row3.add(backspaceKey(1.5f))
        page.addView(rowOf(keyH, *row3.toTypedArray()))

        val row4 = ArrayList<View>()
        row4.add(langKey(1.1f))
        if (layout.hasAltGr) row4.add(altGrKey(1.2f))
        row4.add(letterKey(0x36))                       // ,  (Urdu comma on both layouts)
        row4.add(spaceKey(3.4f))
        row4.add(letterKey(0x37))                       // .  (Urdu full stop)
        row4.add(letterKey(0x38, forceShift = true))    // Shift + /  (Urdu question mark)
        row4.add(enterKey(1.4f))
        page.addView(rowOf(keyH, *row4.toTypedArray()))
        return page
    }

    private fun buildSymbolsPage(): LinearLayout {
        val page = vbox()

        val row1 = ArrayList<View>()
        for (c in "1234567890") row1.add(letterKey(Usage.digit(c)))
        page.addView(rowOf(keyH, *row1.toTypedArray()))

        val row2 = ArrayList<View>()
        for (u in intArrayOf(0x35, 0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x33, 0x34)) row2.add(letterKey(u))
        page.addView(rowOf(keyH, *row2.toTypedArray()))

        val row3 = ArrayList<View>()
        row3.add(shiftKey(1.5f))
        row3.add(letterKey(0x36, false, 1.2f))
        row3.add(letterKey(0x37, false, 1.2f))
        row3.add(letterKey(0x38, false, 1.2f))
        row3.add(backspaceKey(1.5f))
        page.addView(rowOf(keyH, *row3.toTypedArray()))

        val row4 = ArrayList<View>()
        row4.add(langKey(1.2f))
        if (layout.hasAltGr) row4.add(altGrKey(1.2f))
        row4.add(spaceKey(4f))
        row4.add(enterKey(1.5f))
        page.addView(rowOf(keyH, *row4.toTypedArray()))
        return page
    }

    private fun buildToolsPage(): LinearLayout {
        val page = vbox()

        fun combo(label: String, mods: Int, usage: Int): TextView =
            fnKey(label, 1f, false, 12f) { sink.tap(mods, usage) }

        fun seq(label: String, steps: List<Pair<Int, Int>>): TextView =
            fnKey(label, 1f, false, 12f) { sink.play(steps) }

        page.addView(rowOf(toolH,
            seq("Language\nWin+Space", WIN_SPACE),
            seq("Language\nAlt+Shift", ALT_SHIFT),
            seq("Right-to-left\nCtrl+R-Shift", RTL),
            seq("Left-to-right\nCtrl+L-Shift", LTR)))
        page.addView(rowOf(toolH,
            combo("Undo", Mod.LCTRL, Usage.Z),
            combo("Redo", Mod.LCTRL, Usage.Y),
            combo("Save", Mod.LCTRL, Usage.S),
            combo("Select all", Mod.LCTRL, Usage.A)))
        page.addView(rowOf(toolH,
            combo("Copy", Mod.LCTRL, Usage.C),
            combo("Paste", Mod.LCTRL, Usage.V),
            combo("Cut", Mod.LCTRL, Usage.X),
            fnKey("Delete", 1f, true, 12f) { sink.tap(0, Usage.DELETE) }))
        page.addView(rowOf(toolH,
            combo("Bold", Mod.LCTRL, Usage.B),
            combo("Italic", Mod.LCTRL, Usage.I),
            combo("Underline", Mod.LCTRL, Usage.U),
            combo("Tab", 0, Usage.TAB)))
        page.addView(rowOf(toolH,
            fnKey("←", 1f, true, 18f) { sink.tap(0, Usage.LEFT) },
            fnKey("↑", 1f, true, 18f) { sink.tap(0, Usage.UP) },
            fnKey("↓", 1f, true, 18f) { sink.tap(0, Usage.DOWN) },
            fnKey("→", 1f, true, 18f) { sink.tap(0, Usage.RIGHT) }))
        page.addView(rowOf(toolH,
            combo("Home", 0, Usage.HOME),
            combo("End", 0, Usage.END),
            combo("Page up", 0, Usage.PAGE_UP),
            combo("Page down", 0, Usage.PAGE_DOWN)))
        return page
    }

    // ------------------------------------------------------------------ behaviour

    private fun onShift() {
        val now = System.currentTimeMillis()
        shiftState = when {
            shiftState == ShiftState.LOCK -> ShiftState.OFF
            shiftState == ShiftState.ONCE && now - lastShiftTap < 400 -> ShiftState.LOCK
            shiftState == ShiftState.ONCE -> ShiftState.OFF
            else -> ShiftState.ONCE
        }
        lastShiftTap = now
        refreshLabels()
    }

    private fun pressLetter(k: LetterKey) {
        val useShift = !altGr && (shiftState != ShiftState.OFF || k.forceShift)
        val cp = layout.codePoint(k.usage, useShift, altGr)
        if (cp == 0) return
        var mods = 0
        if (useShift) mods = mods or Mod.LSHIFT
        if (altGr) mods = mods or Mod.RALT
        sink.tap(mods, k.usage)
        appendPreview(cp)
        var changed = false
        if (shiftState == ShiftState.ONCE) {
            shiftState = ShiftState.OFF
            changed = true
        }
        if (altGr) {
            altGr = false
            changed = true
        }
        if (changed) refreshLabels()
    }

    private fun refreshLabels() {
        val shiftOn = shiftState != ShiftState.OFF
        for (k in letterKeys) {
            val useShift = !altGr && (shiftOn || k.forceShift)
            val text = Labels.forCodePoint(layout.codePoint(k.usage, useShift, altGr))
            k.view.text = text
            k.view.textSize = if (text.length > 2) 11f else 20f
        }
        for (b in shiftButtons) {
            b.text = if (shiftState == ShiftState.LOCK) "⇪" else "⇧"
            val bg = if (shiftOn) Palette.ACCENT else Palette.KEY_FN
            b.background = pressableBg(context, bg, Palette.KEY_PRESSED)
        }
        for (b in altGrButtons) {
            val bg = if (altGr) Palette.ACCENT else Palette.KEY_FN
            b.background = pressableBg(context, bg, Palette.KEY_PRESSED)
        }
    }

    // ------------------------------------------------------------------ preview strip

    private fun appendPreview(cp: Int) {
        preview.appendCodePoint(cp)
        if (preview.length > 160) preview.delete(0, preview.length - 120)
        renderPreview()
    }

    private fun backspacePreview() {
        if (preview.isNotEmpty()) {
            preview.setLength(preview.offsetByCodePoints(preview.length, -1))
        }
        renderPreview()
    }

    private fun renderPreview() {
        if (preview.isEmpty()) {
            previewView.setTextColor(Palette.MUTED)
            previewView.text = "what you type appears here (approximate)"
        } else {
            previewView.setTextColor(Palette.TEXT)
            previewView.text = preview.toString().replace('\n', '↵')
        }
    }

    companion object {
        // Windows 10/11 "next keyboard layout"
        private val WIN_SPACE: List<Pair<Int, Int>> = listOf(
            Pair(Mod.LGUI, 0), Pair(Mod.LGUI, Usage.SPACE), Pair(Mod.LGUI, 0), Pair(0, 0)
        )

        // Classic Windows layout-switch shortcut
        private val ALT_SHIFT: List<Pair<Int, Int>> = listOf(
            Pair(Mod.LALT, 0), Pair(Mod.LALT or Mod.LSHIFT, 0), Pair(Mod.LALT, 0), Pair(0, 0)
        )

        // Word: paragraph direction right-to-left / left-to-right
        private val RTL: List<Pair<Int, Int>> = listOf(
            Pair(Mod.LCTRL, 0), Pair(Mod.LCTRL or Mod.RSHIFT, 0), Pair(Mod.LCTRL, 0), Pair(0, 0)
        )
        private val LTR: List<Pair<Int, Int>> = listOf(
            Pair(Mod.LCTRL, 0), Pair(Mod.LCTRL or Mod.LSHIFT, 0), Pair(Mod.LCTRL, 0), Pair(0, 0)
        )
    }
}
