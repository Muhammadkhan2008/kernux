package com.kernux.app.terminal

/**
 * Chhota VT100/ANSI terminal emulator.
 * Screen ko characters + color attributes ke grid me rakhta hai.
 * Shell se aane wale bytes (incl. \033[...m escape codes) ko parse karta hai.
 */
class TerminalEmulator(var cols: Int, var rows: Int) {

    // Har cell: ek character + foreground/background color index.
    class Cell {
        var ch: Char = ' '
        var fg: Int = COLOR_DEFAULT_FG
        var bg: Int = COLOR_DEFAULT_BG
        var bold: Boolean = false
    }

    var screen = Array(rows) { Array(cols) { Cell() } }
    var cursorRow = 0
    var cursorCol = 0

    private var curFg = COLOR_DEFAULT_FG
    private var curBg = COLOR_DEFAULT_BG
    private var curBold = false

    // Escape sequence parsing state
    private enum class State { NORMAL, ESC, CSI }
    private var state = State.NORMAL
    private val csiParams = StringBuilder()

    @Synchronized
    fun append(bytes: ByteArray, len: Int) {
        val text = String(bytes, 0, len, Charsets.UTF_8)
        for (c in text) process(c)
    }

    private fun process(c: Char) {
        val code = c.code
        when (state) {
            State.NORMAL -> when (code) {
                27 -> state = State.ESC               // ESC
                10 -> newLine()                       // \n
                13 -> cursorCol = 0                   // \r
                8  -> if (cursorCol > 0) cursorCol--  // backspace
                9  -> cursorCol = ((cursorCol / 8) + 1) * 8  // tab
                7  -> {}                              // bell, ignore
                else -> if (code >= 32) putChar(c)
            }
            State.ESC -> when (c) {
                '[' -> { state = State.CSI; csiParams.clear() }
                else -> state = State.NORMAL          // unsupported, drop
            }
            State.CSI -> {
                if (c in '0'..'9' || c == ';' || c == '?') {
                    csiParams.append(c)
                } else {
                    handleCsi(c, csiParams.toString())
                    state = State.NORMAL
                }
            }
        }
    }

    private fun handleCsi(cmd: Char, params: String) {
        val clean = params.removePrefix("?")
        val nums = clean.split(';').map { it.toIntOrNull() ?: 0 }
        when (cmd) {
            'H', 'f' -> { // cursor position
                cursorRow = ((nums.getOrElse(0) { 1 }) - 1).coerceIn(0, rows - 1)
                cursorCol = ((nums.getOrElse(1) { 1 }) - 1).coerceIn(0, cols - 1)
            }
            'A' -> cursorRow = (cursorRow - nums.getOrElse(0) { 1 }).coerceAtLeast(0)
            'B' -> cursorRow = (cursorRow + nums.getOrElse(0) { 1 }).coerceAtMost(rows - 1)
            'C' -> cursorCol = (cursorCol + nums.getOrElse(0) { 1 }).coerceAtMost(cols - 1)
            'D' -> cursorCol = (cursorCol - nums.getOrElse(0) { 1 }).coerceAtLeast(0)
            'J' -> eraseDisplay(nums.getOrElse(0) { 0 })
            'K' -> eraseLine(nums.getOrElse(0) { 0 })
            'm' -> applySgr(if (clean.isEmpty()) listOf(0) else nums)
            else -> {} // unsupported CSI, ignore
        }
    }

    // SGR: colors aur bold set karna (\033[...m)
    private fun applySgr(codes: List<Int>) {
        for (n in codes) {
            when (n) {
                0 -> { curFg = COLOR_DEFAULT_FG; curBg = COLOR_DEFAULT_BG; curBold = false }
                1 -> curBold = true
                22 -> curBold = false
                in 30..37 -> curFg = n - 30
                39 -> curFg = COLOR_DEFAULT_FG
                in 40..47 -> curBg = n - 40
                49 -> curBg = COLOR_DEFAULT_BG
                in 90..97 -> curFg = (n - 90) + 8     // bright
                in 100..107 -> curBg = (n - 100) + 8
            }
        }
    }

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            2, 3 -> { for (r in 0 until rows) for (c in 0 until cols) clearCell(r, c)
                      cursorRow = 0; cursorCol = 0 }
            0 -> { for (c in cursorCol until cols) clearCell(cursorRow, c)
                   for (r in cursorRow + 1 until rows) for (c in 0 until cols) clearCell(r, c) }
            1 -> { for (r in 0 until cursorRow) for (c in 0 until cols) clearCell(r, c)
                   for (c in 0..cursorCol) clearCell(cursorRow, c) }
        }
    }

    private fun eraseLine(mode: Int) {
        when (mode) {
            0 -> for (c in cursorCol until cols) clearCell(cursorRow, c)
            1 -> for (c in 0..cursorCol) clearCell(cursorRow, c)
            2 -> for (c in 0 until cols) clearCell(cursorRow, c)
        }
    }

    private fun clearCell(r: Int, c: Int) {
        val cell = screen[r][c]
        cell.ch = ' '; cell.fg = COLOR_DEFAULT_FG; cell.bg = COLOR_DEFAULT_BG; cell.bold = false
    }

    private fun putChar(c: Char) {
        if (cursorCol >= cols) { cursorCol = 0; newLine() }
        val cell = screen[cursorRow][cursorCol]
        cell.ch = c; cell.fg = curFg; cell.bg = curBg; cell.bold = curBold
        cursorCol++
    }

    private fun newLine() {
        cursorRow++
        if (cursorRow >= rows) { scrollUp(); cursorRow = rows - 1 }
    }

    private fun scrollUp() {
        val first = screen[0]
        for (r in 0 until rows - 1) screen[r] = screen[r + 1]
        for (c in 0 until cols) { first[c].ch = ' '; first[c].fg = COLOR_DEFAULT_FG
                                  first[c].bg = COLOR_DEFAULT_BG; first[c].bold = false }
        screen[rows - 1] = first
    }

    @Synchronized
    fun resize(newCols: Int, newRows: Int) {
        if (newCols == cols && newRows == rows) return
        val newScreen = Array(newRows) { r -> Array(newCols) { c ->
            if (r < rows && c < cols) screen[r][c] else Cell()
        } }
        screen = newScreen
        cols = newCols; rows = newRows
        cursorRow = cursorRow.coerceIn(0, rows - 1)
        cursorCol = cursorCol.coerceIn(0, cols - 1)
    }

    companion object {
        const val COLOR_DEFAULT_FG = 7
        const val COLOR_DEFAULT_BG = 0
    }
}
