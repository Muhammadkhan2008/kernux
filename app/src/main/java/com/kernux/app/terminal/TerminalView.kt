package com.kernux.app.terminal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

/**
 * TerminalView — Canvas-based terminal emulator view.
 * Renders the screen grid + handles keyboard input.
 */
class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var session: TerminalSession? = null
    var sessionManager: SessionManager? = null

    /** Callback when user types a "pkg ..." command. MainActivity sets this. */
    var onPkgCommand: ((String) -> Unit)? = null

    private val inputBuffer = StringBuilder()
    private var imc: InputMethodManager =
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF41")
        textSize = 36f
        typeface = Typeface.MONOSPACE
    }
    private val bgPaint = Paint().apply { color = Color.BLACK }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(Color.BLACK)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        session?.emulator?.let { emu ->
            val rows = emu.rows
            val cols = emu.cols
            val cellW = width.toFloat() / cols
            val cellH = textPaint.textSize + 8f
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val ch = emu.charAt(r, c)
                    if (ch != ' ' && ch != '\u0000') {
                        val x = c * cellW + 4f
                        val y = r * cellH + textPaint.textSize
                        canvas.drawText(ch.toString(), x, y, textPaint)
                    }
                }
            }
            // cursor
            val cx = emu.cursorX * cellW + 4f
            val cy = emu.cursorY * cellH
            canvas.drawRect(cx, cy - 4f, cx + cellW * 0.8f, cy + cellH, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) requestFocus()
        return super.onTouchEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val s = session ?: return false
        return when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                val cmd = inputBuffer.toString().trim()
                inputBuffer.clear()
                if (cmd.startsWith("pkg") && onPkgCommand != null) {
                    s.write("\r\n")
                    onPkgCommand?.invoke(cmd)
                } else {
                    s.write("\r\n")
                }
                true
            }
            KeyEvent.KEYCODE_DEL -> {
                if (inputBuffer.isNotEmpty()) inputBuffer.deleteCharAt(inputBuffer.length - 1)
                s.write("\b \b")
                true
            }
            KeyEvent.KEYCODE_DPAD_UP    -> { s.write("\u001b[A"); true }
            KeyEvent.KEYCODE_DPAD_DOWN  -> { s.write("\u001b[B"); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { s.write("\u001b[C"); true }
            KeyEvent.KEYCODE_DPAD_LEFT  -> { s.write("\u001b[D"); true }
            KeyEvent.KEYCODE_TAB        -> { s.write("\t");         true }
            else -> false
        }
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        return object : BaseInputConnection(this, true) {
            override fun commitText(text: CharSequence, cursor: Int): Boolean {
                inputBuffer.append(text)
                session?.write(text.toString().toByteArray(Charsets.UTF_8))
                invalidate()
                return true
            }
        }
    }

    fun showKeyboard() {
        requestFocus()
        imc.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    fun attachSession(s: TerminalSession) {
        session = s
        s.onScreenUpdated = { postInvalidate() }
        invalidate()
    }
}
