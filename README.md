# PowerampFree

Kotlin reconstruction of the *core behaviors* observed in the reference players
(Poweramp build 1032 and Poweramp Equalizer build 1028) — analyzed with REA
(evidence: `docs/EVIDENCE.md`). **This is an original implementation** — no
decompiled or proprietary code is included; only behavior-level design
information obtained from the user's own copies.

## How to run the DSP tests (pure JVM, no Android SDK needed)

```powershell
. .\tools\rea-env.ps1
.\tools\gradle\gradle-8.10.2\bin\gradle.bat :core:audio:test --console=plain
```

When an Android SDK is configured (ANDROID_HOME), the app module also builds:

```powershell
gradle.bat :app:assembleDebug
```
