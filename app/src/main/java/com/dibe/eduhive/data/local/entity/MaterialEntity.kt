package com.dibe.eduhive.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dibe.eduhive.domain.model.Material
import com.dibe.eduhive.domain.model.MaterialType


@Entity(
    tableName = "materials",
    indices = [Index("hiveId")],
    foreignKeys = [
        ForeignKey(
            entity = HiveEntity::class,
            parentColumns = ["hiveId"],
            childColumns = ["hiveId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MaterialEntity(
    @PrimaryKey val materialId: String,
    val hiveId: String,
    val title: String,
    val type: MaterialType,
    val localPath: String,
    val processed: Boolean,
    val createdAt: Long
){
    fun toDomain() = Material(
        id = materialId,
        hiveId = hiveId,
        title = title,
        type = type,
        localPath = localPath,
        processed = processed,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(material: Material) = MaterialEntity(
            materialId = material.id,
            hiveId = material.hiveId,
            title = material.title,
            type = material.type,
            localPath = material.localPath,
            processed = material.processed,
            createdAt = material.createdAt
        )
    }
}
