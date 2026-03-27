package com.dibe.eduhive.presentation.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dibe.eduhive.domain.model.Concept
import com.dibe.eduhive.presentation.hiveDashBoard.viewmodel.HiveDashboardEvent
import com.dibe.eduhive.presentation.hiveDashBoard.viewmodel.HiveDashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiveDashboardScreen(
    hiveId: String,
    viewModel: HiveDashboardViewModel = hiltViewModel(),
    onNavigateToStudy: () -> Unit,
    onNavigateToAddMaterial: () -> Unit,
    onNavigateToConcepts: () -> Unit,
    onNavigateToReviews: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToMaterials: () -> Unit, // Add this
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onEvent(HiveDashboardEvent.Refresh)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { 
                    Text(
                        "Knowledge Hive", 
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isLoading && state.overview == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }
                }

                state.overview != null -> {
                    val overview = state.overview!!
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Header Stats Section
                        item {
                            DashboardHeader(
                                confidence = overview.averageConfidence.toFloat(),
                                dueCount = overview.dueFlashcardsCount
                            )
                        }

                        // Expressive Quick Actions
                        item {
                            SectionHeader(title = "Learning Tools", icon = Icons.Outlined.AutoAwesome)
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                ActionCardExpressive(
                                    title = "Study Now",
                                    subtitle = "${overview.dueFlashcardsCount} cards due",
                                    icon = Icons.Rounded.PlayArrow,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    onClick = onNavigateToStudy,
                                    modifier = Modifier.weight(1.1f)
                                )
                                ActionCardExpressive(
                                    title = "Import",
                                    subtitle = "PDF, Image",
                                    icon = Icons.Rounded.Add,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    onClick = onNavigateToAddMaterial,
                                    modifier = Modifier.weight(0.9f)
                                )
                            }
                        }

                        // Weak Concepts with "Needs Focus" styling
                        if (overview.weakConcepts.isNotEmpty()) {
                            item {
                                SectionHeader(title = "Focus Areas", icon = Icons.Default.Warning, color = MaterialTheme.colorScheme.error)
                            }
                            items(overview.weakConcepts.take(3)) { concept ->
                                ConceptFocusCard(
                                    concept = concept,
                                    onClick = onNavigateToConcepts
                                )
                            }
                        }

                        // Statistics Card
                        item {
                            SectionHeader(title = "Analytics", icon = Icons.Default.BarChart)
                            MetricsGrid(
                                totalConcepts = overview.totalConcepts,
                                totalMaterials = overview.totalMaterials,
                                recentReviews = overview.recentReviewsCount,
                                onConceptsClick = onNavigateToConcepts,
                                onReviewsClick = onNavigateToReviews,
                                onMaterialsClick = onNavigateToMaterials // Add this
                            )
                        }
                    }
                }
            }

            // Error Message
            AnimatedVisibility(
                visible = state.error != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                state.error?.let { error ->
                    Snackbar(
                        modifier = Modifier.padding(16.dp),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) { Text(error) }
                }
            }
        }
    }
}

@Composable
fun DashboardHeader(confidence: Float, dueCount: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        "Mastery Level",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "${(confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black
                    )
                }
                
                // Visual Progress Circle
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.size(80.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                        strokeWidth = 8.dp
                    )
                    CircularProgressIndicator(
                        progress = { confidence },
                        modifier = Modifier.size(80.dp),
                        strokeWidth = 8.dp,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    Icon(
                        Icons.Outlined.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Modern progress indicator
            LinearProgressIndicator(
                progress = { confidence },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = color)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
    }
}

@Composable
fun ActionCardExpressive(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(140.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                color = contentColor.copy(alpha = 0.15f),
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                }
            }
            
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 24.sp
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun ConceptFocusCard(concept: Concept, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { concept.confidence.toFloat() },
                    modifier = Modifier.fillMaxSize(),
                    color = if (concept.confidence < 0.4) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = 4.dp
                )
                Text(
                    "${(concept.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = concept.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = concept.description ?: "Requires review",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun MetricsGrid(
    totalConcepts: Int,
    totalMaterials: Int,
    recentReviews: Int,
    onConceptsClick: () -> Unit,
    onReviewsClick: () -> Unit,
    onMaterialsClick: () -> Unit // Add this
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MetricSmallCard(
            label = "Concepts", 
            value = totalConcepts.toString(), 
            icon = Icons.Default.Book, 
            modifier = Modifier.weight(1f).clickable(onClick = onConceptsClick)
        )
        MetricSmallCard(
            label = "Docs", 
            value = totalMaterials.toString(), 
            icon = Icons.Default.Description, 
            modifier = Modifier.weight(1f).clickable(onClick = onMaterialsClick) // Add this
        )
        MetricSmallCard(
            label = "Reviews", 
            value = recentReviews.toString(), 
            icon = Icons.Outlined.History, 
            modifier = Modifier.weight(1f).clickable(onClick = onReviewsClick)
        )
    }
}

@Composable
fun MetricSmallCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    OutlinedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
