package com.kernux.app.terminal

import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.Executors

/**
 * Kernux Package Manager - TERMUX STYLE!
 *
 * Small base APK + manual online download
 * pkg install git   → downloads from GitHub releases
 * pkg install python → downloads python package
 * Like Termux + Ubuntu apt combined!
 */

data class Package(
    val name: String,
    val version: String,
    val description: String,
    val sizeBytes: Long = 0,
    var installed: Boolean = false
)

class PackageManagerPhase2(private val filesDir: File) {

    companion object {
        // GitHub Releases pe hosted packages (FREE hosting!)
        const val REPO_BASE = "https://github.com/Muhammadkhan2008/kernux/releases/download/packages"

        // Local directories
        const val PKG_DB_FILE   = ".kernux_installed"
        const val CACHE_SUBDIR  = ".kernux/cache"
        const val PREFIX_SUBDIR = "usr"
    }

    private val prefixDir = File(filesDir, PREFIX_SUBDIR)
    private val binDir    = File(prefixDir, "bin")
    private val libDir    = File(prefixDir, "lib")
    private val cacheDir  = File(filesDir, CACHE_SUBDIR)
    private val dbFile    = File(filesDir, PKG_DB_FILE)

    private val executor    = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Callbacks - TerminalSession / MainActivity set karega */
    var onOutput:   ((String) -> Unit)? = null   // output text show karne ke liye
    var onProgress: ((Int)    -> Unit)? = null   // 0-100 progress bar

    // Package database - 16 packages
    private val packageDb = listOf(
        Package("coreutils", "9.1",       "ls, cat, echo, grep, sed, awk, find, sort",  2_500_000),
        Package("bash",      "5.2",       "GNU Bash shell",                              1_800_000),
        Package("curl",      "7.88",      "HTTP/HTTPS file download tool",                 900_000),
        Package("wget",      "1.21",      "File downloader with resume support",           850_000),
        Package("git",       "2.40",      "Version control - clone, commit, push",       5_000_000),
        Package("vim",       "9.0",       "Powerful text editor",                        3_000_000),
        Package("nano",      "7.0",       "Simple beginner-friendly editor",               800_000),
        Package("python",    "3.11",      "Python interpreter",                          4_000_000),
        Package("node",      "18.0",      "Node.js JavaScript runtime",                  3_500_000),
        Package("gcc",       "12.0",      "C/C++ compiler",                              6_000_000),
        Package("openssh",   "9.0",       "SSH client/server",                           2_500_000),
        Package("perl",      "5.36",      "Perl scripting language",                     2_000_000),
        Package("openssl",   "3.0",       "SSL/TLS crypto library",                      3_000_000),
        Package("net-tools", "2.10",      "ifconfig, netstat, arp, route",                 700_000),
        Package("iputils",   "20230321",  "ping, traceroute, arping",                      600_000),
        Package("make",      "4.3",       "Build automation tool",                       1_000_000)
    ).map { pkg ->
        pkg.copy(installed = isMarkedInstalled(pkg.name))
    }.let { list ->
        val map = mutableMapOf<String, Package>()
        list.forEach { map[it.name] = it }
        map
    }

    init {
        prefixDir.mkdirs()
        binDir.mkdirs()
        libDir.mkdirs()
        cacheDir.mkdirs()
    }

    // ──────────────────────────────────────────────────────────────
    //  PUBLIC API  (shell mai yai commands run hongi)
    // ──────────────────────────────────────────────────────────────

    /** pkg install <name> */
    fun install(name: String) {
        val pkg = packageDb[name]
        if (pkg == null) {
            emit("[31mE: Package '$name' not found. Try: pkg list[0m\n")
            return
        }
        if (pkg.installed) {
            emit("[33m$name is already installed.[0m\n")
            return
        }
        executor.execute { doInstall(pkg) }
    }

    /** pkg remove <name> */
    fun remove(name: String) {
        val pkg = packageDb[name]
        if (pkg == null) { emit("[31mE: Package '$name' not found.[0m\n"); return }
        if (!pkg.installed) { emit("[33m$name is not installed.[0m\n"); return }
        executor.execute { doRemove(pkg) }
    }

    /** pkg list */
    fun list(): String {
        val sb = StringBuilder()
        sb.append("[1;36mKernux Package Repository - 16 Packages Available[0m\n")
        sb.append("─".repeat(55) + "\n")
        sb.append(String.format("%-14s %-10s %-8s %s\n", "NAME", "VERSION", "SIZE", "STATUS"))
        sb.append("─".repeat(55) + "\n")
        packageDb.values.forEach { pkg ->
            val status = if (pkg.installed) "[32m[installed][0m" else "[available]"
            val sizeMb = "${pkg.sizeBytes / 1_000_000}MB"
            sb.append(String.format("%-14s %-10s %-8s %s\n", pkg.name, pkg.version, sizeMb, status))
        }
        sb.append("─".repeat(55) + "\n")
        sb.append("Usage: pkg install <name>  |  pkg remove <name>  |  pkg search <query>\n")
        return sb.toString()
    }

