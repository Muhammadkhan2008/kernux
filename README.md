# Kernux — Terminal Emulator for Android

A lightweight, native Android terminal emulator with VT100/ANSI support, built entirely from scratch without root privileges.

## Features

- **Real Shell**: Runs `/system/bin/sh` via PTY (pseudo-terminal)
- **ANSI Colors**: Full support for 16-color palette + bold text
- **VT100 Escapes**: Cursor movement, erase, SGR codes
- **Linux Feel**: Green Kali-style prompt, standard shell environment
- **No Root**: Works on any Android device (minSdk 24)
- **No Sandbox Bypass**: Uses Android's own shell and kernel

## Architecture

```
Native PTY Layer (C)
  └─ forkpty() → pty master fd
  └─ execve(/system/bin/sh)
       ↓
TerminalSession (Kotlin)
  └─ Reader thread → TerminalEmulator
       ↓
TerminalEmulator (Parser)
  └─ ANSI → screen grid
       ↓
TerminalView (UI)
  └─ Draw grid + keyboard input
```

## Quick Start

1. **Build**:
   ```bash
   chmod +x gradlew
   ./gradlew assembleDebug
   ```

2. **Install**:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

3. **Run**: Tap Kernux on phone → type commands

## Supported Commands

Any shell builtin or binary in `/system/bin:/system/xbin`:
- `ls`, `cd`, `pwd`, `echo`, `cat`, `grep`, `ps`, `uname`, etc.
- Pipes: `ps | grep`, redirects: `echo test > /tmp/file`
- Variables: `$PATH`, `$HOME`, etc.

## What's NOT Included (Phase 2+)

- Additional packages (`bash`, `python`, `nano`, etc.) — requires cross-compilation
- `apt`/package manager — bootstrapping userland
- Filesystem mods — real `/usr`, `/bin` require root

## Building

Requirements:
- Android SDK (API 34+)
- Android NDK (for native C code)
- Java 17+

GitHub Actions builds and publishes APK on every push to `main`.

## License

GPLv3 (inspired by Termux architecture)

## Author

Built with ❤️ by Kernux Team
