package ru.gigapisar.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.gigapisar.brain.Brain

private val Context.settingsDataStore by preferencesDataStore(
    name = "giga_pisar_settings",
)

/** What a dictation goes into the field as: the words as said, or the Brain's version of them. */
enum class TakeEdit { NONE, TIDY, SHORTEN, TRANSLATE }

/** What volume up does in a text field. */
enum class UpperKey {
    /** Records, the same as volume down. */
    RECORD,

    /** A short press right after a dictation swaps the text for the Brain's version, and back. */
    TIDY,
    SHORTEN,
    TRANSLATE,

    /** A short press puts back the words as said, when the dictation went in edited. */
    ORIGINAL,

    /** Left to the system: plain volume. */
    VOLUME,
    ;

    /** The edit this key asks the Brain for; null when it asks for none. */
    val edit: TakeEdit?
        get() =
            when (this) {
                TIDY -> TakeEdit.TIDY
                SHORTEN -> TakeEdit.SHORTEN
                TRANSLATE -> TakeEdit.TRANSLATE
                else -> null
            }
}

enum class InsertionMode {
    CLIPBOARD,
    TEXT_FIELD,
}

object SettingsRepository {
    private val insertionModeKey =
        stringPreferencesKey("insertion_mode")

    private val virtualButtonVisibleKey =
        booleanPreferencesKey("virtual_button_visible")

    private val volumeKeyEnabledKey =
        booleanPreferencesKey("volume_key_enabled")

    private val sendKeyKey =
        booleanPreferencesKey("send_key")

    private val keyModeKey =
        stringPreferencesKey("key_mode")

    private val keyBrainShortenKey =
        booleanPreferencesKey("key_brain_shorten")

    private val fabScaleKey =
        floatPreferencesKey("fab_scale")

    private val fabHiddenAppsKey =
        stringSetPreferencesKey("fab_hidden_apps")

    private val noClipboardKey =
        booleanPreferencesKey("no_clipboard")

    private val vibrationEnabledKey =
        booleanPreferencesKey("vibration_enabled")

    private val simpleSyntaxKey =
        booleanPreferencesKey("simple_syntax")

    private val fabGesturesKey =
        booleanPreferencesKey("fab_gestures")

    private val fabEdgeHandleKey =
        booleanPreferencesKey("fab_edge_handle")

    private val dropHesitationsKey =
        booleanPreferencesKey("drop_hesitations")

    private val noFinalPeriodKey =
        booleanPreferencesKey("no_final_period")

    private val oemSetupDoneKey =
        booleanPreferencesKey("oem_setup_done")

    private const val BRAIN_MODEL_PREFIX = "brain_model_for_"

    /** A page or two of rules is plenty; more only slows the model down. */
    const val BRAIN_PROMPT_MAX = 8000

    /** Before 0.2.16 one switch turned the whole Brain on; now each thing it does has its own. */
    private val brainEnabledKey = booleanPreferencesKey("brain_enabled")
    private val brainCommandsKey = booleanPreferencesKey("brain_commands")
    private val brainEveryTakeKey = booleanPreferencesKey("brain_every_take")
    private val brainShortenKey = booleanPreferencesKey("brain_shorten")
    private val brainTranslateLanguageKey = stringPreferencesKey("brain_translate_language")

    // The two keys set up one by one. Until a person touches them, they follow the older
    // settings ("every take", the key mode), so an update changes nothing by itself.
    private val lowerEditKey = stringPreferencesKey("lower_edit")
    private val upperKeyKey = stringPreferencesKey("upper_key")
    private val upperLanguageKey = stringPreferencesKey("upper_translate_language")
    private val brainSkipShortKey = booleanPreferencesKey("brain_skip_short")

    private fun brainPromptKey(kind: Brain.Kind) = stringPreferencesKey("brain_prompt_" + kind.name.lowercase())

    private val brainProviderKey = stringPreferencesKey("brain_provider")
    private val brainModelKey = stringPreferencesKey("brain_model")

    /** The address of a local Ollama server in the home network, like "http://192.168.1.10:11434". */
    private val ollamaUrlKey = stringPreferencesKey("ollama_url")

    /** The address of a service of one's own, like "https://api.example.com/v1". */
    private val customUrlKey = stringPreferencesKey("custom_url")

    fun ollamaUrl(context: Context): Flow<String> =
        context.settingsDataStore.data.map { preferences ->
            preferences[ollamaUrlKey] ?: ""
        }

    fun customUrl(context: Context): Flow<String> =
        context.settingsDataStore.data.map { preferences ->
            preferences[customUrlKey] ?: ""
        }

    private fun ollamaKey() = ollamaUrlKey

    suspend fun setOllamaUrl(
        context: Context,
        url: String,
    ) {
        context.settingsDataStore.edit { it[ollamaUrlKey] = url.trim().trimEnd('/') }
    }

