package com.kernux.app.terminal

/** JNI bridge to the native PTY layer (libkernux-pty.so). */
object NativePty {
    init {
        System.loadLibrary("kernux-pty")
    }

    external fun createSubprocess(
        cmd: String,
        cwd: String,
        args: Array<String>,
        envVars: Array<String>,
        pidOut: IntArray,
        rows: Int,
        cols: Int
    ): Int

    external fun readBytes(fd: Int, buf: ByteArray, off: Int, len: Int): Int
    external fun writeBytes(fd: Int, buf: ByteArray, off: Int, len: Int): Int
    external fun setPtyWindowSize(fd: Int, rows: Int, cols: Int)
    external fun closeFd(fd: Int)
    external fun waitFor(pid: Int): Int
}
