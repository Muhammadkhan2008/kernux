package com.kernux.app.terminal

import java.io.File

class SessionManager {
    private val sessions = mutableMapOf<Int, TerminalSession>()
    private var nextSessionId = 1
    var currentSessionId: Int = -1
        private set

    fun createSession(filesDir: File): Int {
        val sessionId = nextSessionId++
        val home = filesDir.absolutePath
        val prefix = File(filesDir, "usr").absolutePath

        val esc = ""
        val ps1 = "${esc}[1;32mkernux@localhost${esc}[0m:${esc}[1;34m\$PWD${esc}[0m\$ "

        val env = arrayOf(
            "HOME=$home",
            "PREFIX=$prefix",
            "PATH=$prefix/bin:$prefix/usr/bin:$prefix/usr/local/bin:/system/bin:/system/xbin",
            "TERM=xterm-256color",
            "LANG=en_US.UTF-8",
            "PS1=$ps1",
            "PS2=> "
        )

        val shell = "/system/bin/sh"
        val args = arrayOf(shell, "-i")

        val session = TerminalSession(shell, args, env, home)
        sessions[sessionId] = session

        if (currentSessionId == -1) {
            currentSessionId = sessionId
        }

        return sessionId
    }

    fun getSession(sessionId: Int): TerminalSession? = sessions[sessionId]

    fun switchSession(sessionId: Int): Boolean {
        return if (sessions.containsKey(sessionId)) {
            currentSessionId = sessionId
            true
        } else {
            false
        }
    }

    fun closeSession(sessionId: Int) {
        sessions[sessionId]?.close()
        sessions.remove(sessionId)
        if (currentSessionId == sessionId) {
            currentSessionId = sessions.keys.firstOrNull() ?: -1
        }
    }

    fun getAllSessions(): List<Int> = sessions.keys.toList()

    fun getSessionCount(): Int = sessions.size

    fun closeAll() {
        sessions.values.forEach { it.close() }
        sessions.clear()
        currentSessionId = -1
    }
}
