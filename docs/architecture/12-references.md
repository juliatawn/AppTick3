# 14. SharedPreferences Key Reference

Most keys stored in `"groupPrefs"` (Context.MODE_PRIVATE).
Premium entitlement is in `"apptick_secure_prefs"` (EncryptedSharedPreferences) — see `PremiumStore.kt`.

## App Settings
| Key | Type | Default | Purpose |
|-----|------|---------|---------|
| `dark_mode` | Boolean | false | Dark mode enabled |
| `custom_color_mode` | Boolean | false | Custom color mode |
| `custom_primary_color` | Int | — | Seed color for custom palette |
| `custom_accent_color` | Int | — | Accent color |
| `custom_background_color` | Int | — | Background color |
| `custom_card_color` | Int | — | Card color |
| `custom_icon_color` | Int | — | Icon color |
| `app_icon_color_mode` | String | — | Icon color mode |
| `showTimeLeft` | Boolean | true | Show time in notification |
| `floatingBubbleEnabled` | Boolean | false | Floating bubble (premium) |
| `storeLongTermUsageStats` | Boolean | true | Store per-app daily usage in local DB |
| `usageStatsBackfillDone` | Boolean | false | One-time backfill from Android completed |
| `group_card_order` | String(JSON) | — | Card order as JSON array of IDs |
| `screenOn` | Boolean | true | Screen state tracking |

## Lock Modes
| Key | Type | Default | Purpose |
|-----|------|---------|---------|
| `active_lock_mode` | String | "NONE" | NONE/PASSWORD/SECURITY_KEY/LOCKDOWN |
| `passUnlocked` | Boolean | false | Password mode: currently unlocked |
| `securityKeyUnlocked` | Boolean | false | Security key: currently unlocked |
| `lockdown_type` | String | "ONE_TIME" | ONE_TIME or RECURRING |
| `lockdown_end_time` | Long | 0 | One-time lockdown end timestamp |
| `lockdown_recurring_days` | String | "" | Comma-separated day numbers (1-7) |
| `lockdown_weekly_used_key` | String | null | Date key for consumed recurring window |
| `lockdown_prompt_after_unlock` | Boolean | — | Show relock prompt after expiry |
| `password` | String | — | Hashed password |
| `recovery_email` | String | — | Recovery email address |
| `force_recovery_email_setup` | Boolean | — | Force email setup on next unlock |

## Bubble State
| Key | Type | Default | Purpose |
|-----|------|---------|---------|
| `bubbleDismissed` | Boolean | false | User dismissed bubble |
| `floatingBubblePosX` | Int | — | Global bubble X position |
| `floatingBubblePosY` | Int | — | Global bubble Y position |
| `floatingBubblePosX_{pkg}` | Int | — | Per-app bubble X position |
| `floatingBubblePosY_{pkg}` | Int | — | Per-app bubble Y position |

## UI State
| Key | Type | Purpose |
|-----|------|---------|
| `batteryOemWarningDismissed` | Boolean | Battery warning dismissed |
| `groupDetailsHintDismissed` | Boolean | Group reorder hint dismissed permanently |
| `appsReorderedHintDismissed` | Boolean | App reorder hint dismissed permanently |
| `has_seen_launch_loading` | Boolean | Loading screen shown |
| `lastSeenChangelogVersionCode` | Long | Changelog version tracking |
| `lastSeenFeaturePhotosVersionCode` | Long | Carousel version tracking |

## Migration Flags
| Key | Type | Purpose |
|-----|------|---------|
| `legacy_app_name_repair_done_v1` | Boolean | App name repair completed |

---

# 15. Intent & Broadcast Reference

## Activities

| Activity | Intent Extras | Purpose |
|----------|--------------|---------|
| `BlockWindowActivity` | app_name, app_package, group_name, block_reason, app_time_spent, group_time_spent, time_limit_minutes, limit_each, use_time_range, block_outside_time_range, blocked_for_outside_range, next_reset_time | Block screen |
| `MainActivity` | EXTRA_EDIT_GROUP_ID (Long) | Edit existing group |
| `EnterPasswordActivity` | EXTRA_SETTINGS_SESSION_UNLOCK (Boolean) | Password entry |
| `EnterSecurityKeyActivity` | EXTRA_SETTINGS_SESSION_UNLOCK (Boolean) | Security key auth |

## Broadcasts

| Action | Sender | Receiver | Purpose |
|--------|--------|----------|---------|
| `ACTION_DISMISS_BLOCK` | BackgroundChecker | BlockWindowActivity | Dismiss block screen |
| `ACTION_SHOW_BUBBLE` | Notification button | BackgroundChecker.BubbleShowReceiver | Re-show dismissed bubble |
| `COLORS_CHANGED` | ThemeModeManager | BaseActivity | Recreate activity for theme change |
| `ACTION_SETTINGS_SESSION_UNLOCKED` | EnterPassword/SecurityKey | BackgroundChecker | Settings session unlocked |
| `ACTION_SERVICE_WATCHDOG` | AlarmManager | Receiver | Restart service if killed |

## System Broadcasts (Receiver.kt)

| Action | Handler |
|--------|---------|
| `ACTION_BOOT_COMPLETED` | Run migration, start service |
| `ACTION_MY_PACKAGE_REPLACED` | Run migration, start service |
| `ACTION_USER_PRESENT` | Start service |
| `ACTION_SCREEN_ON` | Start service |
| `ACTION_SCREEN_OFF` | Set screenOn=false |
