package com.example.dotkeyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo

class DotKeyboardService : InputMethodService(), KeyListener {
    private var kbView: KeyboardView? = null

    override fun onCreateInputView(): View {
        val v = KeyboardView(this, this)
        kbView = v
        return v
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        kbView?.reset()
    }

    override fun onText(s: String) {
        currentInputConnection?.commitText(s, 1)
    }

    override fun onDelete() {
        val ic = currentInputConnection ?: return
        val sel = ic.getSelectedText(0)
        if (sel.isNullOrEmpty()) ic.deleteSurroundingText(1, 0) else ic.commitText("", 1)
    }

    override fun onEnter() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val noAction = info != null && (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (noAction) {
            ic.commitText("\n", 1)
        } else if (!sendDefaultEditorAction(true)) {
            ic.commitText("\n", 1)
        }
    }

    // The emoji key hands over to your next keyboard (e.g. Gboard) for emoji.
    override fun onEmoji() {
        switchToNextInputMethod(false)
    }
}
