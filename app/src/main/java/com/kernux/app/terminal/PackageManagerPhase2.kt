package com.kernux.app.terminal

import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader

data class Package(
    val name: String,
    val version: String,
    val description: String,
    val size: Long = 0,
    var installed: Boolean = false
)

class PackageManagerPhase2(private val filesDir: File) {
    private val prefixDir = File(filesDir, "usr")
    private val binDir = File(prefixDir, "bin")
    private val libDir = File(prefixDir, "lib")
    private val pkgDir = File(filesDir, ".kernux/packages")
    private val cacheDir = File(filesDir, ".kernux/cache")
    
    private val packages = mutableMapOf<String, Package>()
    private val installedPackages = mutableSetOf<String>()

    init {
        prefixDir.mkdirs()
        binDir.mkdirs()
        libDir.mkdirs()
        pkgDir.mkdirs()
        cacheDir.mkdirs()
        
        loadPackageDatabase()
        checkInstalledPackages()
    }

    private fun loadPackageDatabase() {
        val pkgList = listOf(
            Package("coreutils", "9.1", "GNU core utilities (ls, cat, echo, grep, sed, awk, find, sort, etc.)", 2500000),
            Package("bash", "5.1", "GNU Bourne Again Shell - full bash functionality", 1800000),
            Package("curl", "7.85", "HTTP client and file transfer tool - download files from internet", 900000),
            Package("wget", "1.21", "Alternative file downloader with mirror support", 850000),
            Package("git", "2.38", "Version control system - clone, commit, push repositories", 5000000),
            Package("vim", "9.0", "Powerful text editor with syntax highlighting", 3000000),
            Package("nano", "7.0", "Simple text editor for beginners", 800000),
            Package("iputils", "20220127", "Network utilities - ping, traceroute, etc.", 600000),
            Package("net-tools", "2.10", "Network tools - ifconfig, netstat, arp, etc.", 700000),
            Package("perl", "5.36", "Perl scripting language interpreter", 2000000),
            Package("python", "3.10", "Python interpreter for running Python scripts", 4000000),
            Package("node", "18.0", "Node.js runtime for JavaScript execution", 3500000),
            Package("gcc", "12.0", "GNU C/C++ compiler for development", 6000000),
            Package("make", "4.3", "Build automation tool", 1000000),
            Package("openssh", "8.6", "SSH client and server for remote access", 2500000),
            Package("openssl", "3.0", "SSL/TLS toolkit and crypto library", 3000000)
        )
        
        pkgList.forEach { pkg ->
            val installed = File(pkgDir, "${pkg.name}.installed").exists()
            packages[pkg.name] = pkg.copy(installed = installed)
            if (installed) installedPackages.add(pkg.name)
        }
    }

    private fun checkInstalledPackages() {
        binDir.listFiles()?.forEach { file ->
            installedPackages.add(file.name)
        }
    }

    fun install(packageName: String, onProgress: (String) -> Unit = {}): Boolean {
        val pkg = packages[packageName] ?: return false
        if (pkg.installed) return false
        
        onProgress("Installing $packageName...")
        
        try {
            // Download package (simulated - in real version would download .opkg)
            onProgress("Downloading $packageName-${pkg.version}...")
            val pkgFile = downloadPackage(packageName, pkg.version)
            
            if (!pkgFile.exists()) {
                onProgress("Error: Package download failed")
                return false
            }
            
            onProgress("Extracting $packageName...")
            extractPackage(pkgFile, packageName)
            
            onProgress("Setting permissions...")
            setPermissions(packageName)
            
            onProgress("Running post-install script...")
            runPostInstall(packageName)
            
            // Mark as installed
            File(pkgDir, "$packageName.installed").createNewFile()
            packages[packageName] = pkg.copy(installed = true)
            installedPackages.add(packageName)
            
            onProgress("$packageName installed successfully!")
            return true
        } catch (e: Exception) {
            onProgress("Error installing $packageName: ${e.message}")
            return false
        }
    }

    fun uninstall(packageName: String, onProgress: (String) -> Unit = {}): Boolean {
        val pkg = packages[packageName] ?: return false
        if (!pkg.installed) return false
        
        onProgress("Uninstalling $packageName...")
        
        try {
            onProgress("Removing files...")
            removePackageFiles(packageName)
            
            onProgress("Running pre-remove script...")
            runPreRemove(packageName)
            
            File(pkgDir, "$packageName.installed").delete()
            packages[packageName] = pkg.copy(installed = false)
            installedPackages.remove(packageName)
            
            onProgress("$packageName uninstalled successfully!")
            return true
        } catch (e: Exception) {
            onProgress("Error uninstalling $packageName: ${e.message}")
            return false
        }
    }

    fun list(): List<Package> = packages.values.toList()

    fun installed(): List<Package> = packages.values.filter { it.installed }

    fun search(query: String): List<Package> {
        return packages.values.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true)
        }
    }

    fun info(packageName: String): Package? = packages[packageName]

    fun isInstalled(packageName: String): Boolean {
        return packages[packageName]?.installed ?: false
    }

    // Helper functions
    private fun downloadPackage(name: String, version: String): File {
        // In real Phase 2, this would download from repository
        val pkgFile = File(cacheDir, "$name-$version.opkg")
        
        // Create mock package for now
        if (!pkgFile.exists()) {
            pkgFile.createNewFile()
            // Would download real .opkg here
            // For now, simulates presence
        }
        
        return pkgFile
    }

    private fun extractPackage(pkgFile: File, packageName: String) {
        // Would extract .opkg (tar.gz format) here
        // For now, creates marker files
        File(binDir, packageName).createNewFile()
    }

    private fun setPermissions(packageName: String) {
        File(binDir, packageName).setExecutable(true)
    }

    private fun runPostInstall(packageName: String) {
        // Run postinst script from package
        // Would execute shell commands here
    }

    private fun removePackageFiles(packageName: String) {
        File(binDir, packageName).delete()
    }

    private fun runPreRemove(packageName: String) {
        // Run prerm script
    }
}
