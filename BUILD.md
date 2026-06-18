# Kernux Build Guide

## Quick Status

**GitHub Actions**: Automatically builds APK on every push to `main`  
**Status**: https://github.com/Muhammadkhan2008/kernux/actions  
**Download APK**: https://github.com/Muhammadkhan2008/kernux/releases or Artifacts

---

## Build on Your Machine

### Prerequisites

```bash
# macOS
brew install android-sdk android-ndk gradle

# Ubuntu/Debian
sudo apt-get install android-sdk android-ndk gradle

# Windows (with scoop)
scoop install android-sdk android-ndk gradle

# OR Android Studio (includes all)
# Download from: https://developer.android.com/studio
```

### Environment Setup

```bash
# Set Android SDK path
export ANDROID_HOME=~/Android/Sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/25.1.8937393
export PATH=$PATH:$ANDROID_HOME/tools/bin
```

### Build Commands

```bash
# Clone
git clone https://github.com/Muhammadkhan2008/kernux.git
cd kernux

# Build debug APK
gradle assembleDebug

# Build release APK (requires keystore)
gradle assembleRelease

# APK location
# app/build/outputs/apk/debug/app-debug.apk
# app/build/outputs/apk/release/app-release.apk
```

---

## Android Studio (Easiest)

1. Download Android Studio: https://developer.android.com/studio
2. Open → File → Open Folder → select `kernux`
3. Studio auto-downloads SDK, NDK, Gradle (wait 5-10 min)
4. Plug in phone (USB debugging ON) or start emulator
5. Click green ▶️ Run button
6. Select device → APK builds & installs automatically

---

## GitHub Actions (Automated)

1. Code is already on GitHub: https://github.com/Muhammadkhan2008/kernux
2. Push any change to `main` branch → workflow auto-triggers
3. Check status: Actions tab → "Build Kernux APK"
4. When done:
   - Artifacts section: download `kernux-debug-apk`
   - Or Releases tab (if tagged)

### Current Workflow

```yaml
- Checkout code
- Setup Java 17
- Setup Android SDK
- Accept licenses
- Build with Gradle 8.2
- Upload APK artifact
```

---

## Install on Device

### Via adb (CLI)

```bash
# Connect phone or emulator
adb devices

# Install
adb install app/build/outputs/apk/debug/app-debug.apk

# Launch
adb shell am start -n com.kernux.app/.MainActivity

# View logs
adb logcat | grep kernux
```

### Via Android Studio

- Click Run → Select device → Done (auto-installs)

### Via File Manager (Manual)

1. Download APK file
2. Transfer to phone
3. Open file manager → find APK → tap to install
4. Grant permissions
5. Launch Kernux app

---

## Troubleshooting

### Build Fails: "CMake not found"

```bash
# Install CMake
sdkmanager "cmake;3.22.1"
```

### Build Fails: "NDK not found"

```bash
# Install NDK
sdkmanager "ndk;25.1.8937393"
```

### Build Fails: "Gradle version mismatch"

```bash
# Clean build
gradle clean assembleDebug
```

### APK Won't Install: "Signature mismatch"

```bash
# Uninstall old version first
adb uninstall com.kernux.app

# Then reinstall
adb install app/build/outputs/apk/debug/app-debug.apk
```

### App Crashes on Launch

Check logcat:
```bash
adb logcat | grep -E "kernux|JNI|PTY"
```

---

## Build Variants

### Debug (Development)

```bash
gradle assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
# Faster build, debuggable, not for production
```

### Release (Production)

```bash
# Requires signing key (keystore)
# Create keystore:
keytool -genkey -v -keystore ~/kernux.keystore \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias kernux

# Build
gradle assembleRelease

# Output: app/build/outputs/apk/release/app-release.apk
```

---

## Project Structure

```
kernux/
├── app/
│   ├── src/main/
│   │   ├── cpp/              ← C PTY code
│   │   ├── java/             ← Kotlin code
│   │   ├── res/              ← Icons, layouts
│   │   └── AndroidManifest.xml
│   ├── build.gradle          ← App config
│   └── build/                ← Output (APK here)
├── gradle/wrapper/           ← Gradle distribution
├── build.gradle              ← Root config
├── settings.gradle
└── .github/workflows/        ← CI/CD
```

---

## CI/CD Pipeline

**Trigger**: Push to `main` or `master` branch  
**Runner**: `ubuntu-latest`  
**Time**: ~15-20 minutes  
**Output**: `kernux-debug-apk.apk` artifact

Steps:
1. Checkout repo
2. Setup Java 17 + Android SDK
3. Accept Android licenses
4. Run `gradle assembleDebug`
5. Upload APK as artifact
6. (Optional) Create GitHub Release

---

## Development Notes

### Code Organization

- `MainActivity.kt`: App entry, shell startup
- `TerminalView.kt`: UI (drawing, keyboard)
- `TerminalSession.kt`: PTY management, I/O
- `TerminalEmulator.kt`: ANSI parser
- `NativePty.kt`: JNI bridge
- `terminal_jni.c`: Native C PTY operations

### Modify & Rebuild

```bash
# Edit Kotlin code
vim app/src/main/java/...

# Edit C code
vim app/src/main/cpp/terminal_jni.c

# Rebuild
gradle clean assembleDebug
```

### Testing

```bash
# On device
adb shell

# Inside Kernux shell
ls -la
ps
uname -a
echo $PATH
```

---

## GitHub Actions Status

Check here: https://github.com/Muhammadkhan2008/kernux/actions

- **Green checkmark** = Build succeeded, APK ready
- **Red X** = Build failed, check logs
- **Yellow circle** = Build in progress

---

## Download Pre-built APK

**Releases**: https://github.com/Muhammadkhan2008/kernux/releases  
**Actions Artifacts**: https://github.com/Muhammadkhan2008/kernux/actions → latest run → Artifacts

---

## Questions?

- Issues: https://github.com/Muhammadkhan2008/kernux/issues
- Discussions: https://github.com/Muhammadkhan2008/kernux/discussions

Happy building! 🚀
