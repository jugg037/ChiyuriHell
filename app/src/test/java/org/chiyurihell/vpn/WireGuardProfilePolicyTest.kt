package org.chiyurihell.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WireGuardProfilePolicyTest {
    private val validProfile = """
        [Interface]
        PrivateKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
        Address = 10.64.0.2/32, fd00::2/128
        DNS = 1.1.1.1, 2606:4700:4700::1111

        [Peer]
        PublicKey = AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=
        Endpoint = 192.0.2.1:51820
        AllowedIPs = 0.0.0.0/0, ::/0
        PersistentKeepalive = 25
    """.trimIndent().toByteArray(Charsets.UTF_8)

    @Test
    fun acceptsDualStackFullTunnelProfile() {
        val parsed = WireGuardProfilePolicy.parseAndValidate(validProfile)
        assertEquals(1, parsed.getPeers().size)
    }

    @Test
    fun rejectsProfileMissingIpv6DefaultRoute() {
        val invalid = validProfile.toString(Charsets.UTF_8)
            .replace(", ::/0", "")
            .toByteArray(Charsets.UTF_8)
        assertThrows(IllegalArgumentException::class.java) {
            WireGuardProfilePolicy.parseAndValidate(invalid)
        }
    }

    @Test
    fun rejectsProfileWithoutDns() {
        val invalid = validProfile.toString(Charsets.UTF_8)
            .replace("DNS = 1.1.1.1, 2606:4700:4700::1111\n", "")
            .toByteArray(Charsets.UTF_8)
        assertThrows(IllegalArgumentException::class.java) {
            WireGuardProfilePolicy.parseAndValidate(invalid)
        }
    }

    @Test
    fun rejectsEmptyProfile() {
        assertThrows(IllegalArgumentException::class.java) {
            WireGuardProfilePolicy.parseAndValidate(byteArrayOf())
        }
    }
}
