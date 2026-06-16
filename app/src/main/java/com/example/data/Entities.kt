package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
) : Serializable

@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customName: String, // e.g. "123"
    val fileName: String,   // e.g. "123.jpg"
    val filePath: String,   // Physical path or Content URI
    val folderName: String, // Custom folder it belongs to
    val createdAt: Long = System.currentTimeMillis(),
    val fileSize: Long = 0L
) : Serializable
