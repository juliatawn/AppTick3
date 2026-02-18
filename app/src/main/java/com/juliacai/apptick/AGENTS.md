# AGENTS.md

ALWAYS BE SURE TO CHECK THIS FILE FOR GUIDELINES IN HOW TO DO THE CODE
## Code Preferences
- Use kotlin android sdk 36
- Use the best most modern practices for Android SDK 36 app development
- use jetpack compose android sdk 36
- Make sure it is using the best security practices
- Use the most efficient way and name things in a way that it is understandable
- keep The option setting 'android.disallowKotlinSourceSets=true'
- Always try to use the newest library when adding new code
- Always check the files and descriptions in this AGENTS.md file under this section "## ALL FILE NAMES AND PURPOSES" to make sure you either edit the right file, don't create a duplicate on accident, or need to add a new file
- Make sure if you see xml file used to check the res layout directory and convert it to jetpack compose if it doesn't already exist. And if you see icons or ic check the res/drawable directory and move to appropriate location. 
- When creating new files try to organize them to a logical location or even an existing folder inside of com.juliacai.apptick
- When making changes or updates be sure to go through all the files in com.juliacai.apptick to make sure there is no duplicated or redundant code
- Check res/drawable for existing icons to use before trying to use placeholders
- Make sure code is free of errors with a triple check on the file changed and possibly relevant files
- Each new file created or if a files functionality has changed then either add a line in this AGENTS.md file under ALL FILE NAMES AND PURPOSES or edit the existing line with the file name and short description of what the file does, to make it easy for you to know what files already exist or where things need to be changed
- Ensure test coverage is valid:
  - Fix failing unit/integration/instrumentation tests.
  - Add logical tests when missing.
  - Include main flows and edge cases.
- When appropriate have explanations for the user to understand the feature

## Test Creation
- Create a thorough suite of tests for validating functionality works, from integration tests to unit tests
- Make sure you do not create duplicate test cases on accident - such as going through each file in the test directory and ensuring there is no duplicates
- Make comments on the purpose of the test and flow of the test
- Add Preview compose test files as well, be sure to organize them into appropriate test folder locations

## App Flow Functionality
- First time the user installs the app then they open it, there is a loading page of the logo AppTick
- Then they quickly see a tab view of 3 asking for overlay permissions, after they turn that on they see the next tab for app usage stat permissions, then another tab for notification permissions
- Once that is all granted they are taken to the main home screen (MainActivity)
- Here it has a top bar that says AppTick which also has a settings button on it, and has a unlock icon on it, the main screen below that bar says "Add a new app limit group +" centered on the screen
- A floating action button with a + is available to press
- If they press that floating action button it opens up a list of apps they can either search or scroll through to select via checking the box on the right of the app, the top bar now instead says "Select Apps to Limit" (AppSelectScreen and AppSearchScreen)
- Then when they are done selecting they click the next arrow button that is also on the top bar, there is also a option to cancel
- Now this next page displays these options:
  - App Limit Group Name:
    - empty text box
  - hour and minute field with respective labels with radio buttons under for selecting "Limit for EACH" or "Limit for ALL"
  - an option that is on by default that lets you toggle the time limit option off or on - pops up a confirmation dialog on if they really want to set no time limit on the app
  - an option that is off by default that lets you set a time range the limit is active, it shows a start and end time. When you click on each of the selectors it brings up the clock selector option with am and pm options for each as well. If no option is set then by default the app limit is from 12am to 12am the next day.
    - Option for users where when they toggle Time Range on it shows an additional option buttons to either block apps compleletly when not in the time range OR to allow apps with no limits when outside the time range'
  - an option that is off by default that lets you set a reset interval with a min and hr text box options, this is so if someone for example sets 1hr 30min then the time limit will reset and start over (so as if they didnt use the app yet)
    - there is a checkbox option that lets you set cummulative time (it only displays this option if the reset interval is set), cummulative time means that the time limit will keep adding up each unused time to be carried over to the next reset interval, example my reset is 30mins from now, I have 5mins unused then when its 30mins from now I get those 5mins in addition to my regular time allotment)
  - the user can select days of the week they want this app limit group to be active as well - if none are selected it is counted as everyday selected but to the user it just shows as "Everyday" in the group details page and card on the m
  - then after filling out the page the user can press the "Save" button to save it as a app limit group or they can press "Cancel" 
  - If they save it the limit will immediately be active in a background thread (BackgroundChecker) according to the conditions they set and they will also be take back to the home page (MainActivity) where it will display the app limit group as a card with each app icon for the apps they chose to limit and a short summary of the options set such as the group name, hours active (24hrs if no range set, or time range is shown), limit for all or limit for each, days of the week its active, an on/off toggle, a edit button (which lets them edit this app limit or delete it)
  - If they turn off all the app limit groups the background checker wont run until one is turned on again - make the background checker check in a efficient way
  - If they use an app in the app limit group then the persistent notification will display the amount of time used for that app, and how much time left in hours and minutes, and when is the next reset time and day that the limit is reset at. 
  - When they reach the time limit set, then AppTick will do a fullscreen activity (BlockWindow) saying "Time's Up!" listing the App name and icon, the limit group name, AppTick name and logo really small at the bottom, and how much time was spent in that particular app, and how much time was spent for the whole group limit (should match the limit set).
  - If they try to get out of the activity and try to go back to the apps that are suppose to be now blocked due to the limit being reached, keep persistently quickly showing the blocking activity.
  - If the user chooses to click on a app limit group card they are then brought to a page (GroupPage an activity) that allows then to see all the  info on the limit settings they set for that app group (I.e Group name, limit for all, time amount etc.) as well as how much time spent on each app since the last time the limit was reset how much time they have left to use or if it is used up and next reset day and time, includes a back button on the top bar leading back to the main page

