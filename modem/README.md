# modem

The library handling modem stuff for bunnylock. It does band, cell and PLMN locks, network and 5G modes, and diagnostics on Qualcomm, MediaTek, Pixel, Unisoc and the plain Android radio HAL.

It doesn't care how you get root. Hand it a helper started with `RootClient` (su), or with your own Shizuku setup. bunnylock's `ShizukuClient` is a good starting point.

## Setup

Copy the folder in, add `include(":modem")` to `settings.gradle.kts`, and add `implementation(project(":modem"))` to your app. You'll also need `com.android.library` and `kotlinx-coroutines-android` in your version catalog.

Two things the module can't do for you:

- Keep native libs extracted, or QMI won't load:
  ```kotlin
  android { packaging { jniLibs.useLegacyPackaging = true } }
  ```
- Ask for `READ_PHONE_STATE` and `ACCESS_FINE_LOCATION` at runtime.

## Usage

```kotlin
val modem = ModemController(context, scope, starter = { showStep ->
    showStep("Asking for root access...")
    val client = RootClient(context)
    StartedHelper(client, client.start(), Access.ROOT)
})

modem.connect()
modem.state.collect { /* it.groups, it.lteLock, ... */ }

modem.lockBands(Group.LTE, listOf(3, 20)) // returns a message, or throws ModemException
modem.refreshNow()
```

Actions don't refresh the state on their own, so call `refreshNow()` after them.

`lockBands` also takes a map, such as `mapOf(Group.LTE to listOf(3), Group.NR to listOf(78))`, and sends it as one request so the phone only moves once. Only backends where `locksBandsTogether` is true take it, and every list has to leave some bands out. Breaking either rule throws `IllegalStateException` or `IllegalArgumentException` instead of `ModemException`.

The module keeps its state in the `bunnylock` shared prefs, including the radio HAL locks it holds. Don't wipe that file.

## Layout

The module has two halves, which run in two processes. The app side is `ModemController`, and the root helper (`root.RootServer`) runs under su or Shizuku and answers one JSON command per line. Each backend keeps both halves in one package under `backend/`:

- `XBackend.kt` is the app side. It extends `ModemBackend`, and its companion `BackendProfile` says which chipset it's made for, when it suits a phone, when it works there, and what the screens may offer (`BackendFeatures`).
- The helper object (`Qualcomm.kt`, `Mediatek.kt`, ...) talks to the modem.
- `XCommands.kt` is the helper's command table for that backend.

The Android backend is the exception: its helper half is `root/Aosp.kt` and `root/AndroidCommands.kt`. `backend/samsung/` isn't a backend. It adds Samsung's 5G mode and band menu to the others.

Shared code lives outside those packages:

- `backend/` holds `ModemSession` (helper, prefs, state, log) and `HalLocks`, the radio HAL lock engine that backends fall back to. Beside it, `HalSettling` waits for the phone to move after a lock, `HalStrays` spots a phone that ignores one, and `HalQuirks` keeps what bunnylock learned about each phone model's HAL.
- `root/` holds the helper's own plumbing: `Request`, `At`, `OemHook` and `Detect`.
- `modem/` holds `ModemController`, its state types and `Backends.kt`. `data/` and `core/` hold the helper clients, cells, carriers and band tables.

To add a backend:

1. Add a `Backend` entry in `modem/Model.kt`, in preference order. The first one that suits the phone is picked.
2. Write `backend/<name>/`. If its modem takes every network's bands in one request, implement `LocksBandsTogether` too.
3. Register it in `modem/Backends.kt` and in `RootServer`'s command list.
4. Report any facts it needs from `root/Detect.kt`.
5. Put its strings in its own `strings_<name>.xml` and its label as `backend_<name>` in `strings_model.xml`, in every locale.
