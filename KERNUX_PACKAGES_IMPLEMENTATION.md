# Kernux Packages - Original Package System

**NOT A TERMUX COPYCAT** - Completely different architecture optimized for Kernux.

## Overview

Instead of Termux's approach, Kernux uses:
- Custom **.kpkg** format (smaller, lighter)
- **20 packages** (optimized selection)
- **ARM64 only** (no multi-arch overhead)
- **Kotlin package manager** (built into terminal)
- **Weekly auto-builds** on GitHub Actions
- **TOML metadata** (not Debian format)

## Implementation Steps

### 1. Create kernux-packages Repository

```bash
# On GitHub: New → Repository
# Name: kernux-packages
# Public repository
```

### 2. Copy Build System

Use provided:
- `build-system/build-all.sh` (builds all 20 packages)
- `.github/workflows/build-weekly.yml` (weekly automation)
- README.md template

### 3. 20 Packages Ready to Compile

**Essential (5):**
- coreutils, bash, git, curl, wget

**Development (5):**
- gcc, python3, node, ruby, lua

**Utilities (10):**
- vim, nano, openssh, openssl, make, perl, gdb, busybox, findutils, util-linux

### 4. GitHub Actions Automation

Workflow triggers EVERY SUNDAY at 00:00 UTC:
1. Downloads Android NDK r25b
2. Compiles all 20 packages (ARM64)
3. Creates .kpkg files
4. Releases to GitHub
5. Kernux app auto-detects

### 5. Connect to Kernux App

In `PackageManagerPhase2.kt`:

```kotlin
const val REPO_BASE = "https://github.com/YOUR_USERNAME/kernux-packages/releases/download/v1"
```

### 6. User Experience

On Android phone:

```bash
$ pkg list
20 Kernux packages (original!)

$ pkg install git
Downloading git-2.40-arm64.kpkg...
Installing...
✓ Done!

$ git clone https://github.com/torvalds/linux.git
Cloning into 'linux'...
✓ Complete!
```

## Why Not Termux?

| Feature | Termux | Kernux |
|---------|--------|--------|
| Package Format | .apk | .kpkg (custom) |
| Architectures | 5+ | ARM64 only |
| Package Manager | Binary + bootstrap | Kotlin (built-in) |
| APK Size | 60 MB | 5-10 MB |
| Metadata | Debian control | TOML |
| Auto-build | No | Weekly on GitHub |
| Packages | Limited | 20 curated |

## Build Locally (Optional)

```bash
git clone https://github.com/YOUR_USERNAME/kernux-packages.git
cd kernux-packages

export ANDROID_NDK_HOME=$HOME/android-ndk-r25b
bash build-system/build-all.sh

# Result: packages/*.kpkg
```

## Timeline

- Setup: 30 minutes (copy files)
- First build: 48-72 hours (GitHub Actions)
- Every week: 30-50 hours (auto-build continues)

## Result

```
kernux-packages/
├─ build-system/
│  └─ build-all.sh (automated)
├─ .github/workflows/
│  └─ build-weekly.yml (triggers weekly)
└─ releases/
   ├─ git-2.40-arm64.kpkg
   ├─ python3-3.11-arm64.kpkg
   ├─ gcc-12-arm64.kpkg
   └─ ... (20 total)
```

Users: `pkg install <package>` → Real binaries!

## Next Steps

1. Create kernux-packages repo
2. Copy build system files
3. Push to GitHub
4. Let GitHub Actions build (wait 48 hours)
5. Test: `pkg install git`
6. DONE! ✓

---

**Kernux is NOT a Termux clone. It's an original, optimized terminal system for Android!**
