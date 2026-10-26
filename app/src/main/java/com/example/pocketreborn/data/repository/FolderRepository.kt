package com.example.pocketreborn.data.repository

import com.example.pocketreborn.data.local.dao.FolderDao
import com.example.pocketreborn.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class FolderRepository(private val folderDao: FolderDao) {

    fun getAllFolders(): Flow<List<FolderEntity>> = folderDao.getAllFolders()

    suspend fun createFolder(name: String): FolderEntity {
        val folder = FolderEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            createdAt = System.currentTimeMillis()
        )
        folderDao.insertFolder(folder)
        return folder
    }

    suspend fun renameFolder(id: String, newName: String) {
        val existing = folderDao.getFolderById(id) ?: return
        folderDao.updateFolder(existing.copy(name = newName.trim()))
    }

    suspend fun deleteFolder(id: String) {
        folderDao.deleteFolderById(id)
    }
}
