
package bo.aquarella.copiar

/**
 * Sustituye las variables dinámicas de una plantilla
 * por los valores recibidos desde AppSheet.
 *
 * Ejemplo:
 * {{cliente}} -> María
 * {{pedido}}  -> FAC-05000
 */
object MotorVariables {

    private val patronVariable =
        Regex("""\{\{\s*([a-zA-Z0-9_]+)\s*\}\}""")

    fun reemplazar(
        contenido: String,
        valores: Map<String, String>
    ): String {

        val valoresNormalizados = valores.mapKeys {
            it.key.lowercase()
        }

        return patronVariable.replace(contenido) { coincidencia ->

            val nombreVariable =
                coincidencia.groupValues[1].lowercase()

            valoresNormalizados[nombreVariable]
                ?: coincidencia.value
        }
    }
}
