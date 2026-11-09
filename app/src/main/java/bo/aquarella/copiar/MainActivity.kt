
package bo.aquarella.copiar

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class MainActivity : Activity() {

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

    // MÓDULO 2: PDF (PRIMERA PRUEBA)
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

        Toast.makeText(
            this,
            "Solicitud PDF recibida: $pedido",
            Toast.LENGTH_LONG
        ).show()

        finish()
    }
}
