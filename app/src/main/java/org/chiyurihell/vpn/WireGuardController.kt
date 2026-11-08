package org.chiyurihell.vpn

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.util.concurrent.Executors

/**
 * Process-wide owner for the WireGuard backend and stable Tunnel identity.
 * Always-on recovery is entered only from GoBackend's documented system-start callback.
 */
object WireGuardController : Tunnel {
    private var backend: GoBackend? = null
    private var appContext: Context? = null
    private val recoveryExecutor = Executors.newSingleThreadExecutor()

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

    @Volatile
    var recoveryError: String? = null
        private set

    @Synchronized
    fun initialize(context: Context) {
        if (backend != null || initializationError != null) return
        val applicationContext = context.applicationContext
        try {
            appContext = applicationContext
            backend = GoBackend(applicationContext)
            GoBackend.setAlwaysOnCallback { recoverForSystemStart() }
        } catch (error: Exception) {
            initializationError = error.message ?: "Не удалось загрузить WireGuard backend."
        } catch (error: LinkageError) {
            initializationError = "Не удалось загрузить нативное ядро WireGuard."
        }
    }

    /**
     * Android may create GoBackend.VpnService without launching an Activity.
     * The Application initializes this callback before the service is created.
     * Decryption, parsing and backend setup run off the service main thread.
     */
    private fun recoverForSystemStart() {
        val context = appContext ?: return
        recoveryExecutor.execute {
            try {
                val bytes = EncryptedProfileStore(context).load()
                    ?: throw IllegalStateException("Сохранённый профиль не найден. Импортируйте его в приложении.")
                val restoredConfig = WireGuardProfilePolicy.parseAndValidate(bytes)
                synchronized(this) {
                    val activeBackend = checkNotNull(backend) {
                        "WireGuard backend не инициализирован."
                    }
                    profile = restoredConfig
                    val result = activeBackend.setState(this, Tunnel.State.UP, restoredConfig)
                    state = result
                    recoveryError = if (result == Tunnel.State.UP) null else
                        "Android запустил Always-on VPN, но туннель не перешёл в состояние UP."
                    stateListener?.invoke(result)
                }
            } catch (error: Exception) {
                recoveryError = "Не удалось восстановить VPN при системном запуске. Откройте приложение и проверьте сохранённый профиль."
                state = Tunnel.State.DOWN
                stateListener?.invoke(Tunnel.State.DOWN)
            } catch (error: LinkageError) {
                recoveryError = "Не удалось загрузить нативное ядро WireGuard при системном запуске."
                state = Tunnel.State.DOWN
                stateListener?.invoke(Tunnel.State.DOWN)
            }
        }
    }

    @Synchronized
    fun setProfile(config: Config) {
        profile = config
        recoveryError = null
    }

    @Synchronized
    fun clearProfile(clearStoredProfile: () -> Unit) {
        check(currentState() == Tunnel.State.DOWN) {
            "Дождитесь полного отключения VPN, чтобы удалить профиль."
        }
        clearStoredProfile()
        profile = null
        recoveryError = null
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
        if (result == Tunnel.State.UP) recoveryError = null
        stateListener?.invoke(result)
        return result
    }

    override fun getName(): String = "ChiyuriHell"

    override fun onStateChange(newState: Tunnel.State) {
        state = newState
        stateListener?.invoke(newState)
    }
}
