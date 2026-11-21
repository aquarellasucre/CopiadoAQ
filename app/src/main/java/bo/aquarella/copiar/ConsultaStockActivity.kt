package bo.aquarella.copiar

import android.widget.EditText
import androidx.drawerlayout.widget.DrawerLayout
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.view.View
import android.text.InputType
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.os.Environment
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageView
import android.graphics.BitmapFactory
import java.net.HttpURLConnection
import java.net.URL
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
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
    private lateinit var pedidoWebView: WebView
    private lateinit var contenedorWeb: FrameLayout
    private var moduloActivo = "STOCK"
    private var urlPendientePedido = "https://aq.coreva.com.bo/aquarella/Aquarella/Logistica/panelOrdersCustomer.aspx"
    private lateinit var estado: TextView
    private lateinit var progreso: ProgressBar
    private lateinit var articuloInput: EditText
    
    private lateinit var botonLeerData: Button
    private lateinit var botonConsultar: Button
    
    private var consultaEnCurso = false
    private var consultaLista = false
    private var articuloConsultado = ""
    private var loginDialogVisible = false
    private var loginAttemptInProgress = false
    private var navegandoAConsultaTrasLogin = false
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var botonDescargarLiquidacion: Button
    private var modoSeleccionPedido = false
    private var consultaAutoPedidoIniciada = false
    private var codigoPedidoAuto = ""

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    WebView.setWebContentsDebuggingEnabled(true)

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
    modoSeleccionPedido = intent.getBooleanExtra("modoSeleccionPedido", false)
    codigoPedidoAuto = intent.getStringExtra("ArticuloPedido").orEmpty()

    construirPantalla()
    abrirStock()
}

private inner class StockJavascriptBridge {

    @android.webkit.JavascriptInterface
    fun consultaIniciada() {
        runOnUiThread {
            consultaEnCurso = true
            consultaLista = false

            botonConsultar.isEnabled = false
            botonLeerData.isEnabled = false

            estado.text = "Consultando artículo…"
            progreso.visibility = ProgressBar.VISIBLE
        }
    }

    @android.webkit.JavascriptInterface
    fun consultaFinalizada(exito: Boolean, detalle: String) {
        runOnUiThread {
            consultaEnCurso = false

            botonConsultar.isEnabled = true
            progreso.visibility = ProgressBar.GONE

            if (detalle == "SIN_EXISTENCIAS") {
                // Aquarella respondió, pero no encontró filas de stock.
                // No habilitamos Leer data; verificamos la imagen como dato auxiliar.
                consultaLista = false
                botonLeerData.isEnabled = false
                estado.text = "Aquarella no encontró existencias. Verificando artículo…"
                verificarImagenArticulo(articuloConsultado)
                return@runOnUiThread
            }

            consultaLista = exito
            botonLeerData.isEnabled = exito

            estado.text = if (exito) {
                "Consulta finalizada. Mostrando los datos…"
            } else {
                "La consulta terminó con un error."
            }

            if (exito) {
                // Abrir automáticamente el resultado.
                // El botón Leer data permanece habilitado para volver a abrirlo manualmente.
                leerTabla()
            }

            if (!exito) {
                mostrarMensajeStock(
                    "Aquarella no pudo completar la consulta.\\n\\n$detalle"
                )
            }
        }
    }
}

