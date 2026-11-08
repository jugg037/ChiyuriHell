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

    private fun changedProfile(transform: (String) -> String): ByteArray =
        transform(validProfile.toString(Charsets.UTF_8)).toByteArray(Charsets.UTF_8)

    @Test
    fun acceptsDualStackFullTunnelProfile() {
        val parsed = WireGuardProfilePolicy.parseAndValidate(validProfile)
        assertEquals(1, parsed.getPeers().size)
    }

    @Test
    fun rejectsProfileMissingIpv4DefaultRoute() {
        val invalid = changedProfile { it.replace("0.0.0.0/0, ", "") }
        assertThrows(IllegalArgumentException::class.java) {
            WireGuardProfilePolicy.parseAndValidate(invalid)
        }
    }

    @Test
    fun rejectsProfileMissingIpv6DefaultRoute() {
        val invalid = changedProfile { it.replace(", ::/0", "") }
        assertThrows(IllegalArgumentException::class.java) {
            WireGuardProfilePolicy.parseAndValidate(invalid)
        }
    }

    @Test
    fun rejectsProfileWithoutIpv6InterfaceAddress() {
        val invalid = changedProfile { it.replace(", fd00::2/128", "") }
        assertThrows(IllegalArgumentException::class.java) {
            WireGuardProfilePolicy.parseAndValidate(invalid)
        }
    }

    @Test
    fun rejectsProfileWithoutDns() {
        val invalid = changedProfile {
            it.replace("DNS = 1.1.1.1, 2606:4700:4700::1111\n", "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WireGuardProfilePolicy.parseAndValidate(invalid)
        }
    }

    @Test
    fun rejectsProfileWithoutEndpoint() {
        val invalid = changedProfile { it.replace("Endpoint = 192.0.2.1:51820\n", "") }
        assertThrows(IllegalArgumentException::class.java) {
            WireGuardProfilePolicy.parseAndValidate(invalid)
        }
    }

    @Test
    fun rejectsApplicationScopedProfile() {
        val invalid = changedProfile {
            it.replace(
                "DNS = 1.1.1.1, 2606:4700:4700::1111",
                "DNS = 1.1.1.1, 2606:4700:4700::1111\nIncludedApplications = com.example.app"
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            WireGuardProfilePolicy.parseAndValidate(invalid)
        }
    }

    @Test
    fun rejectsMultiplePeersInInitialAllMode() {
        val invalid = changedProfile {
            it + "\n[Peer]\nPublicKey = AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=\nAllowedIPs = 10.0.0.0/8\n"
        }
        assertThrows(IllegalArgumentException::class.java) {
            WireGuardProfilePolicy.parseAndValidate(invalid)
        }
    }

    @Test
    fun rejectsOversizedProfile() {
        assertThrows(IllegalArgumentException::class.java) {
            WireGuardProfilePolicy.parseAndValidate(ByteArray(256 * 1024 + 1))
        }
    }

    @Test
    fun rejectsEmptyProfile() {
        assertThrows(IllegalArgumentException::class.java) {
            WireGuardProfilePolicy.parseAndValidate(byteArrayOf())
        }
    }
}
