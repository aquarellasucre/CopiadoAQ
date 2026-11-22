package bo.aquarella.copiar

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class RegistroPedidoActivity : Activity() {

    companion object {
        private const val API_URL = "https://script.google.com/macros/s/AKfycbx6m4cznqM73clGWY4KbfEpxjhzWvST1ule1oJO9Hj2KRVC9s_qb0xDOkbypDeRhDxl/exec"
    }

    private lateinit var cliente: EditText
    private var tipoVentaSeleccionado = "Cotización"
    private lateinit var grupoTipoVenta: LinearLayout
    private lateinit var botonesTipoVenta: List<TextView>
    private lateinit var descripcion: EditText
    private lateinit var usuario: EditText
    private lateinit var guardar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        construirPantalla()
    }

    private fun construirPantalla() {
        val contenido = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        val titulo = TextView(this).apply {
            text = "Registro de Pedido"
            textSize = 24f
            setTextColor(0xFFAA232D.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        contenido.addView(titulo)
        val ayuda = TextView(this).apply {
            text = "Prueba de registro en Google Sheets. Por ahora se guarda la cabecera del pedido."
            textSize = 14f
            setPadding(0, dp(8), 0, dp(18))
        }
        contenido.addView(ayuda)

        cliente = campo("Cliente (obligatorio)")
        contenido.addView(cliente, parametrosCampo())

        val etiquetaTipo = TextView(this).apply {
            text = "Seleccionar tipo de venta"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFF243654.toInt())
            setPadding(0, dp(4), 0, dp(8))
        }
        contenido.addView(etiquetaTipo, parametrosCampo())
        grupoTipoVenta = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val nombres = listOf("Contado", "Crédito", "Cotización")
        botonesTipoVenta = nombres.map { nombre ->
            TextView(this).apply {
                text = nombre
                gravity = Gravity.CENTER
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                isClickable = true
                isFocusable = true
                setPadding(dp(6), dp(14), dp(6), dp(14))
                setOnClickListener {
                    tipoVentaSeleccionado = nombre
                    actualizarBotonesTipoVenta()
                }
            }
        }
        botonesTipoVenta.forEachIndexed { index, boton ->
            grupoTipoVenta.addView(boton, LinearLayout.LayoutParams(0, dp(70), 1f).apply {
                if (index > 0) leftMargin = dp(5)
            })
        }
        contenido.addView(grupoTipoVenta, parametrosCampo())
        actualizarBotonesTipoVenta()

        descripcion = campo("Descripción / observaciones (opcional)", multilinea = true)
        contenido.addView(descripcion, parametrosCampo())

        usuario = campo("Usuario que registra (opcional)")
        contenido.addView(usuario, parametrosCampo())

        guardar = Button(this).apply {
            text = "Guardar pedido en Google Sheets"
            isAllCaps = false
            setOnClickListener { guardarPedido() }
        }
        contenido.addView(guardar, parametrosCampo())

        val volver = Button(this).apply {
            text = "Volver"
            isAllCaps = false
            setOnClickListener { finish() }
        }
        contenido.addView(volver, parametrosCampo())

        val scroll = ScrollView(this).apply { addView(contenido) }
        setContentView(scroll)
    }

    private fun campo(hintTexto: String, multilinea: Boolean = false): EditText = EditText(this).apply {
        hint = hintTexto
        textSize = 16f
        if (multilinea) {
            minLines = 2
            maxLines = 4
            gravity = Gravity.TOP
        }
    }

    private fun parametrosCampo() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(10) }

    private fun guardarPedido() {
        val nombreCliente = cliente.text.toString().trim()
        if (nombreCliente.isEmpty()) {
            cliente.error = "Ingresa el cliente"
            cliente.requestFocus()
            return
        }
        val tipo = tipoVentaSeleccionado

        val token = BuildConfig.COPIAR_AQ_PEDIDOS_TOKEN
        if (token.isBlank()) {
            AlertDialog.Builder(this)
                .setTitle("Falta configurar el acceso")
                .setMessage("La APK se compiló sin el token de Registro de Pedidos. Configura el secreto COPIAR_AQ_PEDIDOS_TOKEN en GitHub Actions y vuelve a compilar.")
                .setPositiveButton("Aceptar", null)
                .show()
            return
        }

        guardar.isEnabled = false
        guardar.text = "Guardando…"

        val datos = JSONObject().apply {
            put("token", token)
            put("accion", "registrarPedido")
            put("Cliente", nombreCliente)
            put("TipoVenta", tipo)
            put("Estado", "Solicitado")
            put("Descripcion", descripcion.text.toString().trim())
            put("UsuarioRegistro", usuario.text.toString().trim())
        }

        thread {
            var resultado: JSONObject? = null
            var error: String? = null
            try {
                val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 30000
                    doOutput = true
                    instanceFollowRedirects = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }
                connection.outputStream.use { it.write(datos.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val respuesta = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                connection.disconnect()
                if (code !in 200..299) throw Exception("HTTP $code: $respuesta")
                resultado = JSONObject(respuesta)
                if (!resultado!!.optBoolean("ok")) {
                    throw Exception(resultado!!.optString("mensaje", "El servidor rechazó el pedido"))
                }
                if (resultado!!.optString("PedidoID").isBlank()) {
                    throw Exception("El servidor respondió, pero no devolvió PedidoID. Verifica la implementación de Apps Script y vuelve a intentar.")
                }
            } catch (e: Exception) {
                error = e.message ?: "Error de conexión"
            }

            runOnUiThread {
                guardar.isEnabled = true
                guardar.text = "Guardar pedido en Google Sheets"
                if (error != null) {
                    AlertDialog.Builder(this)
                        .setTitle("No se pudo guardar")
                        .setMessage(error)
                        .setPositiveButton("Aceptar", null)
                        .show()
                } else {
                    val id = resultado?.optString("PedidoID", "").orEmpty()
                    AlertDialog.Builder(this)
                        .setTitle("Pedido registrado")
                        .setMessage("El pedido se guardó correctamente en Google Sheets.\n\nPedidoID:\n$id\n\nAhora puedes agregar los artículos al pedido.")
                        .setPositiveButton("Agregar artículos") { _, _ ->
                            val intent = android.content.Intent(this, DetallePedidoActivity::class.java)
                            intent.putExtra("PedidoID", id)
                            intent.putExtra("Cliente", nombreCliente)
                            startActivity(intent)
                            finish()
                        }
                        .setNeutralButton("Nuevo pedido") { _, _ -> limpiarFormulario() }
                        .setNegativeButton("Cerrar") { _, _ -> finish() }
                        .setCancelable(false)
                        .show()
                }
            }
        }
    }

    private fun actualizarBotonesTipoVenta() {
        botonesTipoVenta.forEach { boton ->
            val seleccionado = boton.text.toString() == tipoVentaSeleccionado
            boton.setTextColor(if (seleccionado) 0xFF1768C4.toInt() else 0xFF46536A.toInt())
            boton.background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(if (seleccionado) 0xFFE4F0FF.toInt() else 0xFFF8F9FB.toInt())
                setStroke(dp(if (seleccionado) 2 else 1), if (seleccionado) 0xFF2D83E8.toInt() else 0xFFD0D4DE.toInt())
            }
            boton.contentDescription = if (seleccionado) "${boton.text}, seleccionado" else boton.text.toString()
        }
    }

    private fun limpiarFormulario() {
        cliente.text.clear()
        tipoVentaSeleccionado = "Cotización"
        actualizarBotonesTipoVenta()
        descripcion.text.clear()
        usuario.text.clear()
        cliente.requestFocus()
    }

    private fun dp(valor: Int): Int = (valor * resources.displayMetrics.density).toInt()
}
