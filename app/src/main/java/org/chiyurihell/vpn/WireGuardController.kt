package org.chiyurihell.vpn

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config

/**
 * Process-wide owner for the WireGuard backend.
 * Keeping the Tunnel identity stable avoids losing backend state on Activity recreation.
 * Imported profiles remain memory-only and are discarded when the process exits.
 */
object WireGuardController : Tunnel {
    private var backend: GoBackend? = null

    @Volatile
    var profile: Config? = null
        private set

    @Volatile
    var state: Tunnel.State = Tunnel.State.DOWN
        private set

    @Volatile
    var stateListener: ((Tunnel.State) -> Unit)? = null

    @Volatile
    var initializationError: String? = null
        private set

    @Synchronized
    fun initialize(context: Context) {
        if (backend != null || initializationError != null) return
        try {
            backend = GoBackend(context.applicationContext)
        } catch (error: Exception) {
            initializationError = error.message ?: "Не удалось загрузить WireGuard backend."
        } catch (error: LinkageError) {
            initializationError = "Не удалось загрузить нативное ядро WireGuard."
        }
    }

    @Synchronized
    fun setProfile(config: Config) {
        profile = config
    }

    fun currentState(): Tunnel.State {
        val activeBackend = backend ?: return Tunnel.State.DOWN
        return activeBackend.getState(this)
    }

    @Synchronized
    @Throws(Exception::class)
    fun toggle(): Tunnel.State {
        val activeBackend = checkNotNull(backend) {
            initializationError ?: "VPN backend ещё не инициализирован."
        }
        val config = checkNotNull(profile) { "Сначала импортируйте профиль." }
        val target = if (activeBackend.getState(this) == Tunnel.State.UP) {
            Tunnel.State.DOWN
        } else {
            Tunnel.State.UP
        }
        val result = activeBackend.setState(
            this,
            target,
            if (target == Tunnel.State.UP) config else null
        )
        state = result
        stateListener?.invoke(result)
        return result
    }

    override fun getName(): String = "ChiyuriHell"

    override fun onStateChange(newState: Tunnel.State) {
        state = newState
        stateListener?.invoke(newState)
    }
}
