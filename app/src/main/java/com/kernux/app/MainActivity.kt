package com.kernux.app

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.kernux.app.terminal.SessionManager
import com.kernux.app.terminal.TerminalView
import java.io.File

/**
 * Kernux ki main screen.
 * Multi-session support ke saath Terminal.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var terminalView: TerminalView
    private lateinit var sessionManager: SessionManager
    private var currentSessionId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        sessionManager = SessionManager(filesDir)
        terminalView = TerminalView(this)
        setContentView(terminalView)

        startShell()

        terminalView.setOnClickListener { terminalView.showKeyboard() }
        terminalView.post { terminalView.showKeyboard() }
    }

    private fun startShell() {
        ensureHome(filesDir.absolutePath)

        currentSessionId = sessionManager.createSession(filesDir)
        val session = sessionManager.getSession(currentSessionId)!!

        session.onScreenUpdated = { terminalView.postInvalidate() }
        session.onSessionExit = { runOnUiThread { terminalView.postInvalidate() } }

        terminalView.attachSession(session)
        terminalView.sessionManager = sessionManager
        session.start()

        session.write("\n")
        session.write("# Kernux v1.0 - Multi-Session Terminal\n")
        session.write("# Session #$currentSessionId started\n\n")
    }

    private fun ensureHome(home: String) {
        try {
            val motd = File(home, ".kernux_welcome")
            if (!motd.exists()) {
                motd.writeText("Welcome to Kernux Terminal Emulator\n")
            }
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionManager.closeAll()
    }
}
