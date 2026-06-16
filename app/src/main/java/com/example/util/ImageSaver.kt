package com.example.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object ImageSaver {

    /**
     * Saves the photo from temporary CameraX File into the selected folder.
     * @return The absolute path or Content URI of the saved file, or null on error.
     */
    fun savePhoto(
        context: Context,
        tempFile: File,
        customName: String,
        folderName: String,
        usePublicGallery: Boolean
    ): String? {
        val fileName = if (customName.endsWith(".jpg", ignoreCase = true)) customName else "$customName.jpg"
        
        return if (usePublicGallery && isStorageAvailable()) {
            saveToPublicGallery(context, tempFile, fileName, folderName)
        } else {
            saveToPrivateFolder(context, tempFile, fileName, folderName)
        }
    }

    private fun isStorageAvailable(): Boolean {
        val state = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == state
    }

    private fun saveToPrivateFolder(
        context: Context,
        tempFile: File,
        fileName: String,
        folderName: String
    ): String? {
        try {
            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) 
                ?: context.filesDir
            val targetFolder = File(baseDir, folderName)
            if (!targetFolder.exists()) {
                targetFolder.mkdirs()
            }
            val targetFile = File(targetFolder, fileName)
            tempFile.copyTo(targetFile, overwrite = true)
            return targetFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun saveToPublicGallery(
        context: Context,
        tempFile: File,
        fileName: String,
        folderName: String
    ): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    // Custom subfolder inside Pictures/SnapFolder/
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/SnapFolder/$folderName")
                }
                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    resolver.openOutputStream(imageUri).use { out ->
                        if (out != null) {
                            tempFile.inputStream().use { input ->
                                input.copyTo(out)
                            }
                        }
                    }
                    imageUri.toString()
                } else {
                    saveToPrivateFolder(context, tempFile, fileName, folderName)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                saveToPrivateFolder(context, tempFile, fileName, folderName)
            }
        } else {
            // Older Android versions needing direct file creation inside Environment.DIRECTORY_PICTURES
            try {
                val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appDir = File(publicDir, "SnapFolder/$folderName")
                if (!appDir.exists()) {
                    appDir.mkdirs()
                }
                val targetFile = File(appDir, fileName)
                tempFile.copyTo(targetFile, overwrite = true)
                
                // Keep system media scanner updated
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DATA, targetFile.absolutePath)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                }
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                targetFile.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                saveToPrivateFolder(context, tempFile, fileName, folderName)
            }
        }
    }
}
