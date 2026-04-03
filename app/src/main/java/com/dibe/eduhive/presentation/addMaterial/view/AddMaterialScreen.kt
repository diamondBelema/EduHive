package com.dibe.eduhive.presentation.addMaterial.view

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key.Companion.Back
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dibe.eduhive.presentation.addMaterial.viewmodel.AddMaterialEvent
import com.dibe.eduhive.presentation.addMaterial.viewmodel.AddMaterialViewModel
import com.dibe.eduhive.workers.MaterialProcessingWorker
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMaterialScreen(
    viewModel: AddMaterialViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val workManager = remember { WorkManager.getInstance(context) }
    val activeWorkInfo by workManager.getWorkInfosByTagFlow("material_processing")
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val currentWork = activeWorkInfo
        .filter { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        .maxByOrNull { it.progress.getInt(MaterialProcessingWorker.KEY_PROGRESS, 0) }

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var showTitleSheet by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

    // Monotonic progress floor — ensures the progress bar never visually goes backward,
    // even if WorkManager restarts the worker from scratch (e.g. after process death).
    val progressHighWater = remember { mutableStateOf(0f) }
    LaunchedEffect(currentWork?.id) {
        // Reset the floor only when the work item itself changes identity.
        progressHighWater.value = 0f
    }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(message = error)
            viewModel.onEvent(AddMaterialEvent.ClearError)
        }
    }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            delay(3500) // Longer delay to allow reading the expressive summary
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
                },
                actions = {
                    if (currentWork != null) {
                        FilledTonalIconButton(
                            onClick = { showCancelDialog = true },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(40.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                Icons.Rounded.Stop,
                                contentDescription = "Cancel processing",
                                modifier = Modifier.size(20.dp)
                            )
                        }
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
            AnimatedContent(
                targetState = currentWork != null,
                transitionSpec = {
                    fadeIn(tween(600)) + scaleIn(initialScale = 0.9f) togetherWith
                            fadeOut(tween(500)) + scaleOut(targetScale = 0.95f)
                },
                label = "processing_transition"
            ) { isProcessing ->
                if (isProcessing && currentWork != null) {
                    val rawProgress = currentWork.progress.getInt(MaterialProcessingWorker.KEY_PROGRESS, 0) / 100f
                    // Clamp to the high-water mark so the bar never moves backward.
                    val displayProgress = rawProgress.coerceAtLeast(progressHighWater.value)
                    SideEffect {
                        if (rawProgress > progressHighWater.value) progressHighWater.value = rawProgress
                    }
                    val status = currentWork.progress.getString(MaterialProcessingWorker.KEY_STATUS) ?: "Queued..."
                    val validCount = currentWork.progress.getInt(MaterialProcessingWorker.KEY_VALID_COUNT, 0)
                    val currentConcept = currentWork.progress.getInt(MaterialProcessingWorker.KEY_CURRENT_CONCEPT, 0)
                    val totalConcepts = currentWork.progress.getInt(MaterialProcessingWorker.KEY_TOTAL_CONCEPTS, 0)
                    val summary = currentWork.progress.getString(MaterialProcessingWorker.KEY_SUMMARY)

                    ProcessingStateExpressive(
                        status = status,
                        progress = displayProgress,
                        successMessage = state.successMessage ?: summary,
                        flashcardsValid = validCount,
                        currentConcept = currentConcept,
                        totalConcepts = totalConcepts
                    )
                } else {
                    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                        uri?.let { selectedUri = it; showTitleSheet = true }
                    }
                    val imgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                        uri?.let { selectedUri = it; showTitleSheet = true }
                    }

                    ImportSelectionExpressive(
                        onDocumentClick = { docPicker.launch(arrayOf("application/pdf", "text/plain", "image/*")) },
                        onImageClick = { imgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
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

        if (showCancelDialog) {
            AlertDialog(
                onDismissRequest = { showCancelDialog = false },
                title = { Text("Cancel AI Analysis?") },
                text = { Text("The AI is currently building your study materials. Stopping now will lose current progress.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            workManager.cancelAllWorkByTag("material_processing")
                            showCancelDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Stop Analysis") }
                },
                dismissButton = {
                    TextButton(onClick = { showCancelDialog = false }) { Text("Continue") }
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProcessingStateExpressive(
    status: String,
    progress: Float,
    successMessage: String?,
    flashcardsValid: Int = 0,
    currentConcept: Int = 0,
    totalConcepts: Int = 0
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(stiffness = Spring.StiffnessVeryLow),
        label = "progress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "ai_expressive")

    // Pulse scale for the main content
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Animated glow colors
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val glowColor by infiniteTransition.animateColor(
        initialValue = primaryColor.copy(alpha = 0.15f),
        targetValue = tertiaryColor.copy(alpha = 0.15f),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_color"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Soft background glow
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .blur(40.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(glowColor, Color.Transparent),
                        ),
                        CircleShape
                    )
            )

            val density = LocalDensity.current
            val strokeWidthPx = with(density) { 16.dp.toPx() }

            // The Expressive Squiggly Indicator
            CircularWavyProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(260.dp),
                stroke = Stroke(
                    width = strokeWidthPx,
                    cap = StrokeCap.Round,
                    miter = 20f
                ),
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                color = MaterialTheme.colorScheme.primary,
                gapSize = 0.dp,
                wavelength = 48.dp
            )

            // Central Content
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.scale(pulseScale)
            ) {
                // This creates the standard "Back" overshoot effect
                val BackEasing = CubicBezierEasing(0.175f, 0.885f, 0.32f, 1.275f)

                AnimatedContent(
                    targetState = successMessage != null,
                    transitionSpec = {
                        (scaleIn(tween(500, easing = BackEasing)) + fadeIn()) togetherWith
                                (scaleOut(tween(400)) + fadeOut())
                    },
                    label = "status_icon"
                ) { isDone ->
                    if (isDone) {
                        Surface(
                            modifier = Modifier.size(100.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            tonalElevation = 4.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${(animatedProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Analysis in progress",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(56.dp))

        // Glassmorphic Status Pill
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
            shape = CircleShape,
            tonalElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (successMessage == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(14.dp))
                }

                Text(
                    text = if (successMessage != null) "Knowledge Hive Updated" else status,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        // Summary Badges Section
        AnimatedVisibility(
            visible = totalConcepts > 0 || flashcardsValid > 0,
            enter = slideInVertically { it / 2 } + fadeIn(tween(800)),
            modifier = Modifier.padding(top = 32.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (totalConcepts > 0) {
                        SummaryBadgeExpressive(
                            label = "Concepts",
                            value = if (currentConcept > 0 && successMessage == null) "$currentConcept/$totalConcepts" else "$totalConcepts",
                            icon = Icons.Rounded.AutoAwesome,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (flashcardsValid > 0) {
                        SummaryBadgeExpressive(
                            label = "Flashcards",
                            value = "$flashcardsValid",
                            icon = Icons.Rounded.Style,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                successMessage?.let {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = it,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryBadgeExpressive(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color,
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 2.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Surface(
                color = contentColorFor(color).copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = contentColorFor(color)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = contentColorFor(color)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = contentColorFor(color).copy(alpha = 0.7f)
            )
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
fun TestCircularProgress() {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { 16.dp.toPx() }

    val animatedProgress by animateFloatAsState(
        targetValue = .5f,
        animationSpec = spring(stiffness = Spring.StiffnessVeryLow),
        label = "progress"
    )

    CircularWavyProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier.size(260.dp),
        stroke = Stroke(
            width = strokeWidthPx,
            cap = StrokeCap.Round,
            miter = 20f
        ),
        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        color = MaterialTheme.colorScheme.primary,
        gapSize = 0.dp,
        wavelength = 48.dp
    )
}
