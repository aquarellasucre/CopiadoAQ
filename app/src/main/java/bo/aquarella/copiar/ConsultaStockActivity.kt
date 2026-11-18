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
    val javascript = """
        (function() {
            var tabla = document.getElementById('ContentPlaceHolder1_gvStock');

            if (!tabla) {
                return JSON.stringify({
                    ok: false,
                    texto: 'No se encontró la tabla de stock. Realiza primero una consulta.'
                });
            }

            var filas = Array.from(tabla.rows).map(function(fila) {
                return Array.from(fila.cells).map(function(celda) {
                    return (celda.innerText || celda.textContent || '').trim();
                });
            });

            return JSON.stringify({ok: true, filas: filas});
        })();
    """.trimIndent()

    webView.evaluateJavascript(javascript) { resultado ->

        try {
            val jsonText = resultado?.let {
                if (it == "null") null
                else org.json.JSONTokener(it).nextValue() as? String
            }

            if (jsonText.isNullOrBlank()) {
                mostrarMensajeStock("No se pudo leer la respuesta de Aquarella.")
                return@evaluateJavascript
            }

            val objeto = org.json.JSONObject(jsonText)

            if (!objeto.optBoolean("ok")) {
                mostrarMensajeStock(objeto.optString("texto"))
                return@evaluateJavascript
            }

            val filasJson = objeto.optJSONArray("filas")

            if (filasJson == null || filasJson.length() < 2) {
                mostrarMensajeStock("La tabla no contiene datos.")
                return@evaluateJavascript
            }

            val cabecerasJson = filasJson.getJSONArray(0)

            val cabeceras = (0 until cabecerasJson.length()).map {
                cabecerasJson.optString(it).trim()
            }

            val registros = (1 until filasJson.length()).map { indice ->
                val fila = filasJson.getJSONArray(indice)

                cabeceras.indices.map { columna ->
                    fila.optString(columna, "").trim()
                }
            }

            val normalizar: (String) -> String = { valor ->
                java.text.Normalizer.normalize(
                    valor,
                    java.text.Normalizer.Form.NFD
                )
                    .replace(Regex("\\p{Mn}+"), "")
                    .lowercase(Locale.ROOT)
                    .trim()
            }

            val indiceTienda = cabeceras.indexOfFirst {
                normalizar(it).contains("tienda")
            }

            val indiceArticulo = cabeceras.indexOfFirst {
                normalizar(it).contains("articulo")
            }

            val indiceColor = cabeceras.indexOfFirst {
                normalizar(it).contains("color")
            }

            val indicePrecio = cabeceras.indexOfFirst {
                normalizar(it).contains("precio")
            }

            if (indiceTienda < 0 || indiceArticulo < 0 ||
                indiceColor < 0 || indicePrecio < 0
            ) {
                mostrarMensajeStock(
                    "No se reconocieron las columnas principales de la tabla."
                )
                return@evaluateJavascript
            }

            val indicesTallas = cabeceras.indices.filter { indice ->
            Regex("^(\\d+|T\\d+)$", RegexOption.IGNORE_CASE)
            .matches(cabeceras[indice].trim())
            }

            if (indicesTallas.isEmpty()) {
                mostrarMensajeStock("No se encontraron las columnas de tallas.")
                return@evaluateJavascript
            }

            mostrarConsultaStock(
                cabeceras,
                registros,
                indicesTallas,
                indiceTienda,
                indiceArticulo,
                indiceColor,
                indicePrecio
            )

            } catch (e: Exception) {
            
                android.util.Log.e(
                    "ConsultaStock",
                    "Error al interpretar la tabla de Aquarella",
                    e
                )
            
                mostrarMensajeStock(
                    "Error: ${e.message ?: e.javaClass.simpleName}"
                )
            }
}

