# Testing Matrix

## Profiles

1. `ciHost` (fast host CI; no emulator required)
   - Command: `./gradlew ciHost`
   - Runs:
     - `:app:assembleDebug`
     - `:app:testDebugUnitTest`
     - `:app:assembleDebugAndroidTest`
   - Use for pull requests and quick validation.

2. `ciDevice` (full instrumentation verification)
   - Command: `./gradlew ciDevice`
   - Runs:
     - `:app:connectedDebugAndroidTest`
   - Requires at least one connected emulator/device.

## Recommended CI Strategy

1. Run `ciHost` on every GitLab merge request and branch pipeline.
2. Run `ciDevice` on main branch merges (or nightly) due to emulator cost/time.
3. Use GitLab runner tag `android-emulator` for `ciDevice` jobs.

## Local Workflow

1. Quick local check: `./gradlew ciHost`
2. Full local check (with emulator running): `./gradlew ciDevice`

## Common Failure Modes

1. `connectedDebugAndroidTest` fails with `No connected devices!`
   - Start an emulator or connect a device, then rerun `./gradlew ciDevice`.
2. Unit tests pass but instrumentation assembly fails
   - Rerun `./gradlew :app:assembleDebugAndroidTest --stacktrace` to isolate androidTest compile/runtime resource issues.
