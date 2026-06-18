// NEW PackageManagerPhase2.kt - WITH ONLINE DOWNLOADER
// This will replace the old one

package com.kernux.app.terminal

import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.Executors

class PackageManagerPhase2(private val filesDir: File) {
    
    companion object {
        private const val REPO_URL = "https://kernux.com/packages/"
        private const val CACHE_DIR = "packages-cache"
        private const val DB_FILE = "packages.db"
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    
    // Package database with online repo info
    private val packages = mapOf(
        "git" to PackageInfo("git", "2.40", "Version control system", 15, true),
        "python" to PackageInfo("python", "3.11", "Python interpreter", 30, false),
        "gcc" to PackageInfo("gcc", "12", "C/C++ compiler", 50, false),
        "vim" to PackageInfo("vim", "9", "Text editor", 8, false),
        "curl" to PackageInfo("curl", "7.88", "HTTP downloader", 3, true),
        "wget" to PackageInfo("wget", "1.21", "File downloader", 2, true),
        "openssh" to PackageInfo("openssh", "9", "SSH server", 10, false),
        "node" to PackageInfo("node", "18", "Node.js runtime", 40, false),
        "perl" to PackageInfo("perl", "5.36", "Perl interpreter", 12, false),
        "nano" to PackageInfo("nano", "7", "Simple editor", 1, false),
        "openssl" to PackageInfo("openssl", "3.0", "SSL library", 8, false),
        "net-tools" to PackageInfo("net-tools", "2.10", "Network utilities", 1, false),
        "iputils" to PackageInfo("iputils", "20230321", "Ping utilities", 1, false),
        "make" to PackageInfo("make", "4.3", "Build system", 2, false),
        "coreutils" to PackageInfo("coreutils", "9.1", "Core utilities", 20, true),
        "bash" to PackageInfo("bash", "5.2", "Shell", 15, true)
    )
    
    var onProgress: ((String) -> Unit)? = null
    var onComplete: ((String, Boolean) -> Unit)? = null
    
    fun installPackage(name: String) {
        executor.execute {
            try {
                updateProgress("Starting $name installation...")
                
                val pkg = packages[name] ?: throw Exception("Package not found: $name")
                
                // Check if already installed
                if (isInstalled(name)) {
                    updateProgress("$name already installed")
                    notifyComplete("$name already installed", true)
                    return@execute
                }
                
                // Get device architecture
                val arch = getDeviceArchitecture()
                updateProgress("Device: $arch")
                
                // Check cache
                val cachedFile = File(getCacheDir(), "$name-$arch.opkg")
                if (cachedFile.exists()) {
                    updateProgress("Installing $name from cache...")
                    installPackageFile(cachedFile)
                    markInstalled(name)
                    notifyComplete("$name installed!", true)
                    return@execute
                }
                
                // Download from repo
                val filename = "$name-${pkg.version}-$arch.opkg"
                val url = REPO_URL + filename
                
                updateProgress("Downloading $name (${pkg.sizeInMb}MB)...")
                downloadPackage(url, cachedFile)
                
                updateProgress("Installing $name...")
                installPackageFile(cachedFile)
                markInstalled(name)
                
                notifyComplete("$name installed successfully!", true)
                
            } catch (e: Exception) {
                notifyComplete("Error: ${e.message}", false)
            }
        }
    }
    
    private fun downloadPackage(url: String, outputFile: File) {
        val process = Runtime.getRuntime().exec(
            arrayOf("curl", "-L", "-o", outputFile.absolutePath, url)
        )
        if (process.waitFor() != 0) {
            throw Exception("Download failed")
        }
    }
    
    private fun installPackageFile(file: File) {
        val installDir = File(filesDir, "usr/local")
        installDir.mkdirs()
        
        val process = Runtime.getRuntime().exec(
            arrayOf("tar", "-xzf", file.absolutePath, "-C", installDir.absolutePath)
        )
        if (process.waitFor() != 0) {
            throw Exception("Installation failed")
        }
    }
    
    fun uninstallPackage(name: String) {
        try {
            val installDir = File(filesDir, "usr/local/$name")
            installDir.deleteRecursively()
            unmarkInstalled(name)
            updateProgress("$name removed")
        } catch (e: Exception) {
            updateProgress("Error: ${e.message}")
        }
    }
    
    fun listPackages(): List<String> = packages.keys.toList()
    fun searchPackage(query: String): List<String> =
        packages.keys.filter { it.contains(query, ignoreCase = true) }
    fun getPackageInfo(name: String): PackageInfo? = packages[name]
    fun getInstalledPackages(): Set<String> {
        val db = File(filesDir, DB_FILE)
        return if (db.exists()) db.readLines().toSet() else setOf()
    }
    
    private fun isInstalled(name: String) = getInstalledPackages().contains(name)
    private fun markInstalled(name: String) {
        val current = getInstalledPackages().toMutableSet()
        current.add(name)
        File(filesDir, DB_FILE).writeText(current.joinToString("\n"))
    }
    private fun unmarkInstalled(name: String) {
        val current = getInstalledPackages().toMutableSet()
        current.remove(name)
        File(filesDir, DB_FILE).writeText(current.joinToString("\n"))
    }
    
    private fun getCacheDir() = File(filesDir, CACHE_DIR).apply { mkdirs() }
    
    private fun getDeviceArchitecture(): String = when {
        Build.SUPPORTED_ABIS.contains("arm64-v8a") -> "arm64"
        Build.SUPPORTED_ABIS.contains("armeabi-v7a") -> "armv7"
        Build.SUPPORTED_ABIS.contains("x86_64") -> "x86_64"
        else -> "arm64"
    }
    
    private fun updateProgress(message: String) {
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
