package com.act.geomapper.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "proyectos")
data class ProyectoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val descripcion: String = "",
    val fechaCreacion: Long = Instant.now().epochSecond,
    val totalArea: Double = 0.0,
    val municipio: String = "",
    val departamento: String = ""
)

@Entity(
    tableName = "predios",
    foreignKeys = [ForeignKey(
        entity = ProyectoEntity::class,
        parentColumns = ["id"],
        childColumns = ["proyectoId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("proyectoId")]
)
data class PredioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val proyectoId: Long,
    val nombre: String,
    val propietario: String = "",
    val geometryWKT: String,
    val area: Double = 0.0,
    val perimetro: Double = 0.0,
    val tipo: TipoPredio = TipoPredio.RURAL,
    val fechaCaptura: Long = Instant.now().epochSecond,
    val observaciones: String = ""
)

enum class TipoPredio { RURAL, URBANO, EXPANSION }

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entidadTipo: String,
    val entidadId: Long,
    val operacion: SyncOperacion,
    val payload: String,
    val fechaCreacion: Long = Instant.now().epochSecond,
    val intentos: Int = 0
)

enum class SyncOperacion { INSERT, UPDATE, DELETE }
