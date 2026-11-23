package pe.clinicacardenas.pacientes.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import pe.clinicacardenas.pacientes.domain.repository.TokenStorage
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Almacén seguro del token en Android: una clave AES-256 que vive en el Android Keystore (no se puede extraer del
 * teléfono) cifra el token con AES/GCM; en las preferencias solo queda el texto cifrado y su vector inicial.
 */
class AndroidTokenStorage(context: Context) : TokenStorage {
    private val prefs = context.getSharedPreferences("sesion_segura", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    override fun read(key: String): String? {
        val data = prefs.getString(key, null) ?: return null
        return try {
            val (iv, enc) = data.split(':').let { Base64.decode(it[0], Base64.NO_WRAP) to Base64.decode(it[1], Base64.NO_WRAP) }
            val c = Cipher.getInstance(TRANSFORMATION)
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(c.doFinal(enc), Charsets.UTF_8)
        } catch (e: Exception) {
            // Clave del Keystore perdida (p. ej. tras restaurar el teléfono): la sesión se descarta y se pide ingresar.
            prefs.edit().remove(key).apply()
            null
        }
    }

    override fun write(key: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(key).apply()
            return
        }
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.ENCRYPT_MODE, key())
        val enc = c.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(key, Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(enc, Base64.NO_WRAP)).apply()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "clinica_sesion"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
