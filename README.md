<p align="center">
  <img src=".github/banner-readme.png" alt="bunnylock">
</p>

bunnylock is an Android app for band, RAT, and cell locking. It talks to the modem through root or Shizuku and picks the best backend available on the phone.

Modems are weird, and vendors rarely implement the same controls in the same way. Something working on one phone does not guarantee that it will work on every phone with the same chipset or Android version.

<table>
  <tr>
    <td align="center"><img src=".github/screenshots/home.jpg" alt="Home screen with the lock status, carriers and signal" width="200"></td>
    <td align="center"><img src=".github/screenshots/bands.jpg" alt="Bands screen with network mode and band selection" width="200"></td>
    <td align="center"><img src=".github/screenshots/cells.jpg" alt="Cell lock screen with visible cells" width="200"></td>
    <td align="center"><img src=".github/screenshots/tools.jpg" alt="Tools screen with backend, radio and console options" width="200"></td>
  </tr>
  <tr>
    <td align="center">Home</td>
    <td align="center">Bands</td>
    <td align="center">Cell lock</td>
    <td align="center">Tools</td>
  </tr>
</table>

> [!CAUTION]
> bunnylock changes low-level modem settings. Some of them stay after a reboot, and modems don't always react the way you'd expect. **You use it at your own risk.**
>
> The author isn't responsible for any issues or damage it causes. That includes losing service or messages (emergency calls too), lost data, or a phone that stops working properly. Before you experiment, make sure you know how to undo a change: **Unlock all**, or a network settings reset in your phone's settings.
>
> bunnylock is provided "as is", without any warranty. See the [license](LICENSE) for the full terms.

## Features

Depending on the phone and backend, bunnylock can:

- lock 2G, 3G, 4G, and 5G bands
- force a RAT, such as 4G only or 5G only
- lock LTE and NR frequencies using EARFCN or ARFCN
- lock one or more PCIs on supported modems
- select 5G SA, NSA, or both
- lock to a mobile network (PLMN)
- re-attach, restart the radio, or reboot the modem
- clear active locks with **Unlock all**

See [Tested devices](TESTED_DEVICES.md) for combinations that have been tried on real phones.

## Access

bunnylock needs either root or [Shizuku](https://shizuku.rikka.app/). It tries root first and falls back to Shizuku when root is unavailable or denied.

- **Root or Shizuku running as root** gives bunnylock full access to the available modem backend.
- **Shizuku started through ADB** can use Android's telephony APIs without root.
- Some **MediaTek** phones expose an AT-command passthrough through Shizuku, allowing real band and cell locking without root.

## Backends

bunnylock detects the phone automatically. You can override the backend in **Tools** if the automatic choice is wrong.

- **Qualcomm** - talks to the modem over QMI.
- **MediaTek** - uses the same AT commands as MediaTek EngineerMode.
- **Pixel Tensor** - uses Shannon modem settings exposed by Pixel firmware.
- **Samsung Exynos and other devices** - uses Android's radio HAL. On supported Samsung phones, bunnylock can also open the built-in Samsung band menu.

Available features still depend on what the modem and firmware accept. In particular, Android's radio HAL controls are vendor-dependent.

## Unisoc (Spreadtrum) Support

Unisoc modems do not implement Android's system-selection-channel API correctly. On these devices the standard path throws:

```text
IllegalStateException: Failed to retrieve system selecton channels
```

bunnylock now detects Unisoc devices (`vendor == "unisoc"`) and bypasses the broken radio HAL for LTE band locks. It talks to the vendor `IRadioInteractor` service through the existing root or Shizuku helper and sends the modem's own AT commands:

- `AT+SPLBAND=1,<mask>` writes the LTE band mask.
- `AT+RESET=0` restarts the modem so the new mask takes effect.

This backend requires **Shizuku (started through ADB) or Root**, because the helper must be able to reach the `irit` Binder service. Dual-SIM phones use `phoneId = SIM - 1`.

### Locking and unlocking bands

1. Open **Bands** and select the LTE group.
2. Select the bands you want, for example LTE Band 1 only.
3. Apply the selection. bunnylock writes the Unisoc mask and resets the modem automatically.
4. To undo the lock, use **Unlock** for LTE, or **Unlock all** on the Home screen.

Before the first lock, bunnylock saves the modem's current mask and restores it on unlock. If no saved mask exists, it falls back to the captured RMX3511 default mask. Other Unisoc firmware may use a different default; check the log in **Tools** if unlock does not restore the expected bands.

The B1-only readback is `0,0,0,1,0`. The write command omits the trailing zero field, so it is `AT+SPLBAND=1,0,0,0,1`.

Tested on a **Realme C33 (RMX3511, Unisoc T612, Android 12)** with LTE B1 and B3. The mask persisted across the modem reset used to apply it and across reboots in testing.

## Using it

1. Grant root access, or start Shizuku and allow bunnylock to use it.
2. Pick the SIM you want to control.
3. Use **Bands** to select bands, or **Cell lock** to enter an EARFCN, ARFCN, or PCI.
4. Use **Unlock all** on the Home screen to return to the default configuration.

Locking a band or cell that your carrier does not use can leave the phone without service. **Unlock all** works offline, so keep it in mind while experimenting.

If something fails, copy the diagnostics from **Tools**. They include the detected chipset, backend, available interfaces, and recent operation log.

## Building

You need:

- JDK 17 or newer
- Android SDK
- Android NDK `29.0.14206865`
- CMake `3.22.1`

On Windows:

```powershell
.\gradlew.bat assembleRelease
```

On Linux or macOS:

```sh
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`

### Signing

By default, release builds are signed with your machine's debug key. To use your own key, create `keystore.properties` in the project root.

```properties
storeFile=C:/keys/release.jks
storePassword=...
keyAlias=...
keyPassword=...
lineageFile=C:/keys/lineage.bin
```

`lineageFile` is optional. It points to a lineage made with `apksigner rotate`, which lets builds signed with the new key update installs signed with an older one.