= The same notification information should bw available as a floating bubble that displays when the user is in an app that is time limited AND they have the "Floating TIme Left Bubble" setting on set on in the Apptick SEttings page
  - This bubble is  readable but not too intrusive or large, and possibly slightly see through if you think that would make it less intrusive and still be readable 
    and the user can chose to dismiss it from being displayed (drag to X at bottom of screen like how bubbles in android os normally get removed) then a toast shows up notifyinf them they can click on the AppTick notification in the notification window to bring up the bubble agin
## Premium Mode Features and Options
- Users can purchase premium mode
- If they have the free mode a unlocked icon is displayed at the top bar, if they press it it will popup the activity to purchase premium mode to get features such as lockdown mode, password mode, security key mode, custom color theme:
  - Lockdown mode: except for the phone and messaging app and AppTick (for safety reasons), you can select apps to be unable to change the time limit until a certain date you set, OR you can also select an option where it gives you a chance to change the limit during weekdays picked 
  (ex. I can set to be able to change each group limit on 2/13/26 then It will prompt the user to lockdown the app limit groups immediately after the change or it will stay unlocked for the rest of the day OR I can set the be able to change the limit on Tuesday and Thursday- so use the same Active Days days of week picker as the set time limits page, but same thing once its been changed it will get prompted to lockdown the app limits so they cant be updated OR it will just lock at the end of the day unless the next day is also set as a day they can edit the limits)
  - Password mode: lock ability to change time limits with a password
  - Security key mode: lock ability to change time limits with a security key
  - These 3 lock modes CANNOT be set at the same time, so if one lockmode is active then the other 2 lockmode pages are disabled and say (Disable <list whatever lockmode is active> to use this feature)
  - Each lock mode when set has a START MODE button or CANCEL button at the bottom of the page.
  - If the user sets up a lockmode, then when they reenter that page it will show all their settings except it wont show the password if one is set, it will just give then the option to set a new password with a new password button.
    - Like if the lockdown mode is set it will show the date and time they will next be able to change it and if they can currently change it, along with being able to edit that date and time (or day(s) of week if they chose that option).
  - When Password mode OR Security key mode are active, the user must password or security key to edit the app limit groups (so the edit buttons all have lock icons on them instead of the edit button when this is active), they also need the password/key to enter the lock modes page.
  - When Lockdown mode is active the user can only change the app limit groups if the date and time they set to be able to change it has passed, or they have the reoccuring option where it will give them the option to change it once a day/week during the time window range they set.
  - If any of the previous lock modes are active the unlock icon shows a locked icon instead, and all the limits pause and edit buttons are replaced with a lock icon, if it is password or security key mode then if you click the lock icon it lets you enter the password (or click reset password via email) or security key (or click reset security key via email)
  - Custom color theme: allow the user to set the color of the text (includes icons that represent edit, lock, etc.) AND the color of the cards with a color picker (the circle one with any color option you want along with ability to input hex code), then also be able to toggle dark mode or light mode like the background is black or white, also the BlockWindow screen will show the custom color set, and make it option that the app icon color can also be set to any color or make it automatically match the ANdroid os theme color
  - Premium mode cost is equivalent to USD $4.99 but convert based on locale, and also mention supporting the developer


