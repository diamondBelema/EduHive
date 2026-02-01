package com.dibe.eduhive.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dibe.eduhive.domain.model.Hive


@Entity(tableName = "hives")
data class HiveEntity(
    @PrimaryKey val hiveId: String,
    val name: String,
    val description: String?,
    val createdAt: Long,
    val lastAccessedAt: Long
) {
    fun toDomain() = Hive(
        id = hiveId,
        name = name,
        description = description,
        createdAt = createdAt,
        lastAccessedAt = lastAccessedAt
    )

    companion object {
        fun fromDomain(hive: Hive) = HiveEntity(
            hiveId = hive.id,
            name = hive.name,
            description = hive.description,
            createdAt = hive.createdAt,
            lastAccessedAt = hive.lastAccessedAt
        )
    }
}
