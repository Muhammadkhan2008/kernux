package com.kernux.app.terminal

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

/**
 * Downloads Termux bootstrap environment on first run.
 * After setup, bash/python/git/gcc/curl all work from filesDir/usr/
 */
class BootstrapInstaller(
    private val filesDir: File,
    private val onLog: (String) -> Unit,
    private val onDone: (Boolean) -> Unit
) {
    companion object {
        private const val TAG = "KernuxBootstrap"
        private const val BASE = "https://github.com/termux/termux-packages/releases/download/bootstrap-2024.01.01"
        const val DONE_MARKER = ".kernux_bootstrap_done"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val handler  = Handler(Looper.getMainLooper())
    val usrDir      get() = File(filesDir, "usr")
    val isInstalled get() = File(filesDir, DONE_MARKER).exists()

    fun install() {
        if (isInstalled) { handler.post { onDone(true) }; return }
        executor.execute { doInstall() }
    }

    private fun doInstall() {
        try {
            val arch  = getArch()
            val cache = File(filesDir, "cache").also { it.mkdirs() }
            val zip   = File(cache, "bootstrap-$arch.zip")
            usrDir.mkdirs()
            log("Architecture: $arch")
            if (!zip.exists() || zip.length() < 1000L) {
                log("Downloading Termux bootstrap (~30MB)...")
                download("$BASE/bootstrap-$arch.zip", zip)
            } else {
                log("Using cached bootstrap")
            }
            log("Extracting...")
            extractZip(zip)
            File(filesDir, DONE_MARKER).writeText("done")
            log("Done! bash python git gcc curl are now available.")
            handler.post { onDone(true) }
        } catch (e: Exception) {
            Log.e(TAG, "Bootstrap failed", e)
            log("Error: ${e.message}")
            handler.post { onDone(false) }
        }
    }

    private fun extractZip(zip: File) {
        val symlinks = mutableListOf<String>()
        ZipInputStream(zip.inputStream().buffered(65536)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    if (entry.name == "SYMLINKS.txt") {
                        symlinks.addAll(zis.bufferedReader().readLines())
                    } else {
                        val out = File(usrDir, entry.name)
                        out.parentFile?.mkdirs()
                        try {
                            FileOutputStream(out).use { zis.copyTo(it) }
                            out.setReadable(true, false)
                            val n = entry.name
                            if (n.contains("/bin/") || n.contains("/libexec/"))
                                out.setExecutable(true, false)
                        } catch (_: Exception) {}
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        log("Processing symlinks...")
        var count = 0
        for (line in symlinks) {
            // Termux SYMLINKS.txt format: target<arrow>linkpath
            val arrow = '\u2190'
            val idx = line.indexOf(arrow)
            if (idx > 0) {
                val target = line.substring(0, idx)
                val link   = File(usrDir, line.substring(idx + 1).trim())
                link.parentFile?.mkdirs()
                try {
                    Runtime.getRuntime().exec(arrayOf("ln", "-sf", target, link.absolutePath)).waitFor()
                    count++
                } catch (_: Exception) {}
            }
        }
        log("$count symlinks created")
        File(usrDir, "bin").listFiles()?.forEach { it.setExecutable(true, false) }
    }

    private fun download(url: String, out: File) {
        out.parentFile?.mkdirs()
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 30000
        conn.readTimeout    = 120000
        conn.setRequestProperty("User-Agent", "Kernux/2.0")
        conn.instanceFollowRedirects = true
        if (conn.responseCode != 200) throw Exception("HTTP ${conn.responseCode}: $url")
        val total = conn.contentLengthLong
        var got = 0L
        conn.inputStream.use { inp ->
            FileOutputStream(out).use { fos ->
                val buf = ByteArray(65536)
                var n: Int
                while (inp.read(buf).also { n = it } != -1) {
                    fos.write(buf, 0, n)
                    got += n
                    if (total > 0 && got % (5 * 1024 * 1024) == 0L)
                        log("${got / 1024 / 1024}MB / ${total / 1024 / 1024}MB")
                }
            }
        }
        conn.disconnect()
        log("Downloaded: ${out.length() / 1024}KB")
    }

    private fun getArch() = when {
        android.os.Build.SUPPORTED_ABIS?.any { it.startsWith("arm64") || it.startsWith("aarch64") } == true -> "aarch64"
        android.os.Build.SUPPORTED_ABIS?.any { it.startsWith("arm") } == true -> "arm"
        android.os.Build.SUPPORTED_ABIS?.any { it.contains("x86_64") } == true -> "x86_64"
        else -> "aarch64"
    }

    private fun log(msg: String) {
        Log.d(TAG, msg)
        handler.post { onLog("$msg
") }
    }
}
