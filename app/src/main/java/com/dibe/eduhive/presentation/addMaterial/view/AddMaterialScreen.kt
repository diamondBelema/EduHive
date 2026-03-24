package com.dibe.eduhive.presentation.addMaterial.view

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dibe.eduhive.presentation.addMaterial.viewmodel.AddMaterialEvent
import com.dibe.eduhive.presentation.addMaterial.viewmodel.AddMaterialViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMaterialScreen(
    viewModel: AddMaterialViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var showTitleSheet by remember { mutableStateOf(false) }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            selectedUri = it
            showTitleSheet = true
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            selectedUri = it
            showTitleSheet = true
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(message = error)
            viewModel.onEvent(AddMaterialEvent.ClearError)
        }
    }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            delay(2500) // Longer delay for expressive satisfaction
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "Import Knowledge", 
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Expressive state switching using AnimatedContent
            AnimatedContent(
                targetState = state.isProcessing,
                transitionSpec = {
                    fadeIn(tween(500)) + scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) togetherWith
                    fadeOut(tween(400)) + scaleOut(targetScale = 0.95f)
                },
                label = "processing_transition"
            ) { isProcessing ->
                if (isProcessing) {
                    ProcessingStateExpressive(
                        status = state.processingStatus ?: "Connecting to Hive...",
                        progress = state.progressPercentage / 100f,
                        successMessage = state.successMessage
                    )
                } else {
                    ImportSelectionExpressive(
                        onDocumentClick = {
                            documentPicker.launch(arrayOf("application/pdf", "text/plain", "image/*"))
                        },
                        onImageClick = {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                }
            }
        }

        if (showTitleSheet) {
            TitleEntryBottomSheet(
                onDismiss = { showTitleSheet = false },
                onConfirm = { title ->
                    selectedUri?.let { viewModel.onEvent(AddMaterialEvent.SelectFile(it, title)) }
                    showTitleSheet = false
                }
            )
        }
    }
}

@Composable
fun ImportSelectionExpressive(
    onDocumentClick: () -> Unit,
    onImageClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            "What would you like to learn from?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            lineHeight = 36.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        ImportCardExpressive(
            title = "Document or PDF",
            subtitle = "Books, research papers, notes",
            icon = Icons.Rounded.Description,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            onClick = onDocumentClick
        )

        ImportCardExpressive(
            title = "Physical Media",
            subtitle = "Extract text from photos/camera",
            icon = Icons.Rounded.CameraAlt,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            onClick = onImageClick
        )

        Spacer(modifier = Modifier.weight(1f))
        
        // Expressive AI Badge
        Surface(
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.AutoAwesome, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    "Our AI processes your files locally. Your data stays private within your hive.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ImportCardExpressive(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f, 
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "scale"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        shadowElevation = if (isPressed) 2.dp else 6.dp
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.25f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.width(20.dp))
            Column {
                Text(
                    title, 
                    style = MaterialTheme.typography.titleLarge, 
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle, 
                    style = MaterialTheme.typography.bodyMedium, 
                    color = LocalContentColor.current.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun ProcessingStateExpressive(
    status: String,
    progress: Float,
    successMessage: String?
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(stiffness = Spring.StiffnessVeryLow),
        label = "progress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Outer expressive ring
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.size(240.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                strokeWidth = 12.dp,
                strokeCap = StrokeCap.Round
            )
            
            // Active progress ring
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(240.dp),
                strokeWidth = 16.dp,
                strokeCap = StrokeCap.Round,
                color = if (successMessage != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
            )
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedContent(
                    targetState = successMessage != null,
                    transitionSpec = {
                        scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut()
                    },
                    label = "status_icon"
                ) { isDone ->
                    if (isDone) {
                        Icon(
                            Icons.Rounded.Check, 
                            contentDescription = null, 
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "${(animatedProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(64.dp))

        // Expressive Status Pill
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = CircleShape
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (successMessage == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.width(12.dp))
                }
                
                Text(
                    text = if (successMessage != null) "Complete!" else status,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        
        AnimatedVisibility(
            visible = successMessage != null,
            enter = slideInVertically { it / 2 } + fadeIn()
        ) {
            successMessage?.let {
                Text(
                    it,
                    modifier = Modifier.padding(top = 24.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleEntryBottomSheet(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp, top = 8.dp)
        ) {
            Text(
                "Name this Material", 
                style = MaterialTheme.typography.headlineSmall, 
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Give your knowledge source a clear name for your hive.", 
                style = MaterialTheme.typography.bodyMedium, 
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(Modifier.height(24.dp))
            
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                placeholder = { Text("e.g. Psychology Chapter 1") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            
            Spacer(Modifier.height(32.dp))
            
            Button(
                onClick = { if (title.isNotBlank()) onConfirm(title) },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.large,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Text("Start AI Analysis", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private enum class MaterialType { DOCUMENT, IMAGE }