    suspend fun setCustomUrl(
        context: Context,
        url: String,
    ) {
        context.settingsDataStore.edit { it[customUrlKey] = url.trim().trimEnd('/') }
    }

    fun insertionMode(context: Context): Flow<InsertionMode> =
        context.settingsDataStore.data.map { preferences ->
            when (
                preferences[insertionModeKey]
            ) {
                InsertionMode.CLIPBOARD.name -> InsertionMode.CLIPBOARD
                else -> InsertionMode.TEXT_FIELD
            }
        }

    fun virtualButtonVisible(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[virtualButtonVisibleKey] ?: true
        }

    /** Floating button size relative to the original, [FAB_SCALE_MIN]..[FAB_SCALE_MAX]. */
    fun fabScale(context: Context): Flow<Float> =
        context.settingsDataStore.data.map { preferences ->
            (preferences[fabScaleKey] ?: 1f).coerceIn(FAB_SCALE_MIN, FAB_SCALE_MAX)
        }

    suspend fun setFabScale(
        context: Context,
        scale: Float,
    ) {
        context.settingsDataStore.edit { it[fabScaleKey] = scale.coerceIn(FAB_SCALE_MIN, FAB_SCALE_MAX) }
    }

    /** Apps (package names) where the floating button stays hidden; the volume key still works there. */
    fun fabHiddenApps(context: Context): Flow<Set<String>> =
        context.settingsDataStore.data.map { preferences ->
            preferences[fabHiddenAppsKey] ?: emptySet()
        }

    suspend fun setFabHiddenApps(
        context: Context,
        packages: Set<String>,
    ) {
        context.settingsDataStore.edit { it[fabHiddenAppsKey] = packages }
    }

    /** 44 dp across at rest: the smallest the button gets. */
    const val FAB_SCALE_MIN = 1f

    /** 72 dp across at rest: a comfortable thumb target. */
    const val FAB_SCALE_MAX = 72f / 44f

