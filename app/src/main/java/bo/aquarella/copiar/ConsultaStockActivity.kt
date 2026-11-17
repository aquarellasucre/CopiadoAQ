package bo.aquarella.copiar

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

/**
 * Consulta el stock desde el formulario oficial de Aquarella.
 * La sesión se conserva mediante las cookies persistentes de WebView.
 * El usuario realiza el postback oficial; Copiar AQ lee la tabla resultante.
 */
class ConsultaStockActivity : Activity() {

    companion object {
        private const val HOST_PERMITIDO = "aq.coreva.com.bo"
        private const val URL_LOGIN =
            "https://aq.coreva.com.bo/aquarella/Aquarella/Control/LoginForm.aspx"
        private const val URL_STOCK =
            "https://aq.coreva.com.bo/aquarella/Aquarella/Logistica/stockForm.aspx"
    }

    private lateinit var webView: WebView
    private lateinit var estado: TextView
    private lateinit var progreso: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CookieManager.getInstance().setAcceptCookie(true)

        construirPantalla()
        abrirLogin()
    }

    
private fun construirPantalla() {
    val raiz = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, 0)
    }

    val acciones = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(2, 0, 2, 0)
    }

    acciones.addView(crearBoton("Inicio", 1f) { abrirLogin() })
    acciones.addView(crearBoton("Consulta", 1f) { abrirStock() })
    acciones.addView(crearBoton("Leer data", 1f) { leerTabla() })

    raiz.addView(
        acciones,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    )

    estado = TextView(this).apply {
        text = "Conectando con Aquarella…"
        textSize = 12f
        setPadding(8, 2, 8, 2)
        maxLines = 1
    }

    raiz.addView(
        estado,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    )

    progreso = ProgressBar(
        this,
        null,
        android.R.attr.progressBarStyleHorizontal
    ).apply {
        isIndeterminate = true
        visibility = ProgressBar.GONE
    }

    raiz.addView(
        progreso,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    )

    webView = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadsImagesAutomatically = true
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

        webChromeClient = WebChromeClient()

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return true
                return uri.scheme !in listOf("http", "https") ||
                    uri.host != HOST_PERMITIDO
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                url: String?
            ): Boolean {
                if (url.isNullOrBlank()) return true
                val uri = android.net.Uri.parse(url)
                return uri.scheme !in listOf("http", "https") ||
                    uri.host != HOST_PERMITIDO
            }

            override fun onPageStarted(
                view: WebView?,
                url: String?,
                favicon: android.graphics.Bitmap?
            ) {
                progreso.visibility = ProgressBar.VISIBLE
                estado.text = "Cargando Aquarella…"
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progreso.visibility = ProgressBar.GONE

                estado.text = when {
                    url?.contains("LoginForm.aspx", true) == true ->
                        "Inicia sesión y pulsa Consulta."
                    url?.contains("stockForm.aspx", true) == true ->
                        "Introduce el artículo y pulsa Consultar."
                    else -> "Página cargada."
                }

                CookieManager.getInstance().flush()
            }
        }
    }

    raiz.addView(
        webView,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
    )

    setContentView(raiz)
}


    private fun crearBoton(texto: String, peso: Float, accion: () -> Unit): Button =
        Button(this).apply {
            text = texto
            isAllCaps = false
            setOnClickListener { accion() }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, peso)
        }

    private fun abrirLogin() {
        webView.loadUrl(URL_LOGIN)
    }

    private fun abrirStock() {
        webView.loadUrl(URL_STOCK)
    }

    private fun leerTabla() {
        val javascript = """(function() {
            var tabla = document.getElementById('ContentPlaceHolder1_gvStock');
            if (!tabla) return JSON.stringify({ok:false, texto:'No se encontró la tabla de stock. Realiza primero una consulta.'});
            return JSON.stringify({ok:true, texto:tabla.innerText || tabla.textContent || ''});
        })();""".trimIndent()

        webView.evaluateJavascript(javascript) { resultado ->
            val jsonText = resultado?.let {
                if (it == "null") null else org.json.JSONTokener(it).nextValue() as? String
            }
            if (jsonText.isNullOrBlank()) {
                mostrarResultado("No se pudo leer la respuesta de Aquarella. Comprueba que hayas realizado la consulta.")
                return@evaluateJavascript
            }

            try {
                val objeto = org.json.JSONObject(jsonText)
                val texto = objeto.optString("texto")
                if (objeto.optBoolean("ok")) {
                    mostrarResultado(texto.ifBlank { "La tabla está vacía." })
                } else {
                    mostrarResultado(texto)
                }
            } catch (_: Exception) {
                mostrarResultado("No se pudo interpretar la tabla. Intenta nuevamente.")
            }
        }
    }

    private fun mostrarResultado(texto: String) {
        AlertDialog.Builder(this)
            .setTitle("Resultado de stock")
            .setMessage(texto)
            .setPositiveButton("Copiar") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Stock Aquarella", texto))
                Toast.makeText(this, "Resultado copiado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
        super.onDestroy()
    }
}
