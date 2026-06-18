package com.kernux.app.terminal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.InputType
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import kotlin.math.max

/**
 * TerminalView: emulator ki screen grid ko draw karta hai aur
 * keyboard input ko shell tak pohanchata hai.
 */
class TerminalView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var session: TerminalSession? = null
    var sessionManager: SessionManager? = null

    /** MainActivity sets this to intercept 'pkg ...' commands */
    var onPkgCommand: ((String) -> Unit)? = null

    // Buffer to collect typed input (to detect 'pkg ...' commands)
    private val inputBuffer = StringBuilder()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint()
    private val cursorPaint = Paint()

    private var charWidth = 0f
    private var charHeight = 0f
    private var charAscent = 0f

    // Standard 16-color ANSI palette (Kali/Linux jaisa).
    private val palette = intArrayOf(
        Color.parseColor("#1c1c1c"), // 0 black
        Color.parseColor("#cc0000"), // 1 red
        Color.parseColor("#4e9a06"), // 2 green
        Color.parseColor("#c4a000"), // 3 yellow
        Color.parseColor("#3465a4"), // 4 blue
        Color.parseColor("#75507b"), // 5 magenta
        Color.parseColor("#06989a"), // 6 cyan
        Color.parseColor("#d3d7cf"), // 7 white
        Color.parseColor("#555753"), // 8 bright black
        Color.parseColor("#ef2929"), // 9 bright red
        Color.parseColor("#8ae234"), // 10 bright green
        Color.parseColor("#fce94f"), // 11 bright yellow
        Color.parseColor("#729fcf"), // 12 bright blue
        Color.parseColor("#ad7fa8"), // 13 bright magenta
        Color.parseColor("#34e2e2"), // 14 bright cyan
        Color.parseColor("#eeeeec")  // 15 bright white
    )

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        textPaint.typeface = Typeface.MONOSPACE
        textPaint.textSize = 32f
        charWidth = textPaint.measureText("M")
        val fm = textPaint.fontMetrics
        charHeight = fm.descent - fm.ascent
        charAscent = -fm.ascent
        cursorPaint.color = Color.parseColor("#8ae234")
        setBackgroundColor(palette[0])
    }

    fun attachSession(s: TerminalSession) {
        session = s
        s.onScreenUpdated = { post { invalidate() } }
        recomputeSize()
    }

    private fun recomputeSize() {
        val s = session ?: return
        if (width == 0 || height == 0) return
        val cols = max(1, (width / charWidth).toInt())
        val rows = max(1, (height / charHeight).toInt())
        s.resize(cols, rows)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        recomputeSize()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val emu = session?.emulator ?: return
        synchronized(emu) {
            for (r in 0 until emu.rows) {
                val y = r * charHeight
                for (c in 0 until emu.cols) {
                    val cell = emu.screen[r][c]
                    val x = c * charWidth
                    // background
                    if (cell.bg != TerminalEmulator.COLOR_DEFAULT_BG) {
                        bgPaint.color = palette[cell.bg.coerceIn(0, 15)]
                        canvas.drawRect(x, y, x + charWidth, y + charHeight, bgPaint)
                    }
                    // cursor block
                    if (r == emu.cursorRow && c == emu.cursorCol) {
                        canvas.drawRect(x, y, x + charWidth, y + charHeight, cursorPaint)
                    }
                    if (cell.ch != ' ') {
                        textPaint.color = palette[cell.fg.coerceIn(0, 15)]
                        textPaint.isFakeBoldText = cell.bold
                        canvas.drawText(cell.ch.toString(), x, y + charAscent, textPaint)
                    }
                }
            }
        }
    }

    // ---- Keyboard input ----

    override fun onCheckIsTextEditor() = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_NULL
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                text?.let {
                    inputBuffer.append(it)   // track typed chars for pkg detection
                    session?.write(it.toString())
                }
                return true
            }
            override fun sendKeyEvent(event: KeyEvent?): Boolean {
                if (event != null && event.action == KeyEvent.ACTION_DOWN) {
                    handleKey(event)
                }
                return true
            }
            override fun deleteSurroundingText(before: Int, after: Int): Boolean {
                repeat(before) { session?.write("") } // DEL
                return true
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (handleKey(event)) return true
        return super.onKeyDown(keyCode, event)
    }

    private fun handleKey(event: KeyEvent): Boolean {
        val s = session ?: return false
        when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                val cmd = inputBuffer.toString().trim()
                inputBuffer.clear()
                if (cmd.startsWith("pkg") && onPkgCommand != null) {
                    s.write("
")
                    onPkgCommand?.invoke(cmd)
                } else {
                    s.write("
")
                }
                return true
            }
            KeyEvent.KEYCODE_DEL -> {
                if (inputBuffer.isNotEmpty()) inputBuffer.deleteCharAt(inputBuffer.length - 1)
                s.write("")
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP    -> { s.write("[A"); return true }
            KeyEvent.KEYCODE_DPAD_DOWN  -> { s.write("[B"); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { s.write("[C"); return true }
            KeyEvent.KEYCODE_DPAD_LEFT  -> { s.write("[D"); return true }
            KeyEvent.KEYCODE_TAB        -> { s.write("	"); return true }
        }
        val ch = event.unicodeChar
        if (ch != 0) {
            val c = ch.toChar()
            inputBuffer.append(c)
            s.write(c.toString())
            return true
        }
        return false
    }

    /** Special keys jaise Ctrl, Esc bhejne ke liye (extra key row se). */
    fun sendString(str: String) = session?.write(str)

    /** Soft keyboard dikhata hai aur focus leta hai. */
    fun showKeyboard() {
        requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(this, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }
}
