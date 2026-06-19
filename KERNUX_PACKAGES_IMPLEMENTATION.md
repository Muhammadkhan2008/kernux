# Kernux Package System — Actual Implementation

> **Note (2026-06-19):** This document previously described an aspirational design
> (separate `kernux-packages` repo, `.kpkg` format, ARM64-only). That was never
> implemented. This file is the **single source of truth** going forward — the
> older PHASE2_*.md/.txt files in the repo root are obsolete.

## How it actually works

```
┌──────────────────────────────────────────────────────────────────────┐
│  1. GitHub Actions (weekly + manual)                                  │
│     .github/workflows/build-packages.yml                              │
│     - Cross-compiles 16 packages for arm64-v8a using Android NDK      │
│     - Bundles each into <pkg>-<version>.opkg (Debian-style archive)   │
│     - Uploads to a stable GitHub Release (tag: packages-stable)      │
│     - Same repo (Muhammadkhan2008/kernux), same release, updated      │
└──────────────────────────────────────────────────────────────────────┘
                                  │
                                  │  HTTPS GET
                                  ▼
┌──────────────────────────────────────────────────────────────────────┐
│  2. User runs in app:  pkg install git                                │
│                                                                      │
│     PackageManagerPhase2.kt                                           │
│     - URL = github.com/.../releases/download/packages-stable/git-2.38.opkg
│     - Downloads → caches in filesDir/packages-cache/                  │
│     - Extracts tar.gz → merges into filesDir/usr/                    │
│     - chmod +x on filesDir/usr/bin/*                                  │
│     - Records in filesDir/installed-packages.txt                     │
└──────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
                       filesDir/usr/bin/git   ← real binary, executable
```

## Packages shipped (16 total)

Built by `phase2-build/scripts/build-all-packages.sh`:

| Package   | Version | Size  | Essential | Description                                   |
|-----------|---------|-------|-----------|-----------------------------------------------|
| coreutils | 9.1     | ~3MB  | ✓         | ls, cat, grep, awk, sed, find, sort, uniq    |
| bash      | 5.1     | ~2MB  | ✓         | GNU Bourne-Again Shell                       |
| curl      | 7.85    | ~1MB  | ✓         | URL transfer tool                            |
| wget      | 1.21    | ~1MB  | ✓         | Non-interactive network downloader           |
| git       | 2.38    | ~8MB  |           | Distributed version control                  |
| vim       | 9.0     | ~5MB  |           | Highly configurable text editor              |
| nano      | 7.0     | ~1MB  |           | Small friendly editor                        |
| python    | 3.11    | ~25MB |           | Python interpreter                           |
| node      | 18      | ~35MB |           | Node.js JavaScript runtime                   |
| gcc       | 12      | ~45MB |           | GNU C/C++ compiler                           |
| openssh   | 9       | ~10MB |           | OpenBSD Secure Shell                         |
| perl      | 5.36    | ~12MB |           | Perl interpreter                             |
| openssl   | 3.0     | ~8MB  |           | TLS/SSL and crypto library                   |
| net-tools | 2.10    | ~1MB  |           | ifconfig, netstat, route                     |
| iputils   | 20230321| ~1MB |           | ping, tracepath, arping                      |
| make      | 4.3     | ~1MB  |           | Build automation                             |

**Build artifacts:** `phase2-build/packages/<pkg>-<version>.opkg`
**Release tag:** `packages-stable` (updated each run, no version churn)
**ABI:** arm64-v8a only (per build script). Other ABIs in app are for the terminal itself.

## Building packages locally

```bash
# 1. Install Android NDK r25b
wget https://dl.google.com/android/repository/android-ndk-r25b-linux.zip
unzip android-ndk-r25b-linux.zip -d ~/
export ANDROID_NDK_HOME=$HOME/android-ndk-r25b

# 2. Install build tools
sudo apt install -y build-essential git wget curl

# 3. Build everything
cd phase2-build/scripts
bash build-all-packages.sh     # takes 2-5 hours

# 4. Find packages
ls ../packages/*.opkg
```

Or just push to main — GitHub Actions does it weekly on Sundays 00:00 UTC,
or manually via the Actions tab → "Run workflow".

## App-side configuration

In `app/src/main/java/com/kernux/app/terminal/PackageManagerPhase2.kt`:

```kotlin
private const val REPO_OWNER  = "Muhammadkhan2008"
private const val REPO_NAME   = "kernux"
private const val RELEASE_TAG = "packages-stable"
private const val BASE_URL =
    "https://github.com/$REPO_OWNER/$REPO_NAME/releases/download/$RELEASE_TAG"
```

**To add a new package**, three places must change together:
1. `phase2-build/scripts/build-all-packages.sh` — add to PACKAGES array + URL
2. `PackageManagerPhase2.kt` — add entry to `packages` map
3. Push → trigger build-packages workflow → wait → users can `pkg install <name>`

## Troubleshooting

### "Download HTTP 404"
- The `packages-stable` release doesn't have this file yet.
- Trigger `build-packages.yml` manually from Actions tab.

### "Install failed: Invalid .opkg"
- Build script may have changed. Re-check `phase2-build/scripts/build-all-packages.sh`
- Make sure the .opkg extracts to `usr/` at root (not `data/...`).

### App says "Unknown package"
- Package exists in build script but missing from PM catalog — add to `packages` map.

### Packages work on emulator but not phone
- Emulator is likely x86_64. Packages are arm64-only (per current build script).
- Either build for x86_64 too, or use a real arm64 phone for testing.
