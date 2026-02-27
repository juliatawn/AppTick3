package com.juliacai.apptick.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.google.gson.annotations.SerializedName
import com.juliacai.apptick.groups.TimeRange
import java.io.IOException

data class BackupAppSettings(
    @SerializedName(value = "showTimeLeft", alternate = ["a"])
    val showTimeLeft: Boolean,
    @SerializedName(value = "floatingBubbleEnabled", alternate = ["b"])
    val floatingBubbleEnabled: Boolean,
    @SerializedName(value = "darkModeEnabled", alternate = ["c"])
    val darkModeEnabled: Boolean,
    @SerializedName(value = "customColorModeEnabled", alternate = ["d"])
    val customColorModeEnabled: Boolean,
    @SerializedName(value = "customPrimaryColor", alternate = ["e"])
    val customPrimaryColor: Int?,
    @SerializedName(value = "customAccentColor", alternate = ["f"])
    val customAccentColor: Int?,
    @SerializedName(value = "customBackgroundColor", alternate = ["g"])
    val customBackgroundColor: Int?,
    @SerializedName(value = "customCardColor", alternate = ["h"])
    val customCardColor: Int?,
    @SerializedName(value = "customIconColor", alternate = ["i"])
    val customIconColor: Int?,
    @SerializedName(value = "appIconColorMode", alternate = ["j"])
    val appIconColorMode: String?,
    @SerializedName(value = "groupCardOrder", alternate = ["k"])
    val groupCardOrder: List<Long>? = null
)

data class AppLimitBackup(
    @SerializedName(value = "schemaVersion", alternate = ["a"])
    val schemaVersion: Int,
    @SerializedName(value = "exportedAtMillis", alternate = ["b"])
    val exportedAtMillis: Long,
    @SerializedName(value = "groups", alternate = ["c"])
    val groups: List<AppLimitGroupEntity>,
    @SerializedName(value = "appSettings", alternate = ["d"])
    val appSettings: BackupAppSettings
)

object AppLimitBackupManager {
    private const val CURRENT_SCHEMA_VERSION = 3
    private val gson = Gson()
    private const val KEY_SHOW_TIME_LEFT = "showTimeLeft"
    private const val KEY_FLOATING_BUBBLE_ENABLED = "floatingBubbleEnabled"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_CUSTOM_COLOR_MODE = "custom_color_mode"
    private const val KEY_CUSTOM_PRIMARY_COLOR = "custom_primary_color"
    private const val KEY_CUSTOM_ACCENT_COLOR = "custom_accent_color"
    private const val KEY_CUSTOM_BACKGROUND_COLOR = "custom_background_color"
    private const val KEY_CUSTOM_CARD_COLOR = "custom_card_color"
    private const val KEY_CUSTOM_ICON_COLOR = "custom_icon_color"
    private const val KEY_APP_ICON_COLOR_MODE = "app_icon_color_mode"
    private const val KEY_GROUP_CARD_ORDER = GroupCardOrderStore.KEY_GROUP_CARD_ORDER

    fun createBackup(
        groups: List<AppLimitGroupEntity>,
        appSettings: BackupAppSettings
    ): AppLimitBackup {
        val settingsOnlyGroups = groups.map { group ->
            group.copy(
                timeRemaining = 0L,
                nextResetTime = 0L,
                nextAddTime = 0L,
                perAppUsage = emptyList()
            )
        }
        return AppLimitBackup(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            exportedAtMillis = System.currentTimeMillis(),
            groups = settingsOnlyGroups,
            appSettings = appSettings
        )
    }

    fun toJson(backup: AppLimitBackup): String = gson.toJson(backup)

