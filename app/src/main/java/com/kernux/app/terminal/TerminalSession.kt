package com.kernux.app.terminal

import android.os.Handler
import android.os.Looper
import kotlin.concurrent.thread

/**
 * Ek running shell session.
 * PTY banata hai, reader thread se output padhta hai aur emulator ko feed karta hai.
 * UI ko redraw karne ke liye onScreenUpdated callback call karta hai (main thread par).
 *
 * @param shell  shell binary ka path (jaise /system/bin/sh)
 * @param args   process arguments (argv), pehla element argv[0] hota hai
 * @param env    environment variables ("KEY=VALUE" format)
 * @param cwd    starting working directory
 */
class TerminalSession(
    private val shell: String,
    private val args: Array<String>,
    private val env: Array<String>,
    private val cwd: String,
    initialCols: Int = 80,
    initialRows: Int = 24
) {
    val emulator = TerminalEmulator(initialCols, initialRows)

    /** Screen badalne par UI ko bataane ke liye. MainActivity/TerminalView set karte hain. */
    var onScreenUpdated: (() -> Unit)? = null

    /** Shell exit hone par. */
    var onSessionExit: ((Int) -> Unit)? = null

    private var ptyFd = -1
    private var pid = -1
    private var running = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun start() {
        if (running) return
        val pidArr = IntArray(1)
        ptyFd = NativePty.createSubprocess(
            shell, cwd, args, env, pidArr,
            emulator.rows, emulator.cols
        )
        pid = pidArr[0]
        if (ptyFd < 0) {
            mainHandler.post { onSessionExit?.invoke(-1) }
            return
        }
        running = true
        startReaderThread()
        startWaiterThread()
    }

    private fun startReaderThread() = thread(name = "kernux-reader") {
        val buf = ByteArray(8192)
        while (running) {
            val n = NativePty.readBytes(ptyFd, buf, 0, buf.size)
            if (n <= 0) break
            emulator.append(buf, n)
            mainHandler.post { onScreenUpdated?.invoke() }
        }
    }

    private fun startWaiterThread() = thread(name = "kernux-waiter") {
        val code = NativePty.waitFor(pid)
        running = false
        mainHandler.post { onSessionExit?.invoke(code) }
    }

    fun write(data: ByteArray) {
        if (ptyFd >= 0) NativePty.writeBytes(ptyFd, data, 0, data.size)
    }

    fun write(text: String) = write(text.toByteArray(Charsets.UTF_8))

    /** Note: TerminalView se (cols, rows) order me aata hai. */
    fun resize(cols: Int, rows: Int) {
        emulator.resize(cols, rows)
        if (ptyFd >= 0) NativePty.setPtyWindowSize(ptyFd, rows, cols)
    }

    fun close() {
        running = false
        if (ptyFd >= 0) {
            NativePty.closeFd(ptyFd)
            ptyFd = -1
        }
    }
}
