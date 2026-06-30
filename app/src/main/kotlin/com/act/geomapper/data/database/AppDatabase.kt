package com.act.geomapper.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProyectoEntity::class, PredioEntity::class, SyncQueueEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun proyectoDao(): ProyectoDao
    abstract fun predioDao(): PredioDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "geomapper.db"
                )
                .addCallback(DefaultProjectCallback)
                .build().also { instance = it }
            }
    }
}

/**
 * Garantiza que el proyecto "Sin proyecto" (id=1) siempre exista.
 * Se ejecuta SINCRÓNICAMENTE en onOpen — antes de cualquier coroutine o UI.
 * Usa INSERT OR IGNORE: si ya existe, no hace nada.
 * Elimina la race condition de creación asíncrona de proyecto por defecto.
 */
private object DefaultProjectCallback : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis() / 1000
        db.execSQL("""
            INSERT OR IGNORE INTO proyectos
                (id, nombre, descripcion, fechaCreacion, totalArea, municipio, departamento)
            VALUES
                (1, 'Sin proyecto', '', $now, 0.0, '', '')
        """.trimIndent())
    }
}
