package com.act.geomapper.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProyectoDao {
    @Query("SELECT * FROM proyectos ORDER BY fechaCreacion DESC")
    fun observarTodos(): Flow<List<ProyectoEntity>>

    @Query("SELECT * FROM proyectos WHERE id = :id")
    suspend fun obtenerPorId(id: Long): ProyectoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(proyecto: ProyectoEntity): Long

    @Update
    suspend fun actualizar(proyecto: ProyectoEntity)

    @Delete
    suspend fun eliminar(proyecto: ProyectoEntity)

    @Query("UPDATE proyectos SET totalArea = :area WHERE id = :id")
    suspend fun actualizarArea(id: Long, area: Double)
}

@Dao
interface PredioDao {
    @Query("SELECT * FROM predios WHERE proyectoId = :proyectoId ORDER BY fechaCaptura DESC")
    fun observarPorProyecto(proyectoId: Long): Flow<List<PredioEntity>>

    // Todos los predios de todos los proyectos — para Capas sin importar proyecto activo
    @Query("SELECT * FROM predios ORDER BY fechaCaptura DESC")
    fun observarTodos(): Flow<List<PredioEntity>>

    @Query("SELECT * FROM predios WHERE id = :id")
    suspend fun obtenerPorId(id: Long): PredioEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(predio: PredioEntity): Long

    @Update
    suspend fun actualizar(predio: PredioEntity)

    @Delete
    suspend fun eliminar(predio: PredioEntity)

    @Query("DELETE FROM predios WHERE proyectoId = :proyectoId")
    suspend fun eliminarPorProyecto(proyectoId: Long)
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY fechaCreacion ASC")
    suspend fun obtenerPendientes(): List<SyncQueueEntity>

    @Insert
    suspend fun encolar(item: SyncQueueEntity): Long

    @Delete
    suspend fun eliminar(item: SyncQueueEntity)

    @Query("UPDATE sync_queue SET intentos = intentos + 1 WHERE id = :id")
    suspend fun incrementarIntentos(id: Long)
}
