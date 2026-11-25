package ru.gigapisar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch
import ru.gigapisar.R
import ru.gigapisar.brain.Brain
import ru.gigapisar.settings.SettingsRepository
import ru.gigapisar.settings.TakeEdit
import ru.gigapisar.settings.UpperKey

/** The short line under a key's row on the "volume keys" page: what the key is set to. */
@Composable
internal fun lowerKeySummary(
    brain: SettingsRepository.BrainSettings,
    ready: Boolean,
    sends: Boolean,
): String {
    val text =
        stringResource(
            when (if (ready) brain.lower else TakeEdit.NONE) {
                TakeEdit.NONE -> R.string.lower_sum_plain
                TakeEdit.TIDY -> R.string.lower_sum_tidy
                TakeEdit.SHORTEN -> R.string.lower_sum_short
                TakeEdit.TRANSLATE -> R.string.lower_sum_translate
            },
        )
    return if (sends) text + stringResource(R.string.lower_sum_sends) else text
}

@Composable
internal fun upperKeySummary(
    brain: SettingsRepository.BrainSettings,
    ready: Boolean,
): String =
    stringResource(
        when (brain.upper) {
            UpperKey.RECORD -> R.string.upper_sum_record
            UpperKey.VOLUME -> R.string.upper_sum_volume
            else ->
                when {
                    !ready -> R.string.upper_sum_no_brain
                    brain.upper == UpperKey.TIDY -> R.string.upper_sum_tidy
                    brain.upper == UpperKey.SHORTEN -> R.string.upper_sum_short
                    brain.upper == UpperKey.TRANSLATE -> R.string.upper_sum_translate
                    else -> R.string.upper_sum_original
                }
        },
    )

/** A plain choice: a title, a line under it and the circle. */
@Composable
private fun ChoiceRow(
    title: Int,
    subtitle: Int,
    selected: Boolean,
    enabled: Boolean = true,
    onSelect: () -> Unit,
) {
    OneRow(
        title = stringResource(title),
        subtitle = stringResource(subtitle),
        modifier =
            Modifier
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
                .alpha(if (enabled) 1f else 0.45f),
        trailing = { RadioButton(selected = selected, onClick = null) },
    )
}

/** Shown when no service is connected: the edits are there, faded, and this row leads to the Brain. */
@Composable
private fun ConnectBrainRow(onOpenBrain: () -> Unit) {
    SettingsGroup(
        rows =
            listOf<@Composable () -> Unit>({
                OneRow(
                    title = stringResource(R.string.keys_connect_brain),
                    subtitle = stringResource(R.string.keys_connect_brain_hint),
                    subtitleColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onOpenBrain),
                    trailing = { OneChevron() },
                )
            }),
    )
}

private val edits =
    listOf(
        Triple(TakeEdit.TIDY, Brain.Kind.TIDY, R.string.brain_style_tidy_hint),
        Triple(TakeEdit.SHORTEN, Brain.Kind.SHORTEN, R.string.brain_style_short_hint),
        Triple(TakeEdit.TRANSLATE, Brain.Kind.TRANSLATE, R.string.brain_style_translate_hint),
    )

/**
 * Volume down: it always records, so the page is about what the dictation goes in as and
 * whether a short press sends it. The floating button puts the text in the same way.
 */
