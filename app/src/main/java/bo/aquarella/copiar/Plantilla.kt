package bo.aquarella.copiar

data class Plantilla(
    val id: String,
    var nombre: String,
    val categoria: String,
    var contenido: String,
    var esPredeterminada: Boolean = false
)
