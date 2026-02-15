plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
}

tasks.register("ciHost") {
    group = "verification"
    description = "Host-side CI profile: compile + unit tests + androidTest APK assembly."
    dependsOn(
        ":app:assembleDebug",
        ":app:testDebugUnitTest",
        ":app:assembleDebugAndroidTest"
    )
}

tasks.register("ciDevice") {
    group = "verification"
    description = "Device/emulator CI profile: runs connected instrumentation tests."
    dependsOn(":app:connectedDebugAndroidTest")
}
