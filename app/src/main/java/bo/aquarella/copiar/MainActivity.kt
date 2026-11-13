package bo.aquarella.copiar

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.graphics.Canvas
import android.graphics.Color
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class MainActivity : Activity() {

    companion object {
        private const val APPS_SCRIPT_URL =
            "https://script.google.com/macros/s/AKfycbyNOQpKypyMCnsg0bCOZPMHDX87Bx9HiFqyBf0Rew8kpnTNOMmmzReJsjxr-N6QGWos/exec"

        private const val ESCALA = 4
        private const val CALIDAD_JPEG = 100
    }
        private val scope = CoroutineScope(Dispatchers.Main)

        private val REQUEST_MENSAJE = 1001

        private var imagenesPendientes = ArrayList<File>()
        private var pedidoPendiente = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    @Suppress("DEPRECATION")
override fun onActivityResult(
    requestCode: Int,
    resultCode: Int,
    data: Intent?
) {
    super.onActivityResult(requestCode, resultCode, data)

    if (requestCode == REQUEST_MENSAJE) {

        if (resultCode != RESULT_OK) {
            finish()
            return
        }

        val enviarTexto =
            data?.getBooleanExtra("enviar_texto", false) ?: false

        val mensaje =
            data?.getStringExtra(
                MensajePedidoActivity.EXTRA_MENSAJE
            ).orEmpty()

        compartirImagenes(
            imagenesPendientes,
            pedidoPendiente,
            if (enviarTexto) mensaje else null
        )
    }
}
    
    private fun handleIntent(sourceIntent: Intent?) {
        when (sourceIntent?.data?.host) {
            "copy" -> handleCopyIntent(sourceIntent)
            "pdf" -> handlePdfIntent(sourceIntent)
            else -> {
                val text = sourceIntent?.getStringExtra("text")

                if (!text.isNullOrEmpty()) {
                copyText(text)
                } else if (sourceIntent?.data == null) {
                    startActivity(
                        Intent(this, PlantillasActivity::class.java)
                    )
                    finish()
                } else {
                    finish()
                }
            }
        }
    }

    // MÓDULO 1: COPIADO ACTUAL
    private fun handleCopyIntent(sourceIntent: Intent?) {
        val text = sourceIntent?.data?.getQueryParameter("text")
            ?: sourceIntent?.getStringExtra("text")

        if (text.isNullOrEmpty()) {
            Toast.makeText(
                this,
                "No se recibió ningún texto",
                Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }

        copyText(text)
    }

    private fun copyText(text: String) {
        val clipboard =
            getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText("Artículo AQ", text)
        )

        Toast.makeText(
            this,
            "Copiado: $text",
            Toast.LENGTH_SHORT
        ).show()

        finish()
    }

    // MÓDULO 2: DESCARGA DEL PDF
    private fun handlePdfIntent(sourceIntent: Intent?) {
        val pedido = sourceIntent?.data?.getQueryParameter("pedido")
        val ticket = sourceIntent?.data?.getQueryParameter("ticket")
    // DATOS DEL CLIENTE Y DEL PEDIDO
        val valores = mapOf(
            "cliente" to sourceIntent?.data?.getQueryParameter("cliente").orEmpty(),
            "pedido" to pedido.orEmpty(),
            "tipo_cliente" to sourceIntent?.data?.getQueryParameter("tipo_cliente").orEmpty(),
            "fecha" to sourceIntent?.data?.getQueryParameter("fecha").orEmpty(),
            "saldo" to sourceIntent?.data?.getQueryParameter("saldo").orEmpty()
            )

        if (pedido.isNullOrEmpty()) {
            Toast.makeText(
                this,
                "No se recibió el número del pedido",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        if (ticket.isNullOrEmpty()) {
            Toast.makeText(
                this,
                "No se recibió la ruta del PDF",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
         }

        val token = BuildConfig.COPIAR_AQ_TOKEN

        if (token.isBlank()) {
            Toast.makeText(
                this,
                "Falta configurar el acceso al servidor",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        Toast.makeText(
            this,
            "Descargando PDF de $pedido...",
            Toast.LENGTH_LONG
        ).show()

        thread {
            try {
                val url = URL(
                    APPS_SCRIPT_URL +
                    "?token=" + URLEncoder.encode(token, "UTF-8") +
                    "&pedido=" + URLEncoder.encode(pedido, "UTF-8") +
                    "&ticket=" + URLEncoder.encode(ticket, "UTF-8")
                )

                val connection =
                    url.openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 30000
                connection.readTimeout = 60000
                connection.instanceFollowRedirects = true

                val responseCode = connection.responseCode

                val response = if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val responseText = response.bufferedReader().use {
                    it.readText()
                }

                connection.disconnect()

                if (responseCode !in 200..299) {
                    throw Exception("Error HTTP $responseCode")
                }

                val json = JSONObject(responseText)

                if (!json.optBoolean("ok")) {
                    throw Exception(
                        json.optString(
                            "mensaje",
                            "El servidor rechazó la solicitud"
                        )
                    )
                }

                val contenido = json.optString("contenido")

                if (contenido.isBlank()) {
                    throw Exception("El servidor no entregó el PDF")
                }

                val pdfBytes = Base64.decode(
                    contenido,
                    Base64.DEFAULT
                )

                if (pdfBytes.isEmpty()) {
                    throw Exception("El PDF está vacío")
                }

                val pdfFile = File(
                    cacheDir,
                    "Pedido_$pedido.pdf"
                )

                FileOutputStream(pdfFile).use {
                    it.write(pdfBytes)
                }

                val imagenes = convertirTodasLasPaginas(
                    pdfFile,
                    pedido
                )

                runOnUiThread {
                    mostrarRevisionMensaje(imagenes, pedido, valores)
                }

            } catch (error: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Error: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        }
    }

    // PREPARAR EL MENSAJE ANTES DE COMPARTIR
private fun mostrarRevisionMensaje(
    imagenes: ArrayList<File>,
    pedido: String,
    valores: Map<String, String>
) {
    val categoria = valores["tipo_cliente"].orEmpty().uppercase()

    imagenesPendientes = imagenes
    pedidoPendiente = pedido

    scope.launch {
        try {
            val repository =
                (application as CopiarAQApp).plantillaRepository

            val mensaje = if (categoria in listOf("C2B", "P1B", "PSB")) {
                repository.generarMensajePredeterminado(
                    categoria,
                    valores
                )
            } else {
                null
            }

            val revision = Intent(
                this@MainActivity,
                MensajePedidoActivity::class.java
            ).apply {
                putExtra(
                    MensajePedidoActivity.EXTRA_PEDIDO,
                    pedido
                )
                putExtra(
                    MensajePedidoActivity.EXTRA_MENSAJE,
                    mensaje.orEmpty()
                )
                putExtra(
                    MensajePedidoActivity.EXTRA_TIENE_PLANTILLA,
                    mensaje != null
                )
            }

            startActivityForResult(revision, REQUEST_MENSAJE)

        } catch (error: Exception) {
            Toast.makeText(
                this@MainActivity,
                "No se pudo preparar el mensaje: ${error.message}",
                Toast.LENGTH_LONG
            ).show()

            val revision = Intent(
                this@MainActivity,
                MensajePedidoActivity::class.java
            ).apply {
                putExtra(
                    MensajePedidoActivity.EXTRA_PEDIDO,
                    pedido
                )
                putExtra(
                    MensajePedidoActivity.EXTRA_MENSAJE,
                    ""
                )
                putExtra(
                    MensajePedidoActivity.EXTRA_TIENE_PLANTILLA,
                    false
                )
            }

            startActivityForResult(revision, REQUEST_MENSAJE)
        }
    }
}
    // CONVERTIR TODAS LAS PÁGINAS A JPG
    private fun convertirTodasLasPaginas(
        pdfFile: File,
        pedido: String
    ): ArrayList<File> {

        val imagenes = ArrayList<File>()

        val carpeta = File(cacheDir, "imagenes")

        if (!carpeta.exists()) {
            carpeta.mkdirs()
        }

        // Limpiar imágenes anteriores
        carpeta.listFiles()?.forEach { archivo ->
            if (archivo.isFile) {
                archivo.delete()
            }
        }

        val descriptor = ParcelFileDescriptor.open(
            pdfFile,
            ParcelFileDescriptor.MODE_READ_ONLY
        )

        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->

                val totalPaginas = renderer.pageCount

                if (totalPaginas == 0) {
                    throw Exception("El PDF no contiene páginas")
                }

                for (numero in 0 until totalPaginas) {

                    renderer.openPage(numero).use { page ->

                        val ancho = page.width * ESCALA
                        val alto = page.height * ESCALA

                        val bitmap = Bitmap.createBitmap(
                            ancho,
                            alto,
                            Bitmap.Config.ARGB_8888
                        )

                        try {
                               // Preparar fondo blanco antes de dibujar el PDF
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)

    // Dibujar la página sobre el fondo blanco
                            page.render(
                                bitmap,
                                null,
                                null,
                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                            )

                            val nombre = String.format(
                                java.util.Locale.US,
                                "Pedido_%s_%02d.jpg",
                                pedido,
                                numero + 1
                            )

                            val imagen = File(carpeta, nombre)

                            FileOutputStream(imagen).use { output ->
                                val guardado = bitmap.compress(
                                    Bitmap.CompressFormat.JPEG,
                                    CALIDAD_JPEG,
                                    output
                                )

                                if (!guardado) {
                                    throw Exception(
                                        "No se pudo guardar la página ${numero + 1}"
                                    )
                                }
                            }

                            imagenes.add(imagen)

                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }

        return imagenes
    }

    // COMPARTIR TODAS LAS PÁGINAS COMO FOTOGRAFÍAS
        private fun compartirImagenes(
            imagenes: ArrayList<File>,
                pedido: String,
            mensaje: String?
        ) {
        try {
            if (imagenes.isEmpty()) {
                throw Exception("No se generaron imágenes")
            }

            val uris = ArrayList<android.net.Uri>()

            imagenes.forEach { imagen ->
                val uri = FileProvider.getUriForFile(
                    this,
                    "$packageName.fileprovider",
                    imagen
                )
                uris.add(uri)
            }

            val compartir = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/jpeg"
                putParcelableArrayListExtra(
                    Intent.EXTRA_STREAM,
                    uris
                )

                if (!mensaje.isNullOrBlank()) {
                    putExtra(
                    Intent.EXTRA_TEXT,
                    mensaje
                )
                }

                val datos = ClipData.newUri(
                    contentResolver,
                    "Página 1 del pedido",
                    uris[0]
                )

                for (i in 1 until uris.size) {
                    datos.addItem(
                        ClipData.Item(uris[i])
                    )
                }

                clipData = datos

                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            Toast.makeText(
                this,
                "${imagenes.size} página(s) JPG preparadas",
                Toast.LENGTH_LONG
            ).show()

            startActivity(
                Intent.createChooser(
                    compartir,
                    "Compartir pedido $pedido"
                )
            )

        } catch (error: Exception) {
            Toast.makeText(
                this,
                "Error al compartir: ${error.message}",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }
}
