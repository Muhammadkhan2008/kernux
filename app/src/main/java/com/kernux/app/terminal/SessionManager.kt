package com.kernux.app.terminal

import java.io.File

/**
 * Session manager - multiple terminal sessions handle karta hai.
 * PackageManager bhi yahan inject hota hai taake
 * TerminalSession ke andar 'pkg' command intercept ho sake.
 */
class SessionManager(val filesDir: File) {

    private val sessions      = mutableMapOf<Int, TerminalSession>()
    private var nextSessionId = 1
    var currentSessionId: Int = -1
        private set

    // Shared package manager - sab sessions share karte hain
    val pkgManager = PackageManagerPhase2(filesDir)

    fun createSession(filesDir: File = this.filesDir): Int {
        val sessionId = nextSessionId++
        val home   = filesDir.absolutePath
        val prefix = File(filesDir, "usr").absolutePath
        val binDir = File(filesDir, "usr/bin").absolutePath

        val env = arrayOf(
            "HOME=$home",
            "PREFIX=$prefix",
            "PATH=$binDir:$prefix/local/bin:/system/bin:/system/xbin",
            "TERM=xterm-256color",
            "LANG=en_US.UTF-8",
            "SHELL=/system/bin/sh",
            "USER=kernux",
            "LOGNAME=kernux",
            "PS1=[1;32mkernux@android[0m:[1;34m\$PWD[0m\$ ",
            "PS2=> "
        )

        // Use bootstrap bash if available, else fallback to Android sh
        val bash = File(filesDir, "usr/bin/bash")
        val shell = if (bash.exists() && bash.canExecute()) bash.absolutePath else "/system/bin/sh"
        val args  = arrayOf(shell, "-i")

        val session = TerminalSession(shell, args, env, home)

        // pkg command output → session ke terminal mein print hoga
        pkgManager.onOutput = { text ->
            session.write(("\r\n$text").toByteArray(Charsets.UTF_8))
        }

        sessions[sessionId] = session
        if (currentSessionId == -1) currentSessionId = sessionId
        return sessionId
    }

    fun getSession(id: Int):  TerminalSession?  = sessions[id]
    fun getAllSessions():      List<Int>         = sessions.keys.toList()
    fun getSessionCount():    Int               = sessions.size

    fun switchSession(id: Int): Boolean {
        return if (sessions.containsKey(id)) { currentSessionId = id; true } else false
    }

    fun closeSession(id: Int) {
        sessions[id]?.close()
        sessions.remove(id)
        if (currentSessionId == id) currentSessionId = sessions.keys.firstOrNull() ?: -1
    }

    fun closeAll() {
        sessions.values.forEach { it.close() }
        sessions.clear()
        currentSessionId = -1
    }
}
