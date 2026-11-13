
package bo.aquarella.copiar

import android.app.Application

class CopiarAQApp : Application() {

    val database by lazy {
        CopiarAQDatabase.obtenerInstancia(this)
    }

    val plantillaRepository by lazy {
        PlantillaRepository(database.plantillaDao())
    }
}
