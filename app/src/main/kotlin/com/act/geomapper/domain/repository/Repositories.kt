package com.act.geomapper.domain.repository

import com.act.geomapper.domain.models.Predio
import com.act.geomapper.domain.models.Proyecto
import kotlinx.coroutines.flow.Flow

interface ProyectoRepository {
    fun observarTodos(): Flow<List<Proyecto>>
    suspend fun obtenerPorId(id: Long): Proyecto?
    suspend fun crear(proyecto: Proyecto): Long
    suspend fun actualizar(proyecto: Proyecto)
    suspend fun eliminar(id: Long)
}

interface PredioRepository {
    fun observarTodos(): Flow<List<Predio>>
    fun observarPorProyecto(proyectoId: Long): Flow<List<Predio>>
    suspend fun obtenerPorId(id: Long): Predio?
    suspend fun guardar(predio: Predio): Long
    suspend fun actualizar(predio: Predio)
    suspend fun eliminar(id: Long)
}
