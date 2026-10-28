package com.example.dotkeyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            gravity = Gravity.TOP
            setBackgroundColor(Color.BLACK)
        }
        fun text(s: String, size: Float) = TextView(this).apply {
            this.text = s; textSize = size; setTextColor(Color.WHITE)
            setPadding(0, pad / 2, 0, pad / 2)
        }
        root.addView(text("Dot Keyboard", 28f))
        root.addView(text("1. Enable it, then 2. switch to it, then 3. type in the box below.", 15f))
        root.addView(Button(this).apply {
            text = "1. Enable keyboard"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        })
        root.addView(Button(this).apply {
            text = "2. Switch keyboard"
            setOnClickListener {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        })
        root.addView(EditText(this).apply {
            hint = "3. Test here"
            setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
        })
        setContentView(root)
    }
}
