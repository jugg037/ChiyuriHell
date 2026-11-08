package org.chiyurihell

import android.app.Application
import org.chiyurihell.vpn.WireGuardController

/**
 * Initializes the backend and its Always-on callback before Android can start
 * GoBackend.VpnService directly (for example after reboot or process death).
 */
class ChiyuriHellApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WireGuardController.initialize(this)
    }
}
