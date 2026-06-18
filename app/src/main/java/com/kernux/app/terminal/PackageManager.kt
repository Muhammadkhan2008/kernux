package com.kernux.app.terminal

import java.io.File

data class Package(
    val name: String,
    val version: String,
    val description: String,
    val installed: Boolean = false
)

class PackageManager(private val filesDir: File) {
    private val installDir = File(filesDir, "usr/bin")
    private val pkgDir = File(filesDir, ".kernux/packages")
    private val packages = mutableMapOf<String, Package>()

    init {
        installDir.mkdirs()
        pkgDir.mkdirs()
        loadAvailablePackages()
    }

    private fun loadAvailablePackages() {
        val availablePackages = listOf(
            Package("coreutils", "9.0", "GNU core utilities (ls, cat, grep, etc.)", false),
            Package("bash", "5.1", "GNU Bourne Again Shell", false),
            Package("curl", "7.85", "Command line tool for data transfer", false),
            Package("wget", "1.21", "File downloader", false),
            Package("vim", "8.2", "Text editor", false),
            Package("nano", "6.0", "Simple text editor", false),
            Package("git", "2.38", "Version control system", false),
            Package("perl", "5.36", "Scripting language", false),
            Package("python", "3.10", "Python interpreter", false),
            Package("node", "18.0", "Node.js runtime", false),
            Package("iputils", "20220127", "Network utilities (ping, traceroute)", false),
            Package("net-tools", "2.10", "Network tools (ifconfig, netstat)", false)
        )

        availablePackages.forEach { pkg ->
            val installed = File(installDir, pkg.name).exists()
            packages[pkg.name] = pkg.copy(installed = installed)
        }
    }

    fun install(packageName: String): Boolean {
        val pkg = packages[packageName] ?: return false
        if (pkg.installed) return false

        // Placeholder: In Phase 2, will actually download & install
        val marker = File(pkgDir, "$packageName.installed")
        return if (marker.createNewFile()) {
            packages[packageName] = pkg.copy(installed = true)
            true
        } else {
            false
        }
    }

    fun uninstall(packageName: String): Boolean {
        val pkg = packages[packageName] ?: return false
        if (!pkg.installed) return false

        val marker = File(pkgDir, "$packageName.installed")
        return if (marker.delete()) {
            packages[packageName] = pkg.copy(installed = false)
            true
        } else {
            false
        }
    }

    fun list(): List<Package> = packages.values.toList()

    fun search(query: String): List<Package> {
        return packages.values.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true)
        }
    }

    fun info(packageName: String): Package? = packages[packageName]

    fun installed(): List<Package> = packages.values.filter { it.installed }
}
