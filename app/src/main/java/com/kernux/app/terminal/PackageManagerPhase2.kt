// PackageManagerPhase2.kt — Termux-style package manager for Kernux
//
// Downloads prebuilt packages from GitHub Releases (same repo, tag: packages-stable).
// Build workflow (.github/workflows/build-packages.yml) creates these .opkg files
// and uploads them to the release. Users get them via `pkg install <name>`.

package com.kernux.app.terminal

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class PackageManagerPhase2(private val filesDir: File) {

    companion object {
        private const val TAG = "KernuxPM"

        // GitHub release URL — same repo, stable tag.
        // Workflow builds weekly & updates this release with fresh .opkg files.
        private const val RELEASE_TAG = "packages-stable"
        private const val REPO_OWNER  = "Muhammadkhan2008"
        private const val REPO_NAME   = "kernux"

        private const val BASE_URL =
            "https://github.com/$REPO_OWNER/$REPO_NAME/releases/download/$RELEASE_TAG"

        // Install to /data/data/com.kernux.app/files/usr — matches build script PREFIX
        // (cross-compile-env.sh: PREFIX=/data/data/com.kernux.app/files/usr)
        private const val PREFIX_SUBDIR = "usr"

        private const val CACHE_DIR = "packages-cache"
        private const val DB_FILE   = "installed-packages.txt"
    }

    private val handler  = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    // Package catalog. Version MUST match what build-all-packages.sh produces.
    // Mismatch = 404 on download. Keep in sync with phase2-build/scripts/build-all-packages.sh
    private val packages = mapOf(
        // name -> (version, sizeInMB, essential)
        "coreutils" to PackageInfo("coreutils", "9.1",  "GNU core utilities (ls, cat, grep, awk, sed, find)",  3, true),
        "bash"      to PackageInfo("bash",      "5.1",  "GNU Bourne-Again Shell",                              2, true),
        "curl"      to PackageInfo("curl",      "7.85", "Command-line tool for transferring data with URLs",   1, true),
        "wget"      to PackageInfo("wget",      "1.21", "Non-interactive network downloader",                 1, true),
        "git"       to PackageInfo("git",       "2.38", "Distributed version-control system",                 8, false),
        "vim"       to PackageInfo("vim",       "9.0",  "Highly configurable text editor",                    5, false),
        "nano"      to PackageInfo("nano",      "7.0",  "Small and friendly text editor",                     1, false),
        "python"    to PackageInfo("python",    "3.11", "Python interpreter",                                25, false),
        "node"      to PackageInfo("node",      "18",   "Node.js JavaScript runtime",                        35, false),
        "gcc"       to PackageInfo("gcc",       "12",   "GNU C/C++ compiler",                                45, false),
        "openssh"   to PackageInfo("openssh",   "9",    "OpenBSD Secure Shell",                              10, false),
        "perl"      to PackageInfo("perl",      "5.36", "Highly capable, feature-rich programming language", 12, false),
        "openssl"   to PackageInfo("openssl",   "3.0",  "TLS/SSL and crypto library",                        8, false),
        "net-tools" to PackageInfo("net-tools", "2.10", "Network utilities (ifconfig, netstat, route)",       1, false),
        "iputils"   to PackageInfo("iputils",   "20230321", "Ping, tracepath, arping and other utilities",     1, false),
        "make"      to PackageInfo("make",      "4.3",  "Build automation tool",                              1, false)
    )

    // Callbacks — both new API (onProgress/onComplete) and legacy API (onOutput).
    // The legacy onOutput is kept so old callers (MainActivity/SessionManager) still work.
    var onProgress: ((String) -> Unit)? = null
    var onComplete: ((String, Boolean) -> Unit)? = null

    // Legacy callback — MainActivity/SessionManager attach here.
    // Routes progress AND completion messages through one channel.
    var onOutput: ((String) -> Unit)? = null
        set(value) {
            field = value
            // Mirror to onProgress/onComplete so existing UI shows it
            onProgress = { msg -> value?.invoke(msg) }
            onComplete = { msg, _ -> value?.invoke(msg) }
        }

    fun installPackage(name: String) {
        executor.execute {
            try {
                updateProgress("Starting $name installation...")

                val pkg = packages[name]
                    ?: throw Exception("Unknown package: $name (try `pkg list`)")

                if (isInstalled(name)) {
                    updateProgress("$name already installed")
                    notifyComplete("$name already installed ✓", true)
                    return@execute
                }

                val arch = getDeviceArchitecture()
                updateProgress("Device architecture: $arch")
                updateProgress("Target: $name ${pkg.version}")

                // Filename pattern matches what build-all-packages.sh creates:
                //   tar czf "$pkg-$version.opkg" "$pkg/DEBIAN" "$pkg/usr"
                val filename = "${pkg.name}-${pkg.version}.opkg"
                val url      = "$BASE_URL/$filename"

                // Cache file lives in app's internal storage
                val cachedFile = File(getCacheDir(), filename)

                if (!cachedFile.exists() || cachedFile.length() < 1024) {
                    updateProgress("Downloading $name (~${pkg.sizeInMb}MB)...")
                    Log.i(TAG, "Downloading $url -> ${cachedFile.absolutePath}")
                    downloadWithProgress(url, cachedFile, pkg.sizeInMb)
                } else {
                    updateProgress("Using cached $name (${cachedFile.length() / 1024}KB)")
                }

                updateProgress("Extracting $name...")
                installPackageFile(cachedFile, name)

                markInstalled(name, pkg.version)
                updateProgress("$name installed ✓")
                notifyComplete("$name ${pkg.version} installed successfully!", true)

            } catch (e: Exception) {
                Log.e(TAG, "Install failed for $name", e)
                notifyComplete("Install failed: ${e.message}", false)
            }
        }
    }

    fun uninstallPackage(name: String) {
        executor.execute {
            try {
                val pkg = packages[name] ?: throw Exception("Unknown package: $name")
                val pkgDir = File(getInstallDir(), name)
                if (pkgDir.exists()) {
                    pkgDir.deleteRecursively()
                }
                // Also remove the cached .opkg
                File(getCacheDir(), "${pkg.name}-${pkg.version}.opkg").delete()

                unmarkInstalled(name)
                updateProgress("$name removed")
                notifyComplete("$name uninstalled", true)
            } catch (e: Exception) {
                Log.e(TAG, "Uninstall failed for $name", e)
                notifyComplete("Uninstall failed: ${e.message}", false)
            }
        }
    }

    fun listPackages(): List<String> = packages.keys.sorted()

    fun searchPackage(query: String): List<String> =
        packages.keys.filter { it.contains(query, ignoreCase = true) }

    fun getPackageInfo(name: String): PackageInfo? = packages[name]

    fun listInstalled(): String {
        val installed = getInstalledPackages()
        if (installed.isEmpty()) return "No packages installed yet.\nTry: pkg install git"
        return "Installed (${installed.size}):\n" + installed.joinToString("\n") { "  • $it" }
    }

    // ─── Legacy API (kept for backwards compat with MainActivity.kt) ───

    /** Synchronous list — returns human-readable formatted string. */
    fun list(): String = buildString {
        append("Available packages (${packages.size}):\r\n")
        append("─────────────────────────────────\r\n")
        packages.keys.sorted().forEach { name ->
            val p = packages[name]!!
            append(String.format("  %-12s %-6s  %s\r\n", name, p.version, p.description))
        }
        append("\r\nInstalled:\r\n")
        val installed = getInstalledPackages()
        if (installed.isEmpty()) {
            append("  (none — try: pkg install git)\r\n")
        } else {
            installed.forEach { append("  • $it\r\n") }
        }
    }

    /** Synchronous install — kicks off async install, output via onOutput. */
    fun install(name: String) { installPackage(name) }

    /** Synchronous remove — kicks off async uninstall. */
    fun remove(name: String) { uninstallPackage(name) }

    /** Search — returns formatted string of matches. */
    fun search(query: String): String {
        val matches = searchPackage(query)
        if (matches.isEmpty()) return "No packages match '$query'.\r\n"
        return "Found ${matches.size} match(es) for '$query':\r\n" +
               matches.joinToString("\r\n") { "  • $it" } + "\r\n"
    }

    /** Package info — returns formatted details. */
    fun info(name: String): String {
        val p = packages[name] ?: return "Unknown package: $name\r\n"
        val installed = if (isInstalled(name)) "yes" else "no"
        return buildString {
            append("Package:       $name\r\n")
            append("Version:       ${p.version}\r\n")
            append("Size:          ~${p.sizeInMb} MB\r\n")
            append("Pre-installed: ${if (p.preInstalled) "yes" else "no"}\r\n")
            append("Installed:     $installed\r\n")
            append("Description:   ${p.description}\r\n")
            append("Source:        $BASE_URL/${name}-${p.version}.opkg\r\n")
        }
    }

    /** Check for upgrades — placeholder (versions are pinned to build artifacts). */
    fun upgrade(): String {
        val installed = getInstalledPackages()
        if (installed.isEmpty()) {
            return "No packages installed.\r\n"
        }
        return "Upgrade check:\r\n" +
               "All installed packages are at their pinned versions.\r\n" +
               "Kernux does not currently support in-place upgrades — " +
               "remove and reinstall to update.\r\n"
    }

    // ─── Private helpers ───────────────────────────────────────────────

    private fun downloadWithProgress(url: String, outputFile: File, sizeInMb: Int) {
        outputFile.parentFile?.mkdirs()

        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 30_000
        conn.readTimeout = 120_000
        conn.setRequestProperty("User-Agent", "Kernux/1.0")
        conn.instanceFollowRedirects = true

        try {
            val code = conn.responseCode
            if (code != 200) {
                throw Exception(
                    "Download HTTP $code. " +
                    "Package may not be built yet — check GitHub Actions: " +
                    "https://github.com/$REPO_OWNER/$REPO_NAME/actions"
                )
            }

            val totalBytes = conn.contentLengthLong.takeIf { it > 0 } ?: (sizeInMb.toLong() * 1024 * 1024)
            var downloaded  = 0L
            var lastReport  = 0L

            conn.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var n: Int
                    while (input.read(buffer).also { n = it } != -1) {
                        output.write(buffer, 0, n)
                        downloaded += n

                        // Update UI every ~250KB
                        if (downloaded - lastReport > 256_000) {
                            val pct = if (totalBytes > 0) (downloaded * 100 / totalBytes).toInt() else -1
                            updateProgress(
                                "Downloading... ${downloaded / 1024}KB" +
                                if (pct in 0..100) " ($pct%)" else ""
                            )
                            lastReport = downloaded
                        }
                    }
                }
            }

            Log.i(TAG, "Downloaded ${outputFile.length()} bytes")
        } finally {
            conn.disconnect()
        }
    }

    private fun installPackageFile(file: File, name: String) {
        val installDir = getInstallDir()
        installDir.mkdirs()

        // Strategy: extract the .opkg into a per-package subdir under usr/
        // then move individual bin/lib/share into the global usr/ tree.
        //
        // .opkg layout (Debian-style, built by build-all-packages.sh):
        //   usr/        ← binaries, libs, share
        //   DEBIAN/
        //     control
        //     postinst  (chmod +x on $PREFIX/bin/*)
        //
        // We extract into a temp dir then merge usr/ into our install dir.
        val tmpExtract = File(getCacheDir(), "_extract_${name}_${System.currentTimeMillis()}")
        tmpExtract.mkdirs()

        try {
            extractTarGz(file, tmpExtract)
            val usrDir = File(tmpExtract, "usr")
            if (!usrDir.exists()) {
                throw Exception("Invalid .opkg: missing usr/ directory")
            }

            // Move usr/* into installDir/usr/
            copyDirContents(usrDir, installDir)

            // Run postinst-equivalent: ensure binaries are executable
            val binDir = File(installDir, "bin")
            if (binDir.exists()) {
                binDir.listFiles()?.forEach { f ->
                    if (f.isFile) f.setExecutable(true, false)
                }
            }
        } finally {
            tmpExtract.deleteRecursively()
        }
    }

    private fun extractTarGz(archive: File, destDir: File) {
        // Use Android's built-in tar via Runtime since java.util doesn't have tar.gz support.
        // App is shell-capable via forkpty, so this works.
        val process = Runtime.getRuntime().exec(
            arrayOf("tar", "-xzf", archive.absolutePath, "-C", destDir.absolutePath)
        )
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val stderr = process.errorStream.bufferedReader().readText()
            throw Exception("tar extract failed (exit=$exitCode): $stderr")
        }
    }

    private fun copyDirContents(src: File, dst: File) {
        dst.mkdirs()
        src.listFiles()?.forEach { child ->
            val target = File(dst, child.name)
            if (child.isDirectory) copyDirContents(child, target)
            else child.copyTo(target, overwrite = true)
        }
    }

    fun getInstalledPackages(): Set<String> {
        val db = File(filesDir, DB_FILE)
        return if (db.exists()) db.readLines().map { it.split("|").firstOrNull() ?: it }.toSet()
               else emptySet()
    }

    private fun isInstalled(name: String) = getInstalledPackages().contains(name)

    private fun markInstalled(name: String, version: String) {
        val current = getInstalledPackages().toMutableSet()
        current.add(name)
        File(filesDir, DB_FILE).writeText(current.joinToString("\n") { "$it|$version" })
    }

    private fun unmarkInstalled(name: String) {
        val current = getInstalledPackages().toMutableSet()
        current.remove(name)
        File(filesDir, DB_FILE).writeText(current.joinToString("\n") { "$it|" })
    }

    private fun getCacheDir()    = File(filesDir, CACHE_DIR).apply { mkdirs() }
    private fun getInstallDir()  = File(filesDir, PREFIX_SUBDIR)

    private fun getDeviceArchitecture(): String {
        val abis = Build.SUPPORTED_ABIS ?: emptyArray()
        return when {
            abis.any { it == "arm64-v8a" }    -> "arm64"
            abis.any { it == "armeabi-v7a" }  -> "armv7"
            abis.any { it == "x86_64" }       -> "x86_64"
            else                                -> "arm64"  // best-effort default
        }
    }

    private fun updateProgress(message: String) {
        Log.d(TAG, message)
        handler.post { onProgress?.invoke(message) }
    }

    private fun notifyComplete(message: String, success: Boolean) {
        handler.post { onComplete?.invoke(message, success) }
    }
}

data class PackageInfo(
    val name: String,
    val version: String,
    val description: String,
    val sizeInMb: Int,
    val preInstalled: Boolean
)
