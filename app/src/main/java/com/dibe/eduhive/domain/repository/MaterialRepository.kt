package com.dibe.eduhive.domain.repository

import com.dibe.eduhive.domain.model.Material

/**
 * Repository interface for Material operations.
 */
interface MaterialRepository {

    suspend fun addMaterial(material: Material)

    suspend fun getMaterialsForHive(hiveId: String): List<Material>

    suspend fun markAsProcessed(materialId: String)

    suspend fun deleteMaterial(materialId: String)
}