package ru.gigapisar.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The look of the phone's own settings: a grey page, white rounded cards that group rows,
 * thin dividers between the rows, small blue-or-grey switches behind a short vertical line,
 * and explanations in small grey text under a card.
 */

/** A white rounded card of rows with dividers between them and an optional note under it. */
@Composable
internal fun SettingsGroup(
    rows: List<@Composable () -> Unit>,
    note: String? = null,
) {
    if (rows.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp)) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.surface),
        ) {
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                row()
            }
        }
        if (note != null) {
            Text(
                text = note,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
            )
        }
    }
}

/** One row of a card: a title, an optional small line under it, and something at the end. */
@Composable
internal fun OneRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color = Color.Unspecified,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 17.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = if (subtitleColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else subtitleColor,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        }
    }
}

/** The small switch: blue when on, grey when off, with a short vertical line before it. */
@Composable
internal fun OneSwitch(checked: Boolean) {
    val track by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        label = "switch track",
    )
    val shift by animateDpAsState(if (checked) 16.dp else 0.dp, label = "switch thumb")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Box(
            modifier =
                Modifier
                    .size(width = 38.dp, height = 22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(track)
                    .padding(2.5.dp),
        ) {
            Box(modifier = Modifier.offset(x = shift).size(17.dp).clip(CircleShape).background(Color.White))
        }
    }
}

/** The arrow at the end of a row that opens another page. */
@Composable
internal fun OneChevron() {
    Icon(
        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.outline,
    )
}

/** A white rounded card around free-form content: text fields, buttons, a note. */
@Composable
internal fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        content()
    }
}
