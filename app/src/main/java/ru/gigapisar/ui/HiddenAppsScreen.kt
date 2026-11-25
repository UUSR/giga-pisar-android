package ru.gigapisar.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.gigapisar.R

/** An installed app that can be opened from the launcher. */
internal class LaunchableApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
)

/** Apps with a launcher icon, except Pisar itself, sorted by name. Blocking: call off the main thread. */
internal fun launchableApps(context: Context): List<LaunchableApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val iconPx = (40 * context.resources.displayMetrics.density).toInt()
    return pm
        .queryIntentActivities(intent, 0)
        .map { it.activityInfo.applicationInfo }
        .distinctBy { it.packageName }
        .filter { it.packageName != context.packageName }
        .map { info ->
            LaunchableApp(
                packageName = info.packageName,
                label = pm.getApplicationLabel(info).toString(),
                icon = runCatching { pm.getApplicationIcon(info).toBitmap(iconPx, iconPx).asImageBitmap() }.getOrNull(),
            )
        }.sortedBy { it.label.lowercase() }
}

/** Labels of the given packages, for the summary under "Hide in apps". */
internal fun appLabels(
    context: Context,
    packages: Set<String>,
): List<String> =
    packages
        .mapNotNull { pkg ->
            runCatching {
                context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
            }.getOrNull()
        }.sortedBy { it.lowercase() }

/**
 * Apps where the floating button stays hidden: the list of installed apps with checkboxes and
 * a search. The ones already chosen come first.
 */
@Composable
internal fun HiddenAppsScreen(
    hidden: Set<String>,
    onChange: (Set<String>) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<LaunchableApp>?>(null) }
    var query by remember { mutableStateOf("") }
    // Chosen apps stay on top while the page is open, not jumping as boxes are ticked.
    val firstChosen = remember { hidden }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { launchableApps(context) } }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(
                stringResource(R.string.fab_hidden_apps),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.fab_hidden_search)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(26.dp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = Color.Transparent,
                ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
        Text(
            stringResource(R.string.fab_hidden_hint),
            fontSize = 13.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 10.dp),
        )
        val list = apps
        if (list == null) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }
        val shown =
            list
                .filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
                .sortedBy { if (it.packageName in firstChosen) 0 else 1 }
        // One white card of apps, as the settings groups are.
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier =
                Modifier
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.surface),
        ) {
            itemsIndexed(shown, key = { _, app -> app.packageName }) { index, app ->
                val checked = app.packageName in hidden
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                ListItem(
                    headlineContent = { Text(app.label) },
                    leadingContent = {
                        app.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(36.dp)) }
                            ?: Box(modifier = Modifier.size(36.dp))
                    },
                    trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier =
                        Modifier.toggleable(value = checked, role = Role.Checkbox) { on ->
                            onChange(if (on) hidden + app.packageName else hidden - app.packageName)
                        },
                )
            }
        }
    }
}
