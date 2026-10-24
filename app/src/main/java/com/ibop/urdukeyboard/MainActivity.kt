package com.ibop.urdukeyboard

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity(), HidKeyboard.Listener {

    private lateinit var hid: HidKeyboard
    private lateinit var prefs: SharedPreferences
    private var layoutDef: UrduLayout = Layouts.windowsUrdu
    private var onKeyboardScreen = false

    private var statusView: TextView? = null
    private var fixButton: Button? = null
    private var deviceList: LinearLayout? = null
    private var kbStatus: TextView? = null
    private var kbDot: View? = null

    private val sink = object : KeySink {
        override fun tap(modifiers: Int, usage: Int) {
            hid.tap(modifiers, usage)
        }

        override fun play(steps: List<Pair<Int, Int>>) {
            hid.play(steps)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val i = intent ?: return
            when (i.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val st = i.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (st == BluetoothAdapter.STATE_ON) {
                        hid.start()
                    } else if (st == BluetoothAdapter.STATE_OFF) {
                        hid.stop()
                        hid.start()
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> refreshDevices()
            }
        }
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("settings", MODE_PRIVATE)
        layoutDef = Layouts.byId(prefs.getString("layout", null))
        hid = HidKeyboard(applicationContext, this)

        val filter = IntentFilter()
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        showConnectScreen()
        hid.start()
        if (!hid.hasPermission()) requestBtPermissions()
    }

    override fun onResume() {
        super.onResume()
        hid.start()
        refreshDevices()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
        }
        if (isFinishing) hid.release()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (onKeyboardScreen) showKeyboardScreen() else showConnectScreen()
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onBackPressed() {
        if (onKeyboardScreen) showConnectScreen() else super.onBackPressed()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        hid.start()
        updateStatus()
    }

    override fun onHidState() {
        updateStatus()
    }

    // ------------------------------------------------------------------ helpers

    private fun requestBtPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE),
                7
            )
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun label(text: String, sp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = sp
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    private fun button(text: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        return b
    }

    private fun lp(topDp: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        p.topMargin = dp(topDp)
        return p
    }

    private fun safeName(d: BluetoothDevice): String {
        return try {
            d.name ?: d.address
        } catch (e: SecurityException) {
            "Bluetooth device"
        }
    }

    private fun statusLine(): String {
        return when (hid.state) {
            HidKeyboard.State.CONNECTED -> "Connected to " + (hid.hostName ?: "PC")
            HidKeyboard.State.CONNECTING -> "Connecting…"
            HidKeyboard.State.READY -> "Not connected to a PC yet"
            HidKeyboard.State.BT_OFF -> "Bluetooth is off"
            HidKeyboard.State.NEED_PERMISSION -> "Bluetooth permission needed"
            HidKeyboard.State.UNSUPPORTED -> hid.detail ?: "This phone can't act as a Bluetooth keyboard"
            HidKeyboard.State.STARTING -> "Starting Bluetooth keyboard…"
            HidKeyboard.State.FAILED -> hid.detail ?: "Couldn't start the Bluetooth keyboard"
        }
    }

    private fun statusColor(): Int {
        return when (hid.state) {
            HidKeyboard.State.CONNECTED -> Palette.GREEN
            HidKeyboard.State.CONNECTING, HidKeyboard.State.READY, HidKeyboard.State.STARTING -> Palette.AMBER
            else -> Palette.RED
        }
    }

    private fun dot(color: Int): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(color)
        return g
    }

    private fun updateStatus() {
        val text = statusLine()
        val color = statusColor()

        val sv = statusView
        if (sv != null) {
            sv.text = text
            sv.setTextColor(color)
        }
        val ks = kbStatus
        if (ks != null) ks.text = text
        val kd = kbDot
        if (kd != null) kd.background = dot(color)

        val b = fixButton
        if (b != null) {
            when (hid.state) {
                HidKeyboard.State.NEED_PERMISSION -> {
                    b.visibility = View.VISIBLE
                    b.text = "Allow Bluetooth permission"
                    b.setOnClickListener { requestBtPermissions() }
                }
                HidKeyboard.State.BT_OFF -> {
                    b.visibility = View.VISIBLE
                    b.text = "Turn on Bluetooth"
                    b.setOnClickListener {
                        try {
                            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        } catch (e: Exception) {
                            toast("Turn Bluetooth on from the quick settings panel.")
                        }
                    }
                }
                else -> b.visibility = View.GONE
            }
        }
        refreshDevices()
    }

    private fun makeDiscoverable() {
        if (!hid.hasPermission()) {
            requestBtPermissions()
            return
        }
        try {
            val i = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            i.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
            startActivity(i)
        } catch (e: Exception) {
            toast("Couldn't open the visibility prompt. Use Bluetooth settings instead.")
        }
    }

    private fun openBluetoothSettings() {
        try {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        } catch (e: Exception) {
            toast("Couldn't open Bluetooth settings.")
        }
    }

    private fun chooseLayout(l: UrduLayout) {
        layoutDef = l
        prefs.edit().putString("layout", l.id).apply()
    }

    private fun refreshDevices() {
        val holder = deviceList ?: return
        holder.removeAllViews()
        val devices = hid.bondedDevices()
        if (devices.isEmpty()) {
            holder.addView(
                label("No paired devices yet. Pair the phone from the PC first (step 2).", 13f, Palette.MUTED)
            )
            return
        }
        for (d in devices) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val name = label(safeName(d), 15f, Color.WHITE)
            row.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val connected = hid.isHost(d)
            val btn = button(if (connected) "Disconnect" else "Connect") {
                if (connected) hid.disconnect() else hid.connect(d)
            }
            row.addView(btn)
            holder.addView(row)
        }
    }

    // ------------------------------------------------------------------ connect screen

    private fun layoutChooser(): RadioGroup {
        val group = RadioGroup(this)
        group.orientation = RadioGroup.VERTICAL
        for (l in Layouts.all) {
            val rb = RadioButton(this)
            rb.id = View.generateViewId()
            rb.text = l.title
            rb.setTextColor(Color.WHITE)
            rb.isChecked = l.id == layoutDef.id
            rb.setOnClickListener { chooseLayout(l) }
            group.addView(rb)
            val note = label(l.note, 12f, Palette.MUTED)
            note.setPadding(dp(32), 0, 0, dp(8))
            group.addView(note)
        }
        return group
    }

    private fun showConnectScreen() {
        onKeyboardScreen = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        kbStatus = null
        kbDot = null

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Palette.BG)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(24), dp(16), dp(32))
        scroll.addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        col.addView(label("Urdu Keyboard for PC", 24f, Color.WHITE, true))
        col.addView(label("Use this phone as a Bluetooth keyboard to type Urdu in MS Word on a PC.", 14f, Palette.MUTED), lp(4))

        val status = label("", 16f, Color.WHITE, true)
        statusView = status
        col.addView(status, lp(20))

        val fix = button("") {}
        fix.visibility = View.GONE
        fixButton = fix
        col.addView(fix, lp(8))

        col.addView(label("1. Which Urdu layout is on the PC?", 15f, Palette.ACCENT, true), lp(24))
        col.addView(layoutChooser(), lp(6))

        col.addView(label("2. Pair with the PC (first time only)", 15f, Palette.ACCENT, true), lp(20))
        col.addView(
            label(
                "Tap “Make phone visible”, then on the PC open Settings → Bluetooth & devices → Add device → Bluetooth and pick this phone. Confirm the code on both screens.",
                13f, Palette.MUTED
            ), lp(2)
        )
        col.addView(button("Make phone visible (2 min)") { makeDiscoverable() }, lp(8))
        col.addView(button("Open Bluetooth settings") { openBluetoothSettings() }, lp(4))

        col.addView(label("3. Connect", 15f, Palette.ACCENT, true), lp(20))
        col.addView(label("Tap your PC below. It usually connects by itself right after pairing.", 13f, Palette.MUTED), lp(2))
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        deviceList = list
        col.addView(list, lp(6))

        col.addView(button("Open keyboard") { showKeyboardScreen() }, lp(24))
        col.addView(
            label(
                "On the PC: add Urdu under Settings → Time & language → Language & region → Add a keyboard, " +
                    "then press Win+Space to switch to it before typing. This app only sends key presses; " +
                    "the PC's layout turns them into Urdu letters.",
                12f, Palette.MUTED
            ), lp(16)
        )

        setContentView(scroll)
        updateStatus()
    }

    // ------------------------------------------------------------------ keyboard screen

    private fun showKeyboardScreen() {
        onKeyboardScreen = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (landscape) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
        statusView = null
        fixButton = null
        deviceList = null

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Palette.BG)
        root.setPadding(dp(4), dp(4), dp(4), dp(8))

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(8), dp(4), dp(8), dp(4))

        val back = label("‹ Connect", 14f, Palette.ACCENT, true)
        back.setPadding(0, dp(6), dp(14), dp(6))
        back.setOnClickListener { showConnectScreen() }
        bar.addView(back)

        val d = View(this)
        val dotParams = LinearLayout.LayoutParams(dp(10), dp(10))
        dotParams.rightMargin = dp(8)
        bar.addView(d, dotParams)
        kbDot = d

        val s = label("", 13f, Palette.MUTED)
        kbStatus = s
        bar.addView(s)

        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(
            UrduKeyboardView(this, layoutDef, sink),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        if (landscape) {
            val sv = ScrollView(this)
            sv.setBackgroundColor(Palette.BG)
            sv.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            setContentView(sv)
        } else {
            setContentView(root)
        }
        updateStatus()
    }
}
