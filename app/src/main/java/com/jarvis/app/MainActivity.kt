package com.jarvis.app

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val t = TextView(this)
        t.text = "Namaste! Main Jarvis hoon."
        t.textSize = 24f
        t.setPadding(40, 120, 40, 40)
        setContentView(t)
    }
}
