
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
import android.widget.Toast

class MensajePedidoActivity : Activity() {

    companion object {
        const val EXTRA_PEDIDO = "pedido"
        const val EXTRA_MENSAJE = "mensaje"
        const val EXTRA_TIENE_PLANTILLA = "tiene_plantilla"
    }

    private lateinit var editorMensaje: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pedido = intent.getStringExtra(EXTRA_PEDIDO).orEmpty()
        val mensaje = intent.getStringExtra(EXTRA_MENSAJE).orEmpty()
        val tienePlantilla = intent.getBooleanExtra(
            EXTRA_TIENE_PLANTILLA,
            false
        )

        val pantalla = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 64, 64)
            gravity = Gravity.CENTER_HORIZONTAL
            fitsSystemWindows = true
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
            minLines = 5
            gravity = Gravity.TOP
            hint = "Escribe el mensaje para el cliente"
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

        pantalla.addView(titulo)
        pantalla.addView(descripcion)

        pantalla.addView(
            editorMensaje,
            LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ).apply {
        bottomMargin = 16
    }
)

        pantalla.addView(botonMensaje)
        pantalla.addView(botonSoloImagenes)

        setContentView(pantalla)
    }
}