## Safety features
- Do not allow users to lock phone and or messaging app for safety reasons
- If using password mode or security key mode, allow user option to reset password/security key via email that they set when they set up the password or security key mode (Firebase?)
- IF using Lockdown, Password, or Security key modes then give user a checkbox option to lock their settings app from being opened (password or security key input if thats the mode or just normal block screen if not the mode and time is up) that blocks the user from being able to delete AppTick, so app tick is set with Admin permissions


## Agent testing
- Find abd with this info for abd testing:
source ~/.zshrc
adb version

Android Debug Bridge version 1.0.41
Version 36.0.2-14143358
Installed as /Users/juliatawn/Library/Android/sdk/platform-tools/adb
Running on Darwin 25.3.0 (arm64)


## ALL FILE NAMES AND PURPOSES
- **com.juliacai.apptick**
    - `AGENTS.md`: Contains instructions and guidelines for the AI agent.
    - `AppInfo.kt`: Data class representing an application, including its name, package, icon, and usage statistics.
    - `AppLaunchLoadingScreen.kt`: Compose loading screen that shows the AppTick logo on first launch before permission onboarding.
    - `AppTheme.kt`: Manages the app's theme via an `object AppTheme` (SharedPreferences-backed color helpers used by Activities) and a `@Composable fun AppTheme` (MaterialTheme wrapper for Compose UI), consolidated from the former `AppTickTheme.kt`.
    - `BaseActivity.kt`: A base class for activities that handles shared functionality like theme and color changes.
    - `CustomViewPager.kt`: A custom ViewPager that allows for disabling swipe gestures.
    - `DateTimePickerDialog.kt`: A dialog for picking a date and time.
    - `LockPolicy.kt`: Pure lock-evaluation rules for password/security-key/lockdown logic, including weekly one-time unlock windows.
    - `MainActivity.kt`: The main activity of the application, responsible for checking permissions, setting up billing, displaying the main UI, and handling edit-group deep links into SetTimeLimitsScreen.
    - `MainScreen.kt`: The main UI of the app, built with Jetpack Compose. It displays the top app bar, floating action button, battery-reliability warning banner when optimization is restrictive, and the list of app limit groups.
    - `MainViewModel.kt`: The ViewModel for the MainActivity, responsible for managing app-limit data, premium state, and pause/resume service lifecycle behavior.
    - `Receiver.kt`: A BroadcastReceiver that handles boot/unlock/screen/package-update events plus a watchdog alarm broadcast to keep `BackgroundChecker` running whenever limits or settings-protection require enforcement.
    - `SettingsScreen.kt`: A composable that displays app settings (theme, notifications, premium controls), includes a `Battery Reliability` button that opens a dialog with quick links to battery settings/status refresh, and includes Backup/Restore actions for app-limit settings via JSON file export/import.
    - `TimeFormatting.kt`: Shared clock-time formatting utilities that render times using the device locale and 12/24-hour preference.
    - `TimeManager.kt`: Manages time-related calculations and formatting.
- **appLimit**
    - `AppLimitDetailsScreen.kt`: A composable that displays the details of an app limit group.
    - `AppLimitScreen.kt`: A composable that displays the app limit screen.
    - `AppLimitSettings.kt`: A data class for the app limit settings.
    - `AppUsageItem.kt`: A data class that represents an item of app usage.
    - `AppUsageRow.kt`: A composable that displays a row of app usage information.
