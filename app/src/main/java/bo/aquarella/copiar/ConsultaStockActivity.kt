package bo.aquarella.copiar

import android.widget.EditText
import android.text.InputType
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
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
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
    private lateinit var articuloInput: EditText

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    window.clearFlags(
        android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    )

    window.decorView.systemUiVisibility =
        android.view.View.SYSTEM_UI_FLAG_VISIBLE or
        android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

    window.statusBarColor = android.graphics.Color.WHITE
    window.navigationBarColor = android.graphics.Color.BLACK

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        window.setDecorFitsSystemWindows(true)
    }

    CookieManager.getInstance().setAcceptCookie(true)

    construirPantalla()
    abrirStock()
}

    
private fun construirPantalla() {
    val raiz = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, 0)
    }

    raiz.addView(crearResumenSemana())

    val acciones = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(2, 0, 2, 0)
    }

    acciones.addView(crearBoton("Inicio", 1f) { abrirLogin() })
    acciones.addView(crearBoton("Consulta", 1f) { abrirStock() })
    acciones.addView(crearBoton("Leer data", 1f) { leerTabla() })

    val filaBusqueda = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    setPadding(6, 2, 6, 2)
}


articuloInput = EditText(this).apply {
    hint = "Código de artículo"
    textSize = 14f
    setSingleLine(true)
    inputType = InputType.TYPE_CLASS_TEXT
    gravity = Gravity.CENTER_VERTICAL
    minHeight = (42 * resources.displayMetrics.density).toInt()

    setPadding(
        (12 * resources.displayMetrics.density).toInt(),
        0,
        (12 * resources.displayMetrics.density).toInt(),
        0
    )

    background = GradientDrawable().apply {
        setColor(android.graphics.Color.WHITE)
        cornerRadius = 9 * resources.displayMetrics.density
        setStroke(
            (1 * resources.displayMetrics.density).toInt(),
            android.graphics.Color.rgb(210, 215, 220)
        )
    }
}


filaBusqueda.addView(
    articuloInput,
    LinearLayout.LayoutParams(
        0,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        1f
    )
)


filaBusqueda.addView(
    crearBoton("Consultar", 0f) {
        buscarArticulo()
    }.apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            (42 * resources.displayMetrics.density).toInt()
        ).apply {
            setMargins(
                (6 * resources.displayMetrics.density).toInt(),
                0,
                0,
                0
            )
        }
    }
)


raiz.addView(
    filaBusqueda,
    LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
)

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
    raiz.setOnApplyWindowInsetsListener { view, insets ->

    view.setPadding(
        0,
        insets.systemWindowInsetTop,
        0,
        insets.systemWindowInsetBottom
    )

    insets
}

raiz.requestApplyInsets()
}

    
private fun crearResumenSemana(): LinearLayout {
    val calendario = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        minimalDaysInFirstWeek = 4
    }

    val fecha = SimpleDateFormat(
        "d MMM", Locale("es", "BO")
    ).format(calendario.time).lowercase(Locale("es", "BO"))

    val semana = calendario.get(Calendar.WEEK_OF_YEAR)
    val diaActual = (calendario.get(Calendar.DAY_OF_WEEK) + 5) % 7

    fun dp(valor: Int): Int =
        (valor * resources.displayMetrics.density).toInt()

    val contenedor = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6), dp(1), dp(6), dp(2))
    }

    // IZQUIERDA: fecha actual
    val textoFecha = TextView(this).apply {
        text = android.text.SpannableString("Hoy: $fecha").apply {
            setSpan(
                android.text.style.StyleSpan(Typeface.BOLD),
                5,
                length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        textSize = 12f
        gravity = Gravity.CENTER_VERTICAL
        setSingleLine(true)
    }

    contenedor.addView(
        textoFecha,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(24)
        )
    )

    // CENTRO: días y barra de progreso
    val centro = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(8), 0, dp(8), 0)
    }

    val filaDias = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    val dias = listOf("L", "M", "X", "J", "V", "S", "D")

    dias.forEachIndexed { indice, dia ->
        val etiqueta = TextView(this).apply {
            text = dia
            textSize = 10f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)

            if (indice == diaActual) {
                setTextColor(android.graphics.Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(android.graphics.Color.rgb(217, 45, 32))
                }
            }
        }

        filaDias.addView(
            etiqueta,
            LinearLayout.LayoutParams(
                0,
                dp(20),
                1f
            )
        )
    }

    centro.addView(
        filaDias,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(20)
        )
    )

    // Barra de progreso con siete separaciones
    val barra = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    repeat(7) { indice ->
        val segmento = android.view.View(this).apply {
            setBackgroundColor(
                if (indice <= diaActual)
                    android.graphics.Color.rgb(217, 45, 32)
                else
                    android.graphics.Color.LTGRAY
            )
        }

        val parametros = LinearLayout.LayoutParams(
            0,
            dp(4),
            1f
        ).apply {
            setMargins(dp(1), 0, dp(1), 0)
        }

        barra.addView(segmento, parametros)
    }

    centro.addView(
        barra,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(4)
        )
    )

    contenedor.addView(
        centro,
        LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        )
    )

    // DERECHA: número de semana
    val grupoSemana = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    val textoSemana = TextView(this).apply {
        text = "Semana:"
        textSize = 11f
        setPadding(0, 0, dp(4), 0)
        setSingleLine(true)
    }

    grupoSemana.addView(textoSemana)

    val numeroSemana = TextView(this).apply {
        text = semana.toString()
        textSize = 11f
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(dp(5), 0, dp(5), 0)
        background = GradientDrawable().apply {
            setColor(android.graphics.Color.TRANSPARENT)
            cornerRadius = dp(5).toFloat()
            setStroke(dp(1), android.graphics.Color.LTGRAY)
        }
    }

    grupoSemana.addView(numeroSemana)
    contenedor.addView(grupoSemana)

    return contenedor
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
    private fun buscarArticulo() {
    val articulo = articuloInput.text.toString().trim()

    if (articulo.isBlank()) {
        Toast.makeText(
            this,
            "Introduce un código de artículo",
            Toast.LENGTH_SHORT
        ).show()
        return
    }

    val valorSeguro = org.json.JSONObject.quote(articulo)

    val javascript = """
        (function() {
            var campo = document.getElementById('ContentPlaceHolder1_TxtItem');
            var boton = document.getElementById('ContentPlaceHolder1_btConsult');

            if (!campo || !boton) {
                return 'formulario_no_encontrado';
            }

            campo.value = $valorSeguro;

            campo.dispatchEvent(
                new Event('input', { bubbles: true })
            );

            campo.dispatchEvent(
                new Event('change', { bubbles: true })
            );

            boton.click();

            return 'consulta_enviada';
        })();
    """.trimIndent()

    webView.evaluateJavascript(javascript) { resultado ->
            when {
                resultado == "\"consulta_enviada\"" -> {
                estado.text = "Consultando artículo $articulo…"
                Toast.makeText(
                    this,
                    "Consulta enviada",
                    Toast.LENGTH_SHORT
                ).show()
            }

            else -> {
                Toast.makeText(
                    this,
                    "No se encontró el formulario. Abre primero Consulta.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
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