private inner class LiquidacionJavascriptBridge {
    @android.webkit.JavascriptInterface
    fun pdfDescargado(nombre: String, base64: String) {
        runOnUiThread {
            try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                guardarPdfLiquidacion(nombre, bytes)
            } catch (e: Exception) {
                Toast.makeText(this@ConsultaStockActivity, "No se pudo guardar el PDF: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    @android.webkit.JavascriptInterface
    fun errorDescarga(mensaje: String) {
        runOnUiThread { Toast.makeText(this@ConsultaStockActivity, mensaje, Toast.LENGTH_LONG).show() }
    }
}

private fun guardarPdfLiquidacion(nombreOriginal: String, bytes: ByteArray) {
    val nombre = nombreOriginal.replace(Regex("[^A-Za-z0-9._-]"), "_").let { if (it.endsWith(".pdf", true)) it else "$it.pdf" }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, nombre)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Copiar AQ")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("No se pudo crear el archivo en Descargas")
        contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: throw IllegalStateException("No se pudo escribir el PDF")
        values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
        contentResolver.update(uri, values, null, null)
        Toast.makeText(this, "Liquidación guardada en Descargas/Copiar AQ: $nombre", Toast.LENGTH_LONG).show()
    } else {
        val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
        val file = File(dir, nombre)
        FileOutputStream(file).use { it.write(bytes) }
        Toast.makeText(this, "PDF guardado: ${file.absolutePath}", Toast.LENGTH_LONG).show()
    }
}

private fun descargarLiquidacionDesdeWebView() {
    if (moduloActivo != "PEDIDO") {
        Toast.makeText(this, "Abre primero Crear Pedido / navegación Aquarella", Toast.LENGTH_SHORT).show()
        return
    }
    val js = """(function(){
      var docs=[],seen=new Set();
      function scan(d){if(!d||seen.has(d))return;seen.add(d);var u='';try{u=d.location.href}catch(e){}
        if(/reportLiquidation\.aspx/i.test(u)&&d.querySelector('[id*="crvLiquidation"]')){
          var q=new URL(u).searchParams;var n=q.get('NoLiq')||q.get('Noliq')||q.get('noLiq');if(n)docs.push({d:d,n:n});}
        Array.from(d.querySelectorAll('iframe,frame')).forEach(function(f){try{scan(f.contentDocument)}catch(e){}});}
      try{scan(document);if(!docs.length){AndroidLiquidation.errorDescarga('No se encontró el reporte. Abre primero una liquidación con el icono de imprimir.');return;}
        var x=docs.find(function(o){return o.d.forms.length>0})||docs[0],d=x.d,n=x.n,f=d.forms[0];if(!f){AndroidLiquidation.errorDescarga('No se encontró el formulario de exportación del reporte.');return;}
        var v={};Array.from(f.elements).forEach(function(e){if(e.name&&!e.disabled&&(e.type!=='checkbox'&&e.type!=='radio'||e.checked))v[e.name]=e.value;});
        v.__EVENTTARGET='crvLiquidation';v.__EVENTARGUMENT='{"text":"PDF","range":"false","tb":"crexport"}';
        var body=new URLSearchParams(v);fetch(f.action||d.location.href,{method:'POST',credentials:'include',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:body.toString()})
        .then(function(r){if(!r.ok)throw Error('Error HTTP '+r.status);return r.blob();})
        .then(function(b){if(b.type==='text/html'||b.size<100)throw Error('Aquarella no devolvió el PDF. Comprueba que el reporte esté abierto.');
          var reader=new FileReader();reader.onload=function(){var data=String(reader.result).split(',')[1];AndroidLiquidation.pdfDescargado(n+'.pdf',data);};reader.onerror=function(){throw Error('No se pudo leer el archivo PDF');};reader.readAsDataURL(b);})
        .catch(function(e){AndroidLiquidation.errorDescarga('No se pudo descargar el PDF: '+e.message);});
      }catch(e){AndroidLiquidation.errorDescarga('Error al exportar: '+e.message);}})();"""
    pedidoWebView.evaluateJavascript(js, null)
}

private fun construirPantalla() {
    val contenido = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    contenido.addView(crearResumenSemana())

    val filaBusqueda = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(6), dp(2), dp(6), dp(2)) }
    articuloInput = EditText(this).apply {
        hint = "Código de artículo"; textSize = 14f; setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT; gravity = Gravity.CENTER_VERTICAL; minHeight = dp(42); setPadding(dp(12), 0, dp(12), 0)
        background = GradientDrawable().apply { setColor(android.graphics.Color.WHITE); cornerRadius = dp(9).toFloat(); setStroke(dp(1), android.graphics.Color.rgb(210, 215, 220)) }
    }
    filaBusqueda.addView(articuloInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    filaBusqueda.addView(crearBoton("Pegar", 0f) {
        val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = cm.primaryClip
        if (clip != null && clip.itemCount > 0) { val text = clip.getItemAt(0).coerceToText(this).toString().filterNot { it.isWhitespace() }; articuloInput.setText(text); articuloInput.setSelection(articuloInput.text.length) }
        else Toast.makeText(this, "El portapapeles está vacío", Toast.LENGTH_SHORT).show()
    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)).apply { setMargins(dp(6), 0, 0, 0) })
    filaBusqueda.addView(crearBoton("Limpiar", 0f) { articuloInput.text.clear() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)).apply { setMargins(dp(6), 0, 0, 0) })
    botonConsultar = crearBoton("Consultar", 0f) { buscarArticulo() }
    filaBusqueda.addView(botonConsultar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)).apply { setMargins(dp(6), 0, 0, 0) })
    contenido.addView(filaBusqueda)

    val acciones = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(2), 0, dp(2), 0) }
    botonLeerData = crearBoton("Leer data", 1f) { leerTabla() }; botonLeerData.isEnabled = false
    acciones.addView(botonLeerData); contenido.addView(acciones)
    estado = TextView(this).apply { text = "Conectando con Aquarella…"; textSize = 12f; setPadding(dp(8), dp(2), dp(8), dp(2)); maxLines = 1 }
    contenido.addView(estado)
    progreso = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { isIndeterminate = true; visibility = ProgressBar.GONE }
    contenido.addView(progreso, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

    webView = WebView(this).apply {
        settings.javaScriptEnabled = true; addJavascriptInterface(StockJavascriptBridge(), "AndroidStock")
        settings.domStorageEnabled = true; settings.databaseEnabled = true; settings.loadsImagesAutomatically = true
        settings.javaScriptCanOpenWindowsAutomatically = false; settings.setSupportMultipleWindows(false)
        settings.useWideViewPort = true; settings.loadWithOverviewMode = true; settings.builtInZoomControls = true; settings.displayZoomControls = false
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true); webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean { val uri = request?.url ?: return true; return uri.scheme !in listOf("http", "https") || uri.host != HOST_PERMITIDO }
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean { if (url.isNullOrBlank()) return true; val uri = android.net.Uri.parse(url); return uri.scheme !in listOf("http", "https") || uri.host != HOST_PERMITIDO }
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) { progreso.visibility = ProgressBar.VISIBLE; estado.text = "Cargando Aquarella…" }
            override fun onPageFinished(view: WebView?, url: String?) {
                progreso.visibility = ProgressBar.GONE
                CookieManager.getInstance().flush()
                if (url?.contains("LoginForm.aspx", true) == true) {
                    estado.text = "Sesión cerrada. Inicia sesión para continuar."
                    loginAttemptInProgress = false
                    navegandoAConsultaTrasLogin = false
                    if (!loginDialogVisible) mostrarFormularioLogin(false)
                } else if (loginAttemptInProgress) {
                    // Login validado (ya no estamos en LoginForm): llevar directamente al formulario de stock.
                    if (!url.orEmpty().contains("stockForm.aspx", true)) {
                        if (!navegandoAConsultaTrasLogin) {
                            navegandoAConsultaTrasLogin = true
                            estado.text = "Sesión iniciada. Abriendo Consulta Stock…"
                            webView.loadUrl(URL_STOCK)
                        }
                    } else {
                        estado.text = "Preparando consulta de stock…"
                        prepararFormularioConsultaTrasLogin()
                    }
                } else if (url?.contains("stockForm.aspx", true) == true) {
                    estado.text = if (modoSeleccionPedido) "Preparando consulta de stock para el pedido…" else "Introduce el artículo y pulsa Consultar."
                    loginDialogVisible = false
                    navegandoAConsultaTrasLogin = false
                    iniciarConsultaPedidoAutomatica()
                } else estado.text = "Página cargada."
            }
        }
    }
    // Los dos WebView permanecen creados: cambiar de módulo no destruye su navegación.
    pedidoWebView = WebView(this).apply {
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
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return true
                return uri.scheme !in listOf("http", "https") || uri.host != HOST_PERMITIDO
            }
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url.isNullOrBlank()) return true
                val uri = android.net.Uri.parse(url)
                return uri.scheme !in listOf("http", "https") || uri.host != HOST_PERMITIDO
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                if (moduloActivo == "PEDIDO") estado.text = "Cargando Aquarella…"
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                CookieManager.getInstance().flush()
                if (url.orEmpty().contains("LoginForm.aspx", true)) {
                    if (moduloActivo == "PEDIDO") {
                        estado.text = "Sesión cerrada. Inicia sesión para continuar."
                        loginAttemptInProgress = false
                        mostrarFormularioLogin(false)
                    }
                } else if (loginAttemptInProgress && moduloActivo == "PEDIDO") {
                    loginAttemptInProgress = false
                    estado.text = "Sesión iniciada. Módulo de pedidos disponible."
                } else if (moduloActivo == "PEDIDO") {
                    estado.text = "Aquarella · Crear Pedido / navegación libre"
                }
            }
        }
    }
    pedidoWebView.addJavascriptInterface(LiquidacionJavascriptBridge(), "AndroidLiquidation")
    contenedorWeb = FrameLayout(this)
    contenedorWeb.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    contenedorWeb.addView(pedidoWebView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    pedidoWebView.visibility = View.GONE
    botonDescargarLiquidacion = crearBoton("↓ Descargar Liquidación PDF", 0f) { descargarLiquidacionDesdeWebView() }.apply {
        textSize = 13f
        visibility = View.GONE
        elevation = dp(8).toFloat()
    }
    contenedorWeb.addView(botonDescargarLiquidacion, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44), Gravity.TOP or Gravity.RIGHT).apply {
        setMargins(dp(10), dp(10), dp(10), dp(10))
    })
    contenido.addView(contenedorWeb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    drawerLayout = DrawerLayout(this).apply {
        setScrimColor(0x99000000.toInt())
        addView(contenido, DrawerLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(crearMenuLateral(), DrawerLayout.LayoutParams(dp(290), ViewGroup.LayoutParams.MATCH_PARENT).apply { gravity = Gravity.LEFT })
    }
    setContentView(drawerLayout)
    contenido.setOnApplyWindowInsetsListener { view, insets -> view.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom); insets }
    contenido.requestApplyInsets()
}