private fun mostrarConsultaStock(
    cabeceras: List<String>,
    registros: List<List<String>>,
    indicesTallas: List<Int>,
    indiceTienda: Int,
    indiceArticulo: Int,
    indiceColor: Int,
    indicePrecio: Int
) {
    fun valor(fila: List<String>, indice: Int): String {
        return fila.getOrNull(indice)?.trim() ?: ""
    }

    fun cantidad(fila: List<String>, indice: Int): Int {
        return valor(fila, indice).replace(",", "").toDoubleOrNull()?.toInt() ?: 0
    }

    val tiendasPermitidas = mapOf(
        "21120" to "La Paz",
        "22219" to "Cochabamba",
        "23319" to "Santa Cruz",
        "26204" to "Sucre",
        "27207" to "Tarija"
    )

    val filasDisponibilidad = registros.filter { fila ->
        val tienda = valor(fila, indiceTienda)

        !tienda.startsWith("TOTALES", ignoreCase = true) &&
            tiendasPermitidas.containsKey(tienda) &&
            indicesTallas.any { cantidad(fila, it) > 0 }
    }

    val indicesTallasVisibles = indicesTallas.filter { indice ->
    filasDisponibilidad.any { fila ->
        cantidad(fila, indice) > 0
    }
}

val anchoPantallaDp = (
    resources.displayMetrics.widthPixels /
        resources.displayMetrics.density
).toInt()

val anchoCeldaTalla = (
    (anchoPantallaDp - 164) /
        maxOf(1, indicesTallasVisibles.size)
).coerceIn(20, 28)

    val articulo = registros.firstOrNull()?.let {
        valor(it, indiceArticulo)
    } ?: ""

    val color = registros.firstOrNull()?.let {
        valor(it, indiceColor)
    } ?: ""

    val precio = registros.firstOrNull()?.let {
        valor(it, indicePrecio)
    } ?: ""

    val precioMostrar = if (precio.startsWith("Bs", true)) {
        precio
    } else {
        "Bs.$precio"
    }

    fun dp(valor: Int): Int =
        (valor * resources.displayMetrics.density).toInt()

    // -------------------------------------------------
    // VISTA DISPONIBILIDAD
    // -------------------------------------------------

    val listaDisponibilidad = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(3), dp(4), dp(3), dp(4))
    }

    var textoCopiarDisponibilidad = ""

    if (filasDisponibilidad.isEmpty()) {

        val mensaje = TextView(this).apply {
            text = "No hay existencias disponibles en las tiendas seleccionadas."
            textSize = 14f
            setPadding(dp(12), dp(16), dp(12), dp(16))
        }

        listaDisponibilidad.addView(mensaje)

        } else {

    val encabezado = TextView(this).apply {

        val texto = android.text.SpannableStringBuilder().apply {

            val inicioArticulo = length
            append("Artículo: ")
            setSpan(
                android.text.style.StyleSpan(Typeface.BOLD),
                inicioArticulo,
                length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            append(valor(filasDisponibilidad.first(), indiceArticulo))

            append("    ")

            val inicioColor = length
            append("Color: ")
            setSpan(
                android.text.style.StyleSpan(Typeface.BOLD),
                inicioColor,
                length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            append(valor(filasDisponibilidad.first(), indiceColor))

            append("    ")

            val inicioPrecio = length
            append("Precio: ")
            setSpan(
                android.text.style.StyleSpan(Typeface.BOLD),
                inicioPrecio,
                length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            append(precioMostrar)
        }

        text = texto
        textSize = 10f
        setTextColor(android.graphics.Color.rgb(42, 48, 54))
        gravity = Gravity.CENTER
        setSingleLine(true)
        ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(dp(2), dp(9), dp(2), dp(9))

        background = GradientDrawable().apply {
            setColor(android.graphics.Color.rgb(240, 242, 244))
            cornerRadius = dp(5).toFloat()
        }
    }

    listaDisponibilidad.addView(
        encabezado,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(7)
        }
    )

    filasDisponibilidad.forEach { fila ->

            val codigoTienda = valor(fila, indiceTienda)
            val ciudad = tiendasPermitidas[codigoTienda] ?: ""

            val esSucre = codigoTienda == "26204"

            val fondo = if (esSucre) {
                android.graphics.Color.rgb(226, 242, 229)
            } else {
                android.graphics.Color.rgb(240, 242, 244)
            }

            val borde = if (esSucre) {
                android.graphics.Color.rgb(91, 170, 111)
            } else {
                fondo
            }

            val linea = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                setPadding(
                    dp(5),
                    dp(2),
                    dp(5),
                    dp(2)
                )

                background = GradientDrawable().apply {
                    setColor(fondo)
                    cornerRadius = dp(4).toFloat()
                    setStroke(
                        dp(if (esSucre) 1 else 0),
                        borde
                    )
                }
            }

        val identificacionTexto =
    android.text.SpannableStringBuilder().apply {

        append(codigoTienda)
        append(" ")

        val inicioCiudad = length
        append(ciudad)

        setSpan(
            android.text.style.StyleSpan(Typeface.BOLD),
            inicioCiudad,
            length,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        setSpan(
            android.text.style.RelativeSizeSpan(1.2f),
            inicioCiudad,
            length,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

val informacion = TextView(this).apply {

    text = identificacionTexto
    textSize = 9f

    setTextColor(
        if (esSucre)
            android.graphics.Color.rgb(23, 107, 54)
        else
            android.graphics.Color.rgb(42, 48, 54)
    )

    setSingleLine(true)
    ellipsize = android.text.TextUtils.TruncateAt.END
    gravity = Gravity.CENTER_VERTICAL
}

linea.addView(
    informacion,
    LinearLayout.LayoutParams(
        0,
        dp(44),
        1f
    )
)


            val tallasTexto = mutableListOf<String>()

            // Todas las tiendas conservan las mismas posiciones
            // para que las tallas queden alineadas verticalmente.

           indicesTallasVisibles.forEach { indiceTalla ->

                val numero = cabeceras[indiceTalla]
                val existencia = cantidad(fila, indiceTalla)

                val celda = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                }

                if (existencia <= 0) {

                    // Espacio invisible para conservar la alineación.
                    celda.visibility = android.view.View.INVISIBLE

                } else {

                    val talla = TextView(this).apply {
                        text = numero
                        textSize = 10f
                        setTextColor(
                            android.graphics.Color.rgb(85, 93, 101)
                        )
                        gravity = Gravity.CENTER
                    }

                    val cantidadTexto = TextView(this).apply {
                        text = existencia.toString()
                        textSize = 13f
                        setTextColor(
                            android.graphics.Color.rgb(22, 128, 68)
                        )
                        setTypeface(null, Typeface.BOLD)
                        gravity = Gravity.CENTER
                    }

                    celda.addView(
                        talla,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(15)
                        )
                    )

                    celda.addView(
                        cantidadTexto,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(23)
                        )
                    )

                    tallasTexto.add("$numero:$existencia")
                }

                linea.addView(
                    celda,
                    LinearLayout.LayoutParams(
                        dp(anchoCeldaTalla),
                        dp(40)
                    )
                )
            }

                listaDisponibilidad.addView(
                linea,
                
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(48)
                ).apply {
                    bottomMargin = dp(3)
                }
            )

            textoCopiarDisponibilidad +=
                "${valor(fila, indiceArticulo)} " +
                "${valor(fila, indiceColor)} " +
                "$precioMostrar " +
                "$codigoTienda $ciudad " +
                tallasTexto.joinToString(" ") +
                "\n"
        }
    }

    val scrollDisponibilidad =
    android.widget.ScrollView(this).apply {
        isFillViewport = true
        isHorizontalScrollBarEnabled = false

        addView(
            listaDisponibilidad,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    // -------------------------------------------------
    // VISTA DATA GENERAL
    // -------------------------------------------------

    val textoGeneral = buildString {

        // Encabezados separados por tabulaciones
        append(cabeceras.joinToString("\t"))
        append("\n")

        // Datos separados por tabulaciones
        registros.forEach { fila ->
            append(fila.joinToString("\t"))
            append("\n")
        }
    }

    val textoTabla = TextView(this).apply {
        text = textoGeneral
        textSize = 12f
        typeface = Typeface.MONOSPACE
        setTextIsSelectable(true)
        setPadding(dp(8), dp(8), dp(8), dp(8))
        setHorizontallyScrolling(true)
    }

    val scrollHorizontalGeneral =
        android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = true
            addView(
                textoTabla,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

    val scrollGeneral =
        android.widget.ScrollView(this).apply {
            addView(scrollHorizontalGeneral)
            visibility = android.view.View.GONE
        }

    // -------------------------------------------------
    // CONTENEDOR Y SELECTOR DE VISTAS
    // -------------------------------------------------

    val raizDialogo = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(8), dp(4), dp(8), dp(4))
    }

    val selector = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    val botonDisponibilidad = Button(this).apply {
        text = "Disponibilidad"
        isAllCaps = false
        textSize = 13f
    }

    val botonGeneral = Button(this).apply {
        text = "Data general"
        isAllCaps = false
        textSize = 13f
    }

    selector.addView(
        botonDisponibilidad,
        LinearLayout.LayoutParams(0, dp(44), 1f)
    )

    selector.addView(
        botonGeneral,
        LinearLayout.LayoutParams(0, dp(44), 1f)
    )

    raizDialogo.addView(selector)

    val contenido = android.widget.FrameLayout(this)

    contenido.addView(
        scrollDisponibilidad,
        android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    )

    contenido.addView(
        scrollGeneral,
        android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    )

    raizDialogo.addView(
        contenido,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
    )

    fun actualizarSelector(disponibilidad: Boolean) {

        scrollDisponibilidad.visibility =
            if (disponibilidad) android.view.View.VISIBLE
            else android.view.View.GONE

        scrollGeneral.visibility =
            if (disponibilidad) android.view.View.GONE
            else android.view.View.VISIBLE

        botonDisponibilidad.setTypeface(
            null,
            if (disponibilidad) Typeface.BOLD else Typeface.NORMAL
        )

        botonGeneral.setTypeface(
            null,
            if (!disponibilidad) Typeface.BOLD else Typeface.NORMAL
        )
    }

    botonDisponibilidad.setOnClickListener {
        actualizarSelector(true)
    }

    botonGeneral.setOnClickListener {
        actualizarSelector(false)
    }

    actualizarSelector(true)

    // -------------------------------------------------
    // DIÁLOGO
    // -------------------------------------------------

    val dialogo = AlertDialog.Builder(this)
        .setTitle("Resultado de stock")
        .setView(raizDialogo)
        .setPositiveButton("Copiar", null)
        .setNegativeButton("Cerrar", null)
        .create()

    dialogo.setOnShowListener {

        dialogo.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.78).toInt()
        )

        dialogo.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener {

                val contenidoCopiar = textoGeneral

                val clipboard =
                    getSystemService(CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager

                clipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText(
                        "Stock Aquarella",
                        contenidoCopiar
                    )
                )

                Toast.makeText(
                    this,
                    "Resultado copiado",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    dialogo.show()
}


private fun mostrarMensajeStock(texto: String) {
    AlertDialog.Builder(this)
        .setTitle("Resultado de stock")
        .setMessage(texto)
        .setPositiveButton("Cerrar", null)
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
