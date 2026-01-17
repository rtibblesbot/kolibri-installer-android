# Kolibri Android Development

This document provides the development workflow for the Kolibri Android app.

## Build System

This project uses a Makefile as the primary build interface. Run `make help` to see all available targets.

## Prerequisites

- Android emulator running OR physical device connected via ADB
- Run `adb devices` to verify connection
- If no emulator: `make setup && make emulator`

## Development Loop

### 1. Build and Install

Use the Makefile:
```bash
make install
```

This runs `make kolibri.apk.unsigned` (builds debug APK) then installs via adb.

Or just build without installing:
```bash
make kolibri.apk.unsigned
```

Watch for:
- **BUILD SUCCESSFUL**: Proceed to install
- **Compilation errors**: Fix before continuing
- **Python errors**: Check Chaquopy output for syntax issues

### 2. Launch

```bash
adb shell am start -n org.learningequality.Kolibri.debug/org.learningequality.Kolibri.WebViewActivity
```

### 3. Monitor Logs

Use the Makefile target for filtered Kolibri logs:
```bash
make logcat
```

Or for specific log types:

Python/Kolibri stdout/stderr:
```bash
adb logcat -s python.stdout:V python.stderr:V
```

Specific component tags:
```bash
adb logcat -s KolibriWebView:V KolibriServer:V TaskWorkerImpl:V BaseTaskWorker:V
```

Crash logs:
```bash
adb logcat -s AndroidRuntime:E
```

### 4. Clear and Restart (when needed)

Uninstall app:
```bash
make uninstall
```

Clear app data (preserves install):
```bash
adb shell pm clear org.learningequality.Kolibri.debug
```

Force stop:
```bash
adb shell am force-stop org.learningequality.Kolibri.debug
```

## Makefile Reference

| Target | Description |
|--------|-------------|
| `make setup` | Complete SDK + emulator setup (first time) |
| `make emulator` | Start the emulator |
| `make kolibri.apk.unsigned` | Build debug APK → dist/ |
| `make install` | Build and install debug APK |
| `make uninstall` | Uninstall app from device |
| `make logcat` | View filtered Kolibri logs |
| `make clean` | Clean build artifacts |
| `make test` | Run unit tests |
| `make lint` | Run Android linter |

## Quick Commands

Build + Install + Launch:
```bash
make install && adb shell am start -n org.learningequality.Kolibri.debug/org.learningequality.Kolibri.WebViewActivity
```

Clear logs and monitor:
```bash
adb logcat -c && make logcat
```

## Troubleshooting

### App crashes on startup
1. Check crash logs: `adb logcat -s AndroidRuntime:E`
2. Look for Python import errors: `adb logcat -s python.stderr:V`

### Python changes not appearing
Chaquopy caches Python bytecode. Clear app data:
```bash
adb shell pm clear org.learningequality.Kolibri.debug
```

Or uninstall and reinstall:
```bash
make uninstall && make install
```

### INSTALL_FAILED_UPDATE_INCOMPATIBLE
Signing key mismatch. Uninstall first:
```bash
make uninstall && make install
```

### Service Worker issues
1. Open Chrome DevTools: `chrome://inspect`
2. Find the Kolibri WebView and inspect
3. Check Application > Service Workers

### WorkManager tasks not running
Check task logs:
```bash
adb logcat -s TaskWorkerImpl:V BaseTaskWorker:V WM-WorkerWrapper:V
```

### Emulator not found
```bash
make setup   # Creates SDK + AVD
make emulator
```

## Iterating

1. Make code changes
2. `make install`
3. Launch app and test
4. `make logcat` in another terminal
5. Repeat

For Python-only changes, builds are fast since Java doesn't need recompilation.