private fun crearMenuLateral(): View {
    val menu = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(30), dp(12), dp(12)); background = GradientDrawable().apply { setColor(android.graphics.Color.WHITE) } }
    menu.addView(TextView(this).apply { text = "Copiar AQ"; textSize = 21f; setTypeface(null, Typeface.BOLD); setTextColor(android.graphics.Color.rgb(170, 35, 45)); setPadding(0, 0, 0, dp(22)) })
    fun item(titulo: String, subtitulo: String, accion: () -> Unit) {
        val fila = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(8), dp(12)); background = GradientDrawable().apply { setColor(android.graphics.Color.WHITE); cornerRadius = dp(10).toFloat() }; setOnClickListener { drawerLayout.closeDrawer(Gravity.LEFT); accion() } }
        fila.addView(TextView(this).apply { text = titulo; textSize = 16f; setTextColor(android.graphics.Color.rgb(45, 50, 56)) })
        fila.addView(TextView(this).apply { text = subtitulo; textSize = 12f; setTextColor(android.graphics.Color.GRAY); setPadding(0, dp(3), 0, 0) })
        menu.addView(fila, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    item("Consulta Stock", "Volver a la consulta de artículos y tallas") { mostrarModuloStock() }
    item("Crear Pedido", "Abrir pedidos en Aquarella") { mostrarModuloPedido(true) }
    item("Navegar en Aquarella", "Continuar en el segundo navegador") { mostrarModuloPedido(false) }
    item("Credenciales", "Guardar o actualizar acceso") { mostrarFormularioLogin(true) }
    return menu
}

private fun mostrarModuloStock() {
    moduloActivo = "STOCK"
    pedidoWebView.visibility = View.GONE
    botonDescargarLiquidacion.visibility = View.GONE
    webView.visibility = View.VISIBLE
    consultaLista = false
    consultaEnCurso = false
    botonLeerData.isEnabled = false
    if (!webView.url.orEmpty().contains("stockForm.aspx", true)) abrirStock()
    estado.text = "Consulta Stock"
}

private fun mostrarModuloPedido(abrirInicio: Boolean) {
    moduloActivo = "PEDIDO"
    webView.visibility = View.GONE
    pedidoWebView.visibility = View.VISIBLE
    botonDescargarLiquidacion.visibility = View.VISIBLE
    if (abrirInicio || pedidoWebView.url.isNullOrBlank() || pedidoWebView.url == "about:blank") {
        urlPendientePedido = "https://aq.coreva.com.bo/aquarella/Aquarella/Logistica/panelOrdersCustomer.aspx"
        pedidoWebView.loadUrl(urlPendientePedido)
    } else {
        estado.text = "Aquarella · Navegación libre"
    }
}

private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

private fun credencialesSeguras() = EncryptedSharedPreferences.create(
    this, "aquarella_login_secure",
    MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)

private fun mostrarFormularioLogin(soloGuardar: Boolean) {
    if (loginDialogVisible) return
    loginDialogVisible = true
    val prefs = credencialesSeguras()
    val usuario = EditText(this).apply { hint = "Usuario"; setSingleLine(); setText(prefs.getString("usuario", "") ?: "") }
    val contrasena = EditText(this).apply { hint = "Contraseña"; setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; setText(prefs.getString("contrasena", "") ?: "") }
    val guardar = android.widget.CheckBox(this).apply { text = "Guardar credenciales cifradas"; isChecked = prefs.contains("usuario") && prefs.contains("contrasena") }
    val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(8), dp(22), dp(4)); addView(usuario); addView(contrasena); addView(guardar) }
    val dialog = AlertDialog.Builder(this).setTitle(if (soloGuardar) "Credenciales de Aquarella" else "Iniciar sesión en Aquarella").setView(panel)
        .setNegativeButton(if (soloGuardar) "Cancelar" else "Ahora no", null)
        .setPositiveButton(if (soloGuardar) "Guardar" else "Iniciar sesión", null)
        .setNeutralButton(if (prefs.contains("usuario")) "Eliminar guardadas" else null, null).create()
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val u = usuario.text.toString().trim(); val pass = contrasena.text.toString()
            if (u.isBlank() || pass.isBlank()) { if (u.isBlank()) usuario.error = "Ingresa el usuario"; if (pass.isBlank()) contrasena.error = "Ingresa la contraseña"; return@setOnClickListener }
            if (guardar.isChecked) prefs.edit().putString("usuario", u).putString("contrasena", pass).apply() else prefs.edit().remove("usuario").remove("contrasena").apply()
            loginDialogVisible = false; dialog.dismiss()
            if (!soloGuardar) enviarCredencialesAWeb(u, pass) else Toast.makeText(this, "Credenciales guardadas cifradas", Toast.LENGTH_SHORT).show()
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener { prefs.edit().clear().apply(); usuario.text.clear(); contrasena.text.clear(); guardar.isChecked = false; Toast.makeText(this, "Credenciales guardadas eliminadas", Toast.LENGTH_SHORT).show(); dialog.dismiss(); loginDialogVisible = false }
    }
    dialog.setOnCancelListener { loginDialogVisible = false }
    dialog.show()
}