- **backgroundProcesses**
    - `BackgroundChecker.kt`: A foreground service that monitors app usage with elapsed-time accounting, enforces time limits, and blocks Settings uninstall access when lock-mode uninstall protection is enabled (with deterministic test hooks for instrumentation reliability). Includes watchdog alarm scheduling/cancellation APIs so service state stays aligned with active limits/settings-protection across OEM process kills. Supports time-range outside-window behavior per group (either block apps fully outside the range or allow no limits outside range). Notification/floating-bubble selection only considers currently enforceable profiles (active schedule + positive time limit), picks the lowest effective time remaining, and notes when multiple active profiles cover the same app. Also manages the floating bubble overlay lifecycle.
    - `FloatingBubbleService.kt`: An overlay service that shows a small, semi-transparent draggable bubble with time remaining when the user is in a time-limited app. Drag-to-bottom dismiss target pattern. Controlled by the "floatingBubbleEnabled" preference and re-shown via the AppTick notification action.
- **block**
    - `BlockWindowActivity.kt`: An activity that hosts the BlockWindowScreen composable.
    - `BlockWindowScreen.kt`: A composable that displays the screen that blocks the user from using an app when the time limit is reached.
- **data**
    - `AppLimitGroupDao.kt`: The DAO for the AppLimitGroupEntity, providing methods for accessing the database.
    - `AppLimitGroupEntity.kt`: The Room entity for the AppLimitGroup.
    - `AppTickDatabase.kt`: The main Room database class for the application, defining the database configuration, entities, and providing access to the DAOs.
    - `AppLimitBackupManager.kt`: Serializes/deserializes backup JSON for app-limit configurations plus AppTick UI settings (theme/colors/notification-bubble options) and handles reading/writing backup files through SAF Uris.
    - `Converters.kt`: A Room type converter class that handles the conversion of complex data types, such as lists of integers and AppInGroup objects, into a format that can be stored in the database.
    - `LegacyDataMigrator.kt`: Handles the migration of data from a legacy database schema.
    - `Mapper.kt`: Contains extension functions that handle the mapping between the AppLimitGroup domain model and the AppLimitGroupEntity database entity.
- **deviceApps**
    - `AppListViewModel.kt`: The ViewModel for the AppSearchActivity, responsible for managing the list of apps.
    - `AppManager.kt`: Provides the installed app list used for selection while filtering safety-critical phone and messaging apps from being limited.
    - `AppSearchActivity.kt`: An activity that hosts the AppSearchScreen composable, which allows users to search for and select apps.
    - `AppUsageStats.kt`: An object that provides functions for querying app usage statistics.
    - `GroupPage.kt`: Activity and composable that show group details with a card-focused layout, a scroll-triggered compact sticky summary header (group name + time left/used), a consistent device-preference time-range display, and a FAB options dialog (Edit/Delete) that routes to the existing edit flow.
- **groups**
    - `AppLimitGroups.kt`: A composable that displays a list of app limit groups and switches action behavior between edit/pause and lock-unlock callbacks when lock mode is active.
    - `AppInGroup.kt`: A data class that represents an app within an app limit group.
    - `AppLimitGroupDao.kt`: The DAO for the AppLimitGroupEntity, providing methods for accessing the database.
    - `AppLimitGroupEntity.kt`: The Room entity for the AppLimitGroup.
    - `AppLimitGroupItem.kt`: A composable that displays an app limit group, replacing pause/edit actions with lock actions when group editing is locked, and showing time ranges using the device 12/24-hour preference.
    - `GroupAppItem.kt`: A composable that displays a single app within an app limit group, showing its icon, name, and a progress bar representing the time used.
    - `AppLimitGroupItem.kt`: A composable that displays an app limit group.
    - `AppLimitGroupAdapter.kt`: A RecyclerView adapter for displaying app limit groups.
    - `AppLimitGroup.kt`: A data class that represents an app limit group, containing all the settings for the group.
