package com.ibop.urdukeyboard

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread

/**
 * Makes the phone show up as a Bluetooth keyboard (Bluetooth HID Device profile, Android 9+).
 * The PC sees an ordinary keyboard; it decides which characters the key presses produce.
 */
class HidKeyboard(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onHidState()
    }

    enum class State { NEED_PERMISSION, UNSUPPORTED, BT_OFF, STARTING, READY, CONNECTING, CONNECTED, FAILED }

    var state: State = State.STARTING
        private set
    var detail: String? = null
        private set

    @Volatile
    private var hid: BluetoothHidDevice? = null

    @Volatile
    private var host: BluetoothDevice? = null

    private var starting = false
    private var isRegistered = false

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val sendThread = HandlerThread("hid-send").also { it.start() }
    private val worker = Handler(sendThread.looper)

    val hostName: String?
        get() {
            val h = host ?: return null
            return try {
                h.name ?: h.address
            } catch (e: SecurityException) {
                null
            }
        }

    fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    fun isHost(device: BluetoothDevice): Boolean {
        val h = host ?: return false
        return h.address == device.address
    }

    /** Paired devices, computers first. */
    fun bondedDevices(): List<BluetoothDevice> {
        if (!hasPermission()) return emptyList()
        return try {
            val set: Set<BluetoothDevice> = adapter?.bondedDevices ?: emptySet()
            set.sortedWith(
                compareBy<BluetoothDevice>(
                    { it.bluetoothClass?.majorDeviceClass != BluetoothClass.Device.Major.COMPUTER },
                    { it.name ?: "" }
                )
            )
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    // ------------------------------------------------------------------ lifecycle

    /** Safe to call repeatedly: it re-checks permission / Bluetooth state and only starts once. */
    fun start() {
        if (!hasPermission()) {
            publish(State.NEED_PERMISSION, null)
            return
        }
        val a = adapter
        if (a == null) {
            publish(State.UNSUPPORTED, "This phone has no Bluetooth.")
            return
        }
        if (!a.isEnabled) {
            publish(State.BT_OFF, null)
            return
        }
        if (hid != null || starting) return
        starting = true
        publish(State.STARTING, null)
        val ok = try {
            a.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
        } catch (e: SecurityException) {
            false
        }
        if (!ok) {
            starting = false
            publish(State.UNSUPPORTED, "This phone does not offer the Bluetooth keyboard (HID device) profile.")
        }
    }

    fun stop() {
        val h = hid
        try {
            h?.unregisterApp()
        } catch (e: Exception) {
        }
        try {
            adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, h)
        } catch (e: Exception) {
        }
        hid = null
        host = null
        starting = false
        isRegistered = false
    }

    /** Final cleanup when the app is closing. */
    fun release() {
        stop()
        sendThread.quitSafely()
    }

    fun connect(device: BluetoothDevice) {
        val h = hid ?: return
        try {
            h.connect(device)
        } catch (e: SecurityException) {
        }
    }

    fun disconnect() {
        val h = hid ?: return
        val d = host ?: return
        try {
            h.disconnect(d)
        } catch (e: SecurityException) {
        }
    }

    // ------------------------------------------------------------------ sending keys

    /** Press and release one key, optionally with modifiers held (e.g. Shift, Ctrl). */
    fun tap(modifiers: Int, usage: Int) {
        worker.post {
            if (modifiers != 0) {
                send(modifiers, 0)
                pause(10)
            }
            send(modifiers, usage)
            pause(12)
            send(modifiers, 0)
            if (modifiers != 0) {
                pause(10)
                send(0, 0)
            }
        }
    }

    /** Play a series of (modifiers, key) reports in order - used for shortcuts like Alt+Shift. */
    fun play(steps: List<Pair<Int, Int>>) {
        worker.post {
            for (s in steps) {
                send(s.first, s.second)
                pause(20)
            }
        }
    }

    private fun send(modifiers: Int, key: Int) {
        val h = hid ?: return
        val d = host ?: return
        try {
            h.sendReport(d, HidReport.REPORT_ID, HidReport.make(modifiers, key))
        } catch (e: SecurityException) {
        }
    }

    private fun pause(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
        }
    }

    // ------------------------------------------------------------------ Bluetooth callbacks

    private fun publish(newState: State, newDetail: String?) {
        state = newState
        detail = newDetail
        listener.onHidState()
    }

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            val h = proxy as? BluetoothHidDevice ?: return
            hid = h
            val sdp = BluetoothHidDeviceAppSdpSettings(
                "Urdu Keyboard",
                "Phone as Urdu keyboard",
                "IB OP",
                BluetoothHidDevice.SUBCLASS1_KEYBOARD,
                HidReport.DESCRIPTOR
            )
            val ok = try {
                h.registerApp(sdp, null, null, context.mainExecutor, callback)
            } catch (e: SecurityException) {
                false
            }
            if (!ok) {
                publish(
                    State.FAILED,
                    "Could not register as a Bluetooth keyboard. Some phones (certain Samsung, Xiaomi and Huawei builds) block this feature."
                )
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hid = null
            host = null
            starting = false
            isRegistered = false
            publish(State.STARTING, null)
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            isRegistered = registered
            starting = false
            if (registered) {
                if (host == null) publish(State.READY, null)
            } else {
                host = null
                publish(State.STARTING, null)
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    host = device
                    publish(State.CONNECTED, null)
                }
                BluetoothProfile.STATE_CONNECTING -> publish(State.CONNECTING, null)
                BluetoothProfile.STATE_DISCONNECTED -> {
                    val h = host
                    if (h == null || device == null || h.address == device.address) {
                        host = null
                    }
                    publish(if (isRegistered) State.READY else State.STARTING, null)
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            val d = device ?: return
            try {
                hid?.replyReport(d, type, id, HidReport.make(0, 0))
            } catch (e: SecurityException) {
            }
        }

        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            // The PC sends keyboard LED state here (Caps Lock etc.). Acknowledge and ignore it.
            val d = device ?: return
            try {
                hid?.reportError(d, BluetoothHidDevice.ERROR_RSP_SUCCESS)
            } catch (e: SecurityException) {
            }
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice?) {
            host = null
            publish(State.READY, null)
        }
    }
}
