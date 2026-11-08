package org.chiyurihell

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import org.chiyurihell.vpn.WireGuardProfilePolicy
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class MainActivity : Activity(), Tunnel {
    companion object {
        private const val REQUEST_PROFILE = 4101
        private const val REQUEST_VPN_PERMISSION = 4102
        private const val MAX_PROFILE_BYTES = 256 * 1024
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val backend by lazy { GoBackend(applicationContext) }

    @Volatile
    private var importedConfig: Config? = null

    private lateinit var statusView: TextView
    private lateinit var profileView: TextView
    private lateinit var importButton: Button
    private lateinit var connectButton: Button

    override fun getName(): String = "ChiyuriHell"

    override fun onStateChange(newState: Tunnel.State) {
        runOnUiThread {
            when (newState) {
                Tunnel.State.UP -> {
                    statusView.text = "ЗАЩИЩЁННОЕ СОЕДИНЕНИЕ АКТИВНО"
                    connectButton.text = "ОТКЛЮЧИТЬСЯ"
                    connectButton.isEnabled = true
                }
                Tunnel.State.DOWN -> {
                    statusView.text = if (importedConfig == null) {
                        "ИМПОРТИРУЙТЕ ПРОФИЛЬ"
                    } else {
                        "ОТКЛЮЧЕНО"
                    }
                    connectButton.text = "ПОДКЛЮЧИТЬСЯ"
                    connectButton.isEnabled = importedConfig != null
                }
                Tunnel.State.TOGGLE -> {
                    statusView.text = "ИЗМЕНЕНИЕ СОЕДИНЕНИЯ…"
                    connectButton.isEnabled = false
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusView = findViewById(R.id.connectionStatus)
        profileView = findViewById(R.id.profileStatus)
        importButton = findViewById(R.id.importButton)
        connectButton = findViewById(R.id.connectButton)

        importButton.setOnClickListener { chooseProfile() }
        connectButton.setOnClickListener { toggleTunnel() }
        connectButton.isEnabled = false
    }

    private fun chooseProfile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "application/octet-stream"))
        }
        startActivityForResult(intent, REQUEST_PROFILE)
    }

    @Deprecated("Using the activity result callback keeps compatibility with Android 10.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return

        when (requestCode) {
            REQUEST_PROFILE -> importProfile(data.data!!)
            REQUEST_VPN_PERMISSION -> connectTunnel()
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
                runOnUiThread {
                    importedConfig = parsed
                    statusView.text = "ПРОФИЛЬ ПРОВЕРЕН"
                    profileView.text = "WireGuard • IPv4 + IPv6 • DNS • ALL"
                    connectButton.text = "ПОДКЛЮЧИТЬСЯ"
                    connectButton.isEnabled = true
                    importButton.isEnabled = true
                }
            } catch (error: Exception) {
                runOnUiThread {
                    statusView.text = "ПРОФИЛЬ НЕ ПРИНЯТ"
                    profileView.text = error.message ?: "Не удалось проверить профиль."
                    connectButton.isEnabled = false
                    importButton.isEnabled = true
                }
            }
        }
    }

    private fun toggleTunnel() {
        val config = importedConfig
        if (config == null) {
            statusView.text = "СНАЧАЛА ИМПОРТИРУЙТЕ ПРОФИЛЬ"
            return
        }

        connectButton.isEnabled = false
        statusView.text = "ПОДГОТОВКА СОЕДИНЕНИЯ…"

        val permissionIntent = VpnService.prepare(this)
        if (permissionIntent != null) {
            startActivityForResult(permissionIntent, REQUEST_VPN_PERMISSION)
        } else {
            setTunnelState(config)
        }
    }

    private fun connectTunnel() {
        val config = importedConfig ?: return
        setTunnelState(config)
    }

    private fun setTunnelState(config: Config) {
        executor.execute {
            try {
                val current = backend.getState(this)
                val target = if (current == Tunnel.State.UP) Tunnel.State.DOWN else Tunnel.State.UP
                val result = backend.setState(this, target, if (target == Tunnel.State.UP) config else null)
                runOnUiThread {
                    if (result == Tunnel.State.UP) {
                        statusView.text = "ЗАЩИЩЁННОЕ СОЕДИНЕНИЕ АКТИВНО"
                        connectButton.text = "ОТКЛЮЧИТЬСЯ"
                    } else {
                        statusView.text = "ОТКЛЮЧЕНО"
                        connectButton.text = "ПОДКЛЮЧИТЬСЯ"
                    }
                    connectButton.isEnabled = true
                }
            } catch (error: Exception) {
                runOnUiThread {
                    statusView.text = "НЕ УДАЛОСЬ ПОДКЛЮЧИТЬСЯ"
                    profileView.text = error.message ?: "Ошибка VPN backend."
                    connectButton.text = "ПОВТОРИТЬ"
                    connectButton.isEnabled = true
                }
            }
        }
    }
}
