package com.act.geomapper.data.database

import com.act.geomapper.domain.models.Predio
import com.act.geomapper.domain.models.Proyecto
import com.act.geomapper.domain.repository.PredioRepository
import com.act.geomapper.domain.repository.ProyectoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.io.WKTWriter
import com.act.geomapper.domain.models.TipoPredio as DomainTipo
import com.act.geomapper.data.database.TipoPredio as DbTipo

class ProyectoRepositoryImpl(private val dao: ProyectoDao) : ProyectoRepository {

    override fun observarTodos(): Flow<List<Proyecto>> =
        dao.observarTodos().map { list -> list.map { it.toDomain() } }

    override suspend fun obtenerPorId(id: Long): Proyecto? =
        dao.obtenerPorId(id)?.toDomain()

    override suspend fun crear(proyecto: Proyecto): Long =
        dao.insertar(proyecto.toEntity())

    override suspend fun actualizar(proyecto: Proyecto) =
        dao.actualizar(proyecto.toEntity())

    override suspend fun eliminar(id: Long) {
        dao.obtenerPorId(id)?.let { dao.eliminar(it) }
    }

    private fun ProyectoEntity.toDomain() = Proyecto(
        id = id, nombre = nombre, descripcion = descripcion,
        fechaCreacion = fechaCreacion, totalArea = totalArea,
        municipio = municipio, departamento = departamento
    )

    private fun Proyecto.toEntity() = ProyectoEntity(
        id = id, nombre = nombre, descripcion = descripcion,
        fechaCreacion = fechaCreacion, totalArea = totalArea,
        municipio = municipio, departamento = departamento
    )
}

class PredioRepositoryImpl(private val dao: PredioDao) : PredioRepository {

    private val wktReader = WKTReader()
    private val wktWriter = WKTWriter()

    override fun observarTodos(): Flow<List<Predio>> =
        dao.observarTodos().map { list -> list.map { it.toDomain() } }

    override fun observarPorProyecto(proyectoId: Long): Flow<List<Predio>> =
        dao.observarPorProyecto(proyectoId).map { list -> list.map { it.toDomain() } }

    override suspend fun obtenerPorId(id: Long): Predio? =
        dao.obtenerPorId(id)?.toDomain()

    override suspend fun guardar(predio: Predio): Long =
        dao.insertar(predio.toEntity())

    override suspend fun actualizar(predio: Predio) =
        dao.actualizar(predio.toEntity())

    override suspend fun eliminar(id: Long) {
        dao.obtenerPorId(id)?.let { dao.eliminar(it) }
    }

    private fun PredioEntity.toDomain() = Predio(
        id = id, proyectoId = proyectoId, nombre = nombre,
        propietario = propietario, geometry = wktReader.read(geometryWKT),
        area = area, perimetro = perimetro,
        tipo = tipo.toDomain(), observaciones = observaciones
    )

    private fun Predio.toEntity() = PredioEntity(
        id = id, proyectoId = proyectoId, nombre = nombre,
        propietario = propietario, geometryWKT = wktWriter.write(geometry),
        area = area, perimetro = perimetro,
        tipo = tipo.toDb(), observaciones = observaciones
    )

    private fun DbTipo.toDomain() = when (this) {
        DbTipo.RURAL -> DomainTipo.RURAL
        DbTipo.URBANO -> DomainTipo.URBANO
        DbTipo.EXPANSION -> DomainTipo.EXPANSION
    }

    private fun DomainTipo.toDb() = when (this) {
        DomainTipo.RURAL -> DbTipo.RURAL
        DomainTipo.URBANO -> DbTipo.URBANO
        DomainTipo.EXPANSION -> DbTipo.EXPANSION
    }
}
