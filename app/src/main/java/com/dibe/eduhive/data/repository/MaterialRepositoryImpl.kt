package com.dibe.eduhive.data.repository

import com.dibe.eduhive.data.local.entity.MaterialEntity
import com.dibe.eduhive.data.source.local.MaterialLocalDataSource
import com.dibe.eduhive.domain.model.Material
import com.dibe.eduhive.domain.repository.MaterialRepository
import jakarta.inject.Inject


class MaterialRepositoryImpl @Inject constructor(
    private val localDataSource: MaterialLocalDataSource
) : MaterialRepository {

    override suspend fun addMaterial(material: Material) {
        localDataSource.insert(MaterialEntity.fromDomain(material))
    }

    override suspend fun getMaterialsForHive(hiveId: String): List<Material> {
        return localDataSource.getForHive(hiveId).map { it.toDomain() }
    }

    override suspend fun markAsProcessed(materialId: String) {
        localDataSource.markProcessed(materialId)
    }

    override suspend fun deleteMaterial(materialId: String) {
        localDataSource.delete(materialId)
    }
}