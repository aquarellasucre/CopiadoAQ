
package bo.aquarella.copiar

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.UUID

class PlantillasActivity : Activity() {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main
    )

    private lateinit var repository: PlantillaRepository
    private lateinit var lista: LinearLayout
    private lateinit var tituloCategoria: TextView

    private var categoriaActual = "C2B"
    private var plantillas: List<Plantilla> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = (application as CopiarAQApp)
            .plantillaRepository

        construirPantalla()

        scope.launch {
            repository.obtenerTodas().collect { resultado ->
                plantillas = resultado
                mostrarPlantillas()
            }
        }
    }

    private fun construirPantalla() {
        val principal = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        val titulo = TextView(this).apply {
            text = "Plantillas de mensajes"
            textSize = 23f
            setTypeface(null, Typeface.BOLD)
        }

        principal.addView(titulo)

        val subtitulo = TextView(this).apply {
            text = "Administra tus mensajes para cada tipo de cliente."
            textSize = 14f
            setPadding(0, 8, 0, 20)
        }

        principal.addView(subtitulo)

        val categorias = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        listOf("C2B", "P1B", "PSB").forEach { categoria ->
            val boton = Button(this).apply {
                text = categoria
                isAllCaps = false
                setOnClickListener {
                    categoriaActual = categoria
                    mostrarPlantillas()
                }
            }

            categorias.addView(
                boton,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
        }

        principal.addView(categorias)

        tituloCategoria = TextView(this).apply {
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 20, 0, 12)
        }

        principal.addView(tituloCategoria)

        val nueva = Button(this).apply {
            text = "+ Crear plantilla"
            isAllCaps = false
            setOnClickListener {
                mostrarEditor(null)
            }
        }

        principal.addView(nueva)

        val scroll = ScrollView(this)
        lista = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 12)
        }

        scroll.addView(lista)

        principal.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val cerrar = Button(this).apply {
            text = "Volver"
            isAllCaps = false
            setOnClickListener { finish() }
        }

        principal.addView(cerrar)

        setContentView(principal)
        mostrarPlantillas()
    }

    private fun mostrarPlantillas() {
        if (!::lista.isInitialized) return

        tituloCategoria.text = "Categoría: $categoriaActual"
        lista.removeAllViews()

        val filtradas = plantillas.filter {
            it.categoria == categoriaActual
        }

        if (filtradas.isEmpty()) {
            val vacio = TextView(this).apply {
                text = "Todavía no tienes plantillas en esta categoría."
                textSize = 15f
                setPadding(8, 20, 8, 20)
            }
            lista.addView(vacio)
            return
        }

        filtradas.forEach { plantilla ->
            val bloque = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                setBackgroundColor(0xFFF0F0F0.toInt())
            }

            val nombre = TextView(this).apply {
                text = plantilla.nombre
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
            }

            bloque.addView(nombre)

            val estado = TextView(this).apply {
                text = if (plantilla.esPredeterminada) {
                    "★ Plantilla predeterminada"
                } else {
                    "Plantilla adicional"
                }
                textSize = 13f
                setPadding(0, 6, 0, 8)
            }

            bloque.addView(estado)

            val editar = Button(this).apply {
                text = "Editar"
                setOnClickListener {
                    mostrarEditor(plantilla)
                }
            }

            bloque.addView(editar)

            val duplicar = Button(this).apply {
                text = "Duplicar"
                setOnClickListener {
                    duplicarPlantilla(plantilla)
                }
            }

            bloque.addView(duplicar)

            if (!plantilla.esPredeterminada) {
                val predeterminada = Button(this).apply {
                    text = "Establecer como predeterminada"
                    setOnClickListener {
                        establecerPredeterminada(plantilla)
                    }
                }
                bloque.addView(predeterminada)
            }

            val eliminar = Button(this).apply {
                text = "Eliminar"
                setOnClickListener {
                    confirmarEliminacion(plantilla)
                }
            }

            bloque.addView(eliminar)

            val separador = TextView(this).apply {
                text = " "
                setPadding(0, 8, 0, 8)
            }

            lista.addView(bloque)
            lista.addView(separador)
        }
    }

    private fun mostrarEditor(plantilla: Plantilla?) {
        val formulario = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 12, 24, 8)
        }

        val campoNombre = EditText(this).apply {
            hint = "Nombre de la plantilla"
            setText(plantilla?.nombre ?: "")
        }

        val campoContenido = EditText(this).apply {
            hint = "Escribe el mensaje..."
            minLines = 6
            gravity = Gravity.TOP
            setText(plantilla?.contenido ?: "")
        }

        formulario.addView(campoNombre)
        formulario.addView(campoContenido)

        val dialogo = AlertDialog.Builder(this)
            .setTitle(
                if (plantilla == null) {
                    "Nueva plantilla"
                } else {
                    "Editar plantilla"
                }
            )
            .setView(formulario)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
            .create()

        dialogo.setOnShowListener {
            dialogo.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {

                    val nombre = campoNombre.text.toString().trim()
                    val contenido = campoContenido.text.toString()

                    if (nombre.isBlank()) {
                        campoNombre.error =
                            "Escribe un nombre"
                        return@setOnClickListener
                    }

                    if (contenido.isBlank()) {
                        campoContenido.error =
                            "Escribe el mensaje"
                        return@setOnClickListener
                    }

                    scope.launch {
                        if (plantilla == null) {
                            repository.guardar(
                                Plantilla(
                                    id = UUID.randomUUID().toString(),
                                    nombre = nombre,
                                    categoria = categoriaActual,
                                    contenido = contenido
                                )
                            )
                        } else {
                            repository.actualizar(
                                plantilla.copy(
                                    nombre = nombre,
                                    contenido = contenido
                                )
                            )
                        }

                        dialogo.dismiss()
                        Toast.makeText(
                            this@PlantillasActivity,
                            "Plantilla guardada",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }

        dialogo.show()
    }

    private fun duplicarPlantilla(plantilla: Plantilla) {
        scope.launch {
            repository.guardar(
                plantilla.copy(
                    id = UUID.randomUUID().toString(),
                    nombre = "${plantilla.nombre} (copia)",
                    esPredeterminada = false
                )
            )

            Toast.makeText(
                this@PlantillasActivity,
                "Plantilla duplicada",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun establecerPredeterminada(
        plantilla: Plantilla
    ) {
        scope.launch {
            repository.establecerPredeterminada(plantilla)

            Toast.makeText(
                this@PlantillasActivity,
                "Plantilla predeterminada actualizada",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun confirmarEliminacion(
        plantilla: Plantilla
    ) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar plantilla")
            .setMessage(
                "¿Deseas eliminar '${plantilla.nombre}'?"
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                scope.launch {
                    repository.eliminar(plantilla)
                }
            }
            .show()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
