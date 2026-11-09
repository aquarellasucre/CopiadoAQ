package bo.aquarella.copiar

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(sourceIntent: Intent?) {
        when (sourceIntent?.data?.host) {
            "copy" -> handleCopyIntent(sourceIntent)
            "pdf" -> handlePdfIntent(sourceIntent)
            else -> {
                val text = sourceIntent?.getStringExtra("text")
                if (!text.isNullOrEmpty()) {
                    copyText(text)
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

    // MÓDULO 2: DESCARGA DE PDF
    private fun handlePdfIntent(sourceIntent: Intent?) {
        val pedido = sourceIntent?.data?.getQueryParameter("pedido")

        if (pedido.isNullOrEmpty()) {
            Toast.makeText(
                this,
                "No se recibió el número del pedido",
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
                    "&pedido=" + URLEncoder.encode(pedido, "UTF-8")
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

                val nombreOriginal = json.optString(
                    "nombre",
                    "Pedido_$pedido.pdf"
                )

                val nombreSeguro = nombreOriginal.replace(
                    Regex("[^A-Za-z0-9._-]"),
                    "_"
                )

                val archivo = File(cacheDir, nombreSeguro)

                FileOutputStream(archivo).use {
                    it.write(pdfBytes)
                }

                runOnUiThread {
                    Toast.makeText(
                        this,
                        "PDF descargado correctamente: ${pdfBytes.size} bytes",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }

            } catch (error: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Error al descargar PDF: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        }
    }
}
