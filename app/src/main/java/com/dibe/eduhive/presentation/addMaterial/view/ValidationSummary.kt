package com.dibe.eduhive.presentation.addMaterial.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Shows the final quality breakdown after processing completes:
 * how many flashcards were accepted, rejected, and whether any duplicates
 * were detected across concepts.
 */
@Composable
fun ValidationSummary(
    flashcardsValid: Int,
    flashcardsRejected: Int,
    duplicatesFound: Int,
    modifier: Modifier = Modifier
) {
    val visible = flashcardsValid > 0 || flashcardsRejected > 0
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it / 2 } + fadeIn(),
        modifier = modifier
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Quality Report",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QualityBadge(QualityStatus.VALID, flashcardsValid)
                    if (flashcardsRejected > 0) {
                        QualityBadge(QualityStatus.REJECTED, flashcardsRejected)
                    }
                    if (duplicatesFound > 0) {
                        QualityBadge(QualityStatus.FLAGGED, duplicatesFound)
                    }
                }
                if (duplicatesFound > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "⚠️ $duplicatesFound similar question(s) detected across concepts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