@Composable
internal fun LowerKeyPage(
    brain: SettingsRepository.BrainSettings,
    sendKey: Boolean,
    textField: Boolean,
    keyEnabled: Boolean,
    onSendKey: (Boolean) -> Unit,
    onEditPrompt: (Brain.Kind) -> Unit,
    onOpenBrain: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ready = brainReady(context, brain)
    val lower = if (ready) brain.lower else TakeEdit.NONE

    SettingsGroup(
        rows =
            listOf<@Composable () -> Unit>({
                OneRow(title = stringResource(R.string.lower_hold), subtitle = stringResource(R.string.lower_hold_hint))
            }),
    )

    SectionTitle(R.string.lower_text_title)
    val rows = mutableListOf<@Composable () -> Unit>()
    rows += {
        ChoiceRow(R.string.lower_plain, R.string.lower_plain_hint, lower == TakeEdit.NONE) {
            scope.launch { SettingsRepository.setLowerEdit(context, TakeEdit.NONE) }
        }
    }
    val titles =
        mapOf(TakeEdit.TIDY to R.string.lower_tidy, TakeEdit.SHORTEN to R.string.lower_short, TakeEdit.TRANSLATE to R.string.lower_translate)
    for ((edit, kind, hint) in edits) {
        rows += {
            ChoiceAndOpenRow(
                title = stringResource(titles.getValue(edit)),
                subtitle = stringResource(hint),
                own = brain.prompts.of(kind) != null,
                selected = lower == edit,
                onSelect = { scope.launch { SettingsRepository.setLowerEdit(context, edit) } },
                onOpen = { onEditPrompt(kind) },
                enabled = ready,
            )
        }
        if (edit == TakeEdit.TRANSLATE && lower == TakeEdit.TRANSLATE) {
            rows += {
                TranslateLanguageRow(language = brain.lowerLanguage) { language ->
                    scope.launch { SettingsRepository.setLowerLanguage(context, language) }
                }
            }
        }
    }
    // A translation is wanted for a short take too, so the switch has nothing to say then.
    if (lower == TakeEdit.TIDY || lower == TakeEdit.SHORTEN) {
        rows += {
            SwitchItem(
                title = R.string.brain_skip_short,
                subtitle = R.string.brain_skip_short_hint,
                checked = brain.skipShort,
                enabled = true,
                onChange = { on -> scope.launch { SettingsRepository.setBrainSkipShort(context, on) } },
            )
        }
    }
    SettingsGroup(rows = rows, note = stringResource(R.string.lower_text_note))
    if (!ready) ConnectBrainRow(onOpenBrain)

    if (textField && keyEnabled) {
        SectionTitle(R.string.lower_tap_title)
        SettingsGroup(
            rows =
                listOf<@Composable () -> Unit>({
                    SwitchItem(
                        title = R.string.send_key,
                        subtitle = R.string.send_key_hint_down,
                        checked = sendKey,
                        enabled = true,
                        onChange = onSendKey,
                    )
                }),
        )
    }
}

/** Volume up: one choice of what it does. */
@Composable
internal fun UpperKeyPage(
    brain: SettingsRepository.BrainSettings,
    onEditPrompt: (Brain.Kind) -> Unit,
    onOpenBrain: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ready = brainReady(context, brain)
    val upper = brain.upper

    fun pick(key: UpperKey) {
        scope.launch { SettingsRepository.setUpperKey(context, key) }
    }

    SectionTitle(R.string.upper_title)
    val rows = mutableListOf<@Composable () -> Unit>()
    rows += { ChoiceRow(R.string.upper_record, R.string.upper_record_hint, upper == UpperKey.RECORD) { pick(UpperKey.RECORD) } }
    val keys =
        mapOf(
            TakeEdit.TIDY to (UpperKey.TIDY to R.string.upper_tidy),
            TakeEdit.SHORTEN to (UpperKey.SHORTEN to R.string.upper_short),
            TakeEdit.TRANSLATE to (UpperKey.TRANSLATE to R.string.upper_translate),
        )
    for ((edit, kind, hint) in edits) {
        val (key, title) = keys.getValue(edit)
        rows += {
            ChoiceAndOpenRow(
                title = stringResource(title),
                subtitle = stringResource(hint),
                own = brain.prompts.of(kind) != null,
                selected = upper == key,
                onSelect = { pick(key) },
                onOpen = { onEditPrompt(kind) },
                enabled = ready,
            )
        }
        if (key == UpperKey.TRANSLATE && upper == UpperKey.TRANSLATE && ready) {
            rows += {
                TranslateLanguageRow(language = brain.upperLanguage) { language ->
                    scope.launch { SettingsRepository.setUpperLanguage(context, language) }
                }
            }
        }
    }
    rows += {
        ChoiceRow(R.string.upper_original, R.string.upper_original_hint, upper == UpperKey.ORIGINAL, enabled = ready) {
            pick(UpperKey.ORIGINAL)
        }
    }
    rows += { ChoiceRow(R.string.upper_volume, R.string.upper_volume_hint, upper == UpperKey.VOLUME) { pick(UpperKey.VOLUME) } }
    SettingsGroup(rows = rows, note = stringResource(R.string.upper_note))
    if (!ready) ConnectBrainRow(onOpenBrain)
}
