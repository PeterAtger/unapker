# Unapker

An Android app that installs split-APK bundles. Tap an `.xapk`, `.apks` or `.apkm` file and it installs, with one confirm from Android. No ads, no account, no network access.

- `.xapk` from APKPure and similar sites, including OBB game data
- `.apks` from SAI and split-APK backups
- `.apkm` from APKMirror (unencrypted files only; encrypted ones need APKMirror Installer)
- plain `.apk`

Only the parts that fit your phone are installed, for example the arm64 piece and not the x86 one.

## Build

```
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Needs JDK 17+ and the Android SDK (compileSdk 36). Minimum Android version is 8.0.

## Fonts

Dela Gothic One and Onest, both under the SIL Open Font License. See `licenses/`.
