package com.example.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.PhotoEntity
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun CameraView(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val photos by viewModel.currentPhotos.collectAsStateWithLifecycle()

    var showGalleryOnly by remember { mutableStateOf(false) }

    // Executor for CameraX
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }

    // CameraX elements
    val preview = remember { Preview.Builder().build() }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(uiState.flashMode)
            .build()
    }
    val cameraSelector = remember(uiState.cameraLensFacing) {
        CameraSelector.Builder()
            .requireLensFacing(uiState.cameraLensFacing)
            .build()
    }

    // Refresh flash mode when state changes
    LaunchedEffect(uiState.flashMode) {
        imageCapture.flashMode = uiState.flashMode
    }

    // Snackbar alerts
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header bar
            HeaderSection(
                selectedFolder = uiState.selectedFolder,
                folders = uiState.folders,
                onFolderSelected = { viewModel.setSelectedFolder(it) },
                onCreateFolderClick = { viewModel.openCreateFolderDialog() },
                usePublicGallery = uiState.usePublicGallery,
                onToggleStorage = { viewModel.toggleStorageOption() }
            )

            if (showGalleryOnly) {
                // Large full screen gallery view of selected folder
                GallerySection(
                    photos = photos,
                    folderName = uiState.selectedFolder,
                    onPhotoClick = { viewModel.detailPhoto(it) },
                    onCloseGallery = { showGalleryOnly = false },
                    modifier = Modifier.weight(1f)
                )
            } else {
                // Interactive Camera and split gallery viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val previewView = remember {
                        PreviewView(context).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    }

                    // Bind to lifecycle outside the factory lambda
                    LaunchedEffect(cameraSelector, uiState.cameraLensFacing) {
                        try {
                            val provider = context.getCameraProvider()
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageCapture
                            )
                            preview.setSurfaceProvider(previewView.surfaceProvider)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            viewModel.showSnackbar("Failed to launch camera: ${e.localizedMessage}")
                        }
                    }

                    // Camera Viewport View
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Overlay Controls directly on the Camera preview frame
                    CameraOverlays(
                        flashMode = uiState.flashMode,
                        onCycleFlash = { viewModel.cycleFlashMode() },
                        onToggleCamera = { viewModel.toggleCameraLens() }
                    )

                    // Overlay bottom mask for rich readability
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                            )
                    )

                    // Bottom horizontal roll of recent folder images + capture controls
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        if (photos.isNotEmpty()) {
                            // Mini horizontal preview strip of captured named photos
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                                    .padding(bottom = 8.dp)
                            ) {
                                items(photos) { photo ->
                                    MiniPhotoCard(
                                        photo = photo,
                                        onClick = { viewModel.detailPhoto(photo) }
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                                    .padding(bottom = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No snapshots in '${uiState.selectedFolder}' yet.",
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Physical Capture Row (Controls)
                        CaptureControlsRow(
                            isSaving = uiState.isSaving,
                            onCapture = {
                                val tempFile = File.createTempFile("photo_", ".jpg", context.cacheDir)
                                val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()
                                imageCapture.takePicture(
                                    outputOptions,
                                    executor,
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            viewModel.onPhotoCaptured(tempFile)
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            exception.printStackTrace()
                                            viewModel.showSnackbar("Camera capture error: ${exception.localizedMessage}")
                                        }
                                    }
                                )
                            },
                            onToggleGallery = { showGalleryOnly = true }
                        )
                    }
                }
            }
        }

        // Dialogs management
        DialogsContainer(
            dialogState = uiState.dialogState,
            selectedFolder = uiState.selectedFolder,
            onClose = { viewModel.closeDialog() },
            onAddFolderSubmit = { viewModel.addNewFolder(it) },
            onSaveNamedPhotoSubmit = { viewModel.saveNamedPhoto(it) },
            onDeletePhoto = { viewModel.deletePhoto(it) }
        )
    }
}