private fun enviarCredencialesAWeb(usuario: String, contrasena: String) {
    loginAttemptInProgress = true; estado.text = "Ingresando a Aquarella…"
    val webLogin = if (moduloActivo == "PEDIDO") pedidoWebView else webView
    val js = """(function(){const u=${org.json.JSONObject.quote(usuario)},p=${org.json.JSONObject.quote(contrasena)};const a=Array.from(document.querySelectorAll('input'));const pass=a.find(x=>x.type==='password');const user=a.find(x=>x.type!=='password'&&/user|usuario|login|email|cuenta/i.test((x.name||'')+' '+(x.id||'')+' '+(x.placeholder||'')))||a.find(x=>x.type==='text'||x.type==='email');if(!user||!pass)return 'NO_FIELDS';function setv(e,v){e.focus();e.value=v;e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));}setv(user,u);setv(pass,p);const f=pass.form||user.form;const b=(f&&f.querySelector('button[type=submit],input[type=submit]'))||Array.from(document.querySelectorAll('button,input[type=button]')).find(x=>/entrar|ingresar|login|aceptar/i.test(x.innerText||x.value||''));if(b){b.click();return 'CLICKED';}if(f){f.requestSubmit?f.requestSubmit():f.submit();return 'SUBMITTED';}return 'NO_FORM';})();"""
    webLogin.evaluateJavascript(js) { result -> if (result?.contains("NO_FIELDS") == true || result?.contains("NO_FORM") == true) { loginAttemptInProgress = false; runOnUiThread { estado.text = "No se reconoció el formulario de login."; Toast.makeText(this, "No se reconocieron los campos; termina el acceso en la página oficial.", Toast.LENGTH_LONG).show() } } }
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

    private fun iniciarConsultaPedidoAutomatica() {
        if (!modoSeleccionPedido || consultaAutoPedidoIniciada || codigoPedidoAuto.isBlank()) return
        if (!webView.url.orEmpty().contains("stockForm.aspx", true)) return
        consultaAutoPedidoIniciada = true
        articuloInput.setText(codigoPedidoAuto)
        articuloInput.setSelection(articuloInput.text.length)
        webView.postDelayed({ if (!isFinishing && !isDestroyed) buscarArticulo() }, 450)
    }

    private fun abrirLogin() {
        webView.loadUrl(URL_LOGIN)
    }

    private fun abrirStock() {
        webView.loadUrl(URL_STOCK)
    }

    private fun prepararFormularioConsultaTrasLogin() {
    val js = """(function(){
        if(document.getElementById('ContentPlaceHolder1_TxtItem') && document.getElementById('ContentPlaceHolder1_btConsult')) return 'FORM_OK';
        var elementos=Array.from(document.querySelectorAll('a,button,input[type=button],input[type=submit]'));
        var consulta=elementos.find(function(e){return /^\s*consulta\s*$/i.test((e.innerText||e.textContent||e.value||'').trim());});
        if(consulta){consulta.click();return 'CLICK_CONSULTA';}
        return 'FORM_MISSING';
    })();"""
    webView.evaluateJavascript(js) { result ->
        runOnUiThread {
            when (result) {
                "\"FORM_OK\"" -> {
                    loginAttemptInProgress = false
                    navegandoAConsultaTrasLogin = false
                    loginDialogVisible = false
                    estado.text = "Sesión iniciada. Ya puedes consultar el artículo."
                    iniciarConsultaPedidoAutomatica()
                }
                "\"CLICK_CONSULTA\"" -> {
                    estado.text = "Sesión iniciada. Abriendo formulario de consulta…"
                    webView.postDelayed({
                        webView.evaluateJavascript("!!document.getElementById('ContentPlaceHolder1_TxtItem') && !!document.getElementById('ContentPlaceHolder1_btConsult')") { comprobacion ->
                            if (comprobacion == "true") {
                                loginAttemptInProgress = false
                                navegandoAConsultaTrasLogin = false
                                loginDialogVisible = false
                                estado.text = "Sesión iniciada. Ya puedes consultar el artículo."
                                iniciarConsultaPedidoAutomatica()
                            } else {
                                estado.text = "Sesión iniciada. Pulsa Consultar; la app intentará abrir el formulario automáticamente."
                            }
                        }
                    }, 700)
                }
                else -> {
                    // Reintentar una navegación normal al formulario si la página no expuso el acceso Consulta.
                    if (!navegandoAConsultaTrasLogin) {
                        navegandoAConsultaTrasLogin = true
                        webView.loadUrl(URL_STOCK)
                    } else {
                        loginAttemptInProgress = false
                        navegandoAConsultaTrasLogin = false
                        estado.text = "Sesión iniciada. Si no aparece el formulario, abre Consulta desde el menú lateral."
                    }
                }
            }
        }
    }
}

private fun buscarArticulo() {
    val articulo = articuloInput.text.toString().trim()

    if (webView.url?.contains("LoginForm.aspx", true) == true) {
        mostrarFormularioLogin(false)
        return
    }

    if (articulo.isBlank()) {
        Toast.makeText(
            this,
            "Introduce un código de artículo",
            Toast.LENGTH_SHORT
        ).show()
        return
    }

    if (consultaEnCurso) {
        return
    }

    // Invalidar los datos de la consulta anterior.
    consultaEnCurso = true
    consultaLista = false

    botonConsultar.isEnabled = false
    botonLeerData.isEnabled = false

    estado.text = "Iniciando consulta de $articulo…"
    progreso.visibility = ProgressBar.VISIBLE

    articuloConsultado = articulo
    val valorSeguro = org.json.JSONObject.quote(articulo)

    val javascript = """
        (function() {
            var campo = document.getElementById('ContentPlaceHolder1_TxtItem');
            var boton = document.getElementById('ContentPlaceHolder1_btConsult');

            if (!campo || !boton) {
                return 'formulario_no_encontrado';
            }

            if (!window.Sys ||
                !Sys.WebForms ||
                !Sys.WebForms.PageRequestManager) {
                return 'ajax_no_disponible';
            }

            var prm = Sys.WebForms.PageRequestManager.getInstance();

            if (!window.__aqStockHandlersInstalled) {

                window.__aqConsultaActiva = false;
                window.__aqSolicitudIniciada = false;

                prm.add_beginRequest(function(sender, args) {

                    var elemento = args.get_postBackElement();
                    var corresponde = false;

                    while (elemento) {
                        if (elemento.id === 'ContentPlaceHolder1_btConsult') {
                            corresponde = true;
                            break;
                        }

                        elemento = elemento.parentNode;
                    }

                    if (corresponde) {
                        window.__aqConsultaActiva = true;
                        window.__aqSolicitudIniciada = true;

                        AndroidStock.consultaIniciada();
                    }
                });

                prm.add_endRequest(function(sender, args) {

                    if (window.__aqConsultaActiva) {

                        window.__aqConsultaActiva = false;

                        var error = args.get_error();

                        if (error) {
                            AndroidStock.consultaFinalizada(
                                false,
                                String(error.message || error)
                            );
                        } else {
                            var tabla = document.getElementById(
                                'ContentPlaceHolder1_gvStock'
                            );

                            var sinExistencias = false;

                            if (tabla &&
                                tabla.rows.length === 1 &&
                                tabla.rows[0].cells.length === 1 &&
                                tabla.rows[0].cells[0].colSpan > 1) {
                                sinExistencias = true;
                            }

                            if (sinExistencias) {
                                AndroidStock.consultaFinalizada(
                                    false,
                                    'SIN_EXISTENCIAS'
                                );
                            } else {
                                AndroidStock.consultaFinalizada(
                                    true,
                                    ''
                                );
                            }
                        }
                    }
                });

                window.__aqStockHandlersInstalled = true;
            }

            window.__aqSolicitudIniciada = false;

            campo.value = $valorSeguro;

            campo.dispatchEvent(
                new Event('input', { bubbles: true })
            );

            campo.dispatchEvent(
                new Event('change', { bubbles: true })
            );

            boton.click();

            if (!window.__aqSolicitudIniciada) {
                return 'postback_no_iniciado';
            }

            return 'consulta_enviada';

        })();
    """.trimIndent()

    webView.evaluateJavascript(javascript) { resultado ->

        when {
            resultado == "\"consulta_enviada\"" -> {
                estado.text = "Esperando respuesta de Aquarella…"

                Toast.makeText(
                    this,
                    "Consulta enviada",
                    Toast.LENGTH_SHORT
                ).show()
            }

            else -> {
                consultaEnCurso = false
                consultaLista = false

                botonConsultar.isEnabled = true
                botonLeerData.isEnabled = false

                progreso.visibility = ProgressBar.GONE

                estado.text = "No se pudo iniciar la consulta."

                val mensaje = when (resultado) {
                    "\"formulario_no_encontrado\"" ->
                        "No se encontró el formulario. Abre primero Consulta."

                    "\"ajax_no_disponible\"" ->
                        "No se detectó el sistema AJAX de Aquarella. Recarga la página."

                    "\"postback_no_iniciado\"" ->
                        "Aquarella no inició la consulta. Verifica el formulario e inténtalo nuevamente."

                    else ->
                        "No se pudo iniciar la consulta. Resultado: $resultado"
                }

                Toast.makeText(
                    this,
                    mensaje,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

    
private fun verificarImagenArticulo(codigo: String) {
    if (codigo.isBlank()) {
        mostrarResultadoSinImagen(codigo)
        return
    }

    val urlImagen =
        "https://aq.manaco.com.bo/ArticulosAQ/${android.net.Uri.encode(codigo)}.jpg"

    Thread {
        var imagen: android.graphics.Bitmap? = null

        try {
            val conexion = java.net.URL(urlImagen)
                .openConnection() as java.net.HttpURLConnection

            conexion.requestMethod = "GET"
            conexion.connectTimeout = 10000
            conexion.readTimeout = 10000
            conexion.instanceFollowRedirects = true
            conexion.connect()

            if (conexion.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                imagen = conexion.inputStream.use {
                    android.graphics.BitmapFactory.decodeStream(it)
                }
            }

            conexion.disconnect()
        } catch (e: Exception) {
            android.util.Log.w(
                "ConsultaStock",
                "No se pudo verificar la imagen de $codigo",
                e
            )
        }

        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread

            if (imagen != null) {
                mostrarResultadoSinExistencias(codigo, imagen!!)
            } else {
                mostrarResultadoSinImagen(codigo)
            }
        }
    }.start()
}

private fun mostrarResultadoSinExistencias(
    codigo: String,
    imagen: android.graphics.Bitmap
) {
    val dp = { valor: Int ->
        (valor * resources.displayMetrics.density).toInt()
    }

    val contenido = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(18), dp(12), dp(18), dp(12))
    }

    val textoCodigo = TextView(this).apply {
        text = "Artículo: $codigo"
        textSize = 16f
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
    }

    contenido.addView(
        textoCodigo,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    )

    val vistaImagen = android.widget.ImageView(this).apply {
        setImageBitmap(imagen)
        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        adjustViewBounds = true
        maxHeight = dp(250)
    }

    contenido.addView(
        vistaImagen,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(250)
        ).apply {
            topMargin = dp(10)
            bottomMargin = dp(10)
        }
    )

    val mensaje = TextView(this).apply {
        text = "No tiene existencias en tallas"
        textSize = 17f
        setTypeface(null, Typeface.BOLD)
        setTextColor(android.graphics.Color.RED)
        gravity = Gravity.CENTER
    }

    contenido.addView(
        mensaje,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    )

    AlertDialog.Builder(this)
        .setTitle("Resultado de stock")
        .setView(contenido)
        .setPositiveButton("Cerrar", null)
        .show()
}

private fun mostrarResultadoSinImagen(codigo: String) {
    val mensaje = if (codigo.isBlank()) {
        "Aquarella no encontró existencias en el stock."
    } else {
        "Código no válido o imagen no disponible. Revisa el código e intenta nuevamente."
    }

    val texto = TextView(this).apply {
        text = mensaje
        textSize = 16f
        setTypeface(null, Typeface.BOLD)
        setTextColor(android.graphics.Color.RED)
        gravity = Gravity.CENTER
        setPadding(24, 20, 24, 20)
    }

    AlertDialog.Builder(this)
        .setTitle(if (codigo.isBlank()) "Sin existencias" else "Verificar artículo")
        .setView(texto)
        .setPositiveButton("Cerrar", null)
        .show()
}

private fun leerTabla() {

    if (consultaEnCurso || !consultaLista) {
        Toast.makeText(
            this,
            "Espera a que termine la consulta de Aquarella.",
            Toast.LENGTH_SHORT
        ).show()
        return
    }

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

            // Reconocimiento dinámico de columnas de tallas.
            // No depende de nombres específicos (T, D, números, letras, etc.).
            // Primero descarta columnas identificadoras/descriptivas conocidas y
            // busca columnas con valores de cantidades en las filas de tiendas.
            // Si Aquarella incluye una fila TOTALES, también valida que la suma
            // de las tiendas coincida con el total de cada columna candidata.
            val columnasNoTalla = setOf(
                "tienda", "storage", "almacen", "deposito", "bodega",
                "articulo", "color", "cantidad", "precio", "total",
                "subtotal", "importe", "valor", "peso", "codigo", "cod",
                "ubicacion", "descuento", "porcentaje", "observacion"
            )

            val filasTotales = registros.filter { fila ->
                fila.getOrNull(indiceTienda).orEmpty()
                    .trim()
                    .startsWith("TOTALES", ignoreCase = true)
            }

            val filasDatos = registros.filter { fila ->
                !fila.getOrNull(indiceTienda).orEmpty()
                    .trim()
                    .startsWith("TOTALES", ignoreCase = true)
            }

            fun valorNumericoStock(texto: String): Double? {
                val limpio = texto.trim().replace(",", "")
                return when (limpio) {
                    "-", "—" -> 0.0
                    else -> limpio.toDoubleOrNull()
                }
            }

            val indicesCandidatos = cabeceras.indices.filter { indice ->
                val encabezadoNormalizado = normalizar(cabeceras[indice])
                val esColumnaIdentificadora = indice == indiceTienda ||
                    indice == indiceArticulo ||
                    indice == indiceColor ||
                    indice == indicePrecio
                val esColumnaNoTalla = encabezadoNormalizado in columnasNoTalla ||
                    encabezadoNormalizado.startsWith("total ") ||
                    encabezadoNormalizado.startsWith("subtotal ")

                if (esColumnaIdentificadora || esColumnaNoTalla || encabezadoNormalizado.isBlank()) {
                    false
                } else {
                    val valores = filasDatos.map { fila ->
                        fila.getOrNull(indice).orEmpty().trim()
                    }.filter { it.isNotBlank() }

                    valores.isNotEmpty() && valores.all { valor ->
                        valorNumericoStock(valor) != null
                    }
                }
            }

            // Comprobación adicional basada en la fila TOTALES, si está disponible.
            // Las columnas cuya suma por tiendas coincide con el total tienen
            // evidencia más fuerte de ser cantidades de stock.
            val indicesTallasValidados = if (filasTotales.isNotEmpty()) {
                indicesCandidatos.filter { indice ->
                    val totalTexto = filasTotales.firstOrNull()
                        ?.getOrNull(indice)
                        ?.trim()
                        .orEmpty()
                    val totalEsperado = valorNumericoStock(totalTexto)

                    if (totalEsperado == null) {
                        false
                    } else {
                        val sumaTiendas = filasDatos.sumOf { fila ->
                            valorNumericoStock(
                                fila.getOrNull(indice).orEmpty().trim()
                            ) ?: 0.0
                        }
                        kotlin.math.abs(sumaTiendas - totalEsperado) < 0.0001
                    }
                }
            } else {
                emptyList()
            }

            // Si la fila TOTALES permite validar al menos una columna,
            // usamos el grupo validado. Si no, conservamos el reconocimiento
            // dinámico por valores numéricos para no bloquear consultas legítimas.
            val indicesTallas = if (indicesTallasValidados.isNotEmpty()) {
                indicesTallasValidados
            } else {
                indicesCandidatos
            }

            if (indicesTallas.isEmpty()) {
                mostrarMensajeStock("No se encontraron columnas de tallas con cantidades numéricas.")
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
    "Bs. " + precio.substring(2).trim().removePrefix(".").trim()
    } else {
        "Bs. $precio"
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

    var textoCopiarDisponibilidad = buildString {
        append(
            (
                listOf("Artículo", "Color", "Precio", "Código tienda", "Ciudad") +
                indicesTallasVisibles.map { cabeceras[it] }
            ).joinToString("\t")
        )
        append("\n")
        }

    val seleccionPedido = linkedMapOf<String, Int>()

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

    if (modoSeleccionPedido) {
        listaDisponibilidad.addView(TextView(this).apply { text = "Modo pedido: toca una cantidad para agregar una unidad; mantén presionado para quitar una.", textSize = 11f, setPadding(dp(2), dp(0), dp(2), dp(6)), setTextColor(android.graphics.Color.rgb(30, 90, 150)) })
    }

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
                        setTextColor(android.graphics.Color.rgb(22, 128, 68))
                        setTypeface(null, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        if (modoSeleccionPedido) {
                            isClickable = true
                            isFocusable = true
                            val clave = "$codigoTienda|$ciudad|$numero"
                            setOnClickListener {
                                val actual = seleccionPedido[clave] ?: 0
                                if (actual < existencia) {
                                    seleccionPedido[clave] = actual + 1
                                    text = "$existencia\n×${actual + 1}"
                                    maxLines = 2
                                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
                                    setTextColor(android.graphics.Color.rgb(12, 91, 157))
                                    background = GradientDrawable().apply { setColor(android.graphics.Color.rgb(220, 238, 255)); cornerRadius = dp(4).toFloat(); setStroke(dp(1), android.graphics.Color.rgb(40, 120, 205)) }
                                } else Toast.makeText(this@ConsultaStockActivity, "No puedes seleccionar más que el stock disponible ($existencia)", Toast.LENGTH_SHORT).show()
                            }
                            setOnLongClickListener {
                                val actual = seleccionPedido[clave] ?: 0
                                if (actual > 0) {
                                    if (actual == 1) seleccionPedido.remove(clave) else seleccionPedido[clave] = actual - 1
                                    val restante = seleccionPedido[clave] ?: 0
                                    text = if (restante > 0) "$existencia\n×$restante" else existencia.toString()
                                    if (restante == 0) { setTextColor(android.graphics.Color.rgb(22, 128, 68)); background = null }
                                }
                                true
                            }
                        }
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
            (
                listOf(
                    valor(fila, indiceArticulo),
                    valor(fila, indiceColor),
                    precioMostrar,
                    codigoTienda,
                    ciudad
                ) +
                indicesTallasVisibles.map { indice ->
                    cantidad(fila, indice).toString()
                }
            ).joinToString("\t") + "\n"
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
    // VISTA DATA GENERAL - TABLA ESTILO EXCEL
    // -------------------------------------------------

    // Conservamos el texto original para el botón Copiar.
    val textoGeneral = buildString {
        append(cabeceras.joinToString("\t"))
        append("\n")

        registros.forEach { fila ->
            append(fila.joinToString("\t"))
            append("\n")
        }
    }

    val azulEncabezado = android.graphics.Color.rgb(42, 112, 174)
    val azulBorde = android.graphics.Color.rgb(175, 194, 213)
    val azulTotal = android.graphics.Color.rgb(218, 235, 250)
    val fondoAlterno = android.graphics.Color.rgb(245, 248, 252)
    val fondoBlanco = android.graphics.Color.WHITE
    val textoOscuro = android.graphics.Color.rgb(35, 45, 55)

    fun normalizarTexto(valor: String): String {
    return java.text.Normalizer.normalize(
        valor,
        java.text.Normalizer.Form.NFD
    )
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.ROOT)
        .trim()
    }

    val tablaExcel = android.widget.TableLayout(this).apply {
        isShrinkAllColumns = false
        isStretchAllColumns = false
        setPadding(0, 0, 0, 0)
    }

    // Columnas identificadas como columnas de totales.
    val columnasTotales = cabeceras.map { encabezado ->
        normalizarTexto(encabezado).contains("total")
    }

    fun crearCelda(
        contenido: String,
        esEncabezado: Boolean,
        esTotal: Boolean,
        esPrimeraCeldaTotal: Boolean,
        indiceFila: Int
    ): TextView {

        val fondo = when {
            esEncabezado -> azulEncabezado
            esPrimeraCeldaTotal -> android.graphics.Color.rgb(197, 222, 244)
            esTotal -> azulTotal
            indiceFila % 2 == 0 -> fondoBlanco
            else -> fondoAlterno
        }

        return TextView(this).apply {
            text = contenido
            textSize = if (esEncabezado) 11f else 11f

            gravity = Gravity.CENTER

            setTextColor(
                if (esEncabezado) {
                    android.graphics.Color.WHITE
                } else {
                    textoOscuro
                }
            )

            setTypeface(
                null,
                if (esEncabezado || esTotal) {
                    Typeface.BOLD
                } else {
                    Typeface.NORMAL
                }
            )

            setPadding(
                dp(9),
                dp(9),
                dp(9),
                dp(9)
            )

            minWidth = dp(if (esEncabezado) 90 else 75)

            setTextIsSelectable(true)

            background = GradientDrawable().apply {
                setColor(fondo)
                setStroke(dp(1), azulBorde)
            }
        }
    }

    // Encabezados.
    val filaEncabezados = android.widget.TableRow(this).apply {
        gravity = Gravity.CENTER_VERTICAL
    }

    cabeceras.forEach { encabezado ->

        filaEncabezados.addView(
            crearCelda(
                contenido = encabezado,
                esEncabezado = true,
                esTotal = false,
                esPrimeraCeldaTotal = false,
                indiceFila = 0
            ),
            android.widget.TableRow.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    tablaExcel.addView(
        filaEncabezados,
        android.widget.TableLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    )

    // Filas de información.
    registros.forEachIndexed { indiceFila, fila ->

        val esFilaTotal = fila.any {
            normalizarTexto(it).contains("total")
        }

        val filaTabla = android.widget.TableRow(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        cabeceras.indices.forEach { indiceColumna ->

            val contenido = valor(fila, indiceColumna)

            val esColumnaTotal =
                columnasTotales.getOrNull(indiceColumna) == true

            val esPrimeraCeldaTotal =
                esFilaTotal && indiceColumna == 0

            filaTabla.addView(
                crearCelda(
                    contenido = contenido,
                    esEncabezado = false,
                    esTotal = esFilaTotal || esColumnaTotal,
                    esPrimeraCeldaTotal = esPrimeraCeldaTotal,
                    indiceFila = indiceFila
                ),
                android.widget.TableRow.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        tablaExcel.addView(
            filaTabla,
            android.widget.TableLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    // Marco exterior con esquinas redondeadas.
    val marcoTabla = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(1), dp(1), dp(1), dp(1))

        background = GradientDrawable().apply {
            setColor(android.graphics.Color.WHITE)
            cornerRadius = dp(10).toFloat()
            setStroke(dp(1), azulEncabezado)
        }

        clipToOutline = true

        addView(
            tablaExcel,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    // Desplazamiento horizontal.
    val scrollHorizontalGeneral =
        android.widget.HorizontalScrollView(this).apply {

            isHorizontalScrollBarEnabled = true
            isFillViewport = false

            addView(
                marcoTabla,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

    // Desplazamiento vertical.
    val scrollGeneral =
        android.widget.ScrollView(this).apply {

            isFillViewport = true

            addView(
                scrollHorizontalGeneral,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

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

    if (modoSeleccionPedido) botonGeneral.visibility = android.view.View.GONE

    // Primero se muestran los botones para cambiar entre las vistas.
    raizDialogo.addView(selector)

    // Imagen del artículo: se muestra debajo de los botones.
    val imagenArticulo = ImageView(this).apply {
        adjustViewBounds = true
        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        visibility = android.view.View.GONE
    }

    raizDialogo.addView(
        imagenArticulo,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(170)
        )
    )

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

    // Título personalizado con botón circular rojo para cerrar el resultado.
    val tituloDialogo = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(20), dp(8), dp(12), dp(8))
    }

    val textoTitulo = TextView(this).apply {
        text = if (modoSeleccionPedido) "Seleccionar pedido · Stock" else "Resultado de stock"
        textSize = 20f
        setTypeface(null, Typeface.BOLD)
        setTextColor(android.graphics.Color.BLACK)
        gravity = Gravity.CENTER_VERTICAL
    }

    val botonCerrarX = TextView(this).apply {
        text = "×"
        textSize = 25f
        setTypeface(null, Typeface.BOLD)
        setTextColor(android.graphics.Color.WHITE)
        gravity = Gravity.CENTER
        contentDescription = "Cerrar resultado de stock"
        isClickable = true
        isFocusable = true
        elevation = dp(2).toFloat()

        val colorRojoCalido = android.graphics.Color.rgb(232, 88, 75)
        val colorBorde = android.graphics.Color.rgb(207, 67, 56)
        val formaCircular = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colorRojoCalido)
            setStroke(dp(1), colorBorde)
        }
        val mascara = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(android.graphics.Color.WHITE)
        }
        background = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(
                android.graphics.Color.rgb(255, 190, 180)
            ),
            formaCircular,
            mascara
        )
    }

    tituloDialogo.addView(
        textoTitulo,
        LinearLayout.LayoutParams(0, dp(48), 1f)
    )
    tituloDialogo.addView(
        botonCerrarX,
        LinearLayout.LayoutParams(dp(42), dp(42))
    )

    val dialogo = AlertDialog.Builder(this)
        .setCustomTitle(tituloDialogo)
        .setView(raizDialogo)
        .setPositiveButton(if (modoSeleccionPedido) "Agregar selección" else "Copiar", null)
        .setNegativeButton(if (modoSeleccionPedido) "Cancelar" else "Cerrar", null)
        .create()

    botonCerrarX.setOnClickListener {
        if (modoSeleccionPedido) { setResult(RESULT_CANCELED); finish() }
        dialogo.dismiss()
    }

    dialogo.setOnShowListener {

        dialogo.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.78).toInt()
        )

        if (modoSeleccionPedido) {
            dialogo.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                setResult(RESULT_CANCELED)
                dialogo.dismiss()
                finish()
            }
        }

        dialogo.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener {
                if (modoSeleccionPedido) {
                    val seleccionado = org.json.JSONArray()
                    seleccionPedido.forEach { (clave, qty) ->
                        if (qty > 0) {
                            val partes = clave.split("|", limit = 3)
                            if (partes.size == 3) seleccionado.put(org.json.JSONObject().apply {
                                put("CodigoCiudad", partes[0])
                                put("Ciudad", partes[1])
                                put("Talla", partes[2])
                                put("Cantidad", qty)
                            })
                        }
                    }
                    if (seleccionado.length() == 0) {
                        Toast.makeText(this, "Selecciona al menos una existencia", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    setResult(RESULT_OK, android.content.Intent().putExtra("seleccionStock", seleccionado.toString()))
                    dialogo.dismiss()
                    finish()
                } else {
                    val contenidoCopiar = if (scrollDisponibilidad.visibility == android.view.View.VISIBLE) textoCopiarDisponibilidad else textoGeneral
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Stock Aquarella", contenidoCopiar))
                    Toast.makeText(this, "Resultado copiado", Toast.LENGTH_SHORT).show()
                }
            }
    }

    dialogo.show()

    // El código del artículo se obtiene de los registros ya leídos.
    val codigoArticulo = registros
        .firstOrNull()
        ?.getOrNull(indiceArticulo)
        ?.trim()
        .orEmpty()

    if (codigoArticulo.isNotBlank()) {
        cargarImagenArticulo(codigoArticulo, imagenArticulo)
    }
}


private fun cargarImagenArticulo(codigo: String, vista: ImageView) {
    Thread {
        var bitmap: android.graphics.Bitmap? = null

        try {
            val codigoSeguro = java.net.URLEncoder.encode(codigo, "UTF-8")
            val url = URL(
                "https://raw.githubusercontent.com/aquarellasucre/AQ2026/main/JPG/$codigoSeguro.jpeg"
            )
            val conexion = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 15000
                setRequestProperty("User-Agent", "CopiarAQ")
            }

            try {
                if (conexion.responseCode == HttpURLConnection.HTTP_OK) {
                    conexion.inputStream.use { entrada ->
                        bitmap = BitmapFactory.decodeStream(entrada)
                    }
                }
            } finally {
                conexion.disconnect()
            }
        } catch (e: Exception) {
            android.util.Log.w(
                "ConsultaStock",
                "No se pudo cargar la imagen del artículo $codigo",
                e
            )
        }

        runOnUiThread {
            val imagenCargada = bitmap
            if (imagenCargada != null) {
                vista.setImageBitmap(imagenCargada)
                vista.visibility = android.view.View.VISIBLE
            } else {
                vista.setImageDrawable(null)
                vista.visibility = android.view.View.GONE
            }
        }
    }.start()
}


private fun mostrarMensajeStock(texto: String) {
    AlertDialog.Builder(this)
        .setTitle("Resultado de stock")
        .setMessage(texto)
        .setPositiveButton("Cerrar", null)
        .show()
}


    override fun onBackPressed() {
        val actual = if (moduloActivo == "PEDIDO") pedidoWebView else webView
        if (actual.canGoBack()) actual.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
        if (::pedidoWebView.isInitialized) {
            pedidoWebView.stopLoading()
            pedidoWebView.loadUrl("about:blank")
            pedidoWebView.destroy()
        }
        super.onDestroy()
    }
}
