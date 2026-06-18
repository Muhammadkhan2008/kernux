# Kernux Testing Guide

Complete guide to test Kernux terminal emulator on Android device.

---

## Pre-Testing Setup

### Prerequisites

- Android device (minSdk 24, Android 7.0+)
- USB cable (or Android Emulator)
- APK file: kernux-debug-apk.apk
- ADB installed on PC

### Install APK

Via ADB:
```
adb devices
adb install kernux-debug-apk.apk
```

Via Phone:
1. Download APK
2. Open Files app
3. Tap APK to install

---

## Basic Testing (First Launch)

### What You Should See

Black screen with green prompt:
```
kernux@localhost:/data/data/com.kernux.app/files$
```

If you see this: APP WORKS!

### First Commands to Try

```
pwd
ls -la
echo "Hello Kernux"
uname -a
ps
```

---

## Feature Tests

### 1. Commands

Test basic commands:
```
echo test
ls /
cd /tmp
whoami
date
cat /proc/cpuinfo
```

Status: PASS if commands execute

### 2. Colors

```
ls --color
echo -e '\033[32mGreen\033[0m'
echo -e '\033[31mRed\033[0m'
echo -e '\033[1mBold\033[0m'
```

Status: PASS if colors show

### 3. Keyboard Input

- Type characters -> should appear
- Backspace -> deletes character
- UP arrow -> shows previous command
- Enter -> runs command
- Tab -> auto-complete (if available)

Status: PASS if input works

### 4. Pipes & Redirects

```
ps | grep shell
echo test > /tmp/test.txt
cat /tmp/test.txt
echo more >> /tmp/test.txt
```

Status: PASS if pipe/redirect work

### 5. Environment Variables

```
echo $HOME
echo $PATH
echo $TERM
```

Expected:
- HOME = /data/data/com.kernux.app/files
- TERM = xterm-256color

Status: PASS if output correct

### 6. File Operations

```
touch /tmp/kernux_test.txt
echo "Kernux Test" > /tmp/kernux_test.txt
cat /tmp/kernux_test.txt
rm /tmp/kernux_test.txt
```

Status: PASS if file ops work

### 7. Process Management

```
ps
ps | grep init
```

Status: PASS if process list shows

### 8. System Information

```
uname -a
cat /proc/cpuinfo | head
getprop ro.build.version.release
```

Status: PASS if system info shows

---

## Advanced Tests

### Command History

```
ls -la
# Press UP arrow -> shows ls -la again
# Press UP again -> shows command before that
```

### Multi-line Commands

```
echo "Line 1" \
  "Line 2"
```

### Clear Screen

```
clear
# Screen should clear
```

### Large Output

```
seq 1 1000
# Should show 1000 lines without lag
```

### Complex Pipes

```
ps | grep shell | wc -l
# Should show count
```

---

## UI Tests

### Keyboard Show/Hide

1. Tap screen -> Keyboard appears
2. Type text
3. Press back -> Keyboard hides

### Screen Rotation

1. Rotate phone (portrait/landscape)
2. App should resize without crash

---

## Test Checklist

Core Features:
- [ ] App launches (green prompt visible)
- [ ] Can type characters
- [ ] Backspace deletes
- [ ] Enter runs command
- [ ] UP/DOWN arrow works
- [ ] Colors in output

Commands:
- [ ] ls works
- [ ] pwd works
- [ ] echo works
- [ ] cat works
- [ ] ps works

Advanced:
- [ ] Pipes work (ps | grep)
- [ ] Redirects work (echo > file)
- [ ] File creation works
- [ ] Command history works
- [ ] clear command works

UI:
- [ ] Keyboard appears on tap
- [ ] Screen rotation works
- [ ] Text is readable
- [ ] No lag

---

## Troubleshooting

### App won't launch

Check logs:
```
adb logcat | grep kernux
```

### Command not found

Use full path:
```
/system/bin/ls
```

### No colors

This is normal. Not all terminals support colors.

### Keyboard not responding

Tap screen again and wait 1 second.

### App crashes

Get error log:
```
adb logcat -d | grep kernux
```

Report on GitHub: https://github.com/Muhammadkhan2008/kernux/issues

---

## Report Issues

Found a bug? Report on GitHub with:
1. Device model: adb shell getprop ro.product.model
2. Android version: adb shell getprop ro.build.version.release
3. Steps to reproduce
4. Logcat output: adb logcat -d | grep kernux

---

## Expected Performance

- App launch: < 2 seconds
- Commands: < 500ms
- Large output (1000 lines): < 1 second
- Keyboard response: < 100ms

---

Good luck testing! Let us know results on GitHub. 

For issues/feedback: https://github.com/Muhammadkhan2008/kernux/issues

---

## Known Issues (Phase 1 Limitations)

### Issue 1: No Package Manager

**Symptom:**
```bash
pkg install curl
apt-get install vim
# Command not found
```

**Why:**
- No package manager included in Phase 1
- Need cross-compilation for ARM
- Requires package repository

**Workaround:**
- None (Phase 2 feature)
- Track in PROGRESS.txt

**Status:** Expected in Phase 2

---

### Issue 2: Incomplete PATH

**Symptom:**
```bash
echo $PATH
# Output: /system/bin:/system/xbin

ls /usr/bin
# (empty)

which curl
# not found
```

**Why:**
- PATH doesn't include /usr/bin
- /usr/bin exists but empty
- No custom binaries installed yet

**Workaround:**
```bash
# Temporary fix
export PATH=/system/bin:/system/xbin:$PATH

# Check what's available
ls /system/bin | head -20
```

**Status:** Will be fixed in Phase 1.5 (quick update)

---

### Issue 3: Missing Network Tools

**Symptom:**
```bash
ping 8.8.8.8
ifconfig
curl https://example.com
# Command not found
```

**Why:**
- Network tools not in /system/bin
- Need cross-compilation
- Not included in base image

**Workaround:**
- Use available tools (cat /proc/net/...)
- Network permission missing from manifest

**Status:** Expected in Phase 2

---

### Issue 4: Limited Utilities

**Symptom:**
```bash
vim file.txt
nano file.txt
grep -r "text" /data
# Some commands not found
```

**Available Now:**
- ls, cat, echo, grep, ps, uname, date
- touch, rm, mkdir, cd, pwd
- sed, awk, find, sort, uniq

**Missing:**
- vim, nano (editors)
- curl, wget (downloaders)
- git (version control)
- gcc, make (development)
- python, node (interpreters)

**Workaround:**
- Use available tools only
- Wait for Phase 2

**Status:** Phase 2 will add these

---

## How to Report Issues

Found a bug or issue? Report on GitHub:

1. Go to: https://github.com/Muhammadkhan2008/kernux/issues
2. Click "New Issue"
3. Include:
   - Device: (adb shell getprop ro.product.model)
   - Android: (adb shell getprop ro.build.version.release)
   - Command: (what you ran)
   - Error: (what you got)
   - Expected: (what should happen)
4. Attach logcat if crash:
   ```bash
   adb logcat -d | grep kernux > logcat.txt
   ```

---

## Known Limitations Summary

Phase 1 (Current):
✓ Shell works
✓ Basic commands
✓ Colors work
✗ No package manager
✗ Limited utilities
✗ No network tools

Phase 1.5 (Planned - 1-2 hours):
- Add network permissions
- Fix PATH variable
- Init script

Phase 2 (Planned - 4-6 weeks):
+ Package manager
+ Network tools
+ Modern utilities
+ Development tools

For details, see ISSUES_AND_FIXES.md