// Suspend extension to get Camera Provider asynchronously
private suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCancellableCoroutine { continuation ->
    val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
    cameraProviderFuture.addListener({
        try {
            continuation.resume(cameraProviderFuture.get())
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }, ContextCompat.getMainExecutor(this))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeaderSection(
    selectedFolder: String,
    folders: List<String>,
    onFolderSelected: (String) -> Unit,
    onCreateFolderClick: () -> Unit,
    usePublicGallery: Boolean,
    onToggleStorage: () -> Unit
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Camera,
                        contentDescription = "SnapFolder Logo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SnapFolder",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Storage Switcher Badge (App vs Shared)
                AssistChip(
                    onClick = onToggleStorage,
                    label = {
                        Text(
                            text = if (usePublicGallery) "Public Gallery" else "App Sandbox",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (usePublicGallery) Icons.Rounded.Public else Icons.Rounded.FolderZip,
                            contentDescription = "Storage type",
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (usePublicGallery) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.height(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Scrollable folders list row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Folder:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.padding(end = 8.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    items(folders) { folder ->
                        val isSelected = folder == selectedFolder
                        FilterChip(
                            selected = isSelected,
                            onClick = { onFolderSelected(folder) },
                            label = {
                                Text(
                                    text = folder,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Create folder helper button
                IconButton(
                    onClick = onCreateFolderClick,
                    modifier = Modifier
                        .size(34.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .testTag("add_folder_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CreateNewFolder,
                        contentDescription = "Create Folder",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CameraOverlays(
    flashMode: Int,
    onCycleFlash: () -> Unit,
    onToggleCamera: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Upper left: Flash configuration button
        IconButton(
            onClick = onCycleFlash,
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                .size(44.dp)
        ) {
            val (icon, color) = when (flashMode) {
                ImageCapture.FLASH_MODE_ON -> Icons.Rounded.FlashOn to Color.Yellow
                ImageCapture.FLASH_MODE_AUTO -> Icons.Rounded.FlashAuto to Color.Cyan
                else -> Icons.Rounded.FlashOff to Color.White
            }
            Icon(
                imageVector = icon,
                contentDescription = "Flash mode",
                tint = color
            )
        }

        // Upper right: Camera Flip Selection
        IconButton(
            onClick = onToggleCamera,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                .size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.FlipCameraAndroid,
                contentDescription = "Flip camera",
                tint = Color.White
            )
        }
    }
}

@Composable
fun CaptureControlsRow(
    isSaving: Boolean,
    onCapture: () -> Unit,
    onToggleGallery: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Left spacer/placeholder or action button
        IconButton(
            onClick = onToggleGallery,
            modifier = Modifier
                .size(50.dp)
                .background(Color.White.copy(alpha = 0.15f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Rounded.PhotoLibrary,
                contentDescription = "Open Full Gallery",
                tint = Color.White
            )
        }

        // Capture button with a double-ring animated pulse look
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(80.dp)
                .clickable(enabled = !isSaving) { onCapture() }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(BorderStroke(4.dp, Color.White), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(if (isSaving) Color.Gray else Color.White)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    )
                }
            }
        }

        // Right side: Quick Info / instruction badge
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.CenterFocusStrong,
                contentDescription = "Focusing indicator",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun MiniPhotoCard(
    photo: PhotoEntity,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
        modifier = Modifier
            .width(64.dp)
            .fillMaxHeight()
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = photo.filePath,
                contentDescription = photo.customName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(vertical = 2.dp, horizontal = 4.dp)
            ) {
                Text(
                    text = photo.customName,
                    color = Color.White,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun GallerySection(
    photos: List<PhotoEntity>,
    folderName: String,
    onPhotoClick: (PhotoEntity) -> Unit,
    onCloseGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "'$folderName' Gallery (${photos.size} Items)",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            TextButton(onClick = onCloseGallery) {
                Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close View")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Close Grid")
            }
        }

        if (photos.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoCamera,
                        contentDescription = "Empty",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This folder has no custom photos.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Switch back to Camera view and take some photos!",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(photos.size) { index ->
                    val photo = photos[index]
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable { onPhotoClick(photo) }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = photo.filePath,
                                contentDescription = photo.customName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Title ribbon overlay
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                        )
                                    )
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = photo.customName,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DialogsContainer(
    dialogState: DialogState,
    selectedFolder: String,
    onClose: () -> Unit,
    onAddFolderSubmit: (String) -> Unit,
    onSaveNamedPhotoSubmit: (String) -> Unit,
    onDeletePhoto: (PhotoEntity) -> Unit
) {
    when (dialogState) {
        is DialogState.None -> {}
        
        is DialogState.CreateFolder -> {
            var folderNameInput by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = onClose,
                title = { Text("Create Folder") },
                text = {
                    Column {
                        Text(
                            text = "Add a new custom folder directory to organize your named snapshots.",
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = folderNameInput,
                            onValueChange = { folderNameInput = it },
                            label = { Text("Folder Name") },
                            placeholder = { Text("e.g. Invoices, Family") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("folder_name_input")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onAddFolderSubmit(folderNameInput) },
                        enabled = folderNameInput.isNotBlank()
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onClose) {
                        Text("Cancel")
                    }
                }
            )
        }

        is DialogState.SavePhotoNamed -> {
            var photoNameInput by remember { mutableStateOf("") }
            
            // Generate a placeholder default timestamp name
            LaunchedEffect(Unit) {
                val format = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                photoNameInput = "img_${format.format(Date())}"
            }

            Dialog(onDismissRequest = onClose) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Name Photo & Store",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Preview of taken photo before final save
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black)
                        ) {
                            // Decode temporary JPEG to preview safely in custom dialog
                            val bitmap = remember(dialogState.tempFile) {
                                try {
                                    val filePath = dialogState.tempFile.absolutePath
                                    BitmapFactory.decodeFile(filePath)
                                } catch (e: Exception) {
                                    null
                                }
                            }

                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Captured photo preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Target Directory: SnapFolder/$selectedFolder/",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Custom Photo name textfield input
                        OutlinedTextField(
                            value = photoNameInput,
                            onValueChange = { photoNameInput = it },
                            label = { Text("Photo Name (.jpg)") },
                            placeholder = { Text("e.g. 123, product_A") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("photo_name_input")
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(
                                onClick = {
                                    // Retake scenario deletes current temporary block cache
                                    if (dialogState.tempFile.exists()) {
                                        dialogState.tempFile.delete()
                                    }
                                    onClose()
                                }
                            ) {
                                Text("Retake / Discard", color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { onSaveNamedPhotoSubmit(photoNameInput) },
                                enabled = photoNameInput.isNotBlank()
                            ) {
                                Text("Save Named")
                            }
                        }
                    }
                }
            }
        }

        is DialogState.PhotoDetail -> {
            val photo = dialogState.photo
            Dialog(onDismissRequest = onClose) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = photo.fileName,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onClose) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black)
                        ) {
                            AsyncImage(
                                model = photo.filePath,
                                contentDescription = photo.customName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Metadata details
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            DetailItem(label = "Folder Path", value = "SnapFolder/${photo.folderName}")
                            DetailItem(label = "Physical Path", value = photo.filePath)
                            val format = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
                            DetailItem(label = "Created At", value = format.format(Date(photo.createdAt)))
                            if (photo.fileSize > 0) {
                                DetailItem(label = "Size", value = "%.2f KB".format(photo.fileSize / 1024.0))
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    onDeletePhoto(photo)
                                    onClose()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.DeleteSweep, contentDescription = "Delete")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Delete File")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailItem(label: String, value: String) {
    Column {
        Text(text = label, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
        Text(text = value, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
