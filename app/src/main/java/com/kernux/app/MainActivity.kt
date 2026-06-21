package com.kernux.app

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.kernux.app.terminal.BootstrapInstaller
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
    private lateinit var bootstrap:      BootstrapInstaller
    private var currentSessionId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        ensureHome()
        sessionManager = SessionManager(filesDir)
        terminalView   = TerminalView(this)
        setContentView(terminalView)

        terminalView.setOnClickListener { terminalView.showKeyboard() }
        terminalView.post { terminalView.showKeyboard() }

        bootstrap = BootstrapInstaller(
            filesDir = filesDir,
            onLog  = { msg -> printToTerminal(msg) },
            onDone = { _ -> startShell() }
        )

        if (!bootstrap.isInstalled) {
            // First run: download bootstrap then start shell
            printToTerminal("\r\n[1;33m=== Kernux First-Time Setup ===\r\n[0m")
            printToTerminal("Downloading Linux environment (~30MB)...\r\n")
            printToTerminal("Please wait, this only happens once!\r\n\r\n")
            bootstrap.install()
        } else {
            startShell()
        }
    }

    private fun startShell() {
        currentSessionId = sessionManager.createSession(filesDir)
        val session = sessionManager.getSession(currentSessionId)!!
        val pkg     = sessionManager.pkgManager

        pkg.onOutput = { text -> printToTerminal(text) }

        session.onScreenUpdated = { terminalView.postInvalidate() }
        session.onSessionExit   = { runOnUiThread { terminalView.postInvalidate() } }

        terminalView.attachSession(session)
        terminalView.onPkgCommand = { cmd -> handlePkgCommand(cmd, pkg) }

        session.start()

        val hasBootstrap = bootstrap.isInstalled
        val boot = if (hasBootstrap) "Bootstrap: READY" else "Basic mode - type: pkg setup"
        session.write(byteArrayOf(0x4B,0x65,0x72,0x6E,0x75,0x78,0x20,0x54,0x65,0x72,0x6D,0x69,0x6E,0x61,0x6C,0x20,0x76,0x32,0x2E,0x30,0x0D,0x0A))
    }

    private fun printToTerminal(text: String) {
        val session = sessionManager.getSession(currentSessionId) ?: run {
            // Session not started yet, buffer into emulator once available
            return
        }
        val bytes = text.toByteArray(Charsets.UTF_8)
        runOnUiThread {
            synchronized(session.emulator) { session.emulator.append(bytes, bytes.size) }
            terminalView.postInvalidate()
        }
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
