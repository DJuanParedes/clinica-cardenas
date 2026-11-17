package pe.clinicacardenas.pacientes.platform

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import pe.clinicacardenas.pacientes.MainActivity
import pe.clinicacardenas.pacientes.ui.theme.Brand
import java.io.File

/** Texto como literal de JavaScript (con comillas y caracteres escapados). */
private fun js(s: String): String = Json.encodeToString(String.serializer(), s)

/**
 * Guiones que se ejecutan al terminar de cargar cada página del sistema web:
 * 1) deja iniciada la sesión del personal (la web la guarda en sessionStorage: cc.token y cc.user);
 * 2) las descargas (CSV, XML, PDF) y "Imprimir" pasan por la app, porque el WebView no las maneja solo.
 */
private fun setupScript(url: String, token: String, userJson: String) = """
(function(){
  try {
    var t = JSON.stringify(${js(token)});
    if (sessionStorage.getItem('cc.token') !== t) {
      sessionStorage.setItem('cc.token', t);
      sessionStorage.setItem('cc.user', ${js(userJson)});
      location.replace(${js(url)});
      return;
    }
  } catch (e) {}
  if (window.__clinicaApp) return;
  window.__clinicaApp = true;
  function send(u, name) {
    fetch(u).then(function(r){ return r.blob(); }).then(function(b){
      var fr = new FileReader();
      fr.onload = function(){ ClinicaApp.save(name || '', b.type || 'application/octet-stream', String(fr.result).split(',')[1] || ''); };
      fr.readAsDataURL(b);
    }).catch(function(){ ClinicaApp.fail(); });
  }
  var click = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function(){
    if (this.href && this.href.indexOf('blob:') === 0) { send(this.href, this.download); return; }
    return click.apply(this, arguments);
  };
  var open = window.open;
  window.open = function(u){
    if (u && String(u).indexOf('blob:') === 0) { send(String(u), ''); return null; }
    return open.apply(window, arguments);
  };
  window.print = function(){ ClinicaApp.print(document.title || 'Clinica Cardenas'); };
})();
""".trimIndent()

/** Puente que la página web usa para guardar archivos e imprimir. */
private class Bridge(private val context: Context, private val web: () -> WebView?) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun save(name: String, mime: String, base64: String) {
        val bytes = runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull()
        main.post {
            if (bytes == null) return@post fail()
            val type = mime.substringBefore(';').ifBlank { "application/octet-stream" }
            val ext = when {
                type == "application/pdf" -> ".pdf"
                type == "text/csv" -> ".csv"
                type.endsWith("xml") -> ".xml"
                type == "image/png" -> ".png"
                type.startsWith("image/") -> ".jpg"
                else -> ""
            }
            var file = name.replace(Regex("[^\\w.\\- áéíóúñÁÉÍÓÚÑ]"), "_").trim().ifBlank { "documento-${System.currentTimeMillis()}" }
            if (!file.contains('.') && ext.isNotEmpty()) file += ext
            val saved = saveToDownloads(file, type, bytes)
            openCopy(file, type, bytes, saved)
        }
    }

    @JavascriptInterface
    fun print(title: String) {
        main.post {
            val view = web() ?: return@post
            val activity = MainActivity.current ?: return@post
            val pm = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
            pm.print(title, view.createPrintDocumentAdapter(title), PrintAttributes.Builder().build())
        }
    }

    @JavascriptInterface
    fun fail() {
        main.post { Toast.makeText(context, "No se pudo descargar el archivo.", Toast.LENGTH_SHORT).show() }
    }

    /** Android 10+: copia en la carpeta Descargas del teléfono. */
    private fun saveToDownloads(name: String, mime: String, bytes: ByteArray): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
            context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
            true
        }.getOrDefault(false)
    }

    private fun openCopy(name: String, mime: String, bytes: ByteArray, saved: Boolean) {
        val dir = File(context.cacheDir, "compartidos").apply { mkdirs() }
        val file = File(dir, name).apply { writeBytes(bytes) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".archivos", file)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            if (saved) Toast.makeText(context, "También se guardó en Descargas: $name", Toast.LENGTH_SHORT).show()
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, if (saved) "Guardado en Descargas: $name" else "No hay una aplicación para abrir $name.", Toast.LENGTH_LONG).show()
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun ClinicWebView(url: String, token: String, userJson: String, onExit: () -> Unit, modifier: Modifier) {
    var view by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableStateOf(0) }
    val host = remember(url) { Uri.parse(url).host }
    val script = remember(url, token, userJson) { setupScript(url, token, userJson) }

    BackHandler {
        val v = view
        if (v != null && v.canGoBack()) v.goBack() else onExit()
    }
    DisposableEffect(Unit) {
        onDispose {
            view?.apply { stopLoading(); destroy() }
            view = null
        }
    }

    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    view = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.mediaPlaybackRequiresUserGesture = true
                    settings.allowFileAccess = false
                    settings.userAgentString = settings.userAgentString + " ClinicaCardenasApp"
                    overScrollMode = View.OVER_SCROLL_NEVER
                    addJavascriptInterface(Bridge(ctx.applicationContext) { view }, "ClinicaApp")

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                            val u = request.url
                            if (u.host == host && (u.scheme == "https" || u.scheme == "http")) return false
                            // WhatsApp, teléfono, correo y otros sitios: con la app que corresponda.
                            try {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (e: ActivityNotFoundException) {
                                Toast.makeText(ctx, "No hay una aplicación para abrir esto.", Toast.LENGTH_SHORT).show()
                            }
                            return true
                        }

                        override fun onPageStarted(v: WebView, u: String?, favicon: Bitmap?) {
                            progress = 5
                        }

                        override fun onPageFinished(v: WebView, u: String?) {
                            if (Uri.parse(u ?: "").host == host) v.evaluateJavascript(script, null)
                            progress = 100
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(v: WebView, newProgress: Int) {
                            progress = newProgress
                        }

                        /** Subir fotos, radiografías y PDF a la ficha del paciente desde la web. */
                        override fun onShowFileChooser(
                            v: WebView,
                            callback: ValueCallback<Array<Uri>>,
                            params: FileChooserParams,
                        ): Boolean {
                            val activity = MainActivity.current
                            if (activity == null) {
                                callback.onReceiveValue(null)
                                return true
                            }
                            activity.chooseForWeb(params.createIntent()) { uris -> callback.onReceiveValue(uris) }
                            return true
                        }
                    }
                    loadUrl(url)
                }
            },
        )
        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                color = Brand.Celeste,
            )
        }
    }
}
