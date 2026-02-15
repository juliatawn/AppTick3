package com.juliacai.apptick.lockModes

data class SecurityKeySettings(
    val isEnabled: Boolean = false,
    val recoveryEmail: String? = null
)