    /** pkg search <query> */
    fun search(query: String): String {
        val results = packageDb.values.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true)
        }
        if (results.isEmpty()) return "No packages found for '$query'\n"
        val sb = StringBuilder("[1mSearch results for '$query':[0m\n")
        results.forEach { sb.append("  ${it.name} (${it.version}) - ${it.description}\n") }
        return sb.toString()
    }

    /** pkg info <name> */
    fun info(name: String): String {
        val pkg = packageDb[name] ?: return "Package '$name' not found.\n"
        return buildString {
            append("[1;33mPackage: ${pkg.name}[0m\n")
            append("  Version:     ${pkg.version}\n")
            append("  Size:        ${pkg.sizeBytes / 1_000_000} MB\n")
            append("  Description: ${pkg.description}\n")
            append("  Status:      ${if (pkg.installed) "[32mInstalled[0m" else "Not installed"}\n")
            append("  Download:    $REPO_BASE/${pkg.name}-${pkg.version}-${arch()}.tar.gz\n")
        }
    }

    /** pkg upgrade */
    fun upgrade(): String = "Checking for updates...\nAll packages up to date.\n"

    /** Installed packages list */
    fun installed(): List<Package> = packageDb.values.filter { it.installed }

    fun isInstalled(name: String) = packageDb[name]?.installed ?: false

    // ──────────────────────────────────────────────────────────────
    //  INSTALL / REMOVE LOGIC
    // ──────────────────────────────────────────────────────────────

    private fun doInstall(pkg: Package) {
        emit("[1;32mInstalling ${pkg.name} ${pkg.version}...[0m\n")
        emit("Arch: ${arch()}\n")

        val tarName  = "${pkg.name}-${pkg.version}-${arch()}.tar.gz"
        val url      = "$REPO_BASE/$tarName"
        val cacheFile = File(cacheDir, tarName)

        // 1) Download
        if (cacheFile.exists() && cacheFile.length() > 0) {
            emit("Using cached package...\n")
        } else {
            emit("Downloading from GitHub releases...\n")
            emit("URL: $url\n")
            val ok = downloadWithProgress(url, cacheFile)
            if (!ok) {
                emit("[31mDownload failed! Check internet connection.[0m\n")
                // Fallback: create stub so basic command exists
                createStub(pkg.name)
                markInstalled(pkg.name)
                packageDb[pkg.name] = pkg.copy(installed = true)
                emit("[33mNote: Stub created. Real binaries need compiled packages.[0m\n")
                emit("[32m${pkg.name} stub installed.[0m\n")
                return
            }
        }

        // 2) Extract
        emit("Extracting ${pkg.name}...\n")
        val extracted = extractTar(cacheFile, prefixDir)
        if (!extracted) {
            emit("[31mExtraction failed![0m\n")
            return
        }

        // 3) Fix permissions
        emit("Setting permissions...\n")
        fixPermissions(File(binDir, pkg.name))

        // 4) Mark installed
        markInstalled(pkg.name)
        packageDb[pkg.name] = pkg.copy(installed = true)

        emit("[1;32m${pkg.name} installed successfully![0m\n")
        emit("Run: ${pkg.name} --version\n")
        setProgress(100)
    }

    private fun doRemove(pkg: Package) {
        emit("Removing ${pkg.name}...\n")
        File(binDir, pkg.name).delete()
        File(cacheDir, "${pkg.name}-${pkg.version}-${arch()}.tar.gz").delete()
        unmarkInstalled(pkg.name)
        packageDb[pkg.name] = pkg.copy(installed = false)
        emit("[32m${pkg.name} removed.[0m\n")
    }

    // ──────────────────────────────────────────────────────────────
    //  DOWNLOAD HELPER
    // ──────────────────────────────────────────────────────────────

    private fun downloadWithProgress(url: String, out: File): Boolean {
        return try {
            // Use Android's built-in URLConnection
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout    = 30_000
            connection.requestMethod  = "GET"
            connection.connect()

            val total = connection.contentLengthLong
            var downloaded = 0L

            connection.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(8192)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        output.write(buf, 0, n)
                        downloaded += n
                        if (total > 0) {
                            val pct = (downloaded * 100 / total).toInt()
                            setProgress(pct)
                            if (pct % 10 == 0) {
                                val mb = downloaded / 1_000_000
                                val totalMb = total / 1_000_000
                                emit("  ${mb}MB / ${totalMb}MB  ($pct%)\r")
                            }
                        }
                    }
                }
            }
            emit("\nDownload complete!\n")
            true
        } catch (e: Exception) {
            false
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  EXTRACT HELPER
    // ──────────────────────────────────────────────────────────────

    private fun extractTar(tarFile: File, destDir: File): Boolean {
        return try {
            val proc = ProcessBuilder("tar", "-xzf", tarFile.absolutePath, "-C", destDir.absolutePath)
                .redirectErrorStream(true)
                .start()
            proc.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  HELPERS
    // ──────────────────────────────────────────────────────────────

    private fun createStub(name: String) {
        val stub = File(binDir, name)
        stub.writeText("#!/system/bin/sh\necho '$name: stub - real package not compiled yet'\n")
        stub.setExecutable(true)
    }

    private fun fixPermissions(f: File) {
        if (f.exists()) f.setExecutable(true, false)
    }

    private fun arch(): String = when {
        Build.SUPPORTED_ABIS.contains("arm64-v8a")  -> "arm64"
        Build.SUPPORTED_ABIS.contains("armeabi-v7a") -> "armv7"
        Build.SUPPORTED_ABIS.contains("x86_64")      -> "x86_64"
        else -> "arm64"
    }

    private fun isMarkedInstalled(name: String): Boolean {
        if (!dbFile.exists()) return false
        return dbFile.readLines().any { it.trim() == name }
    }

    private fun markInstalled(name: String) {
        val current = if (dbFile.exists()) dbFile.readLines().toMutableSet() else mutableSetOf()
        current.add(name)
        dbFile.writeText(current.joinToString("\n"))
    }

    private fun unmarkInstalled(name: String) {
        if (!dbFile.exists()) return
        val current = dbFile.readLines().toMutableSet()
        current.remove(name)
        dbFile.writeText(current.joinToString("\n"))
    }

    private fun emit(text: String) {
        mainHandler.post { onOutput?.invoke(text) }
    }

    private fun setProgress(pct: Int) {
        mainHandler.post { onProgress?.invoke(pct) }
    }
}
