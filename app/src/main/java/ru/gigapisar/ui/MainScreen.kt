package ru.gigapisar.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import ru.gigapisar.R
import ru.gigapisar.model.ModelManager
import ru.gigapisar.settings.InsertionMode
import ru.gigapisar.brain.Brain
import ru.gigapisar.settings.SettingsRepository

@Composable
fun MainScreen(activity: ComponentActivity) {
    val context = LocalContext.current
    var insertionMode by remember { mutableStateOf(InsertionMode.TEXT_FIELD) }
    var virtualButtonVisible by remember { mutableStateOf(true) }
    var volumeKeyEnabled by remember { mutableStateOf(true) }
    var sendKey by remember { mutableStateOf(false) }
    // The page a key's page was opened from, to go back to; and the instructions being read.
    var keyPageFrom by remember { mutableStateOf(SettingsPage.KEYS) }
    var editingPrompt by remember { mutableStateOf<Brain.Kind?>(null) }
    var vibrationEnabled by remember { mutableStateOf(true) }
    var fabGestures by remember { mutableStateOf(true) }
    var fabEdgeHandle by remember { mutableStateOf(false) }
    var settingsPage by remember { mutableStateOf(SettingsPage.MAIN) }
    var noClipboard by remember { mutableStateOf(false) }
    var simpleSyntax by remember { mutableStateOf(false) }
    var dropHesitations by remember { mutableStateOf(true) }
    var noFinalPeriod by remember { mutableStateOf(false) }
    var fabScale by remember { mutableStateOf(1f) }
    var fabHidden by remember { mutableStateOf<Set<String>>(emptySet()) }
    var brain by remember { mutableStateOf(SettingsRepository.BrainSettings()) }
    var page by rememberSaveable { mutableStateOf(Page.MAIN) }
    var modelInstalled by remember { mutableStateOf(ModelManager(context).isInstalled()) }
    var accessibilityEnabled by remember {
        mutableStateOf(isAccessibilityServiceEnabled(context))
    }
    var microphoneGranted by remember { mutableStateOf(isMicrophonePermissionGranted(context)) }
    var oemSetupDone by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            microphoneGranted = isGranted
        }
    val requestMicrophone = {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    // Google Play wants the service explained, and agreed to, before the user turns it on.
    var accessibilityDisclosure by remember { mutableStateOf(false) }
    val openAccessibilitySettings = {
        accessibilityDisclosure = true
    }

    LaunchedEffect(context) {
        SettingsRepository.insertionMode(context).collect { insertionMode = it }
    }
    LaunchedEffect(context) {
        SettingsRepository.virtualButtonVisible(context).collect { virtualButtonVisible = it }
    }
    LaunchedEffect(context) {
        SettingsRepository.volumeKeyEnabled(context).collect { volumeKeyEnabled = it }
    }
    LaunchedEffect(context) {
        SettingsRepository.decideSendKey(context)
        SettingsRepository.sendKey(context).collect { sendKey = it }
    }
    LaunchedEffect(context) {
        SettingsRepository.vibrationEnabled(context).collect { vibrationEnabled = it }
    }
    LaunchedEffect(context) {
        SettingsRepository.fabGestures(context).collect { fabGestures = it }
    }
    LaunchedEffect(context) {
        SettingsRepository.fabEdgeHandle(context).collect { fabEdgeHandle = it }
    }
    LaunchedEffect(context) {
        SettingsRepository.noClipboard(context).collect { noClipboard = it }
    }
    LaunchedEffect(context) {
        SettingsRepository.simpleSyntax(context).collect { simpleSyntax = it }
    }
    LaunchedEffect(context) {
        SettingsRepository.dropHesitations(context).collect { dropHesitations = it }
    }
    LaunchedEffect(context) {
        SettingsRepository.noFinalPeriod(context).collect { noFinalPeriod = it }
    }
    LaunchedEffect(context) {
        SettingsRepository.brain(context).collect { brain = it }
    }
    LaunchedEffect(context) {
        SettingsRepository.fabScale(context).collect { fabScale = it }
    }
    LaunchedEffect(context) {
        SettingsRepository.fabHiddenApps(context).collect { fabHidden = it }
    }
    LaunchedEffect(context) {
        SettingsRepository.oemSetupDone(context).collect { oemSetupDone = it }
    }
    val hiddenAppNames = remember(fabHidden) { appLabels(context, fabHidden) }

    LifecycleResumeEffect(context) {
        accessibilityEnabled = isAccessibilityServiceEnabled(context)
        microphoneGranted = isMicrophonePermissionGranted(context)
        modelInstalled = ModelManager(context).isInstalled()

        onPauseOrDispose {}
    }

    GigaPisarTheme {
        if (accessibilityDisclosure) {
            AccessibilityDisclosure(
                onAgree = {
                    accessibilityDisclosure = false
                    activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onDecline = { accessibilityDisclosure = false },
            )
        }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (page == Page.BRAIN) {
                BrainScreen(
                    brain = brain,
                    onBack = { page = Page.MAIN },
                    onOpenKeys = {
                        settingsPage = SettingsPage.KEYS
                        page = Page.MAIN
                    },
                )
                return@Surface
            }
            if (page == Page.HIDDEN_APPS) {
                HiddenAppsScreen(
                    hidden = fabHidden,
                    onChange = { apps -> scope.launch { SettingsRepository.setFabHiddenApps(context, apps) } },
                    onBack = { page = Page.MAIN },
                )
                return@Surface
            }
            editingPrompt?.let { kind ->
                BrainPromptEditor(kind = kind, custom = brain.prompts.of(kind), onClose = { editingPrompt = null })
                return@Surface
            }
            val brainConnected = brainReady(context, brain)
            val textField = insertionMode == InsertionMode.TEXT_FIELD
            val settings: @Composable (SettingsPage) -> Unit = { shown ->
                when (shown) {
                    SettingsPage.KEY_DOWN ->
                        LowerKeyPage(
                            brain = brain,
                            sendKey = sendKey,
                            textField = textField,
                            keyEnabled = volumeKeyEnabled,
                            onSendKey = { scope.launch { SettingsRepository.setSendKey(context, it) } },
                            onEditPrompt = { editingPrompt = it },
                            onOpenBrain = { page = Page.BRAIN },
                        )

                    SettingsPage.KEY_UP ->
                        UpperKeyPage(brain = brain, onEditPrompt = { editingPrompt = it }, onOpenBrain = { page = Page.BRAIN })

                    else -> Unit
                }
                SettingsList(
                    insertionMode = insertionMode,
                    virtualButtonVisible = virtualButtonVisible,
                    volumeKeyEnabled = volumeKeyEnabled,
                    vibrationEnabled = vibrationEnabled,
                    microphoneGranted = microphoneGranted,
                    accessibilityEnabled = accessibilityEnabled,
                    onInsertionMode = { scope.launch { SettingsRepository.setInsertionMode(context, it) } },
                    onVirtualButton = { scope.launch { SettingsRepository.setVirtualButtonVisible(context, it) } },
                    onVolumeKey = { scope.launch { SettingsRepository.setVolumeKeyEnabled(context, it) } },
                    lowerSummary = lowerKeySummary(brain, brainConnected, sendKey && textField && volumeKeyEnabled),
                    upperSummary = upperKeySummary(brain, brainConnected),
                    onVibration = { scope.launch { SettingsRepository.setVibrationEnabled(context, it) } },
                    noClipboard = noClipboard,
                    onNoClipboard = { scope.launch { SettingsRepository.setNoClipboard(context, it) } },
                    simpleSyntax = simpleSyntax,
                    onSimpleSyntax = { scope.launch { SettingsRepository.setSimpleSyntax(context, it) } },
                    dropHesitations = dropHesitations,
                    onDropHesitations = { scope.launch { SettingsRepository.setDropHesitations(context, it) } },
                    noFinalPeriod = noFinalPeriod,
                    onNoFinalPeriod = { scope.launch { SettingsRepository.setNoFinalPeriod(context, it) } },
                    fabScale = fabScale,
                    onFabScale = { scope.launch { SettingsRepository.setFabScale(context, it) } },
                    fabGestures = fabGestures,
                    onFabGestures = { scope.launch { SettingsRepository.setFabGestures(context, it) } },
                    fabEdgeHandle = fabEdgeHandle,
                    onFabEdgeHandle = { scope.launch { SettingsRepository.setFabEdgeHandle(context, it) } },
                    hiddenAppNames = hiddenAppNames,
                    onOpenHiddenApps = { page = Page.HIDDEN_APPS },
                    onRequestMicrophone = requestMicrophone,
                    onOpenAccessibilitySettings = openAccessibilitySettings,
                    brainSection = { BrainRow(brain = brain, onOpen = { page = Page.BRAIN }) },
                    onLanguageChanged = { activity.recreate() },
                    page = shown,
                    onOpen = {
                        if (it == SettingsPage.KEY_DOWN || it == SettingsPage.KEY_UP) keyPageFrom = settingsPage
                        settingsPage = it
                    },
                )
            }
            if (settingsPage != SettingsPage.MAIN) {
                // A page opened from the short list: its name, a way back, and its rows.
                val isKeyPage = settingsPage == SettingsPage.KEY_DOWN || settingsPage == SettingsPage.KEY_UP
                val goBack = { settingsPage = if (isKeyPage) keyPageFrom else SettingsPage.MAIN }
                BackHandler(onBack = goBack)
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .navigationBarsPadding()
                            .padding(bottom = 24.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
                    ) {
                        IconButton(onClick = goBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                        Text(
                            stringResource(
                                when (settingsPage) {
                                    SettingsPage.BUTTON -> R.string.floating_button
                                    SettingsPage.KEYS -> R.string.volume_key
                                    SettingsPage.KEY_DOWN ->
                                        if (keyPageFrom == SettingsPage.BUTTON) R.string.take_text_title else R.string.key_down_title
                                    SettingsPage.KEY_UP -> R.string.key_up_title
                                    SettingsPage.TEXT -> R.string.settings_text
                                    else -> R.string.section_about
                                },
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        )
                    }
                    settings(settingsPage)
                }
                return@Surface
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        // Before the scroll, so the list slides under a fixed edge, not under the clock.
                        .statusBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp),
            ) {
                Header()
                if (!(microphoneGranted && accessibilityEnabled && modelInstalled)) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SetupWizard(
                            microphoneGranted = microphoneGranted,
                            accessibilityEnabled = accessibilityEnabled,
                            oemSetupDone = oemSetupDone,
                            onRequestMicrophone = requestMicrophone,
                            onOpenAccessibilitySettings = openAccessibilitySettings,
                            onOemSetupDone = { scope.launch { SettingsRepository.setOemSetupDone(context, true) } },
                            onModelInstalled = { modelInstalled = true },
                        )
                    }
                    return@Column
                }
                UpdateBanner(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
                AskNotificationsOnce()
                ReadyCard(
                    sendKey = sendKey && insertionMode == InsertionMode.TEXT_FIELD,
                    volumeKeyEnabled = volumeKeyEnabled,
                    buttonEnabled = virtualButtonVisible,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                settings(SettingsPage.MAIN)
            }
        }
    }
}

/** App name with the version next to it, small and grey: handy when testers report bugs. */
@Composable
private fun Header() {
    val context = LocalContext.current
    val version =
        remember(context) {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (_: Exception) {
                null
            }
        }
    Row(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.alignByBaseline(),
        )
        version?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

/** The app's pages: the settings list, and the Brain and "hide in apps" pages opened from it. */
private enum class Page { MAIN, BRAIN, HIDDEN_APPS }

/**
 * Android 13+ shows notifications only with permission. Asked once, when the app is set
 * up: that is how a new version can be told about while Pisar is closed.
 */
@Composable
private fun AskNotificationsOnce() {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("giga_pisar_updates", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean("askedNotifications", false)) {
            prefs.edit().putBoolean("askedNotifications", true).apply()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
