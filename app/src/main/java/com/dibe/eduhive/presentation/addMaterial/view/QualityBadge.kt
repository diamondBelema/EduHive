package com.dibe.eduhive.presentation.addMaterial.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Quality state for a flashcard or batch of flashcards. */
enum class QualityStatus { VALID, FLAGGED, REJECTED }

/**
 * Animated badge that communicates flashcard quality at a glance.
 *
 * - ✅ Green  — valid flashcards accepted
 * - ⚠️ Yellow — flagged for review
 * - ❌ Red    — rejected by the quality validator
 */
@Composable
fun QualityBadge(
    status: QualityStatus,
    count: Int,
    modifier: Modifier = Modifier
) {
    val (icon, label, containerColor, contentColor) = when (status) {
        QualityStatus.VALID -> BadgeStyle(
            Icons.Rounded.Check, "valid",
            Color(0xFF1B5E20).copy(alpha = 0.15f), Color(0xFF2E7D32)
        )
        QualityStatus.FLAGGED -> BadgeStyle(
            Icons.Rounded.Warning, "flagged",
            Color(0xFFF57F17).copy(alpha = 0.15f), Color(0xFFF57F17)
        )
        QualityStatus.REJECTED -> BadgeStyle(
            Icons.Rounded.Close, "rejected",
            Color(0xFFB71C1C).copy(alpha = 0.15f), Color(0xFFD32F2F)
        )
    }

    AnimatedVisibility(
        visible = count > 0,
        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
        modifier = modifier
    ) {
        Surface(
            shape = CircleShape,
            color = containerColor
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }
    }
}

private data class BadgeStyle(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val containerColor: Color,
    val contentColor: Color
)
