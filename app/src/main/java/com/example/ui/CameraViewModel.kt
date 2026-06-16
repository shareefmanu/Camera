package com.example.ui

import android.app.Application
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.FolderEntity
import com.example.data.PhotoEntity
import com.example.data.PhotoRepository
import com.example.util.ImageSaver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

sealed interface DialogState {
    object None : DialogState
    object CreateFolder : DialogState
    data class SavePhotoNamed(val tempFile: File) : DialogState
    data class PhotoDetail(val photo: PhotoEntity) : DialogState
}

data class CameraUiState(
    val folders: List<String> = listOf("General"),
    val selectedFolder: String = "General",
    val cameraLensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val flashMode: Int = ImageCapture.FLASH_MODE_OFF,
    val usePublicGallery: Boolean = true,
    val dialogState: DialogState = DialogState.None,
    val isSaving: Boolean = false,
    val snackbarMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = PhotoRepository(db.photoDao())

    private val _selectedFolder = MutableStateFlow("General")
    private val _lensFacing = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    private val _flashMode = MutableStateFlow(ImageCapture.FLASH_MODE_OFF)
    private val _usePublicGallery = MutableStateFlow(true)
    private val _dialogState = MutableStateFlow<DialogState>(DialogState.None)
    private val _isSaving = MutableStateFlow(false)
    private val _snackbarMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CameraUiState> = combine(
        repository.allFolders,
        _selectedFolder,
        _lensFacing,
        _flashMode,
        _usePublicGallery,
        _dialogState,
        _isSaving,
        _snackbarMessage
    ) { flowArray ->
        @Suppress("UNCHECKED_CAST")
        val folderEntities = flowArray[0] as List<FolderEntity>
        val selFolder = flowArray[1] as String
        val lens = flowArray[2] as Int
        val flash = flowArray[3] as Int
        val pubGallery = flowArray[4] as Boolean
        val dialog = flowArray[5] as DialogState
        val saving = flowArray[6] as Boolean
        val msg = flowArray[7] as String?

        val folderNames = if (folderEntities.isEmpty()) {
            listOf("General")
        } else {
            val list = folderEntities.map { it.name }
            if ("General" !in list) listOf("General") + list else list
        }

        CameraUiState(
            folders = folderNames,
            selectedFolder = selFolder,
            cameraLensFacing = lens,
            flashMode = flash,
            usePublicGallery = pubGallery,
            dialogState = dialog,
            isSaving = saving,
            snackbarMessage = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CameraUiState())

    val currentPhotos: StateFlow<List<PhotoEntity>> = _selectedFolder
        .flatMapLatest { folder ->
            repository.allPhotos.map { list ->
                list.filter { it.folderName == folder }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.insertFolder("General")
        }
    }

    fun setSelectedFolder(folder: String) {
        _selectedFolder.value = folder
    }

    fun toggleCameraLens() {
        _lensFacing.value = if (_lensFacing.value == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
    }

    fun cycleFlashMode() {
        _flashMode.value = when (_flashMode.value) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
    }

    fun toggleStorageOption() {
        _usePublicGallery.value = !_usePublicGallery.value
    }

    fun openCreateFolderDialog() {
        _dialogState.value = DialogState.CreateFolder
    }

    fun closeDialog() {
        _dialogState.value = DialogState.None
    }

    fun addNewFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.insertFolder(trimmed)
            _selectedFolder.value = trimmed
            showSnackbar("Folder '$trimmed' created successfully!")
            closeDialog()
        }
    }

    fun onPhotoCaptured(tempFile: File) {
        _dialogState.value = DialogState.SavePhotoNamed(tempFile)
    }

    fun saveNamedPhoto(customName: String) {
        val state = _dialogState.value
        if (state !is DialogState.SavePhotoNamed) return

        val tempFile = state.tempFile
        val trimmedName = customName.trim()
        if (trimmedName.isEmpty()) {
            showSnackbar("Please specify a valid photo name.")
            return
        }

        _isSaving.value = true
        _dialogState.value = DialogState.None

        viewModelScope.launch {
            try {
                val folder = _selectedFolder.value
                val isPublic = _usePublicGallery.value
                
                val savedPath = ImageSaver.savePhoto(
                    context = getApplication(),
                    tempFile = tempFile,
                    customName = trimmedName,
                    folderName = folder,
                    usePublicGallery = isPublic
                )

                if (savedPath != null) {
                    val finalFileName = if (trimmedName.endsWith(".jpg", ignoreCase = true)) trimmedName else "$trimmedName.jpg"
                    val entity = PhotoEntity(
                        customName = trimmedName,
                        fileName = finalFileName,
                        filePath = savedPath,
                        folderName = folder,
                        fileSize = tempFile.length()
                    )
                    repository.insertPhoto(entity)
                    showSnackbar("Photo saved as $finalFileName inside '$folder' folder.")
                } else {
                    showSnackbar("Error saving the photo.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showSnackbar("Failed to save photo: ${e.localizedMessage}")
            } finally {
                _isSaving.value = false
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }
    }

    fun deletePhoto(photo: PhotoEntity) {
        viewModelScope.launch {
            try {
                if (photo.filePath.startsWith("content://")) {
                    try {
                        getApplication<Application>().contentResolver.delete(
                            android.net.Uri.parse(photo.filePath),
                            null,
                            null
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    val file = File(photo.filePath)
                    if (file.exists()) {
                        file.delete()
                    }
                }
                repository.deletePhoto(photo)
                showSnackbar("Photo '${photo.fileName}' deleted successfully.")
            } catch (e: Exception) {
                showSnackbar("Error deleting photo.")
            }
        }
    }

    fun detailPhoto(photo: PhotoEntity) {
        _dialogState.value = DialogState.PhotoDetail(photo)
    }

    fun showSnackbar(message: String) {
        _snackbarMessage.value = message
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }
}
