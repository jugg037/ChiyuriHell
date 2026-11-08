package org.chiyurihell

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.wireguard.android.backend.Tunnel
import org.chiyurihell.vpn.EncryptedProfileStore
import org.chiyurihell.vpn.WireGuardController
import org.chiyurihell.vpn.WireGuardProfilePolicy
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class MainActivity : Activity() {
    companion object {
        private const val REQUEST_PROFILE = 4101
        private const val REQUEST_VPN_PERMISSION = 4102
        private const val MAX_PROFILE_BYTES = 256 * 1024
    }

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var statusView: TextView
    private lateinit var profileView: TextView
    private lateinit var importButton: Button
    private lateinit var connectButton: Button
    private lateinit var profileStore: EncryptedProfileStore

    private val stateListener: (Tunnel.State) -> Unit = { state ->
        runOnUiThread { renderState(state) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        WireGuardController.initialize(applicationContext)
        profileStore = EncryptedProfileStore(applicationContext)

        statusView = findViewById(R.id.connectionStatus)
        profileView = findViewById(R.id.profileStatus)
        importButton = findViewById(R.id.importButton)
        connectButton = findViewById(R.id.connectButton)

        importButton.setOnClickListener { chooseProfile() }
        connectButton.setOnClickListener { toggleTunnel() }
        WireGuardController.stateListener = stateListener

        try {
            val savedProfile = profileStore.load()
            if (savedProfile != null) {
                WireGuardController.setProfile(WireGuardProfilePolicy.parseAndValidate(savedProfile))
                profileView.text = "Сохранённый профиль • WireGuard • IPv4 + IPv6 • DNS • ALL"
            }
        } catch (error: Exception) {
            statusView.text = "НЕ УДАЛОСЬ ВОССТАНОВИТЬ ПРОФИЛЬ"
            profileView.text = error.message ?: "Повторно импортируйте конфигурацию WireGuard."
        }

        renderState(WireGuardController.currentState())
        if (WireGuardController.profile != null) {
            profileView.text = "Сохранённый профиль • WireGuard • IPv4 + IPv6 • DNS • ALL"
        }
    }

    override fun onResume() {
        super.onResume()
        if (::connectButton.isInitialized) {
            WireGuardController.stateListener = stateListener
            renderState(WireGuardController.currentState())
        }
    }

    override fun onDestroy() {
        if (::connectButton.isInitialized && WireGuardController.stateListener === stateListener) {
            WireGuardController.stateListener = null
        }
        super.onDestroy()
    }

    private fun renderState(state: Tunnel.State) {
        val backendError = WireGuardController.initializationError
        if (backendError != null) {
            statusView.text = "VPN-ЯДРО НЕДОСТУПНО"
            profileView.text = backendError
            connectButton.text = "ПОДКЛЮЧЕНИЕ НЕДОСТУПНО"
            connectButton.isEnabled = false
            importButton.isEnabled = false
            return
        }

        when (state) {
            Tunnel.State.UP -> {
                statusView.text = "VPN-ТУННЕЛЬ АКТИВЕН"
                connectButton.text = "ОТКЛЮЧИТЬСЯ"
                connectButton.isEnabled = true
                importButton.isEnabled = false
            }
            Tunnel.State.DOWN -> {
                statusView.text = if (WireGuardController.profile == null) {
                    "ИМПОРТИРУЙТЕ ПРОФИЛЬ"
                } else {
                    "ОТКЛЮЧЕНО"
                }
                connectButton.text = "ПОДКЛЮЧИТЬСЯ"
                connectButton.isEnabled = WireGuardController.profile != null
                importButton.isEnabled = true
            }
            Tunnel.State.TOGGLE -> {
                statusView.text = "ИЗМЕНЕНИЕ СОЕДИНЕНИЯ…"
                connectButton.isEnabled = false
                importButton.isEnabled = false
            }
        }
    }

    private fun chooseProfile() {
        if (WireGuardController.currentState() == Tunnel.State.UP) {
            statusView.text = "СНАЧАЛА ОТКЛЮЧИТЕ ТУННЕЛЬ"
            return
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "application/octet-stream"))
        }
        startActivityForResult(intent, REQUEST_PROFILE)
    }

    @Deprecated("Using activity result callback preserves Android 10 compatibility.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_VPN_PERMISSION) {
            if (resultCode == RESULT_OK) {
                connectTunnel()
            } else {
                statusView.text = "РАЗРЕШЕНИЕ VPN НЕ ПРЕДОСТАВЛЕНО"
                connectButton.isEnabled = WireGuardController.profile != null
            }
            return
        }

        if (requestCode == REQUEST_PROFILE && resultCode == RESULT_OK && data?.data != null) {
            importProfile(data.data!!)
        }
    }

    private fun importProfile(uri: android.net.Uri) {
        statusView.text = "ПРОВЕРКА ПРОФИЛЯ…"
        importButton.isEnabled = false
        connectButton.isEnabled = false

        executor.execute {
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_PROFILE_BYTES) { "Файл профиля слишком большой." }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: throw IllegalArgumentException("Не удалось открыть файл профиля.")

                val parsed = WireGuardProfilePolicy.parseAndValidate(bytes)
                profileStore.save(bytes)
                WireGuardController.setProfile(parsed)

                runOnUiThread {
                    profileView.text = "WireGuard • IPv4 + IPv6 • DNS • ALL"
                    statusView.text = "ПРОФИЛЬ ПРОВЕРЕН"
                    importButton.isEnabled = true
                    connectButton.text = "ПОДКЛЮЧИТЬСЯ"
                    connectButton.isEnabled = true
                }
            } catch (error: Exception) {
                runOnUiThread {
                    statusView.text = "ПРОФИЛЬ НЕ ПРИНЯТ"
                    profileView.text = error.message ?: "Не удалось проверить профиль."
                    connectButton.isEnabled = WireGuardController.profile != null
                    importButton.isEnabled = true
                }
            }
        }
    }

    private fun toggleTunnel() {
        if (WireGuardController.initializationError != null) {
            renderState(WireGuardController.currentState())
            return
        }
        if (WireGuardController.profile == null) {
            statusView.text = "СНАЧАЛА ИМПОРТИРУЙТЕ ПРОФИЛЬ"
            return
        }
        connectButton.isEnabled = false
        statusView.text = "ПОДГОТОВКА СОЕДИНЕНИЯ…"

        val permissionIntent = VpnService.prepare(this)
        if (permissionIntent != null) {
            startActivityForResult(permissionIntent, REQUEST_VPN_PERMISSION)
        } else {
            connectTunnel()
        }
    }

    private fun connectTunnel() {
        connectButton.isEnabled = false
        statusView.text = "ПОДКЛЮЧЕНИЕ…"
        executor.execute {
            try {
                val state = WireGuardController.toggle()
                runOnUiThread { renderState(state) }
            } catch (error: Exception) {
                runOnUiThread {
                    statusView.text = "НЕ УДАЛОСЬ ПОДКЛЮЧИТЬСЯ"
                    profileView.text = error.message ?: "Ошибка WireGuard backend."
                    connectButton.text = "ПОВТОРИТЬ"
                    connectButton.isEnabled = WireGuardController.profile != null
                }
            } catch (error: LinkageError) {
                runOnUiThread {
                    statusView.text = "VPN-ЯДРО НЕДОСТУПНО"
                    profileView.text = "Не удалось запустить нативный компонент WireGuard."
                    connectButton.isEnabled = false
                    importButton.isEnabled = false
                }
            }
        }
    }
}
