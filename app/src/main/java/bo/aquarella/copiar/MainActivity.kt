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
        handleCopyIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCopyIntent(intent)
    }

    private fun handleCopyIntent(sourceIntent: Intent?) {
        val text = sourceIntent?.data?.getQueryParameter("text")
            ?: sourceIntent?.getStringExtra("text")

        if (text.isNullOrEmpty()) {
            Toast.makeText(this, "No se recibió ningún texto", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Artículo AQ", text))

        Toast.makeText(this, "Copiado: $text", Toast.LENGTH_SHORT).show()
        finish()
    }
}
