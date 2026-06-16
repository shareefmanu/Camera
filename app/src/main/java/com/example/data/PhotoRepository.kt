package com.example.data

import kotlinx.coroutines.flow.Flow

class PhotoRepository(private val photoDao: PhotoDao) {
    val allFolders: Flow<List<FolderEntity>> = photoDao.getAllFolders()
    val allPhotos: Flow<List<PhotoEntity>> = photoDao.getAllPhotos()

    fun getPhotosByFolder(folderName: String): Flow<List<PhotoEntity>> {
        return photoDao.getPhotosByFolder(folderName)
    }

    suspend fun insertFolder(name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1
        val existing = photoDao.getFolderByName(trimmed)
        if (existing != null) return existing.id.toLong()
        return photoDao.insertFolder(FolderEntity(name = trimmed))
    }

    suspend fun deleteFolder(folder: FolderEntity) {
        photoDao.deletePhotosByFolder(folder.name)
        photoDao.deleteFolder(folder)
    }

    suspend fun insertPhoto(photo: PhotoEntity): Long {
        return photoDao.insertPhoto(photo)
    }

    suspend fun deletePhoto(photo: PhotoEntity) {
        photoDao.deletePhoto(photo)
    }
}
