
package bo.aquarella.copiar

import kotlinx.coroutines.flow.Flow

class PlantillaRepository(
    private val dao: PlantillaDao
) {

    fun obtenerTodas(): Flow<List<Plantilla>> {
        return dao.obtenerTodas()
    }

    fun obtenerPorCategoria(
        categoria: String
    ): Flow<List<Plantilla>> {
        return dao.obtenerPorCategoria(categoria)
    }

    suspend fun obtenerPorId(id: String): Plantilla? {
        return dao.obtenerPorId(id)
    }

    suspend fun obtenerPredeterminada(
        categoria: String
    ): Plantilla? {
        return dao.obtenerPredeterminada(categoria)
    }

    suspend fun guardar(plantilla: Plantilla) {
        dao.insertar(plantilla)
    }

    suspend fun actualizar(plantilla: Plantilla) {
        dao.actualizar(plantilla)
    }

    suspend fun eliminar(plantilla: Plantilla) {
        dao.eliminar(plantilla)
    }

    suspend fun establecerPredeterminada(
        plantilla: Plantilla
    ) {
        dao.guardarComoPredeterminada(plantilla)
    }
    
    suspend fun generarMensaje(
        idPlantilla: String,
        valores: Map<String, String>
    ): String? {
        val plantilla = dao.obtenerPorId(idPlantilla)
            ?: return null

        return MotorVariables.reemplazar(
            plantilla.contenido,
            valores
        )
    }

}
