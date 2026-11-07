<p align="center">
  <img src=".github/banner-readme.png" alt="bunnylock">
</p>

bunnylock locks bands, RATs and cells on Android phones. It works through root or [Shizuku](https://shizuku.rikka.app/) and picks the right way to talk to your phone's modem.

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
> bunnylock changes low-level modem settings, and some of them stay after a reboot. A wrong lock can leave you without service, emergency calls included. **Use it at your own risk.** The author isn't responsible for any damage it causes, such as lost service, lost data or a phone that stops working properly. It comes without any warranty. See the [license](LICENSE).
>
> **Unlock all** on the Home screen tries to undo bunnylock's locks, even without service, but the modem doesn't always go along with it. Before you experiment, find out how else your phone can get back to normal, such as a network settings reset or its engineering menu.

## Features

Depending on your phone, bunnylock can:

- lock 2G, 3G, 4G and 5G bands
- force a RAT, such as 4G only or 5G only
- lock 4G and 5G cells by EARFCN, ARFCN or PCI
- choose 5G SA, NSA or both
- lock to one mobile network
- turn VoLTE, Wi-Fi calling and carrier aggregation on or off
- restart the radio or reboot the modem
- open the built-in band menu on supported Samsung phones

See [Tested devices](TESTED_DEVICES.md) for what has been tried on real phones.

## Supported phones

bunnylock tries root first, then Shizuku. Shizuku started through ADB can do less, though some MediaTek and Unisoc phones can still lock bands with it.

It picks a backend for your chipset, and you can change it in **Settings**:

- **Qualcomm**: QMI
- **MediaTek**: AT commands
- **Pixel (Tensor)**: the modem's NV settings
- **Unisoc**: AT commands
- **Anything else**: Android's radio HAL

If something doesn't work, copy the diagnostics from **Tools** and include them in your report.

## Building

You need JDK 17 or newer, the Android SDK, NDK `29.0.14206865` and CMake `3.22.1`.

```sh
./gradlew assembleRelease
```

The APK ends up in `app/build/outputs/apk/release/`. It's signed with your debug key unless you add a `keystore.properties` in the project root with `storeFile`, `storePassword`, `keyAlias`, `keyPassword` and, optionally, a `lineageFile` from `apksigner rotate`.