    fun fromJson(json: String): AppLimitBackup {
        val root = JsonParser.parseString(json).asJsonObject
        val modelParsedBackup = runCatching { gson.fromJson(json, AppLimitBackup::class.java) }.getOrNull()
        val schemaVersion = root.get("schemaVersion")?.asInt ?: modelParsedBackup?.schemaVersion ?: 0
        if (schemaVersion !in 1..CURRENT_SCHEMA_VERSION) {
            throw IllegalArgumentException("Unsupported backup schema version: $schemaVersion")
        }

        val groupsType = object : TypeToken<List<AppLimitGroupEntity>>() {}.type
        val rawGroups = if (root.has("groups")) {
            gson.fromJson<List<AppLimitGroupEntity>>(
                root.get("groups"),
                groupsType
            )
        } else {
            modelParsedBackup?.groups
        }
        val groups = rawGroups?.map { group ->
            val parsedRanges = runCatching { group.timeRanges }.getOrNull().orEmpty()
            val safeWeekDays = runCatching { group.weekDays }.getOrNull().orEmpty()
            val safeApps = runCatching { group.apps }.getOrNull().orEmpty()
            val safePerAppUsage = runCatching { group.perAppUsage }.getOrNull().orEmpty()
            val normalizedRanges = if (parsedRanges.isNotEmpty()) {
                parsedRanges
            } else if (group.useTimeRange) {
                listOf(
                    TimeRange(
                        startHour = group.startHour,
                        startMinute = group.startMinute,
                        endHour = group.endHour,
                        endMinute = group.endMinute
                    )
                )
            } else {
                emptyList()
            }
            if (schemaVersion < 3) {
                group.copy(
                    isExpanded = true,
                    weekDays = safeWeekDays,
                    apps = safeApps,
                    timeRanges = normalizedRanges,
                    perAppUsage = safePerAppUsage
                )
            } else {
                group.copy(
                    weekDays = safeWeekDays,
                    apps = safeApps,
                    timeRanges = normalizedRanges,
                    perAppUsage = safePerAppUsage
                )
            }
        } ?: emptyList()

        val appSettingsObject = root.getAsJsonObject("appSettings")
        val legacyPreferencesObject = root.getAsJsonObject("preferences")
        val modelSettings = modelParsedBackup?.appSettings
        val settingsObject = appSettingsObject ?: legacyPreferencesObject ?: JsonObject()
        val showTimeLeft = settingsObject.get("showTimeLeft")?.asBoolean
            ?: settingsObject.get(KEY_SHOW_TIME_LEFT)?.asBoolean
            ?: modelSettings?.showTimeLeft
            ?: true
        val floatingBubbleEnabled = settingsObject.get("floatingBubbleEnabled")?.asBoolean
            ?: settingsObject.get(KEY_FLOATING_BUBBLE_ENABLED)?.asBoolean
            ?: modelSettings?.floatingBubbleEnabled
            ?: false
        val exportedAtMillis = root.get("exportedAtMillis")?.asLong
            ?: modelParsedBackup?.exportedAtMillis
            ?: 0L

        return AppLimitBackup(
            schemaVersion = schemaVersion,
            exportedAtMillis = exportedAtMillis,
            groups = groups,
            appSettings = BackupAppSettings(
                showTimeLeft = showTimeLeft,
                floatingBubbleEnabled = floatingBubbleEnabled,
                darkModeEnabled = settingsObject.get("darkModeEnabled")?.asBoolean
                    ?: settingsObject.get(KEY_DARK_MODE)?.asBoolean
                    ?: modelSettings?.darkModeEnabled
                    ?: false,
                customColorModeEnabled = settingsObject.get("customColorModeEnabled")?.asBoolean
                    ?: settingsObject.get(KEY_CUSTOM_COLOR_MODE)?.asBoolean
                    ?: modelSettings?.customColorModeEnabled
                    ?: false,
                customPrimaryColor = settingsObject.get("customPrimaryColor")?.asInt
                    ?: settingsObject.get(KEY_CUSTOM_PRIMARY_COLOR)?.asInt
                    ?: modelSettings?.customPrimaryColor,
                customAccentColor = settingsObject.get("customAccentColor")?.asInt
                    ?: settingsObject.get(KEY_CUSTOM_ACCENT_COLOR)?.asInt
                    ?: modelSettings?.customAccentColor,
                customBackgroundColor = settingsObject.get("customBackgroundColor")?.asInt
                    ?: settingsObject.get(KEY_CUSTOM_BACKGROUND_COLOR)?.asInt
                    ?: modelSettings?.customBackgroundColor,
                customCardColor = settingsObject.get("customCardColor")?.asInt
                    ?: settingsObject.get(KEY_CUSTOM_CARD_COLOR)?.asInt
                    ?: modelSettings?.customCardColor,
                customIconColor = settingsObject.get("customIconColor")?.asInt
                    ?: settingsObject.get(KEY_CUSTOM_ICON_COLOR)?.asInt
                    ?: modelSettings?.customIconColor,
                appIconColorMode = settingsObject.get("appIconColorMode")?.asString
                    ?: settingsObject.get(KEY_APP_ICON_COLOR_MODE)?.asString
                    ?: modelSettings?.appIconColorMode,
                groupCardOrder = settingsObject.get("groupCardOrder")?.let { element ->
                    gson.fromJson<List<Long>>(element, object : TypeToken<List<Long>>() {}.type)
                } ?: settingsObject.get(KEY_GROUP_CARD_ORDER)?.let { element ->
                    gson.fromJson<List<Long>>(element, object : TypeToken<List<Long>>() {}.type)
                } ?: modelSettings?.groupCardOrder
            )
        )
    }

