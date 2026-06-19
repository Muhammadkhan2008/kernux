# Kernux - Terminal Emulator for Android

Real Shell Environment. No Root. No Sandbox Bypass. Pure PTY Magic.

[![Build APK](https://github.com/Muhammadkhan2008/kernux/actions/workflows/build-apk.yml/badge.svg)](https://github.com/Muhammadkhan2008/kernux/actions/workflows/build-apk.yml)
[![License: GPLv3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

## What is Kernux?

**Kernux** is a full-featured terminal emulator for Android that runs real shell commands via pseudo-terminal (PTY) without requiring root access.

- Real /system/bin/sh shell executed via forkpty()
- ANSI/VT100 terminal support (colors, cursor movement)
- 16-color Kali/Linux aesthetic (green-on-black theme)
- No root, no exploits - works on stock Android (minSdk 24)
- Native performance - C PTY layer + Kotlin UI
- Full keyboard input with arrow keys, Enter, Backspace, Tab

## Features

| Feature | Status | Details |
|---------|--------|---------|
| Real Shell | OK | /system/bin/sh (mksh) via PTY |
| ANSI Colors | OK | 16-color palette + bold text |
| VT100 Escapes | OK | Cursor move, erase, SGR codes |
| Linux Prompt | OK | Green kernux@localhost:~$ theme |
| Keyboard | OK | Full input + arrow keys |
| No Root | OK | Works on stock Android |
| GitHub Actions | OK | Auto-build APK on push |
| APK Download | OK | Pre-built in Releases |

## How It Works

```
User Input (Keyboard)
    |
    v
TerminalView (UI Layer)
    |
    v
TerminalSession (I/O Controller)
    |
    v
NativePty (JNI Bridge)
    |
    v
terminal_jni.c (C - PTY Operations)
    |
    v
/system/bin/sh (Real Shell via PTY)
    |
    v
Android Linux Kernel
```

## Supported Commands

Any binary in /system/bin or /system/xbin:

- Utilities: ls, cd, pwd, echo, cat, grep, find, ps, top
- Text: sed, awk, cut, sort, uniq  
- System: uname, id, whoami, date, uptime
- Network: ping, netstat, ifconfig
- Shell: pipes |, redirects >, <, &&, ||

Example commands:
```bash
ls -la
pwd
echo $HOME
ps | grep shell
cat /proc/cpuinfo
```

## Building from Source

### Requirements

- Android Studio 2023.1+
- Java 17+
- Android SDK API 34
- Android NDK
- CMake 3.22+

### Build Steps

```bash
git clone https://github.com/Muhammadkhan2008/kernux.git
cd kernux
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or open in Android Studio and click Run.

## Installation

### From Releases (Easiest)

1. Download kernux-debug-apk.apk from Releases
2. adb install kernux-debug-apk.apk

### From Android Studio

1. Clone repo
2. Plug in phone (USB debugging ON)
3. Click green Run button
4. Select device

## Architecture Details

### Native PTY Layer (C)
- forkpty() creates pseudo-terminal pair
- execve(/system/bin/sh) runs shell in child
- read/write operations on PTY master fd

### Terminal Emulator (Kotlin)
- Parses ANSI/VT100 escape codes
- Maintains screen grid with colors
- Supports cursor movement, erase, attributes

### UI Layer (Android)
- Canvas-based high-performance rendering
- Keyboard input capture
- Touch to show/hide soft keyboard

### JNI Bridge
- Kotlin external functions linked to C symbols
- Type marshalling between Java/C
- Thread-safe PTY operations

## Security

- No Root: Uses Android's PTY subsystem (kernel feature)
- Sandbox Intact: Runs in app's /data/data directory
- No Exploitation: targetSdk 28 respects W^X restrictions
- Open Source: All code visible, no native blobs

### Limitations

- Cannot access /system or /data outside app
- Cannot run as root (unless device rooted)
- Cannot modify system files
- Additional packages need Phase 2 bootstrap

## Roadmap
## Package System (Phase 2)

Kernux can install real Linux tools (git, python, gcc, vim, openssh...) on top of the
stock Android shell. Packages are prebuilt by GitHub Actions and downloaded at runtime.

### How a package gets onto your phone

```
┌─────────────────────────────────────────────────────────────┐
│ Weekly cron (Sun 00:00 UTC) + manual trigger               │
│ .github/workflows/build-packages.yml                       │
│   → cross-compiles 16 packages with Android NDK r25b       │
│   → bundles each as <pkg>-<version>.opkg                   │
│   → uploads to GitHub Release tag: packages-stable         │
└─────────────────────────────────────────────────────────────┘
                            │
                            │  HTTPS download
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ Your phone, in the Kernux app:                              │
│   $ pkg install git                                         │
│   PackageManagerPhase2.kt fetches from releases URL         │
│   extracts into filesDir/usr/  (chmod +x on bin/*)          │
│   records in filesDir/installed-packages.txt                │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
                   $ git clone https://github.com/torvalds/linux
```

### Available commands

| Command | What it does |
|---------|--------------|
| `pkg list`           | Show all 16 available packages |
| `pkg search <query>` | Search by name |
| `pkg install <name>` | Download, extract, install |
| `pkg remove <name>`  | Uninstall |
| `pkg installed`      | List what's on this device |

### Available packages

`coreutils`, `bash`, `curl`, `wget`, `git`, `vim`, `nano`, `python`, `node`,
`gcc`, `openssh`, `perl`, `openssl`, `net-tools`, `iputils`, `make`

### Building packages yourself

The full reference is in [`KERNUX_PACKAGES_IMPLEMENTATION.md`](KERNUX_PACKAGES_IMPLEMENTATION.md).
TL;DR: push a change to main → Actions builds weekly → release gets updated → app picks it up.

Or trigger manually: **Actions tab → Build Kernux Packages → Run workflow**.

### Requirements for the app to use packages

- ARM64 device (most modern phones). x86_64 emulators don't have arm64 packages.
- minSdk 24+ (Android 7+). Already the app's minimum.
- Network access on first install (subsequent installs use cache).

## Roadmap

### Phase 1 - DONE ✅
- Native PTY layer
- ANSI terminal emulator
- Keyboard input
- GitHub Actions CI/CD for APK

### Phase 2 - DONE ✅
- Cross-compiled 16 packages (arm64) on GitHub Actions
- Termux-style `pkg install <name>` with GitHub Releases backend
- Per-user package DB + cache
- Weekly auto-rebuild

### Phase 3 - Future
- Multi-session tabs
- Clipboard integration
- SSH support
- Custom themes
- x86_64 + armeabi-v7a package builds

## FAQ

Q: Why no root needed?
A: Android's PTY subsystem is a kernel feature already available.

Q: Can I run bash/python?
A: Not yet - Phase 2 will cross-compile ARM binaries.

Q: Compatible with which Android versions?
A: minSdk 24 (Android 7.0+), works on modern devices.

Q: Production ready?
A: Great for dev/learning. Not for untrusted scripts.

Q: Comparison to Termux?
A: Termux has huge bootstrap + packages. Kernux is minimal, pure PTY, learning-focused.

## Project Stats

```
Lines of Code:
- Kotlin: ~600 (UI + emulator)
- C: ~130 (PTY layer)
- Config: ~150 (Gradle, CMake)
- Total: ~880 LoC

Architecture:
- Minimal dependencies
- Native performance
- Modern Android APIs
```

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Native | C (POSIX) |
| JNI | Kotlin |
| Parser | Kotlin |
| UI | Android Canvas |
| Build | Gradle + CMake |
| CI/CD | GitHub Actions |

## Contributing

1. Fork repo
2. Create feature branch
3. Commit changes
4. Push to branch
5. Open Pull Request

## Issues & Bugs

Found a bug? Open an Issue with:
- Device model & Android version
- Steps to reproduce
- Terminal output/screenshots

## License

Kernux is licensed under GPLv3 - see LICENSE file.

Inspired by Termux architecture but built from scratch.

## Author

Built with love by Muhammadkhan2008

Give it a star if you love terminal emulators!

- GitHub: https://github.com/Muhammadkhan2008/kernux
- Issues: https://github.com/Muhammadkhan2008/kernux/issues
- Releases: https://github.com/Muhammadkhan2008/kernux/releases
