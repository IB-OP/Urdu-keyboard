# Urdu Keyboard for PC

An Android app that turns your phone into a **Bluetooth keyboard** for a PC, with an on-screen keyboard labelled in Urdu. Use it to type Urdu into MS Word (or anything else) on a computer that has an Urdu keyboard layout installed.

## How it works

The phone pretends to be an ordinary Bluetooth keyboard (Bluetooth HID Device profile). When you tap a key, the app sends the *physical* key (for example "Q") to the PC. The PC's installed Urdu layout turns it into the Urdu letter. The app's keys are labelled with the letters that layout produces, so what you see on the phone matches what appears in Word.

Because the PC does the translation, **the Urdu layout must be installed on the PC** and switched on (Win+Space).

Two layouts are built in (pick the one installed on the PC):

| Layout | Source of the key table |
|---|---|
| Windows "Urdu" (built-in) | IBM keyboard registry KBD492, which matches Microsoft's Urdu layout |
| CRULP Urdu Phonetic | xkeyboard-config `urd-crulp` (CRULP phonetic v1.1) |

The tables live in `LayoutData.kt` (generated) and `LayoutModel.kt`; adding another layout is one more table.

## Get the APK (no Android Studio needed)

1. Create a new GitHub repository and upload everything in this folder (keep the `.github` folder).
2. Open the repository's **Actions** tab, run **Build APK** (it also runs on every push).
3. When it finishes, open the run and download the **UrduKeyboard-apk** artifact. Unzip it and install `app-debug.apk` (allow "install unknown apps" when asked).

To build locally instead: `./gradlew assembleDebug` (JDK 17 and the Android SDK are needed). The APK is in `app/build/outputs/apk/debug/`.

## Using it

1. Open the app and allow the Bluetooth permission.
2. Pick the Urdu layout installed on the PC.
3. Pair once: tap **Make phone visible**, then on the PC go to Settings → Bluetooth & devices → Add device → Bluetooth and choose the phone. Confirm the code on both screens.
4. The PC normally connects by itself. If not, tap it in the paired-devices list and press **Connect**.
5. On the PC, switch to the Urdu layout (Win+Space), click in Word, then tap **Open keyboard** and type.

The keyboard has three tabs:

- **Keys**: the letters, Shift (double-tap to lock), AltGr (phonetic layout), Urdu comma and full stop, the Urdu question mark, a language-switch key (Win+Space).
- **123**: digits and punctuation.
- **Word tools**: Alt+Shift layout switch, right-to-left / left-to-right paragraph direction (Ctrl+Right Shift / Ctrl+Left Shift), undo, redo, save, select all, copy, paste, cut, bold, italic, underline, arrows, Home, End, Page up/down.

The strip above the keys is an approximate echo of what you typed. The PC's screen is the real result.

## Notes and limits

- Needs **Android 9 or newer** and a phone whose Bluetooth stack supports the HID Device profile. Most Pixel and many other phones do. Some Samsung, Xiaomi and Huawei builds do not, in which case the app shows an error on the first screen.
- The screen stays on while the keyboard is open so the Bluetooth link is not dropped.
- Digits come out the way the PC's layout types them. The Windows Urdu layout types Western digits; the phonetic layout types Urdu digits (۱۲۳).
- The symbols produced by the shifted number row on the Windows Urdu layout come from the IBM registry listing of that layout and are the least-verified part. Check them once on the PC.
- Check that your exam or test centre allows you to connect your own device before you rely on this.
