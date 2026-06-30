package com.act.geomapper.domain.usecase

import com.act.geomapper.domain.models.Predio
import com.act.geomapper.domain.models.Proyecto
import com.act.geomapper.domain.repository.PredioRepository
import com.act.geomapper.domain.repository.ProyectoRepository
import kotlinx.coroutines.flow.Flow

class ObtenerProyectosUseCase(private val repo: ProyectoRepository) {
    operator fun invoke(): Flow<List<Proyecto>> = repo.observarTodos()
}

class CrearProyectoUseCase(private val repo: ProyectoRepository) {
    suspend operator fun invoke(nombre: String, descripcion: String = ""): Long {
        require(nombre.isNotBlank()) { "El nombre del proyecto no puede estar vacío" }
        return repo.crear(Proyecto(nombre = nombre.trim(), descripcion = descripcion))
    }
}

class EliminarProyectoUseCase(private val repo: ProyectoRepository) {
    suspend operator fun invoke(id: Long) = repo.eliminar(id)
}

class ObtenerPrediosUseCase(private val repo: PredioRepository) {
    operator fun invoke(proyectoId: Long): Flow<List<Predio>> =
        repo.observarPorProyecto(proyectoId)
}

class ObtenerTodosPrediosUseCase(private val repo: PredioRepository) {
    operator fun invoke(): Flow<List<Predio>> = repo.observarTodos()
}

class GuardarPredioUseCase(private val repo: PredioRepository) {
    suspend operator fun invoke(predio: Predio): Long = repo.guardar(predio)
}

class EliminarPredioUseCase(private val repo: PredioRepository) {
    suspend operator fun invoke(id: Long) = repo.eliminar(id)
}
