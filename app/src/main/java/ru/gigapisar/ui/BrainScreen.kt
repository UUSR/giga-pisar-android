package ru.gigapisar.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.gigapisar.R
import ru.gigapisar.brain.Brain
import ru.gigapisar.brain.BrainClient
import ru.gigapisar.brain.BrainException
import ru.gigapisar.brain.BrainProvider
import ru.gigapisar.brain.BrainProviders
import ru.gigapisar.brain.KeyVault
import ru.gigapisar.brain.say
import ru.gigapisar.settings.TakeEdit
import ru.gigapisar.settings.SettingsRepository

/** A service is connected: there is someone to send a dictation to. */
internal fun brainReady(
    context: Context,
    brain: SettingsRepository.BrainSettings,
): Boolean {
    val provider = BrainProviders.endpoint(BrainProviders.byId(brain.providerId), brain.ollamaUrl, brain.customUrl)
    return provider != null && brain.model != null && (provider.local || provider.custom || KeyVault.load(context, provider.id) != null)
}

/** The Brain row in the main settings list: its state at a glance, opens the Brain page. */
@Composable
internal fun BrainRow(
    brain: SettingsRepository.BrainSettings,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    val provider = BrainProviders.endpoint(BrainProviders.byId(brain.providerId), brain.ollamaUrl, brain.customUrl)
    val ready = brainReady(context, brain)
    OneRow(
        title = stringResource(R.string.brain_switch),
        subtitle =
            when {
                !ready -> stringResource(R.string.brain_row_none)
                brain.inUse -> stringResource(R.string.brain_row_on, provider!!.name, brain.model!!)
                else -> stringResource(R.string.brain_row_off, provider!!.name)
            },
        subtitleColor =
            if (ready && brain.inUse) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clickable(onClick = onOpen),
        trailing = { OneChevron() },
    )
}

private sealed interface Status {
    data object Idle : Status

    data object Checking : Status

    data class Ok(
        val text: String,
    ) : Status

    data class Failed(
        val text: String,
    ) : Status
}

/**
 * The Brain page, like a page of the system Settings: switches on top, then the saved
 * services (one key each, switch between them with a tap), the models of the active one,
 * and a field to add a key. Every change is checked with a real one-word request.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BrainScreen(
    brain: SettingsRepository.BrainSettings,
    onBack: () -> Unit,
    onOpenKeys: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(BrainTab.CLOUD) }
    var editing by rememberSaveable { mutableStateOf<Brain.Kind?>(null) }
    val ollamaActive = brain.providerId == BrainProviders.Ollama.id
    val customActive = brain.providerId == BrainProviders.Custom.id
    editing?.let { kind ->
        BrainPromptEditor(kind = kind, custom = brain.prompts.of(kind), onClose = { editing = null })
        return
    }
    val tabs = listOf(BrainTab.CLOUD, BrainTab.CUSTOM, BrainTab.LOCAL)
    val shown = tab.takeIf { it in tabs } ?: BrainTab.CLOUD
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(
                stringResource(R.string.brain_switch),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
        }
        TabRow(selectedTabIndex = tabs.indexOf(shown), containerColor = MaterialTheme.colorScheme.background) {
            for (t in tabs) {
                Tab(
                    selected = shown == t,
                    onClick = { tab = t },
                    text = {
                        Text(
                            stringResource(
                                when (t) {
                                    BrainTab.CLOUD -> R.string.brain_tab_cloud
                                    BrainTab.CUSTOM -> R.string.brain_tab_custom
                                    BrainTab.LOCAL -> R.string.brain_tab_local
                                },
                            ),
                        )
                    },
                )
            }
        }
        when (shown) {
            BrainTab.CLOUD -> BrainCloudTab(brain = brain, onEditPrompt = { editing = it }, onOpenKeys = onOpenKeys)
            BrainTab.CUSTOM -> BrainCustomTab(brain = brain, customActive = customActive, onEditPrompt = { editing = it }, onOpenKeys = onOpenKeys)
            BrainTab.LOCAL -> BrainLocalTab(brain = brain, ollamaActive = ollamaActive, onEditPrompt = { editing = it }, onOpenKeys = onOpenKeys)
        }
    }
}

private enum class BrainTab { CLOUD, CUSTOM, LOCAL }

/**
 * What the Brain does, each thing with its own switch: voice commands, every take, the volume
 * key. Nothing depends on another; all of it waits for a connected service ([active]).
 */
