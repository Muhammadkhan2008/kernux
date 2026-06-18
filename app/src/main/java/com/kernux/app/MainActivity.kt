package com.kernux.app

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.kernux.app.terminal.PackageManagerPhase2
import com.kernux.app.terminal.SessionManager
import com.kernux.app.terminal.TerminalView
import java.io.File

/**
 * Kernux - Main Activity
 *
 * Termux-style terminal emulator:
 *  - Small base APK
 *  - pkg install <name>  → downloads from GitHub Releases
 *  - pkg list            → show available packages
 *  - pkg search <query>  → search packages
 *  - pkg remove <name>   → remove package
 */
class MainActivity : AppCompatActivity() {

    private lateinit var terminalView:   TerminalView
    private lateinit var sessionManager: SessionManager
    private var currentSessionId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        sessionManager = SessionManager(filesDir)
        terminalView   = TerminalView(this)
        setContentView(terminalView)

        startShell()

        terminalView.setOnClickListener { terminalView.showKeyboard() }
        terminalView.post { terminalView.showKeyboard() }
    }

    private fun startShell() {
        ensureHome()

        currentSessionId = sessionManager.createSession(filesDir)
        val session = sessionManager.getSession(currentSessionId)!!
        val pkg     = sessionManager.pkgManager

        // ── pkg command output → terminal mein show karo ──
        pkg.onOutput = { text ->
            val emulator = session.emulator
            synchronized(emulator) { emulator.append(text.toByteArray(Charsets.UTF_8), text.length) }
            terminalView.postInvalidate()
        }

        // ── Screen update callback ──
        session.onScreenUpdated = { terminalView.postInvalidate() }
        session.onSessionExit   = { runOnUiThread { terminalView.postInvalidate() } }

        terminalView.attachSession(session)
        terminalView.sessionManager = sessionManager

        // ── Set pkg command handler in TerminalView ──
        terminalView.onPkgCommand = { cmd -> handlePkgCommand(cmd, pkg) }

        session.start()

        // ── Welcome message ──
        val welcome = buildString {
            append("\r\n")
            append("[1;32m")
            append("  _  __                         \r\n")
            append(" | |/ / ___ _ __ _ __  _   ___  __\r\n")
            append(" | ' / / _ \\ '__| '_ \\| | | \\ \\/ /\r\n")
            append(" | . \\|  __/ |  | | | | |_| |>  < \r\n")
            append(" |_|\\_\\\\___|_|  |_| |_|\\__,_/_/\\_\\\r\n")
            append("[0m")
            append("\r\n")
            append("[1;33m Kernux Terminal v2.0 - Termux Style! [0m\r\n")
            append(" Type [1;32mpkg list[0m to see available packages\r\n")
            append(" Type [1;32mpkg install git[0m to install git\r\n")
            append(" Type [1;32mpkg install python[0m to install python\r\n")
            append("\r\n")
        }

        val welcomeBytes = welcome.toByteArray(Charsets.UTF_8)
        session.emulator.append(welcomeBytes, welcomeBytes.size)
        terminalView.postInvalidate()
    }

    /**
     * pkg command handler - TerminalView input intercept karta hai
     * Jab user "pkg ..." type kare, ye function call hota hai
     */
    private fun handlePkgCommand(cmd: String, pkg: PackageManagerPhase2) {
        val parts = cmd.trim().split("\\s+".toRegex())
        if (parts.isEmpty() || parts[0] != "pkg") return

        val output = when {
            parts.size == 1 || (parts.size == 2 && parts[1] == "help") -> {
                buildString {
                    append("\r\n[1;36mKernux Package Manager[0m\r\n")
                    append("─────────────────────────────────\r\n")
                    append("  pkg list              - List all packages\r\n")
                    append("  pkg install <name>    - Install a package\r\n")
                    append("  pkg remove  <name>    - Remove a package\r\n")
                    append("  pkg search  <query>   - Search packages\r\n")
                    append("  pkg info    <name>    - Package details\r\n")
                    append("  pkg upgrade           - Check updates\r\n")
                    append("─────────────────────────────────\r\n")
                }
            }
            parts.size >= 2 && parts[1] == "list" -> "\r\n" + pkg.list()
            parts.size >= 2 && parts[1] == "upgrade" -> "\r\n" + pkg.upgrade()
            parts.size >= 3 && parts[1] == "install" -> {
                pkg.install(parts[2])
                return   // async - output via callback
            }
            parts.size >= 3 && parts[1] == "remove" -> {
                pkg.remove(parts[2])
                return   // async - output via callback
            }
            parts.size >= 3 && parts[1] == "search" -> "\r\n" + pkg.search(parts[2])
            parts.size >= 3 && parts[1] == "info"   -> "\r\n" + pkg.info(parts[2])
            else -> "\r\n[31mUnknown command. Try: pkg help[0m\r\n"
        }

        val session = sessionManager.getSession(currentSessionId) ?: return
        val bytes   = output.toByteArray(Charsets.UTF_8)
        synchronized(session.emulator) {
            session.emulator.append(bytes, bytes.size)
        }
        terminalView.postInvalidate()
    }

    private fun ensureHome() {
        try {
            File(filesDir, "usr/bin").mkdirs()
            File(filesDir, "usr/lib").mkdirs()
            File(filesDir, ".kernux/cache").mkdirs()
            val motd = File(filesDir, ".kernux_welcome")
            if (!motd.exists()) motd.writeText("Welcome to Kernux!\n")
        } catch (_: Exception) { }
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionManager.closeAll()
    }
}
