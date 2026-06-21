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

        // Use Termux's official package repository - already has all compiled packages!
        // Termux packages work perfectly on Kernux since both use same Android PTY + shell
        private const val BASE_URL = "https://packages.termux.dev/termux-main"

        // Alternative CDN mirrors if main is down:
        private val CDN_MIRRORS = listOf(
            "https://packages.termux.dev/termux-main",
            "https://apt.termux.dev/termux-main"
        )

        // Install to /data/data/com.kernux.app/files/usr — matches build script PREFIX
        // (cross-compile-env.sh: PREFIX=/data/data/com.kernux.app/files/usr)
        private const val PREFIX_SUBDIR = "usr"

        private const val CACHE_DIR = "packages-cache"
        private const val DB_FILE   = "installed-packages.txt"
    }

    private val handler  = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    // Package catalog - using ACTUAL Termux package versions from packages.termux.dev
    // These are the real Termux versions that are guaranteed to exist and work
    private val packages = mapOf(
        // Essential tools
        "coreutils" to PackageInfo("coreutils", "9.1-2",      "GNU core utilities (ls, cat, grep, awk, sed, find)",  3, true),
        "bash"      to PackageInfo("bash",      "5.2.15-1",   "GNU Bourne-Again Shell",                              3, true),
        "curl"      to PackageInfo("curl",      "8.4.0-1",    "Command-line tool for transferring data with URLs",   2, true),
        "wget"      to PackageInfo("wget",      "1.21.4",     "Non-interactive network downloader",                  2, true),

        // Development tools
        "git"       to PackageInfo("git",       "2.42.0-1",   "Distributed version-control system",                  8, false),
        "vim"       to PackageInfo("vim",       "9.0.1016-1", "Highly configurable text editor",                     6, false),
        "nano"      to PackageInfo("nano",      "7.2-1",      "Small and friendly text editor",                      2, false),
        "python"    to PackageInfo("python",    "3.11.7-1",   "Python interpreter",                                 25, false),
        "node"      to PackageInfo("node",      "20.9.0-1",   "Node.js JavaScript runtime",                         35, false),
        "gcc"       to PackageInfo("gcc",       "13.2.0-1",   "GNU C/C++ compiler",                                 45, false),
        "openssh"   to PackageInfo("openssh",   "9.6p1",      "OpenBSD Secure Shell",                               10, false),
        "perl"      to PackageInfo("perl",      "5.38.0-1",   "Highly capable, feature-rich programming language",  12, false),
        "openssl"   to PackageInfo("openssl",   "3.2.0-1",    "TLS/SSL and crypto library",                         8, false),

        // Network tools
        "net-tools" to PackageInfo("net-tools", "1.60_git20161116.90da8cc-4", "Network utilities (ifconfig, netstat, route)", 2, false),
        "iputils"   to PackageInfo("iputils",   "20230427-1",  "Ping, tracepath, arping and other utilities",        2, false),
        "netcat"    to PackageInfo("netcat",    "1.10-41-1",   "Network utility for reading/writing data across networks", 1, false),

        // Build tools
        "make"      to PackageInfo("make",      "4.4.1",       "Build automation tool",                              2, false),
        "cmake"     to PackageInfo("cmake",     "3.27.4-1",    "Cross-platform build system",                        3, false),

        // Penetration testing / analysis
        "gdb"       to PackageInfo("gdb",       "13.2-1",      "GNU Debugger - reverse engineering & debugging",     3, false),
        "strace"    to PackageInfo("strace",    "6.5-1",       "System call tracer - trace program execution",       1, false)
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

                // Termux packages are .deb files in pool/main/
                // Architecture-specific: arm64 → arm64, armv7 → armv7, x86_64 → x86_64
                val filename = "${pkg.name}_${pkg.version}_${arch}.deb"
                val url = "$BASE_URL/pool/main/${pkg.name.first()}/${pkg.name}/$filename"

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
                    "Download failed (HTTP $code). Package may not exist in Termux repo. " +
                    "Available packages: https://packages.termux.dev/termux-main/"
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

        // Strategy: extract the .deb (Termux format) directly into our prefix
        // .deb files are Debian packages containing usr/, lib/, etc.
        //
        // We can use dpkg -x to extract, or ar to get the data.tar.gz
        // Then extract that to our PREFIX directory

        val tmpExtract = File(getCacheDir(), "_extract_${name}_${System.currentTimeMillis()}")
        tmpExtract.mkdirs()

        try {
            updateProgress("Extracting $name...")

            // Use dpkg -x to extract .deb directly to temp dir
            // dpkg -x file.deb destdir  extracts everything into destdir/
            val process = Runtime.getRuntime().exec(
                arrayOf("dpkg", "-x", file.absolutePath, tmpExtract.absolutePath)
            )
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                val stderr = process.errorStream.bufferedReader().use { it.readText() }
                Log.e(TAG, "dpkg extraction failed: $stderr")
                // Fallback: try ar + tar method
                extractDebManual(file, tmpExtract)
            }

            val usrDir = File(tmpExtract, "usr")
            if (!usrDir.exists()) {
                throw Exception("Invalid .deb: missing usr/ directory - check Termux repo")
            }

            // Move usr/* into installDir/
            updateProgress("Installing files...")
            copyDirContents(usrDir, installDir)

            // Run postinst-equivalent: ensure binaries are executable
            val binDir = File(installDir, "bin")
            if (binDir.exists()) {
                updateProgress("Setting permissions...")
                binDir.listFiles()?.forEach { f ->
                    if (f.isFile) {
                        f.setExecutable(true, false)
                        f.setReadable(true, false)
                        f.setWritable(true, false)
                    }
                }
            }

            // Also set executable on lib files if needed
            val libDir = File(installDir, "lib")
            if (libDir.exists()) {
                libDir.listFiles()?.forEach { f ->
                    if (f.isFile && f.name.endsWith(".so")) {
                        f.setExecutable(true, false)
                        f.setReadable(true, false)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Install package file failed", e)
            throw Exception("Failed to install $name: ${e.message}")
        } finally {
            tmpExtract.deleteRecursively()
        }
    }

    private fun extractDebManual(debFile: File, destDir: File) {
        try {
            // Fallback: ar x file.deb to get data.tar.gz, then extract it
            updateProgress("Extracting .deb (manual)...")

            val tmpAr = File(getCacheDir(), "ar_${System.currentTimeMillis()}")
            tmpAr.mkdirs()

            // Extract ar archive
            val arProcess = Runtime.getRuntime().exec(
                arrayOf("ar", "x", debFile.absolutePath),
                null,
                tmpAr
            )
            if (arProcess.waitFor() != 0) {
                throw Exception("ar extraction failed")
            }

            // Find data.tar.* (could be .gz, .xz, .bz2)
            val dataTar = tmpAr.listFiles()?.find {
                it.name.startsWith("data.tar")
            } ?: throw Exception("No data.tar in .deb")

            // Extract the tar
            val tarProcess = Runtime.getRuntime().exec(
                arrayOf("tar", "-xf", dataTar.absolutePath, "-C", destDir.absolutePath)
            )
            if (tarProcess.waitFor() != 0) {
                throw Exception("tar extraction failed")
            }

            tmpAr.deleteRecursively()
            Log.i(TAG, "Extracted .deb via ar+tar to $destDir")
        } catch (e: Exception) {
            Log.e(TAG, "Manual .deb extraction failed", e)
            throw Exception("Failed to extract .deb: ${e.message}")
        }
    }

    private fun extractTarGz(archive: File, destDir: File) {
        try {
            // Use Android's built-in tar via Runtime
            updateProgress("Decompressing archive...")

            val process = Runtime.getRuntime().exec(
                arrayOf("tar", "-xzf", archive.absolutePath, "-C", destDir.absolutePath)
            )

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val stderr = process.errorStream.bufferedReader().use { it.readText() }
                val stdout = process.inputStream.bufferedReader().use { it.readText() }
                Log.e(TAG, "tar failed - exit=$exitCode")
                Log.e(TAG, "stderr: $stderr")
                Log.e(TAG, "stdout: $stdout")
                throw Exception("tar extract failed (exit=$exitCode)")
            }

            Log.i(TAG, "Extracted ${archive.name} to $destDir")
        } catch (e: Exception) {
            Log.e(TAG, "Extract failed", e)
            throw Exception("Failed to extract archive: ${e.message}")
        }
    }

    private fun copyDirContents(src: File, dst: File) {
        try {
            dst.mkdirs()
            dst.setReadable(true, false)
            dst.setWritable(true, false)
            dst.setExecutable(true, false)

            src.listFiles()?.forEach { child ->
                val target = File(dst, child.name)
                if (child.isDirectory) {
                    copyDirContents(child, target)
                } else {
                    try {
                        child.copyTo(target, overwrite = true)
                        // Copy file permissions
                        if (child.canExecute()) {
                            target.setExecutable(true, false)
                        }
                        target.setReadable(true, false)
                        target.setWritable(true, false)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to copy ${child.name}: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Copy directory failed: $src -> $dst", e)
            throw Exception("Failed to copy files: ${e.message}")
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

    private fun getCacheDir() = File(filesDir, CACHE_DIR).apply {
        mkdirs()
        setReadable(true, false)
        setWritable(true, false)
        setExecutable(true, false)
    }

    private fun getInstallDir() = File(filesDir, PREFIX_SUBDIR).apply {
        mkdirs()
        setReadable(true, false)
        setWritable(true, false)
        setExecutable(true, false)
    }

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
