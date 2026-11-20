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
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import java.util.Locale

class DetallePedidoActivity : Activity() {

    companion object {
        private const val API_URL = "https://script.google.com/macros/s/AKfycbx6m4cznqM73clGWY4KbfEpxjhzWvST1ule1oJO9Hj2KRVC9s_qb0xDOkbypDeRhDxl/exec"
    }

    private lateinit var pedidoId: String
    private lateinit var clienteNombre: String
    private lateinit var articulo: EditText
    private lateinit var descripcion: EditText
    private lateinit var talla: EditText
    private lateinit var cantidad: EditText
    private lateinit var precio: EditText
    private lateinit var descuento: EditText
    private lateinit var guardarLinea: Button
    private lateinit var resumen: TextView
    private lateinit var totalSesion: TextView
    private lateinit var finalizar: Button

    private var cantidadLineas = 0
    private var totalAcumulado = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pedidoId = intent.getStringExtra("PedidoID").orEmpty()
        clienteNombre = intent.getStringExtra("Cliente").orEmpty()
        if (pedidoId.isBlank()) {
            Toast.makeText(this, "No se recibió el PedidoID", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        construirPantalla()
    }

    private fun construirPantalla() {
        val contenedor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        val scrollContenido = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val titulo = TextView(this).apply {
            text = "Agregar artículos"
            textSize = 23f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFFAA232D.toInt())
        }
        scrollContenido.addView(titulo, parametros(0))
        scrollContenido.addView(TextView(this).apply {
            text = "Pedido: $pedidoId\nCliente: $clienteNombre"
            textSize = 15f
            setPadding(0, dp(8), 0, dp(16))
        }, parametros(0))
        scrollContenido.addView(TextView(this).apply {
            text = "Ingresa los artículos manualmente. El catálogo todavía no está conectado."
            textSize = 13f
            setPadding(0, 0, 0, dp(14))
        }, parametros(0))

        articulo = campo("Código / artículo (obligatorio)")
        descripcion = campo("Descripción (opcional)")
        talla = campo("Talla (opcional)")
        cantidad = campo("Cantidad", numeric = true).apply { setText("1") }
        precio = campo("Precio unitario (Bs)", decimal = true)
        descuento = campo("Descuento total de esta línea (Bs, opcional)", decimal = true).apply { setText("0") }
        listOf(articulo, descripcion, talla, cantidad, precio, descuento).forEach { scrollContenido.addView(it, parametros(10)) }

        guardarLinea = Button(this).apply {
            text = "Guardar artículo"
            isAllCaps = false
            setOnClickListener { enviarDetalle() }
        }
        scrollContenido.addView(guardarLinea, parametros(10))

        resumen = TextView(this).apply {
            text = "Artículos guardados en esta sesión: 0"
            textSize = 15f
            setPadding(0, dp(18), 0, dp(8))
        }
        scrollContenido.addView(resumen, parametros(0))
        totalSesion = TextView(this).apply {
            text = "Total agregado en esta sesión: Bs 0.00"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(18))
        }
        scrollContenido.addView(totalSesion, parametros(0))

        finalizar = Button(this).apply {
            text = "Terminar pedido"
            isAllCaps = false
            setOnClickListener {
                AlertDialog.Builder(this@DetallePedidoActivity)
                    .setTitle("Pedido actualizado")
                    .setMessage("Se guardaron $cantidadLineas artículo(s) en esta sesión.\nTotal agregado: Bs ${formato(totalAcumulado)}\n\nPedidoID: $pedidoId")
                    .setPositiveButton("Aceptar") { _, _ -> finish() }
                    .show()
            }
        }
        scrollContenido.addView(finalizar, parametros(8))

        val scroll = ScrollView(this).apply { addView(scrollContenido) }
        contenedor.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(contenedor)
    }

    private fun campo(hintTexto: String, numeric: Boolean = false, decimal: Boolean = false): EditText = EditText(this).apply {
        hint = hintTexto
        textSize = 16f
        if (numeric) inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        if (decimal) inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
    }

    private fun parametros(margen: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(margen) }

    private fun enviarDetalle() {
        val codigo = articulo.text.toString().trim()
        val qty = cantidad.text.toString().trim().toDoubleOrNull()
        val price = precio.text.toString().trim().replace(',', '.').toDoubleOrNull()
        val disc = descuento.text.toString().trim().replace(',', '.').toDoubleOrNull() ?: 0.0
        if (codigo.isBlank()) { articulo.error = "Ingresa el artículo"; articulo.requestFocus(); return }
        if (qty == null || qty <= 0) { cantidad.error = "Cantidad debe ser mayor a cero"; cantidad.requestFocus(); return }
        if (price == null || price < 0) { precio.error = "Ingresa un precio válido"; precio.requestFocus(); return }
        if (disc < 0 || disc > qty * price) { descuento.error = "El descuento no puede superar el importe de la línea"; descuento.requestFocus(); return }
        val subtotal = qty * price - disc
        val token = BuildConfig.COPIAR_AQ_PEDIDOS_TOKEN
        if (token.isBlank()) {
            AlertDialog.Builder(this).setTitle("Falta configurar el acceso")
                .setMessage("La APK no contiene el token de Registro de Pedidos.")
                .setPositiveButton("Aceptar", null).show()
            return
        }
        guardarLinea.isEnabled = false
        guardarLinea.text = "Guardando…"
        val datos = JSONObject().apply {
            put("token", token)
            put("accion", "agregarDetalle")
            put("PedidoID", pedidoId)
            put("Articulo", codigo)
            put("Descripcion", descripcion.text.toString().trim())
            put("Talla", talla.text.toString().trim())
            put("Cantidad", qty)
            put("Precio", price)
            put("Descuento", disc)
        }
        thread {
            var result: JSONObject? = null
            var err: String? = null
            try {
                val conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 30000
                    doOutput = true
                    instanceFollowRedirects = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }
                conn.outputStream.use { it.write(datos.toString().toByteArray(Charsets.UTF_8)) }
                val status = conn.responseCode
                val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                conn.disconnect()
                if (status !in 200..299) throw Exception("HTTP $status: $body")
                result = JSONObject(body)
                if (!result!!.optBoolean("ok")) throw Exception(result!!.optString("mensaje", "El servidor rechazó el artículo"))
            } catch (e: Exception) { err = e.message ?: "Error de conexión" }
            runOnUiThread {
                guardarLinea.isEnabled = true
                guardarLinea.text = "Guardar artículo"
                if (err != null) {
                    AlertDialog.Builder(this).setTitle("No se guardó el artículo")
                        .setMessage(err).setPositiveButton("Aceptar", null).show()
                } else {
                    cantidadLineas++
                    totalAcumulado = result?.optDouble("TotalPedido", totalAcumulado + subtotal) ?: (totalAcumulado + subtotal)
                    resumen.text = "Artículos guardados en esta sesión: $cantidadLineas"
                    totalSesion.text = "Total del pedido: Bs ${formato(totalAcumulado)}"
                    Toast.makeText(this, "Artículo guardado. Subtotal: Bs ${formato(subtotal)}", Toast.LENGTH_LONG).show()
                    articulo.text.clear(); descripcion.text.clear(); talla.text.clear(); cantidad.setText("1"); precio.text.clear(); descuento.setText("0")
                    articulo.requestFocus()
                }
            }
        }
    }

    private fun formato(valor: Double): String = String.format(Locale.US, "%.2f", valor)
    private fun dp(valor: Int): Int = (valor * resources.displayMetrics.density).toInt()
}
