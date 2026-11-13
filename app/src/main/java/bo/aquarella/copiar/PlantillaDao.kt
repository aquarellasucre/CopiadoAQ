package bo.aquarella.copiar

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlantillaDao {

    @Query("SELECT * FROM plantillas ORDER BY categoria, nombre")
    fun obtenerTodas(): Flow<List<Plantilla>>

    @Query("SELECT * FROM plantillas WHERE categoria = :categoria ORDER BY nombre")
    fun obtenerPorCategoria(categoria: String): Flow<List<Plantilla>>

    @Query("SELECT * FROM plantillas WHERE id = :id LIMIT 1")
    suspend fun obtenerPorId(id: String): Plantilla?

    @Query("SELECT * FROM plantillas WHERE categoria = :categoria AND esPredeterminada = 1 LIMIT 1")
    suspend fun obtenerPredeterminada(categoria: String): Plantilla?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(plantilla: Plantilla)

    @Update
    suspend fun actualizar(plantilla: Plantilla)

    @Delete
    suspend fun eliminar(plantilla: Plantilla)

    @Query("UPDATE plantillas SET esPredeterminada = 0 WHERE categoria = :categoria")
    suspend fun quitarPredeterminada(categoria: String)

    @Query("UPDATE plantillas SET esPredeterminada = 1 WHERE id = :id")
    suspend fun establecerPredeterminada(id: String)
}
