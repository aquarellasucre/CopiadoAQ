package bo.aquarella.copiar

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.View
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
    private lateinit var seleccionStock: TextView
    private lateinit var consultarSeleccion: Button
    private lateinit var precio: EditText
    private lateinit var descuento: EditText
    private lateinit var guardarLinea: Button
    private lateinit var resumen: TextView
    private lateinit var totalSesion: TextView
    private lateinit var finalizar: Button

    private var cantidadLineas = 0
    private var totalAcumulado = 0.0
    private val REQUEST_SELECCION_STOCK = 731

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pedidoId = intent.getStringExtra("PedidoID").orEmpty()
        clienteNombre = intent.getStringExtra("Cliente").orEmpty()
        if (pedidoId.isBlank()) {
            Toast.makeText(this, "No se recibió el PedidoID", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
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
            text = "Busca el artículo en el catálogo. Al seleccionarlo se cargarán sus datos y precio."
            textSize = 13f
            setPadding(0, 0, 0, dp(14))
        }, parametros(0))

        articulo = campo("Código / artículo (escribe o pega)")
        val buscar = Button(this).apply {
            text = "Buscar en catálogo"
            isAllCaps = false
            setOnClickListener { buscarCatalogo() }
        }
        scrollContenido.addView(articulo, parametros(4))
        scrollContenido.addView(buscar, parametros(10))
        descripcion = campo("Descripción (opcional)")
        precio = campo("Precio unitario (Bs)", decimal = true)
        descuento = campo("Descuento total de la selección (Bs, opcional)", decimal = true).apply { setText("0") }
        scrollContenido.addView(descripcion, parametros(10))
        scrollContenido.addView(precio, parametros(10))
        scrollContenido.addView(descuento, parametros(10))

        seleccionStock = TextView(this).apply {
            text = "Todavía no se seleccionaron tallas."
            textSize = 14f
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        scrollContenido.addView(seleccionStock, parametros(6))

        consultarSeleccion = Button(this).apply {
            text = "Consultar stock y seleccionar"
            isAllCaps = false
            setOnClickListener { abrirSeleccionStock() }
        }
        scrollContenido.addView(consultarSeleccion, parametros(10))

        guardarLinea = Button(this).apply {
            text = "Agregar selección al pedido"
            isAllCaps = false
            isEnabled = false
            setOnClickListener { guardarSeleccionStock() }
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

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            addView(scrollContenido)
        }
        contenedor.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(contenedor)

        // Cuando el teclado está abierto, el formulario se reduce y sigue siendo deslizable.
        articulo.setOnFocusChangeListener { _, focused ->
            if (focused) scroll.post { scroll.smoothScrollTo(0, articulo.top) }
        }
        descripcion.setOnFocusChangeListener { _, focused ->
            if (focused) scroll.post { scroll.smoothScrollTo(0, descripcion.top) }
        }
        precio.setOnFocusChangeListener { _, focused ->
            if (focused) scroll.post { scroll.smoothScrollTo(0, precio.top) }
        }
        descuento.setOnFocusChangeListener { _, focused ->
            if (focused) scroll.post { scroll.smoothScrollTo(0, descuento.top) }
        }
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


    private fun buscarCatalogo() {
        val consulta = articulo.text.toString().trim()
        if (consulta.isBlank()) {
            articulo.error = "Escribe o pega un código o texto para buscar"
            articulo.requestFocus()
            return
        }
        val token = BuildConfig.COPIAR_AQ_PEDIDOS_TOKEN
        if (token.isBlank()) {
            AlertDialog.Builder(this).setTitle("Falta configurar el acceso")
                .setMessage("La APK no contiene el token de Registro de Pedidos.")
                .setPositiveButton("Aceptar", null).show()
            return
        }

        val botonBuscar = (articulo.parent as? ViewGroup)?.getChildAt(4) as? Button
        botonBuscar?.isEnabled = false
        botonBuscar?.text = "Buscando…"
        val datos = JSONObject().apply {
            put("token", token)
            put("accion", "buscarCatalogo")
            put("consulta", consulta)
        }

        thread {
            var respuesta: JSONObject? = null
            var error: String? = null
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
                respuesta = JSONObject(body)
                if (!respuesta!!.optBoolean("ok")) throw Exception(respuesta!!.optString("mensaje", "Error en la búsqueda"))
            } catch (e: Exception) {
                error = e.message ?: "Error de conexión"
            }
            runOnUiThread {
                botonBuscar?.isEnabled = true
                botonBuscar?.text = "Buscar en catálogo"
                if (error != null) {
                    AlertDialog.Builder(this).setTitle("No se pudo buscar")
                        .setMessage(error).setPositiveButton("Aceptar", null).show()
                    return@runOnUiThread
                }

                val resultados = respuesta?.optJSONArray("resultados")
                if (resultados == null || resultados.length() == 0) {
                    AlertDialog.Builder(this).setTitle("Artículo no encontrado")
                        .setMessage("No se encontraron coincidencias para '$consulta'. Puedes verificar el código e intentarlo nuevamente.")
                        .setPositiveButton("Aceptar", null).show()
                    return@runOnUiThread
                }

                val opciones = ArrayList<JSONObject>()
                val etiquetas = ArrayList<String>()
                for (i in 0 until resultados.length()) {
                    val item = resultados.optJSONObject(i) ?: continue
                    opciones.add(item)
                    val codigo = item.optString("Articulo")
                    val desc = item.optString("BRV_DESCRIPTION")
                    val precioItem = item.optDouble("Precio", 0.0)
                    val stock = item.optString("StockID")
                    etiquetas.add(buildString {
                        append(codigo)
                        if (desc.isNotBlank()) append(" — ").append(desc)
                        append(" — Bs ").append(formato(precioItem))
                        if (stock.isNotBlank()) append(" — ").append(stock)
                    })
                }

                if (opciones.size == 1) {
                    seleccionarArticulo(opciones[0])
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("${opciones.size} coincidencias (selecciona una)")
                        .setItems(etiquetas.toTypedArray()) { _, cual ->
                            seleccionarArticulo(opciones[cual])
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }
            }
        }
    }

    private fun seleccionarArticulo(item: JSONObject) {
        articulo.setText(item.optString("Articulo"))
        descripcion.setText(item.optString("BRV_DESCRIPTION"))
        precio.setText(String.format(Locale.US, "%.2f", item.optDouble("Precio", 0.0)))
        descuento.setText("0")
        seleccionStock.text = "Todavía no se seleccionaron tallas."
        guardarLinea.isEnabled = false
        consultarSeleccion.isEnabled = true
    }

    private fun abrirSeleccionStock() {
        val codigo = articulo.text.toString().trim()
        val price = precio.text.toString().trim().replace(',', '.').toDoubleOrNull()
        if (codigo.isBlank()) { articulo.error = "Selecciona primero un artículo del catálogo"; return }
        if (price == null || price < 0) { precio.error = "Ingresa un precio válido"; return }
        val intent = android.content.Intent(this, ConsultaStockActivity::class.java).apply {
            putExtra("modoSeleccionPedido", true)
            putExtra("ArticuloPedido", codigo)
            putExtra("DescripcionPedido", descripcion.text.toString().trim())
            putExtra("PrecioPedido", price)
            putExtra("PedidoID", pedidoId)
        }
        guardarLinea.tag = null
        guardarLinea.isEnabled = false
        seleccionStock.text = "Consultando existencias…"
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_SELECCION_STOCK)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SELECCION_STOCK) return
        if (resultCode != RESULT_OK) {
            seleccionStock.text = "Selección cancelada. Puedes volver a consultar el stock."
            guardarLinea.tag = null
            guardarLinea.isEnabled = false
            return
        }
        val seleccionJson = data?.getStringExtra("seleccionStock").orEmpty()
        val seleccion = try { org.json.JSONArray(seleccionJson) } catch (_: Exception) { org.json.JSONArray() }
        if (seleccion.length() == 0) {
            seleccionStock.text = "No se seleccionaron tallas."
            guardarLinea.isEnabled = false
            return
        }
        val resumenTexto = (0 until seleccion.length()).mapNotNull { i ->
            seleccion.optJSONObject(i)?.let { "${it.optString("Ciudad")} · ${it.optString("Talla")} × ${it.optInt("Cantidad", 1)}" }
        }.joinToString("\n")
        seleccionStock.text = "Selección recibida:\n$resumenTexto"
        guardarLinea.isEnabled = true
        guardarLinea.tag = seleccionJson
    }

    private fun guardarSeleccionStock() {
        val selectedText = guardarLinea.tag as? String ?: return
        val seleccion = try { org.json.JSONArray(selectedText) } catch (_: Exception) { return }
        val price = precio.text.toString().trim().replace(',', '.').toDoubleOrNull()
        val descuentoTotal = descuento.text.toString().trim().replace(',', '.').toDoubleOrNull() ?: 0.0
        if (price == null || price < 0) { precio.error = "Ingresa un precio válido"; return }
        val unidades = (0 until seleccion.length()).sumOf { seleccion.optJSONObject(it)?.optInt("Cantidad", 0) ?: 0 }
        if (unidades <= 0) return
        if (descuentoTotal < 0 || descuentoTotal > price * unidades) { descuento.error = "El descuento supera el importe seleccionado"; return }

        val lineas = org.json.JSONArray()
        var restante = Math.round(descuentoTotal * 100.0) / 100.0
        var unidadIndex = 0
        for (i in 0 until seleccion.length()) {
            val grupo = seleccion.optJSONObject(i) ?: continue
            val tallaGrupo = grupo.optString("Talla")
            val ciudad = grupo.optString("Ciudad")
            val cantidadGrupo = grupo.optInt("Cantidad", 0)
            repeat(cantidadGrupo) { j ->
                unidadIndex++
                val descUnidad = if (unidadIndex == unidades) restante else Math.round((descuentoTotal / unidades) * 100.0) / 100.0
                restante = Math.round((restante - descUnidad) * 100.0) / 100.0
                lineas.put(org.json.JSONObject().apply { put("Talla", tallaGrupo); put("Ciudad", ciudad); put("Descuento", descUnidad) })
            }
        }

        val token = BuildConfig.COPIAR_AQ_PEDIDOS_TOKEN
        if (token.isBlank()) {
            AlertDialog.Builder(this).setTitle("Falta configurar el acceso")
                .setMessage("La APK no contiene el token de Registro de Pedidos.")
                .setPositiveButton("Aceptar", null).show(); return
        }
        guardarLinea.isEnabled = false
        guardarLinea.text = "Guardando selección…"
        consultarSeleccion.isEnabled = false
        val datos = JSONObject().apply {
            put("token", token); put("accion", "agregarDetallesSeleccionStock")
            put("PedidoID", pedidoId); put("Articulo", articulo.text.toString().trim())
            put("Descripcion", descripcion.text.toString().trim()); put("Precio", price); put("Lineas", lineas)
        }
        thread {
            var result: JSONObject? = null; var err: String? = null
            try {
                val conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 20000; readTimeout = 30000; doOutput = true; instanceFollowRedirects = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8"); setRequestProperty("Accept", "application/json")
                }
                conn.outputStream.use { it.write(datos.toString().toByteArray(Charsets.UTF_8)) }
                val status = conn.responseCode; val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty(); conn.disconnect()
                if (status !in 200..299) throw Exception("HTTP $status: $body")
                result = JSONObject(body); if (!result!!.optBoolean("ok")) throw Exception(result!!.optString("mensaje", "Error guardando selección"))
            } catch (e: Exception) { err = e.message ?: "Error de conexión" }
            runOnUiThread {
                guardarLinea.isEnabled = true; guardarLinea.text = "Agregar selección al pedido"; consultarSeleccion.isEnabled = true
                if (err != null) {
                    AlertDialog.Builder(this).setTitle("No se guardó la selección").setMessage(err).setPositiveButton("Aceptar", null).show()
                } else {
                    val agregados = result?.optInt("CantidadRegistros", unidades) ?: unidades
                    cantidadLineas += agregados
                    totalAcumulado = result?.optDouble("TotalPedido", totalAcumulado) ?: totalAcumulado
                    resumen.text = "Artículos guardados en esta sesión: $cantidadLineas"
                    totalSesion.text = "Total del pedido: Bs ${formato(totalAcumulado)}"
                    Toast.makeText(this, "$agregados unidad(es) agregadas al pedido", Toast.LENGTH_LONG).show()
                    seleccionStock.text = "Selección guardada. Puedes consultar otro artículo."
                    guardarLinea.tag = null; guardarLinea.isEnabled = false
                    articulo.text.clear(); descripcion.text.clear(); precio.text.clear(); descuento.setText("0")
                    articulo.requestFocus()
                }
            }
        }
    }

    private fun formato(valor: Double): String = String.format(Locale.US, "%.2f", valor)
    private fun dp(valor: Int): Int = (valor * resources.displayMetrics.density).toInt()
}
