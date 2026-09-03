package io.github.dant3.kotest.android.e2e

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/** Minimal activity used by the e2e specs to check that real UI work happens on the device. */
class TestActivity : Activity() {
    lateinit var label: TextView
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        label = TextView(this).apply { text = GREETING }
        setContentView(label)
    }

    companion object {
        const val GREETING: String = "Hello from a real device"
    }
}
