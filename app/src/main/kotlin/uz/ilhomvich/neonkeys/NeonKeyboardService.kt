package uz.ilhomvich.neonkeys

import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

class NeonKeyboardService : InputMethodService(), KeyboardCallback {
    private var kv: KeyboardView? = null
    private lateinit var prefs: Prefs
    private lateinit var engine: Suggest
    private var lastSpace = 0L
    private var noSuggest = false

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        engine = Suggest.get(prefs)
    }

    override fun onCreateInputView(): View {
        val v = KeyboardView(this)
        v.callback = this
        kv = v
        return v
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val v = kv ?: return
        try { window?.window?.navigationBarColor = Color.parseColor("#05050A") } catch (e: Throwable) {}
        v.applyPrefs()

        val type = info?.inputType ?: 0
        val cls = type and InputType.TYPE_MASK_CLASS
        val va = type and InputType.TYPE_MASK_VARIATION
        noSuggest = cls != InputType.TYPE_CLASS_TEXT ||
            (type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0 ||
            va == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            va == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            va == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            va == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            va == InputType.TYPE_TEXT_VARIATION_URI

        v.mode = if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) "sym1" else "abc"

        val opts = info?.imeOptions ?: 0
        val action = opts and EditorInfo.IME_MASK_ACTION
        v.enterLabel = if ((opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) null else when (action) {
            EditorInfo.IME_ACTION_GO -> "GO"
            EditorInfo.IME_ACTION_SEARCH -> "FIND"
            EditorInfo.IME_ACTION_SEND -> "SEND"
            EditorInfo.IME_ACTION_NEXT -> "NEXT"
            EditorInfo.IME_ACTION_DONE -> "OK"
            EditorInfo.IME_ACTION_PREVIOUS -> "PREV"
            else -> null
        }
        refresh()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        engine.save()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        refresh()
    }

    // ---------- yordamchilar ----------
    private fun isWordChar(c: Char) = c.isLetter() || c == '\'' || c == 'ʻ' || c == 'ʼ'

    private fun wordBefore(ic: InputConnection): String {
        val b = ic.getTextBeforeCursor(40, 0)?.toString() ?: return ""
        return b.takeLastWhile { isWordChar(it) }.trimStart('\'')
    }

    private fun refresh() {
        val v = kv ?: return
        val ic = currentInputConnection ?: return
        v.suggestions = if (prefs.suggestions && !noSuggest) engine.suggest(wordBefore(ic), prefs.lang) else emptyList()
        if (prefs.autoCap) {
            val ei = currentInputEditorInfo
            val caps = if (ei != null) ic.getCursorCapsMode(ei.inputType) else 0
            v.setAutoShift(caps != 0)
        }
    }

    // ---------- KeyboardCallback ----------
    override fun onText(s: String) {
        val ic = currentInputConnection ?: return
        if (s == " ") {
            val now = SystemClock.uptimeMillis()
            val b = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
            if (b.length == 2 && b[1] == ' ' && b[0].isLetterOrDigit() && now - lastSpace < 700) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                ic.endBatchEdit()
                lastSpace = 0L
                refresh()
                return
            }
            lastSpace = now
        }
        if (!noSuggest && s.length == 1 && (s[0].isWhitespace() || s[0] in ".,!?;:")) {
            val w = wordBefore(ic)
            if (w.isNotEmpty()) engine.learn(w, prefs.lang)
        }
        ic.commitText(s, 1)
        refresh()
    }

    override fun onDelete() {
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        refresh()
    }

    override fun onEnter() {
        val ic = currentInputConnection ?: return
        val opts = currentInputEditorInfo?.imeOptions ?: 0
        val action = opts and EditorInfo.IME_MASK_ACTION
        if ((opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0 &&
            action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED
        ) ic.performEditorAction(action)
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
    }

    override fun onCursor(dir: Int) {
        sendDownUpKeyEvents(if (dir < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
    }

    override fun onSuggestion(s: String) {
        val ic = currentInputConnection ?: return
        val w = wordBefore(ic)
        ic.beginBatchEdit()
        ic.deleteSurroundingText(w.length, 0)
        ic.commitText("$s ", 1)
        ic.endBatchEdit()
        lastSpace = SystemClock.uptimeMillis()
        if (!noSuggest) engine.learn(s, prefs.lang)
        refresh()
    }

    override fun onPicker() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    override fun onLangChanged() = refresh()
}
