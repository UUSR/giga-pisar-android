package ru.gigapisar.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import ru.gigapisar.BuildConfig
import ru.gigapisar.R
import ru.gigapisar.settings.AppLanguage
import ru.gigapisar.settings.InsertionMode
import ru.gigapisar.update.Updates

private const val SITE_URL = "https://gigapisar.github.io"
private const val SOURCE_URL = "https://github.com/moznoazachem/giga-pisar-android"
private const val DOWNLOAD_URL = "https://github.com/moznoazachem/giga-pisar-android/releases/latest"

/** The settings as one list in the style of the system Settings app. */
@Composable
internal fun SettingsList(
    insertionMode: InsertionMode,
    virtualButtonVisible: Boolean,
    volumeKeyEnabled: Boolean,
    lowerSummary: String = "",
    upperSummary: String = "",
    vibrationEnabled: Boolean,
    noClipboard: Boolean = false,
    simpleSyntax: Boolean = false,
    dropHesitations: Boolean = true,
    noFinalPeriod: Boolean = false,
    fabScale: Float = 1f,
    fabGestures: Boolean = true,
    fabEdgeHandle: Boolean = false,
    hiddenAppNames: List<String> = emptyList(),
    microphoneGranted: Boolean,
    accessibilityEnabled: Boolean,
    onInsertionMode: (InsertionMode) -> Unit,
    onVirtualButton: (Boolean) -> Unit,
    onVolumeKey: (Boolean) -> Unit,
    onVibration: (Boolean) -> Unit,
    onNoClipboard: (Boolean) -> Unit = {},
    onSimpleSyntax: (Boolean) -> Unit = {},
    onDropHesitations: (Boolean) -> Unit = {},
    onNoFinalPeriod: (Boolean) -> Unit = {},
    onFabScale: (Float) -> Unit = {},
    onFabGestures: (Boolean) -> Unit = {},
    onFabEdgeHandle: (Boolean) -> Unit = {},
    onOpenHiddenApps: () -> Unit = {},
    onRequestMicrophone: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    brainSection: @Composable () -> Unit = {},
    onLanguageChanged: () -> Unit = {},
    page: SettingsPage = SettingsPage.MAIN,
    onOpen: (SettingsPage) -> Unit = {},
) {
    val context = LocalContext.current
    val permissionsOk = microphoneGranted && accessibilityEnabled
    val permissionRows: Rows.() -> Unit = {
        row {
            StatusRow(
                title = R.string.microphone,
                ok = microphoneGranted,
                okText = R.string.permission_granted,
                badText = R.string.permission_denied,
                onClick = if (microphoneGranted) null else onRequestMicrophone,
            )
        }
        row {
            StatusRow(
                title = R.string.accessibility_service,
                ok = accessibilityEnabled,
                okText = R.string.service_enabled,
                badText = R.string.service_disabled,
                onClick = onOpenAccessibilitySettings,
                chevron = true,
            )
        }
    }
    Column {
        when (page) {
            // The short list: each row either switches a thing or opens its page.
            SettingsPage.MAIN -> {
                if (!permissionsOk) {
                    SectionTitle(R.string.section_permissions)
                    Group(build = permissionRows)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Group {
                    row {
                        SwitchAndOpenRow(
                            title = R.string.floating_button,
                            subtitle = R.string.settings_button_summary,
                            checked = virtualButtonVisible,
                            onChange = onVirtualButton,
                            onOpen = { onOpen(SettingsPage.BUTTON) },
                        )
                    }
                    row {
                        SwitchAndOpenRow(
                            title = R.string.volume_key,
                            subtitle = R.string.settings_keys_summary,
                            checked = volumeKeyEnabled,
                            onChange = onVolumeKey,
                            onOpen = { onOpen(SettingsPage.KEYS) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Group {
                    row {
                        OneRow(
                            title = stringResource(R.string.settings_text),
                            subtitle = stringResource(R.string.settings_text_summary),
                            modifier = Modifier.clickable { onOpen(SettingsPage.TEXT) },
                            trailing = { OneChevron() },
                        )
                    }
                    row { brainSection() }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Group { switch(R.string.vibration, R.string.vibration_hint, vibrationEnabled, onVibration) }
                Spacer(modifier = Modifier.height(8.dp))
                Group {
                    row {
                        OneRow(
                            title = stringResource(R.string.section_about),
                            subtitle = stringResource(R.string.settings_about_summary),
                            modifier = Modifier.clickable { onOpen(SettingsPage.ABOUT) },
                            trailing = { OneChevron() },
                        )
                    }
                }
            }

            SettingsPage.BUTTON -> {
                Group {
                    switch(
                        R.string.settings_enabled,
                        if (insertionMode == InsertionMode.TEXT_FIELD) {
                            R.string.floating_button_text_field
                        } else {
                            R.string.floating_button_always
                        },
                        virtualButtonVisible,
                        onVirtualButton,
                    )
                }
                if (virtualButtonVisible) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Group {
                        switch(R.string.fab_edge_handle, R.string.fab_edge_handle_hint, fabEdgeHandle, onFabEdgeHandle)
                        switch(R.string.fab_gestures, R.string.fab_gestures_hint, fabGestures, onFabGestures)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Group { row { FabSizeRow(scale = fabScale, onScale = onFabScale) } }
                    Spacer(modifier = Modifier.height(8.dp))
                    Group { row { FabHiddenAppsRow(names = hiddenAppNames, onOpen = onOpenHiddenApps) } }
                    Spacer(modifier = Modifier.height(8.dp))
                    // The button puts the text in the way volume down does: the same page.
                    Group {
                        row {
                            OneRow(
                                title = stringResource(R.string.take_text_title),
                                subtitle = lowerSummary,
                                modifier = Modifier.clickable { onOpen(SettingsPage.KEY_DOWN) },
                                trailing = { OneChevron() },
                            )
                        }
                    }
                }
            }

            SettingsPage.KEYS -> {
                Group { switch(R.string.settings_enabled, R.string.volume_key_hint, volumeKeyEnabled, onVolumeKey) }
                if (volumeKeyEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    // Each key has its own page: what it does is set up there, in one place.
                    Group(note = stringResource(R.string.volume_key_note)) {
                        row {
                            OneRow(
                                title = stringResource(R.string.key_down_title),
                                subtitle = lowerSummary,
                                modifier = Modifier.clickable { onOpen(SettingsPage.KEY_DOWN) },
                                trailing = { OneChevron() },
                            )
                        }
                        // Without a field to put the text in, volume up has nothing to work on.
                        if (insertionMode == InsertionMode.TEXT_FIELD) {
                            row {
                                OneRow(
                                    title = stringResource(R.string.key_up_title),
                                    subtitle = upperSummary,
                                    modifier = Modifier.clickable { onOpen(SettingsPage.KEY_UP) },
                                    trailing = { OneChevron() },
                                )
                            }
                        }
                    }
                }
            }

            // Drawn by the main screen: they need the Brain settings, not this list's switches.
            SettingsPage.KEY_DOWN, SettingsPage.KEY_UP -> Unit

            SettingsPage.TEXT -> {
                Group {
                    switch(R.string.simple_syntax, R.string.simple_syntax_hint, simpleSyntax, onSimpleSyntax)
                    switch(R.string.no_final_period, R.string.no_final_period_hint, noFinalPeriod, onNoFinalPeriod)
                    switch(R.string.drop_hesitations, R.string.drop_hesitations_hint, dropHesitations, onDropHesitations)
                }
                SectionTitle(R.string.section_insertion)
                Group {
                    radio(R.string.mode_text_field, R.string.mode_text_field_hint, insertionMode == InsertionMode.TEXT_FIELD) {
                        onInsertionMode(InsertionMode.TEXT_FIELD)
                    }
                    radio(R.string.mode_clipboard, R.string.mode_clipboard_hint, insertionMode == InsertionMode.CLIPBOARD) {
                        onInsertionMode(InsertionMode.CLIPBOARD)
                    }
                }
                if (insertionMode == InsertionMode.TEXT_FIELD) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Group { switch(R.string.no_clipboard, R.string.no_clipboard_hint, noClipboard, onNoClipboard) }
                }
            }

            SettingsPage.ABOUT -> {
                val play = remember { if (BuildConfig.SELF_UPDATE) Updates.playStoreIntent(context) else null }
                Group {
                    row { LanguageRow(onLanguageChanged) }
                    link(R.string.about_site, SITE_URL.removePrefix("https://")) { openUrl(context, SITE_URL) }
                    link(R.string.about_source, context.getString(R.string.about_source_hint)) {
                        openUrl(context, SOURCE_URL)
                    }
                    link(R.string.about_share, null) { share(context) }
                    if (play != null) {
                        link(R.string.about_play, context.getString(R.string.about_play_hint)) {
                            try {
                                context.startActivity(play)
                            } catch (_: Exception) {
                                // The store is there but would not open.
                            }
                        }
                    }
                }
                SectionTitle(R.string.section_recognition)
                Group {
                    row {
                        OneRow(
                            title = stringResource(R.string.model_name),
                            subtitle = stringResource(R.string.model_installed_hint),
                        )
                    }
                }
                SectionTitle(R.string.section_permissions)
                Group(build = permissionRows)
            }
        }
    }
}

/** The pages of the settings: the short main list and what its rows open. */
internal enum class SettingsPage { MAIN, BUTTON, KEYS, KEY_DOWN, KEY_UP, TEXT, ABOUT }

/**
 * A row that does two things, as Wi-Fi does in the phone's settings: a tap on the switch turns
 * the thing on or off, a tap on the name opens its page.
 */
@Composable
internal fun SwitchAndOpenRow(
    title: Int,
    subtitle: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
        // The text opens the page, and the chevron says so: without them
        // the row reads as a plain switch.
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .clickable(onClick = onOpen)
                    .padding(start = 20.dp, end = 2.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(title),
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(subtitle),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            OneChevron()
        }
        Box(
            modifier =
                Modifier
                    .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                    .padding(start = 0.dp, end = 20.dp, top = 18.dp, bottom = 18.dp),
        ) {
            OneSwitch(checked)
        }
    }
}

/** Rows of one card, collected first so the card can put dividers between those that are shown. */
private class Rows {
    val list = mutableListOf<@Composable () -> Unit>()

    fun row(content: @Composable () -> Unit) {
        list += content
    }

    fun switch(
        title: Int,
        subtitle: Int,
        checked: Boolean,
        onChange: (Boolean) -> Unit,
    ) = row {
        OneRow(
            title = stringResource(title),
            subtitle = stringResource(subtitle),
            modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
            trailing = { OneSwitch(checked) },
        )
    }

    fun radio(
        title: Int,
        subtitle: Int,
        selected: Boolean,
        onClick: () -> Unit,
    ) = row {
        OneRow(
            title = stringResource(title),
            subtitle = stringResource(subtitle),
            modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
            trailing = { RadioButton(selected = selected, onClick = null) },
        )
    }

    fun link(
        title: Int,
        subtitle: String?,
        onClick: () -> Unit,
    ) = row {
        OneRow(title = stringResource(title), subtitle = subtitle, modifier = Modifier.clickable(onClick = onClick))
    }
}

@Composable
private fun Group(
    note: String? = null,
    build: Rows.() -> Unit,
) {
    SettingsGroup(rows = Rows().apply(build).list, note = note)
}

@Composable
internal fun SectionTitle(text: Int) {
    Text(
        text = stringResource(text),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 32.dp, end = 20.dp, top = 22.dp, bottom = 2.dp),
    )
}

@Composable
private fun StatusRow(
    title: Int,
    ok: Boolean,
    okText: Int,
    badText: Int,
    onClick: (() -> Unit)?,
    chevron: Boolean = false,
) {
    OneRow(
        title = stringResource(title),
        subtitle = stringResource(if (ok) okText else badText),
        subtitleColor = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        trailing = if (chevron) ({ OneChevron() }) else null,
    )
}

private fun openUrl(
    context: Context,
    url: String,
) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: Exception) {
        // No browser: nothing to open it with.
    }
}

private fun share(context: Context) {
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text, DOWNLOAD_URL))
        }
    try {
        context.startActivity(Intent.createChooser(send, null))
    } catch (_: Exception) {
        // Nothing can share text: nothing to do.
    }
}

/** Interface language: the phone's own, Russian or English. A change restarts the screen in the new language. */
@Composable
private fun LanguageRow(onChanged: () -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val current = AppLanguage.get(context)
    val options =
        listOf(
            AppLanguage.SYSTEM to R.string.language_system,
            AppLanguage.RUSSIAN to R.string.language_ru,
            AppLanguage.ENGLISH to R.string.language_en,
        )
    OneRow(
        title = stringResource(R.string.language),
        subtitle = stringResource(options.first { it.first == current }.second),
        subtitleColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { open = true },
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.language)) },
            text = {
                Column {
                    for ((code, label) in options) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = code == current, role = Role.RadioButton) {
                                        open = false
                                        if (code != current) {
                                            AppLanguage.set(context, code)
                                            onChanged()
                                        }
                                    }.padding(vertical = 10.dp),
                        ) {
                            RadioButton(selected = code == current, onClick = null)
                            Text(stringResource(label), modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.language_close)) } },
        )
    }
}