- **lockModes**
    - `EnterPasswordActivity.kt`: An activity that hosts the EnterPasswordScreen composable.
    - `EnterPasswordScreen.kt`: A composable that provides the UI for entering a password, with options to use biometric or USB key authentication, and to reset the password.
    - `EnterSecurityKeyActivity.kt`: An activity that handles authentication with a security key.
    - `PasswordResetActivity.kt`: An activity that hosts the PasswordResetScreen composable.
    - `PasswordResetScreen.kt`: A composable for resetting the password.
    - `RecoveryEmailSetupActivity.kt`: An activity that hosts the RecoveryEmailSetupScreen composable.
    - `RecoveryEmailSetupScreen.kt`: A composable for setting up a recovery email.
    - `SecurityKeySettings.kt`: A data class for the security key settings.
    - `SecurityKeySettingsScreen.kt`: A composable that provides the UI for setting a security key, including optional Device Admin-backed uninstall protection.
    - `SetPassword.kt`: An activity that hosts the SetPasswordScreen composable, allowing users to set a password to lock the app's settings.
    - `SetPasswordScreen.kt`: A composable that provides the UI for setting a password, including input fields for the password and confirmation, a recovery email, and optional Device Admin-backed uninstall protection.
- **newAppLimit**
    - `AppLimitViewModel.kt`: ViewModel used by app-selection and time-limit setup flows to persist groups and ensure service state matches active groups.
    - `AppSearchScreen.kt`: A composable that provides a search bar for filtering a list of applications.
    - `AppSelectScreen.kt`: A composable that allows the user to select apps to limit.
    - `SetTimeLimitsScreen.kt`: A composable that allows the user to configure the time limits for an app limit group, with daily-only periodic reset (hour/minute interval) and cumulative-time controls shown only when periodic reset is enabled, plus an outside-time-range behavior selector (block apps vs allow no limits) and device-preference time rendering in range controls.
- **permissions**
    - `PermissionOnboardingScreen.kt`: A single composable that presents all 3 required permissions (Overlay, Usage Stats, Notifications) as steps in a unified onboarding flow with animated transitions, progress dots, and auto-advance on grant.
    - `BatteryOptimizationHelper.kt`: Helper for checking battery/background restriction status and opening app/general battery optimization settings with fallbacks across Android devices/OEM skins.
- **premiumMode**
    - `LockdownModeActivity.kt`: An activity that hosts the LockdownModeScreen composable.
    - `LockdownModeScreen.kt`: A composable that allows the user to configure the "Lockdown Mode" premium feature, including optional Device Admin-backed uninstall protection.
    - `LockModesBlockedScreen.kt`: A composable block page shown when lockdown prevents changing lock settings, with guidance on when settings can be changed.
    - `LockdownSettings.kt`: A data class that defines the settings for the "Lockdown Mode" premium feature.
    - `LockdownTimeActivity.kt`: An activity that hosts the LockdownTimeScreen composable.
    - `LockdownTimeScreen.kt`: A composable that allows the user to set a lockdown time.
    - `PremiumModeScreen.kt`: A composable that displays premium purchase for free users and the Lock Modes configuration page for premium users.
- **settings**
    - `ColorPickerScreen.kt`: A composable color customization screen that mirrors the current app theme mode (default dark/light or custom), keeps an icon "Match System Theme" toggle, and provides quick-pick swatches plus a spectrum wheel and brightness control for text/background/icon colors.
- **androidTest/settings**
    - `ColorPickerScreenTest.kt`: Instrumentation tests that verify color wheel updates are isolated to the active tab target (text/background/icon), and that text-color edits do not mutate icon preview color in custom icon mode.
- **androidTest**
    - `MainActivityLockModesIntentTest.kt`: Activity-level instrumentation tests that verify locked top-bar lock-mode icon behavior routes to password/security-key unlock activities or the lockdown blocked screen.
- **test/backgroundProcesses**
    - `NotificationGroupSelectionTest.kt`: Unit tests for `pickNotificationGroup()` active profile selection logic and `formatGroupNotificationText()` formatting, covering single/multiple/paused profiles and limitEach ranking.
