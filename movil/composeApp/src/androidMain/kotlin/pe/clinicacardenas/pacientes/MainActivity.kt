package pe.clinicacardenas.pacientes

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import pe.clinicacardenas.pacientes.platform.AppLifecycle
import pe.clinicacardenas.pacientes.platform.PickSource
import pe.clinicacardenas.pacientes.platform.PickedFile
import java.io.ByteArrayOutputStream
import java.io.File

class MainActivity : ComponentActivity() {

    // Permiso de notificaciones (capacidad nativa de recordatorios): se resuelve en sus tres estados.
    private var pendingPermission: ((String) -> Unit)? = null
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        getSharedPreferences(PERMISOS, MODE_PRIVATE).edit().putBoolean(PREGUNTADO, true).apply()
        val cb = pendingPermission
        pendingPermission = null
        cb?.invoke(if (granted) "CONCEDIDO" else notificationState())
    }

    /** Estado actual del permiso: CONCEDIDO, NO_PREGUNTADO, DENEGADO o DENEGADO_PARA_SIEMPRE. */
    fun notificationState(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return if (androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()) "CONCEDIDO" else "DENEGADO_PARA_SIEMPRE"
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return "CONCEDIDO"
        if (!getSharedPreferences(PERMISOS, MODE_PRIVATE).getBoolean(PREGUNTADO, false)) return "NO_PREGUNTADO"
        // Android deja de mostrar el diálogo cuando el usuario lo negó dos veces o marcó "no volver a preguntar".
        return if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) "DENEGADO" else "DENEGADO_PARA_SIEMPRE"
    }

    fun requestNotifications(onResult: (String) -> Unit) {
        val state = notificationState()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || state == "CONCEDIDO" || state == "DENEGADO_PARA_SIEMPRE") {
            onResult(state)
            return
        }
        pendingPermission = onResult
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private var pendingPick: ((PickedFile?) -> Unit)? = null
    private var cameraUri: Uri? = null

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        deliver(if (ok && uri != null) read(uri, "foto-${System.currentTimeMillis()}.jpg", image = true) else null)
    }
    private val getImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        deliver(uri?.let { read(it, null, image = true) })
    }
    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        deliver(uri?.let { read(it, null, image = false) })
    }

    // Archivos que se suben desde los módulos web (fichas, consentimientos).
    private var pendingWebPick: ((Array<Uri>?) -> Unit)? = null
    private val webChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val cb = pendingWebPick
        pendingWebPick = null
        val data = r.data
        val uris: Array<Uri>? = if (r.resultCode != Activity.RESULT_OK || data == null) null else {
            val clip = data.clipData
            when {
                clip != null -> Array(clip.itemCount) { clip.getItemAt(it).uri }
                data.data != null -> arrayOf(data.data!!)
                else -> null
            }
        }
        cb?.invoke(uris)
    }

    fun chooseForWeb(intent: Intent, onResult: (Array<Uri>?) -> Unit) {
        pendingWebPick?.invoke(null)
        pendingWebPick = onResult
        try {
            webChooser.launch(intent)
        } catch (e: Exception) {
            pendingWebPick = null
            onResult(null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        current = this
        askNotificationPermission()
        setContent { App() }
    }

    override fun onStart() {
        super.onStart()
        current = this
        AppLifecycle.onStart()
    }

    override fun onStop() {
        super.onStop()
        AppLifecycle.onStop()
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    /** En Android 13+ los recordatorios necesitan permiso explícito: se pregunta una vez al abrir la app. */
    private fun askNotificationPermission() {
        if (notificationState() == "NO_PREGUNTADO") requestNotifications { }
    }

    // ------------------------------------------------------------- cámara, galería y documentos

    fun pick(source: PickSource, onResult: (PickedFile?) -> Unit) {
        pendingPick = onResult
        try {
            when (source) {
                PickSource.CAMARA -> {
                    val dir = File(cacheDir, "camara").apply { mkdirs() }
                    val file = File(dir, "foto.jpg")
                    val uri = FileProvider.getUriForFile(this, "$packageName.archivos", file)
                    cameraUri = uri
                    takePicture.launch(uri)
                }
                PickSource.GALERIA -> getImage.launch("image/*")
                PickSource.DOCUMENTO -> openDocument.launch(arrayOf("application/pdf", "image/*"))
            }
        } catch (e: Exception) {
            deliver(null)
        }
    }

    private fun deliver(file: PickedFile?) {
        val cb = pendingPick
        pendingPick = null
        cb?.invoke(file)
    }

    /** Lee el archivo elegido. Las fotos se reducen a 2000 px y JPEG para que suban rápido. */
    private fun read(uri: Uri, fallbackName: String?, image: Boolean): PickedFile? = try {
        val mime = contentResolver.getType(uri) ?: if (image) "image/jpeg" else "application/octet-stream"
        val name = fallbackName ?: displayName(uri) ?: "archivo"
        val raw = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        if (mime.startsWith("image/")) {
            val jpeg = downscale(raw)
            if (jpeg != null) PickedFile(name.substringBeforeLast('.') + ".jpg", "image/jpeg", jpeg) else PickedFile(name, mime, raw)
        } else {
            PickedFile(name, mime, raw)
        }
    } catch (e: Exception) {
        null
    }

    private fun displayName(uri: Uri): String? =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private fun downscale(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 2000 || bounds.outHeight / (sample * 2) >= 2000) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bmp.recycle()
        return out.toByteArray()
    }

    // ------------------------------------------------------------- huella / rostro / PIN del teléfono

    fun authenticate(reason: String, onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            onResult(false)
            return
        }
        val builder = BiometricPrompt.Builder(this)
            .setTitle("Clínica Cárdenas")
            .setSubtitle(reason)
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> builder.setDeviceCredentialAllowed(true)
            else -> builder.setNegativeButton("Cancelar", mainExecutor) { _, _ -> onResult(false) }
        }
        var done = false
        fun finish(ok: Boolean) {
            if (!done) {
                done = true
                onResult(ok)
            }
        }
        try {
            builder.build().authenticate(
                CancellationSignal(),
                mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = finish(true)
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) = finish(false)
                },
            )
        } catch (e: Exception) {
            finish(false)
        }
    }

    companion object {
        /** Actividad visible (para lanzar la cámara o la huella desde el código compartido). */
        @Volatile
        var current: MainActivity? = null

        private const val PERMISOS = "permisos"
        private const val PREGUNTADO = "notificaciones.preguntado"
    }
}