    fun collectAppSettings(prefs: SharedPreferences): BackupAppSettings {
        return BackupAppSettings(
            showTimeLeft = prefs.getBoolean(KEY_SHOW_TIME_LEFT, true),
            floatingBubbleEnabled = prefs.getBoolean(KEY_FLOATING_BUBBLE_ENABLED, false),
            darkModeEnabled = prefs.getBoolean(KEY_DARK_MODE, false),
            customColorModeEnabled = prefs.getBoolean(KEY_CUSTOM_COLOR_MODE, false),
            customPrimaryColor = if (prefs.contains(KEY_CUSTOM_PRIMARY_COLOR)) prefs.getInt(KEY_CUSTOM_PRIMARY_COLOR, 0) else null,
            customAccentColor = if (prefs.contains(KEY_CUSTOM_ACCENT_COLOR)) prefs.getInt(KEY_CUSTOM_ACCENT_COLOR, 0) else null,
            customBackgroundColor = if (prefs.contains(KEY_CUSTOM_BACKGROUND_COLOR)) prefs.getInt(KEY_CUSTOM_BACKGROUND_COLOR, 0) else null,
            customCardColor = if (prefs.contains(KEY_CUSTOM_CARD_COLOR)) prefs.getInt(KEY_CUSTOM_CARD_COLOR, 0) else null,
            customIconColor = if (prefs.contains(KEY_CUSTOM_ICON_COLOR)) prefs.getInt(KEY_CUSTOM_ICON_COLOR, 0) else null,
            appIconColorMode = if (prefs.contains(KEY_APP_ICON_COLOR_MODE)) {
                prefs.getString(KEY_APP_ICON_COLOR_MODE, "system")
            } else {
                null
            },
            groupCardOrder = GroupCardOrderStore.readOrder(prefs).takeIf { it.isNotEmpty() }
        )
    }

    fun applyAppSettings(prefs: SharedPreferences, settings: BackupAppSettings) {
        prefs.edit().apply {
            putBoolean(KEY_SHOW_TIME_LEFT, settings.showTimeLeft)
            putBoolean(KEY_FLOATING_BUBBLE_ENABLED, settings.floatingBubbleEnabled)
            putBoolean(KEY_DARK_MODE, settings.darkModeEnabled)
            putBoolean(KEY_CUSTOM_COLOR_MODE, settings.customColorModeEnabled)
            if (settings.customPrimaryColor != null) putInt(KEY_CUSTOM_PRIMARY_COLOR, settings.customPrimaryColor) else remove(KEY_CUSTOM_PRIMARY_COLOR)
            if (settings.customAccentColor != null) putInt(KEY_CUSTOM_ACCENT_COLOR, settings.customAccentColor) else remove(KEY_CUSTOM_ACCENT_COLOR)
            if (settings.customBackgroundColor != null) putInt(KEY_CUSTOM_BACKGROUND_COLOR, settings.customBackgroundColor) else remove(KEY_CUSTOM_BACKGROUND_COLOR)
            if (settings.customCardColor != null) putInt(KEY_CUSTOM_CARD_COLOR, settings.customCardColor) else remove(KEY_CUSTOM_CARD_COLOR)
            if (settings.customIconColor != null) putInt(KEY_CUSTOM_ICON_COLOR, settings.customIconColor) else remove(KEY_CUSTOM_ICON_COLOR)
            if (settings.appIconColorMode != null) putString(KEY_APP_ICON_COLOR_MODE, settings.appIconColorMode) else remove(KEY_APP_ICON_COLOR_MODE)
            if (settings.groupCardOrder != null) {
                putString(KEY_GROUP_CARD_ORDER, gson.toJson(settings.groupCardOrder))
            } else {
                remove(KEY_GROUP_CARD_ORDER)
            }
            apply()
        }
    }

    @Throws(IOException::class)
    fun writeBackupToUri(context: Context, uri: Uri, backup: AppLimitBackup) {
        val json = toJson(backup)
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.writer().use { writer ->
                writer.write(json)
            }
        } ?: throw IOException("Unable to open output stream.")
    }

    @Throws(IOException::class)
    fun readBackupFromUri(context: Context, uri: Uri): AppLimitBackup {
        val json = context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader().use { reader ->
                reader.readText()
            }
        } ?: throw IOException("Unable to open input stream.")

        return fromJson(json)
    }
}
