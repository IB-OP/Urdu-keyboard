package com.ibop.urdukeyboard

/** HID keyboard modifier bits (byte 0 of a keyboard report). */
object Mod {
    const val LCTRL = 0x01
    const val LSHIFT = 0x02
    const val LALT = 0x04
    const val LGUI = 0x08
    const val RCTRL = 0x10
    const val RSHIFT = 0x20
    const val RALT = 0x40 // acts as AltGr on Windows layouts that define an AltGr level
}

/** HID usage IDs (Keyboard/Keypad page 0x07) for the keys this app presses. */
object Usage {
    const val ENTER = 0x28
    const val BACKSPACE = 0x2A
    const val TAB = 0x2B
    const val SPACE = 0x2C
    const val PAGE_UP = 0x4B
    const val DELETE = 0x4C
    const val HOME = 0x4A
    const val END = 0x4D
    const val PAGE_DOWN = 0x4E
    const val RIGHT = 0x4F
    const val LEFT = 0x50
    const val DOWN = 0x51
    const val UP = 0x52

    const val A = 0x04
    const val B = 0x05
    const val C = 0x06
    const val I = 0x0C
    const val S = 0x16
    const val U = 0x18
    const val V = 0x19
    const val X = 0x1B
    const val Y = 0x1C
    const val Z = 0x1D

    /** 'a'..'z' -> usage ID. */
    fun letter(c: Char): Int = 0x04 + (c - 'a')

    /** '0'..'9' -> usage ID. */
    fun digit(c: Char): Int = if (c == '0') 0x27 else 0x1E + (c - '1')
}

/** Builds the HID report descriptor and the 8-byte input reports sent to the PC. */
object HidReport {
    const val REPORT_ID = 1

    /** Standard keyboard descriptor: modifiers, reserved byte, LED output, 6 simultaneous keys. */
    val DESCRIPTOR: ByteArray = intArrayOf(
        0x05, 0x01,             // Usage Page (Generic Desktop)
        0x09, 0x06,             // Usage (Keyboard)
        0xA1, 0x01,             // Collection (Application)
        0x85, REPORT_ID,        //   Report ID
        0x05, 0x07,             //   Usage Page (Key Codes)
        0x19, 0xE0,             //   Usage Minimum (224)
        0x29, 0xE7,             //   Usage Maximum (231)
        0x15, 0x00,             //   Logical Minimum (0)
        0x25, 0x01,             //   Logical Maximum (1)
        0x75, 0x01,             //   Report Size (1)
        0x95, 0x08,             //   Report Count (8)
        0x81, 0x02,             //   Input (Data, Variable, Absolute)  - modifier byte
        0x95, 0x01,             //   Report Count (1)
        0x75, 0x08,             //   Report Size (8)
        0x81, 0x01,             //   Input (Constant)                  - reserved byte
        0x95, 0x05,             //   Report Count (5)
        0x75, 0x01,             //   Report Size (1)
        0x05, 0x08,             //   Usage Page (LEDs)
        0x19, 0x01,             //   Usage Minimum (1)
        0x29, 0x05,             //   Usage Maximum (5)
        0x91, 0x02,             //   Output (Data, Variable, Absolute) - LED bits
        0x95, 0x01,             //   Report Count (1)
        0x75, 0x03,             //   Report Size (3)
        0x91, 0x01,             //   Output (Constant)                 - LED padding
        0x95, 0x06,             //   Report Count (6)
        0x75, 0x08,             //   Report Size (8)
        0x15, 0x00,             //   Logical Minimum (0)
        0x25, 0x65,             //   Logical Maximum (101)
        0x05, 0x07,             //   Usage Page (Key Codes)
        0x19, 0x00,             //   Usage Minimum (0)
        0x29, 0x65,             //   Usage Maximum (101)
        0x81, 0x00,             //   Input (Data, Array)               - 6 key slots
        0xC0                    // End Collection
    ).map { it.toByte() }.toByteArray()

    /** One 8-byte keyboard report: [modifiers, reserved, key1..key6]. */
    fun make(modifiers: Int, key: Int): ByteArray =
        byteArrayOf(modifiers.toByte(), 0, key.toByte(), 0, 0, 0, 0, 0)
}
