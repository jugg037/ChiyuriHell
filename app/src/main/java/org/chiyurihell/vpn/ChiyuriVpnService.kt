package org.chiyurihell.vpn

import android.net.VpnService

/**
 * VPN service entry point.
 * No tunnel is established in this foundation commit. A reviewed backend must be
 * integrated before this service can route user traffic.
 */
class ChiyuriVpnService : VpnService()