@Composable
private fun BrainOptions(
    active: Boolean,
    brain: SettingsRepository.BrainSettings,
    onEditPrompt: (Brain.Kind) -> Unit,
    onOpenKeys: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rows = mutableListOf<@Composable () -> Unit>()

    fun switch(
        title: Int,
        subtitle: Int,
        checked: Boolean,
        enabled: Boolean,
        onChange: (Boolean) -> Unit,
    ) {
        rows += { SwitchItem(title = title, subtitle = subtitle, checked = checked, enabled = enabled, onChange = onChange) }
    }
    if (active) {
        // The text opens the instructions the commands run by, the switch turns them on.
        rows += {
            SwitchAndOpenRow(
                title = R.string.brain_commands,
                subtitle = R.string.brain_switch_hint,
                checked = brain.commands,
                onChange = { on -> scope.launch { SettingsRepository.setBrainCommands(context, on) } },
                onOpen = { onEditPrompt(Brain.Kind.COMMAND) },
            )
        }
    } else {
        switch(R.string.brain_commands, R.string.brain_switch_hint, false, false) { }
    }
    SettingsGroup(rows = rows)

    // What the Brain does with a dictation is chosen where the keys are set up.
    SettingsGroup(
        rows =
            listOf<@Composable () -> Unit>({
                OneRow(
                    title = stringResource(R.string.brain_edit_in_keys),
                    subtitle = stringResource(R.string.brain_edit_in_keys_hint),
                    modifier = Modifier.clickable(onClick = onOpenKeys),
                    trailing = { OneChevron() },
                )
            }),
    )
}

