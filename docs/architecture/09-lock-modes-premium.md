# 10. Lock Modes & Premium

## `LockPolicy.kt` — Pure evaluation logic

```kotlin
data class LockState(
    val activeLockMode: LockMode,           // NONE, PASSWORD, SECURITY_KEY, LOCKDOWN
    val passwordUnlocked: Boolean,
    val securityKeyUnlocked: Boolean,
    val lockdownType: LockdownType,          // ONE_TIME or RECURRING
    val lockdownEndTimeMillis: Long,
    val lockdownRecurringDays: List<Int>,
    val lockdownRecurringUsedKey: String?
)

data class LockDecision(
    val isLocked: Boolean,
    val shouldClearExpiredLockdown: Boolean,
    val consumeKey: String?                  // Set for recurring: marks day as consumed
)
```

**Lock modes:**

| Mode | Locked when | Unlocked when |
|------|-------------|---------------|
| NONE | Never | Always |
| PASSWORD | `!passwordUnlocked` | User enters correct password |
| SECURITY_KEY | `!securityKeyUnlocked` | User authenticates with USB key |
| LOCKDOWN (ONE_TIME) | `now < lockdownEndTimeMillis` | After end time (auto-clears) |
| LOCKDOWN (RECURRING) | Not on allowed day, or day already consumed | On allowed day, not yet consumed |

**Auto-relock:** PASSWORD and SECURITY_KEY auto-relock when user leaves MainActivity (if unlocked).

## Premium Features

Gated by `PremiumStore.isPremium(context)` (EncryptedSharedPreferences):
- Dark mode
- Custom color mode
- Floating time bubble
- Time range configuration
- Periodic reset
- Cumulative time
- Backup/restore
- Lockdown mode
- Group duplication

## `PremiumStore.kt` — Encrypted premium storage

Singleton that stores premium entitlement in `EncryptedSharedPreferences` (AES-256).
All premium reads/writes across the app go through `PremiumStore.isPremium(context)` and
`PremiumStore.setPremium(context, value)`.

**On first access:** migrates the legacy plaintext `"premium"` key from `groupPrefs` into
the encrypted file, then removes the plaintext key.

**Billing re-query:** `MainActivity.checkPendingPurchases()` calls `queryPurchasesAsync` on
each billing connection. If Google Play returns OK with no valid purchases (e.g. refund),
premium is revoked. If the query fails (offline), the local encrypted state is preserved.

### Cumulative Time — Reset & Midnight Carryover Rules

When `cumulativeTime = true` and `resetMinutes > 0`, unused time carries over across periodic
resets **within the same calendar day**:

1. **Intra-day periodic reset:** `newTimeRemaining = currentRemaining + missedCount × fullLimit`
   (e.g., 10 min left + 3 missed resets × 5 min limit = 25 min after catch-up).
2. **Missed-reset catch-up:** When the service is dormant (Doze, battery optimization, app kill)
   and multiple reset intervals elapse, `nextResetTime` is advanced along the interval grid
   (not jumped to `now + interval`). All missed intervals are credited in a single tick:
   `missedCount = ((now − nextResetTime) / intervalMs) + 1`.
3. **Midnight boundary:** If the previous reset occurred before today's start-of-day (00:00),
   carryover is suppressed. Only today's elapsed intervals count:
   `newTimeRemaining = todayResetCount × fullLimit`. Yesterday's accumulated time does not
   leak into the next day.
4. **Normalization:** `normalizeGroupForPersistence()` does **not** cap `timeRemaining` at
   `limitInMillis` when cumulative mode is active, so carried-over time survives group edits.
5. **`nextAddTime`:** Set to `nextResetTime` for cumulative periodic groups; used by UI to show
   "Next time addition" countdown. Set to `0L` for non-cumulative groups.
