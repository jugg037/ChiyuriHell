package org.chiyurihell

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<TextView>(R.id.connectionStatus).text =
            getString(R.string.infrastructure_not_configured)
        // Keep disabled until a real tunnel backend, trusted profiles, and fail-closed
        // routing are implemented and tested. Never display a fake connected state.
        findViewById<Button>(R.id.connectButton).isEnabled = false
    }
}
