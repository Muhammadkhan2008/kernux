package com.kernux.app

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.kernux.app.terminal.TerminalSession
import com.kernux.app.terminal.TerminalView
import java.io.File

/**
 * Kernux ki main screen.
 * Ek TerminalView dikhata hai aur usme ek shell session chalata hai.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var terminalView: TerminalView
    private var session: TerminalSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keyboard khulne par screen adjust ho
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        terminalView = TerminalView(this)
        setContentView(terminalView)

        startShell()

        // Tap karne par keyboard khule
        terminalView.setOnClickListener { terminalView.showKeyboard() }
        terminalView.post { terminalView.showKeyboard() }
    }

    private fun startShell() {
        val home = filesDir.absolutePath              // app ka private HOME
        val prefix = File(filesDir, "usr").absolutePath
        ensureHome(home)

        // ESC byte (0x1B) ko Kotlin string me daalne ke liye  use karte hain.
        val esc = ""
        // mksh (Android /system/bin/sh) PS1 me ANSI color codes seedhe chal jaate hain.
        // Green Kali-style prompt: kernux@localhost:<cwd>$
        val ps1 = "${esc}[1;32mkernux@localhost${esc}[0m:${esc}[1;34m\$PWD${esc}[0m\$ "

        val env = arrayOf(
            "HOME=$home",
            "PREFIX=$prefix",
            "PATH=$prefix/bin:/system/bin:/system/xbin",
            "TERM=xterm-256color",
            "LANG=en_US.UTF-8",
            "PS1=$ps1",
            "PS2=> "
        )

        // System ka shell — har device par maujood, koi cross-compile nahi.
        val shell = "/system/bin/sh"
        val args = arrayOf(shell, "-i")  // interactive

        val s = TerminalSession(
            shell = shell,
            args = args,
            env = env,
            cwd = home
        )
        s.onScreenUpdated = { terminalView.postInvalidate() }
        s.onSessionExit = { runOnUiThread { terminalView.postInvalidate() } }
        session = s
        terminalView.attachSession(s)
        s.start()
    }

    /** Pehli baar HOME me ek welcome file banata hai (Linux feel). */
    private fun ensureHome(home: String) {
        try {
            val motd = File(home, ".kernux_welcome")
            if (!motd.exists()) {
                motd.writeText("Welcome to Kernux\n")
            }
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        session?.close()
    }
}
