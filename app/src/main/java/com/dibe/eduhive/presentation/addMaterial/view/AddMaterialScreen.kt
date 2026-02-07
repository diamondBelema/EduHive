package com.dibe.eduhive.presentation.addMaterial.view

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dibe.eduhive.presentation.addMaterial.viewmodel.AddMaterialEvent
import com.dibe.eduhive.presentation.addMaterial.viewmodel.AddMaterialViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMaterialScreen(
    viewModel: AddMaterialViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var showTitleDialog by remember { mutableStateOf(false) }

    // Document picker
    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            selectedUri = it
            showTitleDialog = true
        }
    }

    // Image picker
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            selectedUri = it
            showTitleDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Material") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Upload Study Material",
                    style = MaterialTheme.typography.titleLarge
                )

                // Document Upload Card
                UploadCard(
                    icon = "📄",
                    title = "Upload Document",
                    subtitle = "PDF • HTML",
                    onClick = {
                        documentPicker.launch(
                            arrayOf(
                                "application/pdf",
                                "text/html"
                            )
                        )
                    }
                )

                Text(
                    text = "Or",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                // Image Upload Card (OCR)
                UploadCard(
                    icon = "📸",
                    title = "Upload Image (OCR)",
                    subtitle = "JPG • PNG",
                    onClick = {
                        imagePicker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }
                )
            }

            // Processing Dialog with Progress
            if (state.isProcessing) {
                ProcessingProgressDialog(
                    status = state.processingStatus ?: "Processing...",
                    progress = state.progressPercentage/ 100f
                )
            }

            // Success Dialog
            state.successMessage?.let { message ->
                SuccessDialog(
                    message = message,
                    onDismiss = {
                        viewModel.onEvent(AddMaterialEvent.DismissSuccess)
                        onNavigateBack()
                    }
                )
            }

            // Error Snackbar
            state.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.onEvent(AddMaterialEvent.ClearError) }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }

        // Title Input Dialog
        if (showTitleDialog) {
            TitleInputDialog(
                onDismiss = {
                    showTitleDialog = false
                    selectedUri = null
                },
                onConfirm = { title ->
                    selectedUri?.let { uri ->
                        viewModel.onEvent(AddMaterialEvent.SelectFile(uri, title))
                    }
                    showTitleDialog = false
                    selectedUri = null
                }
            )
        }
    }
}

@Composable
fun UploadCard(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ProcessingProgressDialog(
    status: String,
    progress: Float
) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Processing Material") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("${(progress * 100).toInt()}%")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = { }
    )
}

@Composable
fun SuccessDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Success!") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
fun TitleInputDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var title by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Material Title") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Enter a title") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title.ifBlank { "Untitled" }) },
                enabled = title.isNotBlank()
            ) {
                Text("Upload")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}