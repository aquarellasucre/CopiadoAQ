package bo.aquarella.copiar

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Plantilla::class],
    version = 1,
    exportSchema = false
)
abstract class CopiarAQDatabase : RoomDatabase() {

    abstract fun plantillaDao(): PlantillaDao

    companion object {
        @Volatile
        private var INSTANCIA: CopiarAQDatabase? = null

        fun obtenerInstancia(context: Context): CopiarAQDatabase {
            return INSTANCIA ?: synchronized(this) {
                INSTANCIA ?: Room.databaseBuilder(
                    context.applicationContext,
                    CopiarAQDatabase::class.java,
                    "copiar_aq_database"
                ).build().also {
                    INSTANCIA = it
                }
            }
        }
    }
}
