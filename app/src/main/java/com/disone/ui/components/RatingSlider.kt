package com.disone.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Rating display and slider. API uses 0-100 for both average and user rating.
 * @param averageRating Average in 0-100 scale (from API)
 * @param userRating User's rating in 0-100 scale (from API), null if none
 */
@Composable
fun RatingSlider(
    averageRating: Double,
    totalRatings: Int,
    userRating: Int?,
    isAuthenticated: Boolean,
    onSubmitRating: (Int) -> Unit,
    onClear: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val sliderValue0to10 = ((userRating ?: averageRating.toInt()) / 10f).coerceIn(0f, 10f)
    var sliderValue by remember(userRating, averageRating) {
        mutableFloatStateOf(sliderValue0to10)
    }

    Column(modifier = modifier) {
        Text(
            text = "Rating",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "%.1f".format(averageRating / 10),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "$totalRatings ratings",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isAuthenticated) {
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                valueRange = 0f..10f,
                steps = 9,
                onValueChangeFinished = {
                    onSubmitRating((sliderValue * 10).toInt().coerceIn(0, 100))
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your rating: ${"%.1f".format(sliderValue)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (userRating != null && onClear != null) {
                    Button(onClick = onClear) {
                        Text("Clear")
                    }
                }
            }
        }
    }
}
