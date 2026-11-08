package org.chiyurihell.vpn

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores a single WireGuard profile encrypted with an AES-256-GCM key held by Android Keystore.
 * The plaintext configuration (including its private key) is never written to disk.
 */
class EncryptedProfileStore(context: Context) {
    private val file = AtomicFile(context.applicationContext.getFileStreamPath(FILE_NAME))
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    @Synchronized
    @Throws(Exception::class)
    fun save(profile: ByteArray) {
        require(profile.isNotEmpty() && profile.size <= MAX_PROFILE_BYTES) {
            "Недопустимый размер профиля."
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(AAD)
        val encrypted = cipher.doFinal(profile)
        val iv = cipher.iv
        require(iv.size == GCM_IV_BYTES) { "Android Keystore вернул некорректный IV." }

        var output: java.io.FileOutputStream? = null
        try {
            output = file.startWrite()
            DataOutputStream(output).use { stream ->
                stream.write(MAGIC)
                stream.writeByte(iv.size)
                stream.write(iv)
                stream.writeInt(encrypted.size)
                stream.write(encrypted)
                stream.flush()
            }
            file.finishWrite(output)
        } catch (error: Exception) {
            if (output != null) file.failWrite(output)
            throw error
        }
    }

    @Synchronized
    @Throws(Exception::class)
    fun load(): ByteArray? {
        if (!file.baseFile.exists()) return null
        val stored = file.openRead().use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(chunk)
                if (count < 0) break
                total += count
                require(total <= MAX_STORED_BYTES) { "Зашифрованный профиль повреждён или слишком велик." }
                buffer.write(chunk, 0, count)
            }
            buffer.toByteArray()
        }

        try {
            DataInputStream(ByteArrayInputStream(stored)).use { stream ->
                val magic = ByteArray(MAGIC.size)
                stream.readFully(magic)
                require(magic.contentEquals(MAGIC)) { "Неизвестный формат сохранённого профиля." }
                val ivLength = stream.readUnsignedByte()
                require(ivLength == GCM_IV_BYTES) { "Некорректный заголовок сохранённого профиля." }
                val iv = ByteArray(ivLength)
                stream.readFully(iv)
                val encryptedLength = stream.readInt()
                require(encryptedLength in 16..(MAX_PROFILE_BYTES + 16)) {
                    "Некорректный размер зашифрованного профиля."
                }
                val encrypted = ByteArray(encryptedLength)
                stream.readFully(encrypted)
                require(stream.available() == 0) { "В сохранённом профиле есть лишние данные." }

                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, getExistingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
                cipher.updateAAD(AAD)
                return cipher.doFinal(encrypted).also {
                    require(it.isNotEmpty() && it.size <= MAX_PROFILE_BYTES) {
                        "Расшифрованный профиль имеет недопустимый размер."
                    }
                }
            }
        } catch (error: GeneralSecurityException) {
            throw GeneralSecurityException(
                "Не удалось расшифровать профиль. Возможно, ключ Android Keystore недоступен.",
                error
            )
        }
    }

    @Synchronized
    fun clear() {
        file.delete()
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun getExistingKey(): SecretKey {
        val key = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        return key ?: throw GeneralSecurityException(
            "Ключ сохранённого профиля отсутствует. Импортируйте профиль заново."
        )
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val FILE_NAME = "wireguard-profile.enc"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "org.chiyurihell.wireguard.profile.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val MAX_PROFILE_BYTES = 256 * 1024
        const val MAX_STORED_BYTES = MAX_PROFILE_BYTES + 128
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        val MAGIC = byteArrayOf(0x43, 0x48, 0x50, 0x01)
        val AAD = "ChiyuriHell/WireGuardProfile/v1".toByteArray(Charsets.UTF_8)
    }
}