/** A choice whose text opens a page (the chevron says so) and whose circle picks it. */
@Composable
internal fun ChoiceAndOpenRow(
    title: String,
    subtitle: String,
    own: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
    enabled: Boolean = true,
) {
    // Waits for a connected service: shown, but faded.
    Row(modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .clickable(enabled = enabled, onClick = onOpen)
                    .padding(start = 20.dp, end = 2.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 17.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (own) {
                    Text(
                        text = stringResource(R.string.brain_prompt_own_note),
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            OneChevron()
        }
        Box(
            modifier =
                Modifier
                    .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
                    .padding(start = 10.dp, end = 20.dp, top = 18.dp, bottom = 18.dp),
        ) {
            RadioButton(selected = selected, onClick = null)
        }
    }
}

/** The listed languages by the name they are stored and sent to the model under. */
private val languageNames =
    mapOf(
        "English" to R.string.lang_english,
        "Spanish" to R.string.lang_spanish,
        "French" to R.string.lang_french,
        "German" to R.string.lang_german,
        "Chinese" to R.string.lang_chinese,
    )

/** The language to translate into: one row that names it and opens the list; any other is typed in there. */
@Composable
internal fun TranslateLanguageRow(
    language: String,
    onChange: (String) -> Unit,
) {
    var choosing by remember { mutableStateOf(false) }
    val listed = languageNames[language]
    OneRow(
        title = stringResource(R.string.brain_translate_language),
        subtitle = listed?.let { stringResource(it) } ?: language,
        subtitleColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { choosing = true },
        trailing = { OneChevron() },
    )
    if (!choosing) return
    var own by remember { mutableStateOf(if (listed == null) language else "") }
    AlertDialog(
        onDismissRequest = { choosing = false },
        title = { Text(stringResource(R.string.brain_translate_language)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                for (stored in Brain.TRANSLATE_LANGUAGES) {
                    val selected = own.isBlank() && language == stored
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = selected, role = Role.RadioButton) {
                                    onChange(stored)
                                    choosing = false
                                }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(text = stringResource(languageNames.getValue(stored)), fontSize = 17.sp, modifier = Modifier.padding(start = 14.dp))
                    }
                }
                OutlinedTextField(
                    value = own,
                    onValueChange = { own = it.take(40) },
                    label = { Text(stringResource(R.string.brain_translate_own)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // An empty field means "leave it as it is", never "no language".
                    own.trim().takeIf { it.isNotEmpty() }?.let(onChange)
                    choosing = false
                },
            ) { Text(stringResource(R.string.brain_translate_done)) }
        },
    )
}

/** The server answered that it has no such model (see BrainClient.describeFailure). */
private fun notInstalled(e: BrainException): Boolean = e.message?.contains("ollama pull") == true

@Composable
private fun BrainCloudTab(
    brain: SettingsRepository.BrainSettings,
    onEditPrompt: (Brain.Kind) -> Unit,
    onOpenKeys: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf(KeyVault.savedProviders(context)) }
    val active = BrainProviders.byId(brain.providerId)?.takeIf { it in saved }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var status by remember { mutableStateOf<Status>(Status.Idle) }
    var newKey by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<BrainProvider?>(null) }
    val ready = active != null && brain.model != null

    // Models of the active service, fetched when the page opens or the service changes.
    LaunchedEffect(active?.id) {
        models = emptyList()
        val p = active ?: return@LaunchedEffect
        val key = KeyVault.load(context, p.id) ?: return@LaunchedEffect
        models =
            withContext(Dispatchers.IO) {
                try {
                    BrainProviders.chatModels(BrainClient.listModels(p, key))
                } catch (_: Exception) {
                    emptyList()
                }
            }
    }

    /** Checks [provider] with [model] (or a sensible one) and makes it the active service. */
    fun activate(
        provider: BrainProvider,
        key: String,
        model: String?,
        saveKey: Boolean,
    ) {
        status = Status.Checking
        scope.launch {
            try {
                val picked =
                    withContext(Dispatchers.IO) {
                        val want =
                            model ?: run {
                                val list =
                                    try {
                                        BrainProviders.chatModels(BrainClient.listModels(provider, key))
                                    } catch (_: BrainException) {
                                        // Some services do not list models; the preferred one may still answer.
                                        emptyList()
                                    }
                                BrainProviders.pickDefault(provider, list)
                            } ?: throw BrainException(context.getString(R.string.brain_no_model))
                        BrainClient.probe(provider, key, want)
                        want
                    }
                if (saveKey) {
                    KeyVault.save(context, provider.id, key)
                    saved = KeyVault.savedProviders(context)
                    newKey = ""
                    chosen = null
                }
                SettingsRepository.setBrainService(context, provider.id, picked)
                // The first service connected turns voice commands on; later ones leave the switch alone.
                SettingsRepository.setBrainEnabled(context, true)
                status = Status.Ok(context.getString(R.string.brain_check_ok, provider.name, picked))
            } catch (e: BrainException) {
                status = Status.Failed(e.message ?: "")
            } catch (e: Exception) {
                status = Status.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun forget(provider: BrainProvider) {
        KeyVault.clear(context, provider.id)
        saved = KeyVault.savedProviders(context)
        scope.launch {
            SettingsRepository.forgetBrainService(context, provider.id)
            if (provider == active) {
                val next = saved.firstOrNull()
                if (next == null) {
                    SettingsRepository.setBrainEnabled(context, false)
                    SettingsRepository.setBrainService(context, null, null)
                } else {
                    SettingsRepository.setBrainService(context, next.id, brain.modelsByProvider[next.id])
                }
            }
        }
        status = Status.Idle
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
    ) {
        StatusCard(status = status, ready = ready, active = active, model = brain.model)

        BrainOptions(active = ready, brain = brain, onEditPrompt = onEditPrompt, onOpenKeys = onOpenKeys)

        if (saved.isNotEmpty()) {
            SectionTitle(R.string.brain_services)
            SettingsGroup(
                rows =
                    saved.map { p ->
                        {
                            OneRow(
                                title = p.name,
                                subtitle = brain.modelsByProvider[p.id] ?: stringResource(R.string.brain_key_saved),
                                subtitleColor =
                                    if (p == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier =
                                    Modifier.selectable(selected = p == active, role = Role.RadioButton) {
                                        if (p != active && status != Status.Checking) {
                                            val key = KeyVault.load(context, p.id) ?: return@selectable
                                            activate(p, key, brain.modelsByProvider[p.id], saveKey = false)
                                        }
                                    },
                                trailing = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = p == active, onClick = null)
                                        IconButton(onClick = { forget(p) }) {
                                            Icon(
                                                Icons.Outlined.Delete,
                                                contentDescription = stringResource(R.string.brain_forget),
                                                tint = MaterialTheme.colorScheme.outline,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    },
            )
        }

        if (active != null && models.size > 1) {
            SectionTitle(R.string.brain_models)
            SettingsGroup(
                rows =
                    models.take(30).map { m ->
                        {
                            OneRow(
                                title = m,
                                modifier =
                                    Modifier.selectable(selected = m == brain.model, role = Role.RadioButton) {
                                        if (m != brain.model && status != Status.Checking) {
                                            val key = KeyVault.load(context, active.id) ?: return@selectable
                                            activate(active, key, m, saveKey = false)
                                        }
                                    },
                                trailing = { RadioButton(selected = m == brain.model, onClick = null) },
                            )
                        }
                    },
            )
        }

        SectionTitle(if (saved.isEmpty()) R.string.brain_key else R.string.brain_add_key)
        val guessed = BrainProviders.fromKey(newKey)
        val target = if (newKey.isNotBlank()) guessed ?: chosen else null
        Column(
            modifier =
                Modifier
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = newKey,
                onValueChange = {
                    newKey = it
                    if (status is Status.Failed) status = Status.Idle
                },
                label = { Text(stringResource(R.string.brain_key_placeholder)) },
                leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                newKey.isBlank() ->
                    Text(
                        stringResource(R.string.brain_key_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                guessed != null ->
                    Text(
                        stringResource(R.string.brain_guessed, guessed.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                else -> {
                    Text(stringResource(R.string.brain_pick_service), style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (p in BrainProviders.cloud) {
                            FilterChip(selected = chosen == p, onClick = { chosen = p }, label = { Text(p.name) })
                        }
                    }
                }
            }
            Button(
                onClick = { target?.let { activate(it, newKey.trim(), null, saveKey = true) } },
                enabled = target != null && status != Status.Checking,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(stringResource(R.string.brain_check)) }
            Text(
                text = stringResource(R.string.brain_where_keys),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier.clickable {
                        openUrl(context, (target ?: active ?: BrainProviders.DeepSeek).keysUrl)
                    },
            )
        }

        Text(
            text = stringResource(R.string.brain_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp),
        )
    }
}

/**
 * The "own server" tab: any OpenAI-compatible API at an address the person types — a gateway,
 * a proxy, a self-hosted service. A key is optional (local gateways take none), and a model
 * can be typed by hand: not every gateway lists its models.
 */
@Composable
private fun BrainCustomTab(
    brain: SettingsRepository.BrainSettings,
    customActive: Boolean,
    onEditPrompt: (Brain.Kind) -> Unit,
    onOpenKeys: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(brain.customUrl) }
    var key by remember { mutableStateOf("") }
    var manualModel by remember {
        mutableStateOf(
            brain.modelsByProvider[BrainProviders.Custom.id]
                ?: brain.model.takeIf { customActive }
                ?: "",
        )
    }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var status by remember { mutableStateOf<Status>(Status.Idle) }
    val savedKey = KeyVault.load(context, BrainProviders.Custom.id)

    LaunchedEffect(customActive, brain.customUrl, savedKey) {
        if (!customActive) return@LaunchedEffect
        models = emptyList()
        val server = BrainProviders.customAt(brain.customUrl) ?: return@LaunchedEffect
        val stored = KeyVault.load(context, BrainProviders.Custom.id) ?: ""
        models =
            withContext(Dispatchers.IO) {
                try {
                    BrainProviders.chatModels(BrainClient.listModels(server, stored))
                } catch (_: Exception) {
                    emptyList()
                }
            }
    }

    /** Checks the address (and the key, when one is given) and makes this service the active one. */
    fun connect(
        address: String,
        rawKey: String,
        model: String?,
    ) {
        val server = BrainProviders.customAt(address)
        if (server == null) {
            status = Status.Failed(context.getString(R.string.brain_custom_bad_url))
            return
        }
        // The saved key belongs to the saved server. A new address gets only a key typed for it.
        val sameServer = BrainProviders.sameCustomServer(address, brain.customUrl)
        val useKey = rawKey.trim().ifBlank { savedKey?.takeIf { sameServer } ?: "" }
        status = Status.Checking
        scope.launch {
            try {
                val picked =
                    withContext(Dispatchers.IO) {
                        val want =
                            model?.takeIf { it.isNotBlank() } ?: run {
                                val list =
                                    try {
                                        BrainProviders.chatModels(BrainClient.listModels(server, useKey))
                                    } catch (_: BrainException) {
                                        // Some gateways do not list models; the field below is the way then.
                                        emptyList()
                                    }
                                BrainProviders.pickDefault(server, list)
                            } ?: throw BrainException(context.getString(R.string.brain_no_model))
                        BrainClient.probe(server, useKey, want)
                        want
                    }
                if (rawKey.isNotBlank()) {
                    KeyVault.save(context, BrainProviders.Custom.id, rawKey.trim())
                } else if (!sameServer) {
                    // Otherwise later requests would carry the old server's key to the new one.
                    KeyVault.clear(context, BrainProviders.Custom.id)
                }
                SettingsRepository.setCustomUrl(context, address)
                SettingsRepository.setBrainService(context, BrainProviders.Custom.id, picked)
                if (!customActive) SettingsRepository.setLowerEdit(context, TakeEdit.NONE)
                SettingsRepository.setBrainEnabled(context, true)
                key = ""
                status = Status.Ok(context.getString(R.string.brain_check_ok, server.name, picked))
            } catch (e: BrainException) {
                status = Status.Failed(e.message ?: "")
            } catch (e: Exception) {
                status = Status.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun disconnect() {
        KeyVault.clear(context, BrainProviders.Custom.id)
        scope.launch {
            SettingsRepository.forgetBrainService(context, BrainProviders.Custom.id)
            SettingsRepository.setBrainEnabled(context, false)
            SettingsRepository.setBrainService(context, null, null)
        }
        models = emptyList()
        status = Status.Idle
    }

    val server = BrainProviders.customAt(brain.customUrl)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
    ) {
        StatusCard(
            status = status,
            ready = customActive && brain.model != null,
            active = server?.takeIf { customActive },
            model = brain.model.takeIf { customActive },
        )

        BrainOptions(active = customActive, brain = brain, onEditPrompt = onEditPrompt, onOpenKeys = onOpenKeys)

        if (customActive && models.size > 1) {
            SectionTitle(R.string.brain_models)
            SettingsGroup(
                rows =
                    models.take(30).map { m ->
                        {
                            OneRow(
                                title = m,
                                modifier =
                                    Modifier.selectable(selected = m == brain.model, role = Role.RadioButton) {
                                        if (m != brain.model && status != Status.Checking) connect(brain.customUrl, "", m)
                                    },
                                trailing = { RadioButton(selected = m == brain.model, onClick = null) },
                            )
                        }
                    },
            )
        }

        SectionTitle(R.string.brain_tab_custom)
        val schemeOk = url.trim().startsWith("https://")
        Column(
            modifier =
                Modifier
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    if (status is Status.Failed) status = Status.Idle
                },
                label = { Text(stringResource(R.string.brain_custom_placeholder)) },
                leadingIcon = { Icon(Icons.Outlined.Dns, contentDescription = null) },
                singleLine = true,
                supportingText = { Text(stringResource(R.string.brain_custom_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = key,
                onValueChange = {
                    key = it
                    if (status is Status.Failed) status = Status.Idle
                },
                label = { Text(stringResource(R.string.brain_custom_key)) },
                leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                supportingText = {
                    Text(
                        stringResource(
                            if (savedKey != null && BrainProviders.sameCustomServer(url, brain.customUrl)) {
                                R.string.brain_custom_key_saved
                            } else {
                                R.string.brain_custom_key_hint
                            },
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = manualModel,
                onValueChange = {
                    manualModel = it
                    if (status is Status.Failed) status = Status.Idle
                },
                label = { Text(stringResource(R.string.brain_ollama_model_label)) },
                placeholder = { Text(stringResource(R.string.brain_ollama_model_placeholder)) },
                leadingIcon = { Icon(Icons.Outlined.Memory, contentDescription = null) },
                singleLine = true,
                supportingText = { Text(stringResource(R.string.brain_ollama_model_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { connect(url.trim().trimEnd('/'), key, manualModel.trim()) },
                enabled = schemeOk && url.isNotBlank() && status != Status.Checking,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(stringResource(R.string.brain_custom_connect))
            }
            if (customActive) {
                Button(
                    onClick = { disconnect() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(stringResource(R.string.brain_custom_disconnect))
                }
            }
            if (!schemeOk && url.isNotBlank()) {
                Text(
                    stringResource(R.string.brain_custom_bad_url),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = stringResource(R.string.brain_custom_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The second tab: connecting to an Ollama server running on a computer in the home network.
 * One address is the whole setup — no key, models come from the server itself.
 */
@Composable
private fun BrainLocalTab(
    brain: SettingsRepository.BrainSettings,
    ollamaActive: Boolean,
    onEditPrompt: (Brain.Kind) -> Unit,
    onOpenKeys: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(brain.ollamaUrl) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var manualModel by remember {
        mutableStateOf(
            brain.modelsByProvider[BrainProviders.Ollama.id]
                ?: brain.model.takeIf { brain.providerId == BrainProviders.Ollama.id }
                ?: "",
        )
    }
    var status by remember { mutableStateOf<Status>(Status.Idle) }
    val ollama = BrainProviders.Ollama

    LaunchedEffect(ollamaActive, brain.ollamaUrl) {
        if (!ollamaActive || brain.ollamaUrl.isBlank()) return@LaunchedEffect
        models = emptyList()
        models =
            withContext(Dispatchers.IO) {
                try {
                    BrainProviders.chatModels(BrainClient.listModels(BrainProviders.ollamaAt(brain.ollamaUrl), ""))
                } catch (_: Exception) {
                    emptyList()
                }
            }
    }

    fun connect(
        address: String,
        model: String? = null,
    ) {
        status = Status.Checking
        val server = BrainProviders.ollamaAt(address)
        scope.launch {
            try {
                val picked =
                    withContext(Dispatchers.IO) {
                        // A model typed by hand skips the server list entirely: the person
                        // knows better what is installed (e.g. a custom or pulled model).
                        if (!model.isNullOrBlank()) {
                            try {
                                BrainClient.probe(server, "", model)
                            } catch (e: BrainException) {
                                if (notInstalled(e)) {
                                    // The model name is wrong: show what the server actually has.
                                    val installed =
                                        try {
                                            BrainProviders.chatModels(BrainClient.listModels(server, ""))
                                        } catch (_: Exception) {
                                            emptyList()
                                        }
                                    val hint =
                                        if (installed.isEmpty()) {
                                            say(
                                                "выполните на нём: ollama pull $model (или ollama list, чтобы увидеть установленные)",
                                                "run there: ollama pull $model (or ollama list to see what is installed)",
                                            )
                                        } else {
                                            say("на сервере установлены: ", "installed on the server: ") +
                                                installed.take(8).joinToString(", ")
                                        }
                                    throw BrainException(
                                        say(
                                            "модель $model не отвечает на сервере: $hint",
                                            "model $model does not answer on the server: $hint",
                                        ),
                                    )
                                }
                                throw e
                            }
                            return@withContext model
                        }
                        // If the server gave no list, remember why: falling back to the
                        // default model name would only produce a misleading 404 later.
                        var listError: BrainException? = null
                        val list =
                            try {
                                BrainProviders.chatModels(BrainClient.listModels(server, ""))
                            } catch (e: BrainException) {
                                listError = e
                                emptyList()
                            }
                        val want =
                            if (list.isEmpty()) {
                                throw listError ?: BrainException(context.getString(R.string.brain_no_model))
                            } else {
                                BrainProviders.pickDefault(server, list)
                                    ?: throw BrainException(context.getString(R.string.brain_no_model))
                            }
                        try {
                            BrainClient.probe(server, "", want)
                        } catch (e: BrainException) {
                            throw if (list.size > 1 && notInstalled(e)) {
                                // The picked model is not actually usable; say it plainly.
                                BrainException(
                                    say(
                                        "модель $want не отвечает на сервере: установите её (ollama pull $want) или выберите другую",
                                        "model $want does not answer on the server: install it (ollama pull $want) or pick another one",
                                    ),
                                )
                            } else {
                                e
                            }
                        }
                        want
                    }
                SettingsRepository.setOllamaUrl(context, address)
                SettingsRepository.setBrainService(context, ollama.id, picked)
                if (!ollamaActive) SettingsRepository.setLowerEdit(context, TakeEdit.NONE)
                SettingsRepository.setBrainEnabled(context, true)
                status = Status.Ok(context.getString(R.string.brain_ollama_connected, address))
            } catch (e: BrainException) {
                status = Status.Failed(e.message ?: "")
            } catch (e: Exception) {
                status = Status.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
    ) {
        StatusCard(
            status = status,
            ready = ollamaActive && brain.model != null,
            active =
                ollama.takeIf {
                    ollamaActive
                },
            model = brain.model.takeIf { ollamaActive },
        )

        BrainOptions(active = ollamaActive, brain = brain, onEditPrompt = onEditPrompt, onOpenKeys = onOpenKeys)

        if (ollamaActive && models.isNotEmpty()) {
            SectionTitle(R.string.brain_models)
            SettingsGroup(
                rows =
                    models.take(30).map { m ->
                        {
                            OneRow(
                                title = m,
                                modifier =
                                    Modifier.selectable(selected = m == brain.model, role = Role.RadioButton) {
                                        if (m != brain.model && status != Status.Checking) connect(url, m)
                                    },
                                trailing = { RadioButton(selected = m == brain.model, onClick = null) },
                            )
                        }
                    },
            )
        } else if (ollamaActive && status is Status.Ok) {
            Text(
                text = stringResource(R.string.brain_ollama_no_models),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        SectionTitle(R.string.brain_tab_local)
        val schemeOk = url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
        Column(
            modifier =
                Modifier
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    if (status is Status.Failed) status = Status.Idle
                },
                label = { Text(stringResource(R.string.brain_ollama_placeholder)) },
                leadingIcon = { Icon(Icons.Outlined.Dns, contentDescription = null) },
                singleLine = true,
                supportingText = { Text(stringResource(R.string.brain_ollama_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = manualModel,
                onValueChange = {
                    manualModel = it
                    if (status is Status.Failed) status = Status.Idle
                },
                label = { Text(stringResource(R.string.brain_ollama_model_label)) },
                placeholder = { Text(stringResource(R.string.brain_ollama_model_placeholder)) },
                leadingIcon = { Icon(Icons.Outlined.Memory, contentDescription = null) },
                singleLine = true,
                supportingText = { Text(stringResource(R.string.brain_ollama_model_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val m = manualModel.trim()
                    connect(url.trim().trimEnd('/'), m.takeIf { it.isNotBlank() })
                },
                enabled = schemeOk && url.isNotBlank() && status != Status.Checking,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(stringResource(R.string.brain_ollama_connect))
            }
            if (ollamaActive) {
                Button(
                    onClick = {
                        scope.launch {
                            SettingsRepository.setBrainEnabled(context, false)
                            SettingsRepository.setBrainService(context, null, null)
                        }
                        status = Status.Idle
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(stringResource(R.string.brain_ollama_disconnect))
                }
            }
            if (!schemeOk && url.isNotBlank()) {
                Text(
                    stringResource(R.string.brain_ollama_bad_url),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = stringResource(R.string.brain_ollama_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusCard(
    status: Status,
    ready: Boolean,
    active: BrainProvider?,
    model: String?,
) {
    val (icon, title, text, error) =
        when (status) {
            is Status.Failed -> Quad(Icons.Outlined.ErrorOutline, stringResource(R.string.brain_status_failed), status.text, true)
            is Status.Ok -> Quad(Icons.Outlined.CheckCircle, stringResource(R.string.brain_status_ok), status.text, false)
            Status.Checking -> Quad(null, stringResource(R.string.brain_status_checking), "", false)
            Status.Idle ->
                if (ready) {
                    Quad(Icons.Outlined.CheckCircle, stringResource(R.string.brain_status_ready), "${active!!.name}, $model", false)
                } else {
                    Quad(
                        Icons.Outlined.Key,
                        stringResource(R.string.brain_status_none),
                        stringResource(R.string.brain_status_none_text),
                        false,
                    )
                }
        }
    val neutral = status == Status.Checking || (status == Status.Idle && !ready)
    val container = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface
    val content = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    // Blue only when something works; red on failure; grey while there is nothing to say yet.
    val mark =
        when {
            error -> content
            neutral -> MaterialTheme.colorScheme.outline
            else -> MaterialTheme.colorScheme.primary
        }
    Card(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = mark)
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = content)
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private data class Quad(
    val icon: ImageVector?,
    val title: String,
    val text: String,
    val error: Boolean,
)

@Composable
internal fun SwitchItem(
    title: Int,
    subtitle: Int,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    OneRow(
        title = stringResource(title),
        subtitle = stringResource(subtitle),
        modifier =
            Modifier
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
                // Waits for a connected service: shown, but faded.
                .alpha(if (enabled) 1f else 0.45f),
        trailing = { OneSwitch(checked) },
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
