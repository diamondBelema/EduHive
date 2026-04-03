package com.dibe.eduhive.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dibe.eduhive.domain.model.Hive


@Entity(tableName = "hives")
data class HiveEntity(
    @PrimaryKey val hiveId: String,
    val name: String,
    val description: String?,
    val createdAt: Long,
    val lastAccessedAt: Long,
    @ColumnInfo(name = "isArchived", defaultValue = "0")
    val isArchived: Boolean = false
) {
    fun toDomain() = Hive(
        id = hiveId,
        name = name,
        description = description,
        createdAt = createdAt,
        lastAccessedAt = lastAccessedAt,
        isArchived = isArchived
    )

    companion object {
        fun fromDomain(hive: Hive) = HiveEntity(
            hiveId = hive.id,
            name = hive.name,
            description = hive.description,
            createdAt = hive.createdAt,
            lastAccessedAt = hive.lastAccessedAt,
            isArchived = hive.isArchived
        )
    }
}
