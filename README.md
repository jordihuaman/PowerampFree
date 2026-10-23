# PowerampFree

Clean-room Kotlin reconstruction of the *core behaviors* observed in the
reference players (Poweramp build 1032 + Equalizer build 1028), analyzed from
the user's own APKs. **Original implementation** — no decompiled or proprietary
code ships here.

DSL core library + Android app (Media3). Runs on stock Android; all knowledge
informs behavior, never resources or layout.

## Build & test

```powershell
# Pure-JVM DSP tests — no Android SDK needed (102 tests, green):
. .\tools\rea-env.ps1
.\tools\gradle\gradle-8.10.2\bin\gradle.bat :core:audio:test --console=plain

# App module (requires ANDROID_HOME/local.properties):
.\tools\gradle\gradle-8.10.2\bin\gradle.bat :app:assembleDebug --console=plain
```

The vendored Gradle/JDK live in `tools/` (untracked). `extract/` and `targets/`
hold the analysis of the reference APKs (untracked).

## Layout

- `core/audio/` — pure-JVM DSP engine + policies (unit-tested)
- `app/`       — Compose UI + MediaSessionService + stores
- `docs/`      — `EVIDENCE.md` (findings) · `INTEGRATION.md` (architecture/API) ·
                 `FEATURES.md` (status & roadmap) · `archive/` (history)
