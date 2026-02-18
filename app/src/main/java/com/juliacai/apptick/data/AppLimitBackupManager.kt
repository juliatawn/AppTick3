package com.juliacai.apptick.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.IOException

data class BackupAppSettings(
    val showTimeLeft: Boolean,
    val floatingBubbleEnabled: Boolean,
    val darkModeEnabled: Boolean,
    val customColorModeEnabled: Boolean,
    val customPrimaryColor: Int?,
    val customAccentColor: Int?,
    val customBackgroundColor: Int?,
    val customCardColor: Int?,
    val customIconColor: Int?,
    val appIconColorMode: String?
)

data class AppLimitBackup(
    val schemaVersion: Int,
    val exportedAtMillis: Long,
    val groups: List<AppLimitGroupEntity>,
    val appSettings: BackupAppSettings
)

object AppLimitBackupManager {
    private const val CURRENT_SCHEMA_VERSION = 1
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
        val schemaVersion = root.get("schemaVersion")?.asInt ?: 0
        if (schemaVersion !in 1..CURRENT_SCHEMA_VERSION) {
            throw IllegalArgumentException("Unsupported backup schema version: $schemaVersion")
        }

        val groupsType = object : TypeToken<List<AppLimitGroupEntity>>() {}.type
        val groups = gson.fromJson<List<AppLimitGroupEntity>>(
            root.get("groups"),
            groupsType
        ) ?: emptyList()

        val appSettingsObject = root.getAsJsonObject("appSettings")
        val legacyPreferencesObject = root.getAsJsonObject("preferences")
        val settingsObject = appSettingsObject ?: legacyPreferencesObject ?: JsonObject()
        val showTimeLeft = settingsObject.get("showTimeLeft")?.asBoolean
            ?: settingsObject.get(KEY_SHOW_TIME_LEFT)?.asBoolean
            ?: true
        val floatingBubbleEnabled = settingsObject.get("floatingBubbleEnabled")?.asBoolean
            ?: settingsObject.get(KEY_FLOATING_BUBBLE_ENABLED)?.asBoolean
            ?: false
        val exportedAtMillis = root.get("exportedAtMillis")?.asLong ?: 0L

        return AppLimitBackup(
            schemaVersion = schemaVersion,
            exportedAtMillis = exportedAtMillis,
            groups = groups,
            appSettings = BackupAppSettings(
                showTimeLeft = showTimeLeft,
                floatingBubbleEnabled = floatingBubbleEnabled,
                darkModeEnabled = settingsObject.get("darkModeEnabled")?.asBoolean
                    ?: settingsObject.get(KEY_DARK_MODE)?.asBoolean
                    ?: false,
                customColorModeEnabled = settingsObject.get("customColorModeEnabled")?.asBoolean
                    ?: settingsObject.get(KEY_CUSTOM_COLOR_MODE)?.asBoolean
                    ?: false,
                customPrimaryColor = settingsObject.get("customPrimaryColor")?.asInt
                    ?: settingsObject.get(KEY_CUSTOM_PRIMARY_COLOR)?.asInt,
                customAccentColor = settingsObject.get("customAccentColor")?.asInt
                    ?: settingsObject.get(KEY_CUSTOM_ACCENT_COLOR)?.asInt,
                customBackgroundColor = settingsObject.get("customBackgroundColor")?.asInt
                    ?: settingsObject.get(KEY_CUSTOM_BACKGROUND_COLOR)?.asInt,
                customCardColor = settingsObject.get("customCardColor")?.asInt
                    ?: settingsObject.get(KEY_CUSTOM_CARD_COLOR)?.asInt,
                customIconColor = settingsObject.get("customIconColor")?.asInt
                    ?: settingsObject.get(KEY_CUSTOM_ICON_COLOR)?.asInt,
                appIconColorMode = settingsObject.get("appIconColorMode")?.asString
                    ?: settingsObject.get(KEY_APP_ICON_COLOR_MODE)?.asString
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
            }
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
