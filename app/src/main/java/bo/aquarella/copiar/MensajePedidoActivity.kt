
package bo.aquarella.copiar

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.view.ViewGroup
import android.view.Gravity
import android.graphics.Typeface
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MensajePedidoActivity : Activity() {

    companion object {
        const val EXTRA_PEDIDO = "pedido"
        const val EXTRA_MENSAJE = "mensaje"
        const val EXTRA_TIENE_PLANTILLA = "tiene_plantilla"
    }

    private lateinit var editorMensaje: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fun dp(valor: Int): Int {
            return (valor * resources.displayMetrics.density).toInt()
        }

        val pedido = intent.getStringExtra(EXTRA_PEDIDO).orEmpty()
        val mensaje = intent.getStringExtra(EXTRA_MENSAJE).orEmpty()

        val tienePlantilla = intent.getBooleanExtra(
            EXTRA_TIENE_PLANTILLA,
            false
        )

        val pantalla = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 0)
        }

        // RESPETAR LAS BARRAS DEL SISTEMA ANDROID
        ViewCompat.setOnApplyWindowInsetsListener(pantalla) { vista, insets ->
            val barras = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            vista.setPadding(
                dp(24),
                dp(16) + barras.top,
                dp(24),
                dp(16) + barras.bottom
            )

            insets
        }

        val titulo = TextView(this).apply {
            text = "Pedido $pedido"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
        }

        val descripcion = TextView(this).apply {
            text = if (tienePlantilla) {
                "Revisa y edita el mensaje antes de compartir."
            } else {
                "No hay una plantilla predeterminada para esta categoría."
            }
            textSize = 16f
        }

        editorMensaje = EditText(this).apply {
            setText(mensaje)
            minLines = 4
            textSize = 16f
            gravity = Gravity.TOP
            hint = "Escribe el mensaje para el cliente"
        }

        val botonMensaje = Button(this).apply {
            text = "Continuar con mensaje"
            isEnabled = tienePlantilla && mensaje.isNotBlank()

            setOnClickListener {
                setResult(
                    RESULT_OK,
                    intent.apply {
                        putExtra("enviar_texto", true)
                        putExtra(
                            EXTRA_MENSAJE,
                            editorMensaje.text.toString()
                        )
                    }
                )
                finish()
            }
        }

        val botonSoloImagenes = Button(this).apply {
            text = "Enviar solamente imágenes"

            setOnClickListener {
                setResult(
                    RESULT_OK,
                    intent.apply {
                        putExtra("enviar_texto", false)
                    }
                )
                finish()
            }
        }

        pantalla.addView(
            titulo,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        pantalla.addView(
            descripcion,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }
        )

        pantalla.addView(
            editorMensaje,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dp(16)
                bottomMargin = dp(12)
            }
        )

        pantalla.addView(
            botonMensaje,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            )
        )

        pantalla.addView(
            botonSoloImagenes,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                topMargin = dp(8)
            }
        )

        setContentView(pantalla)

        ViewCompat.requestApplyInsets(pantalla)
    }
}
