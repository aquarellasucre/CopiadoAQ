package bo.aquarella.copiar

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plantillas")
data class Plantilla(
    @PrimaryKey
    val id: String,

    var nombre: String,

    val categoria: String,

    var contenido: String,

    var esPredeterminada: Boolean = false
)