    suspend fun setInsertionMode(
        context: Context,
        mode: InsertionMode,
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[insertionModeKey] = mode.name
        }
    }

    suspend fun setVirtualButtonVisible(
        context: Context,
        visible: Boolean,
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[virtualButtonVisibleKey] = visible
        }
    }

    fun volumeKeyEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[volumeKeyEnabledKey] ?: true
        }

    /** Volume up right after a dictation taps the messenger's "Send" button. */
    fun sendKey(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[sendKeyKey] ?: false
        }

    /**
     * Sending by key is on from the start for a new installation and stays off for a person
     * who installed before it became the default and never touched the switch. Decided once
     * and written down, so a later update does not read as "not new" and switch it off.
     */
    suspend fun decideSendKey(context: Context) {
        val fresh =
            try {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                info.firstInstallTime == info.lastUpdateTime
            } catch (_: Exception) {
                false
            }
        context.settingsDataStore.edit { if (it[sendKeyKey] == null) it[sendKeyKey] = fresh }
    }

    suspend fun setSendKey(
        context: Context,
        enabled: Boolean,
    ) {
        context.settingsDataStore.edit { it[sendKeyKey] = enabled }
    }

    /** The user has been through the phone-maker's own switches (restricted settings, autostart). */
    fun oemSetupDone(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[oemSetupDoneKey] ?: false
        }

    suspend fun setOemSetupDone(
        context: Context,
        done: Boolean,
    ) {
        context.settingsDataStore.edit { it[oemSetupDoneKey] = done }
    }

    /** Never paste through the clipboard: where a field does not take text directly, say so instead. */
    fun noClipboard(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[noClipboardKey] ?: false
        }

    suspend fun setNoClipboard(
        context: Context,
        enabled: Boolean,
    ) {
        context.settingsDataStore.edit { it[noClipboardKey] = enabled }
    }

    /** A single dictated sentence goes in lowercase and without the final period, like a chat line. */
    fun simpleSyntax(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[simpleSyntaxKey] ?: false
        }

    suspend fun setSimpleSyntax(
        context: Context,
        enabled: Boolean,
    ) {
        context.settingsDataStore.edit { it[simpleSyntaxKey] = enabled }
    }

    /** "Э-э", "а-а", "м-м" are cut out of every take, on the phone itself. */
    fun dropHesitations(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[dropHesitationsKey] ?: true
        }

    suspend fun setDropHesitations(
        context: Context,
        enabled: Boolean,
    ) {
        context.settingsDataStore.edit { it[dropHesitationsKey] = enabled }
    }

    /** The take goes in without its final period, however many sentences it has. */
    fun noFinalPeriod(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[noFinalPeriodKey] ?: false
        }

    suspend fun setNoFinalPeriod(
        context: Context,
        enabled: Boolean,
    ) {
        context.settingsDataStore.edit { it[noFinalPeriodKey] = enabled }
    }

    fun vibrationEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[vibrationEnabledKey] ?: true
        }

    suspend fun setVolumeKeyEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[volumeKeyEnabledKey] = enabled
        }
    }

    /** Slide up for hands-free and toward the middle to cancel, on the floating button. */
    fun fabGestures(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[fabGesturesKey] ?: true
        }

    suspend fun setFabGestures(
        context: Context,
        enabled: Boolean,
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[fabGesturesKey] = enabled
        }
    }

    /** The floating button tucked into a screen edge: a narrow strip until it is held. */
    fun fabEdgeHandle(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[fabEdgeHandleKey] ?: false
        }

    suspend fun setFabEdgeHandle(
        context: Context,
        enabled: Boolean,
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[fabEdgeHandleKey] = enabled
        }
    }

    suspend fun setVibrationEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[vibrationEnabledKey] = enabled
        }
    }

    /** Brain settings in one piece; the key itself lives in [ru.gigapisar.brain.KeyVault]. */
    data class BrainSettings(
        /** Commands by voice at the end of a take: "…Писарь, сделай короче". */
        val commands: Boolean = false,
        /** What every dictation goes in as, by volume down or by the floating button. */
        val lower: TakeEdit = TakeEdit.NONE,
        val lowerLanguage: String = Brain.DEFAULT_TRANSLATE_LANGUAGE,
        val upper: UpperKey = UpperKey.RECORD,
        val upperLanguage: String = Brain.DEFAULT_TRANSLATE_LANGUAGE,
        /** "Edit on the fly" leaves a short take alone: it goes in at once, with no request. */
        val skipShort: Boolean = true,
        /** The parts of the instructions a person rewrote; the core rules are not stored. */
        val prompts: Brain.Custom = Brain.Custom(),
        val providerId: String? = null,
        val model: String? = null,
        /** The address of the local Ollama server in the home network. */
        val ollamaUrl: String = "",
        /** The address of a service of one's own. */
        val customUrl: String = "",
        /** The last model chosen for each service, so switching services keeps each one's pick. */
        val modelsByProvider: Map<String, String> = emptyMap(),
    ) {
        /** Something is switched on for the Brain to do. */
        val inUse: Boolean get() = commands || lower != TakeEdit.NONE || upper.edit != null

        /**
         * The edit volume up makes of the words as said; null when it has none of its own and
         * so puts the words back: it is set to that, or to the very edit volume down makes.
         */
        val upperEdit: TakeEdit?
            get() {
                val edit = upper.edit ?: return null
                val same =
                    edit == lower &&
                        (edit != TakeEdit.TRANSLATE || lowerLanguage.trim().equals(upperLanguage.trim(), ignoreCase = true))
                return if (same) null else edit
            }
    }

    private fun language(stored: String?): String = stored?.trim()?.takeIf { it.isNotEmpty() } ?: Brain.DEFAULT_TRANSLATE_LANGUAGE

    private fun <T : Enum<T>> named(
        values: List<T>,
        name: String?,
    ): T? = values.firstOrNull { it.name == name }

    /** "Every take" of the versions before the keys were set up one by one. */
    private fun oldEveryTake(preferences: Preferences): Boolean =
        // The old switch was off: "edit on the fly" under it did nothing, and still must not.
        !switchedOffBefore(preferences) && (preferences[brainEveryTakeKey] ?: false)

    private fun lowerEdit(preferences: Preferences): TakeEdit =
        named(TakeEdit.entries, preferences[lowerEditKey]) ?: when {
            !oldEveryTake(preferences) -> TakeEdit.NONE
            preferences[brainShortenKey] == true -> TakeEdit.SHORTEN
            else -> TakeEdit.TIDY
        }

    private fun upperKey(preferences: Preferences): UpperKey =
        named(UpperKey.entries, preferences[upperKeyKey]) ?: when {
            // The mode "either key records": volume up went on recording, whatever the Brain did.
            preferences[keyModeKey] != "BRAIN" -> UpperKey.RECORD
            // Every take went in edited already, so the key has the words as said to put back.
            oldEveryTake(preferences) -> UpperKey.ORIGINAL
            preferences[keyBrainShortenKey] == true -> UpperKey.SHORTEN
            else -> UpperKey.TIDY
        }

    fun brain(context: Context): Flow<BrainSettings> =
        context.settingsDataStore.data.map { preferences ->
            BrainSettings(
                commands = preferences[brainCommandsKey] ?: (preferences[brainEnabledKey] ?: false),
                lower = lowerEdit(preferences),
                lowerLanguage = language(preferences[brainTranslateLanguageKey]),
                upper = upperKey(preferences),
                upperLanguage = language(preferences[upperLanguageKey] ?: preferences[brainTranslateLanguageKey]),
                skipShort = preferences[brainSkipShortKey] ?: true,
                prompts =
                    Brain.Custom(
                        command = preferences[brainPromptKey(Brain.Kind.COMMAND)],
                        tidy = preferences[brainPromptKey(Brain.Kind.TIDY)],
                        shorten = preferences[brainPromptKey(Brain.Kind.SHORTEN)],
                        translate = preferences[brainPromptKey(Brain.Kind.TRANSLATE)],
                    ),
                providerId = preferences[brainProviderKey],
                model = preferences[brainModelKey],
                ollamaUrl = preferences[ollamaKey()] ?: "",
                customUrl = preferences[customUrlKey] ?: "",
                modelsByProvider =
                    preferences
                        .asMap()
                        .filterKeys { it.name.startsWith(BRAIN_MODEL_PREFIX) }
                        .map { (k, v) -> k.name.removePrefix(BRAIN_MODEL_PREFIX) to v.toString() }
                        .toMap(),
            )
        }

    private fun switchedOffBefore(preferences: Preferences): Boolean =
        preferences[brainCommandsKey] == null && preferences[brainEnabledKey] == false

    /** Moves the old single switch over to the separate ones, once; nothing turns on by itself. */
    private fun splitOldSwitch(preferences: MutablePreferences) {
        if (preferences[brainCommandsKey] != null) return
        val wasOn = preferences[brainEnabledKey] ?: false
        if (!wasOn) preferences[brainEveryTakeKey] = false
        preferences[brainCommandsKey] = wasOn
    }

    /** A service was just connected (voice commands come on the first time) or the last one forgotten. */
    suspend fun setBrainEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        context.settingsDataStore.edit {
            if (enabled && it[brainCommandsKey] == null) {
                splitOldSwitch(it)
                it[brainCommandsKey] = true
            }
            it[brainEnabledKey] = enabled
        }
    }

    suspend fun setBrainCommands(
        context: Context,
        enabled: Boolean,
    ) {
        context.settingsDataStore.edit {
            splitOldSwitch(it)
            it[brainCommandsKey] = enabled
        }
    }

    /**
     * What every dictation goes in as. The other key is written down as it stands too: it
     * followed the older settings until now, and must not move because this one did.
     */
    suspend fun setLowerEdit(
        context: Context,
        edit: TakeEdit,
    ) {
        context.settingsDataStore.edit {
            it[upperKeyKey] = upperKey(it).name
            it[lowerEditKey] = edit.name
        }
    }

    suspend fun setUpperKey(
        context: Context,
        key: UpperKey,
    ) {
        context.settingsDataStore.edit {
            it[lowerEditKey] = lowerEdit(it).name
            it[upperKeyKey] = key.name
        }
    }

    suspend fun setLowerLanguage(
        context: Context,
        language: String,
    ) {
        context.settingsDataStore.edit {
            // Volume up shared this language until now: it keeps the one it had.
            if (it[upperLanguageKey] == null) it[upperLanguageKey] = language(it[brainTranslateLanguageKey])
            it[brainTranslateLanguageKey] = language
        }
    }

    suspend fun setUpperLanguage(
        context: Context,
        language: String,
    ) {
        context.settingsDataStore.edit { it[upperLanguageKey] = language }
    }

    suspend fun setBrainSkipShort(
        context: Context,
        enabled: Boolean,
    ) {
        context.settingsDataStore.edit { it[brainSkipShortKey] = enabled }
    }

    /** A rewritten body of the instructions; null or the standard text puts the standard one back. */
    suspend fun setBrainPrompt(
        context: Context,
        kind: Brain.Kind,
        body: String?,
    ) {
        val own = body?.trim()?.take(BRAIN_PROMPT_MAX)?.takeIf { it.isNotEmpty() && it != Brain.instructions(kind).body.trim() }
        context.settingsDataStore.edit { if (own == null) it.remove(brainPromptKey(kind)) else it[brainPromptKey(kind)] = own }
    }

    /** Forgets a service's saved model along with its key. */
    suspend fun forgetBrainService(
        context: Context,
        providerId: String,
    ) {
        context.settingsDataStore.edit { it.remove(stringPreferencesKey(BRAIN_MODEL_PREFIX + providerId)) }
    }

    /** A checked service: remembers where to send text and which model answers. */
    suspend fun setBrainService(
        context: Context,
        providerId: String?,
        model: String?,
    ) {
        context.settingsDataStore.edit { preferences ->
            if (providerId == null) preferences.remove(brainProviderKey) else preferences[brainProviderKey] = providerId
            if (model == null) preferences.remove(brainModelKey) else preferences[brainModelKey] = model
            if (providerId != null && model != null) preferences[stringPreferencesKey(BRAIN_MODEL_PREFIX + providerId)] = model
        }
    }
}
