package com.dibe.eduhive.presentation.reviewList.view

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dibe.eduhive.domain.model.ReviewEvent
import com.dibe.eduhive.presentation.reviewList.viewmodel.ReviewListViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewListScreen(
    viewModel: ReviewListViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Progress & History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Streak card
            item {
                StreakCard(
                    streakDays = state.streakDays,
                    reviewedToday = state.reviewedToday,
                    correctToday = state.correctToday
                )
            }

            // Weekly activity chart
            item {
                WeeklyActivityCard(weeklyActivity = state.weeklyActivity)
            }

            // History header
            if (state.reviews.isEmpty()) {
                item {
                    EmptyReviewsState(modifier = Modifier.fillMaxWidth().padding(top = 32.dp))
                }
            } else {
                item {
                    Text(
                        "Review History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(state.reviews) { review ->
                    ReviewListItem(review = review)
                }
            }
        }
    }
}

@Composable
private fun StreakCard(
    streakDays: Int,
    reviewedToday: Int,
    correctToday: Int
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (streakDays > 0)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Flame icon
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = if (streakDays > 0)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.LocalFireDepartment,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = if (streakDays > 0)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$streakDays",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (streakDays > 0)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (streakDays == 1) "day streak" else "day streak",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (streakDays > 0)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (streakDays == 0) {
                    Text(
                        text = "Study today to start your streak!",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Today's stats
            Column(horizontalAlignment = Alignment.End) {
                StatChip(
                    icon = Icons.Rounded.CheckCircle,
                    label = "$reviewedToday reviewed",
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(6.dp))
                StatChip(
                    icon = Icons.Rounded.AutoAwesome,
                    label = "$correctToday correct",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun StatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color
) {
    Surface(
        color = tint.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = tint)
            Text(label, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun WeeklyActivityCard(weeklyActivity: List<Int>) {
    val maxVal = weeklyActivity.maxOrNull()?.takeIf { it > 0 } ?: 1
    val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").let { labels ->
        // Align based on today's day of week
        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) // Sun=1..Sat=7
        val todayIndex = (today + 5) % 7 // Mon=0..Sun=6
        (0..6).map { i ->
            val dayIdx = (todayIndex - 6 + i + 7) % 7
            labels[dayIdx]
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.Timeline,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "This Week",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${weeklyActivity.sum()} total",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))

            val barColor = MaterialTheme.colorScheme.primary
            val barColorLight = MaterialTheme.colorScheme.primaryContainer
            val todayColor = MaterialTheme.colorScheme.tertiary

            Row(
                modifier = Modifier.fillMaxWidth().height(80.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                weeklyActivity.forEachIndexed { index, count ->
                    val fraction = count.toFloat() / maxVal.toFloat()
                    val isToday = index == 6
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(0.6f),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            val fillColor = if (isToday) todayColor else barColor
                            val emptyColor = if (isToday) todayColor.copy(alpha = 0.15f) else barColorLight
                            Canvas(modifier = Modifier.fillMaxWidth().height(64.dp)) {
                                val barWidth = size.width * 0.7f
                                val x = (size.width - barWidth) / 2f
                                val fullHeight = size.height
                                val fillHeight = fullHeight * fraction.coerceIn(0f, 1f)
                                // Empty track
                                drawRoundRect(
                                    color = emptyColor,
                                    topLeft = Offset(x, 0f),
                                    size = Size(barWidth, fullHeight),
                                    cornerRadius = CornerRadius(barWidth / 2f)
                                )
                                // Fill
                                if (fillHeight > 0f) {
                                    drawRoundRect(
                                        color = fillColor,
                                        topLeft = Offset(x, fullHeight - fillHeight),
                                        size = Size(barWidth, fillHeight),
                                        cornerRadius = CornerRadius(barWidth / 2f)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            dayLabels.getOrElse(index) { "" },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isToday) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewListItem(review: ReviewEvent) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val isCorrect = review.outcome >= 0.5f
    
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = if (isCorrect)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer,
                shape = CircleShape,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (isCorrect) "✓" else "✕",
                        fontWeight = FontWeight.Bold,
                        color = if (isCorrect)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isCorrect) "Correct answer" else "Needs review",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dateFormat.format(Date(review.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Score chip
            Surface(
                color = if (isCorrect)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                else
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "${(review.outcome * 100).toInt()}%",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isCorrect)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
fun EmptyReviewsState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.LocalFireDepartment,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No study history yet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Start studying flashcards and quizzes — your progress and streaks will appear here.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
