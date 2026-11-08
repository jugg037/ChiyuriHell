package org.chiyurihell.vpn

import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.net.Inet4Address
import java.net.Inet6Address

/**
 * Validates an imported WireGuard profile against ChiyuriHell's ALL-routing policy.
 * Config objects contain a private key: never log or serialize them.
 */
object WireGuardProfilePolicy {
    private const val MAX_PROFILE_BYTES = 256 * 1024

    @JvmStatic
    fun parseAndValidate(profileBytes: ByteArray): Config {
        require(profileBytes.isNotEmpty()) { "Файл профиля пуст." }
        require(profileBytes.size <= MAX_PROFILE_BYTES) { "Файл профиля слишком большой." }

        val config = Config.parse(ByteArrayInputStream(profileBytes))
        val iface = config.getInterface()
        val peers = config.getPeers()

        require(iface.getAddresses().any { it.getAddress() is Inet4Address }) {
            "В профиле отсутствует IPv4-адрес интерфейса."
        }
        require(iface.getAddresses().any { it.getAddress() is Inet6Address }) {
            "Для режима ALL требуется IPv6-адрес интерфейса."
        }
        require(iface.getDnsServers().isNotEmpty()) {
            "В профиле не задан DNS-сервер."
        }
        require(iface.getIncludedApplications().isEmpty() &&
            iface.getExcludedApplications().isEmpty()) {
            "Профили с ограничением списка приложений не подходят для режима ALL."
        }
        require(peers.size == 1) {
            "Для первого безопасного режима ALL требуется ровно один WireGuard peer."
        }

        val routes = peers.single().getAllowedIps()
        val hasIpv4Default = routes.any {
            it.getAddress() is Inet4Address && it.getMask() == 0
        }
        val hasIpv6Default = routes.any {
            it.getAddress() is Inet6Address && it.getMask() == 0
        }
        require(hasIpv4Default && hasIpv6Default) {
            "Нужны оба маршрута по умолчанию: 0.0.0.0/0 и ::/0. Иначе возможна утечка трафика."
        }
        require(peers.single().getEndpoint().isPresent) {
            "В профиле не указан адрес сервера."
        }

        return config
    }
}
