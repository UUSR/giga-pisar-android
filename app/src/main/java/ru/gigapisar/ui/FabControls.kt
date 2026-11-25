package ru.gigapisar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.gigapisar.R
import ru.gigapisar.overlay.FabPreview
import ru.gigapisar.settings.SettingsRepository

/**
 * Size slider for the floating button. Smooth, no steps; the original size sits in the middle.
 * While it moves, the real button shows over the screen at that size (see [FabPreview]): no sample next to it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FabSizeRow(
    scale: Float,
    onScale: (Float) -> Unit,
) {
    var value by remember { mutableFloatStateOf(scale) }
    LaunchedEffect(scale) { value = scale }
    Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 10.dp)) {
        Text(stringResource(R.string.fab_size), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
        Slider(
            value = value,
            onValueChange = {
                value = it
                FabPreview.scale.value = it
            },
            onValueChangeFinished = {
                onScale(value)
                FabPreview.scale.value = null
            },
            valueRange = SettingsRepository.FAB_SCALE_MIN..SettingsRepository.FAB_SCALE_MAX,
            // A thin track and a round thumb, as in the phone's own settings.
            thumb = {
                Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            },
            track = { state ->
                val span = state.valueRange.endInclusive - state.valueRange.start
                val filled = if (span > 0f) ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f) else 0f
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(filled)
                                .height(6.dp)
                                .background(MaterialTheme.colorScheme.primary),
                    )
                }
            },
        )
        Row {
            Text(
                stringResource(R.string.fab_size_smaller),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.fab_size_bigger),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Hide in apps": which apps, opening the list. */
@Composable
internal fun FabHiddenAppsRow(
    names: List<String>,
    onOpen: () -> Unit,
) {
    val summary =
        when {
            names.isEmpty() -> stringResource(R.string.fab_hidden_none)
            names.size <= 2 -> names.joinToString(", ")
            else -> stringResource(R.string.fab_hidden_more, names.take(2).joinToString(", "), names.size - 2)
        }
    OneRow(
        title = stringResource(R.string.fab_hidden_apps),
        subtitle = summary,
        subtitleColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable(onClick = onOpen),
        trailing = { OneChevron() },
    )
}
