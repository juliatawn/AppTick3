package com.juliacai.apptick.lockModes

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.juliacai.apptick.AppTheme
import com.juliacai.apptick.MainActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit

class PasswordResetActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private var resetMode = "password"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
        resetMode = intent.getStringExtra("reset_mode") ?: "password"

        AppTheme.applyTheme(this)

        // Check if we were opened from a recovery email link
        if (RecoveryEmailHelper.isRecoveryLink(intent)) {
            handleRecoveryLink(intent)
        } else {
            showResetScreen()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (RecoveryEmailHelper.isRecoveryLink(intent)) {
            handleRecoveryLink(intent)
        }
    }

    private fun handleRecoveryLink(intent: Intent) {
        RecoveryEmailHelper.verifyRecoveryLink(
            context = this,
            intent = intent,
            onSuccess = { verifiedEmail, purpose ->
                when (purpose) {
                    RecoveryEmailHelper.PURPOSE_SETUP_PASSWORD -> {
                        prefs.edit {
                            putString("recovery_email", verifiedEmail)
                            putBoolean("recovery_email_password_verified", true)
                        }
                        showSetupVerificationSuccessScreen(
                            modeName = "Password",
                            verifiedEmail = verifiedEmail,
                            purpose = purpose
                        )
                    }

                    RecoveryEmailHelper.PURPOSE_SETUP_SECURITY_KEY -> {
                        prefs.edit {
                            putString("recovery_email_security_key", verifiedEmail)
                            putBoolean("recovery_email_security_key_verified", true)
                        }
                        showSetupVerificationSuccessScreen(
                            modeName = "Security key",
                            verifiedEmail = verifiedEmail,
                            purpose = purpose
                        )
                    }

                    else -> {
                        performReset()
                        setContent {
                            AppTheme {
                                ResetSuccessScreen(
                                    resetMode = resetMode,
                                    onDone = { navigateToPostLinkDestination(purpose) }
                                )
                            }
                        }
                    }
                }
            },
            onError = { error ->
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                showResetScreen()
            }
        )
    }

    private fun performReset() {
        val isSecurityKeyReset = resetMode == "security_key"

        if (isSecurityKeyReset) {
            prefs.edit {
                remove("security_key_value")
                remove("security_usb_key_fingerprint")
                remove("recovery_email_security_key")
                putBoolean("security_key_enabled", false)
                putBoolean("securityKeyUnlocked", false)
            }
        } else {
            prefs.edit {
                remove("password")
                putBoolean("locked", false)
                putBoolean("passUnlocked", false)
            }
        }
    }

    private fun showResetScreen() {
        setContent {
            AppTheme {
                PasswordResetScreen(
                    prefs = prefs,
                    resetMode = resetMode,
                    onPasswordReset = { finish() }
                )
            }
        }
    }

    private fun showSetupVerificationSuccessScreen(
        modeName: String,
        verifiedEmail: String,
        purpose: String?
    ) {
        setContent {
            AppTheme {
                SetupVerificationSuccessScreen(
                    modeName = modeName,
                    verifiedEmail = verifiedEmail,
                    onDone = { navigateToPostLinkDestination(purpose) }
                )
            }
        }
    }

    @VisibleForTesting
    internal fun navigateAfterSuccessForTest(purpose: String?) {
        navigateToPostLinkDestination(purpose)
    }

    private fun navigateToPostLinkDestination(purpose: String?) {
        val destination = resolvePostLinkDestination(purpose = purpose, resetMode = resetMode)
        val destinationIntent = when (destination) {
            PostLinkDestination.SET_PASSWORD -> Intent(this, SetPassword::class.java)
            PostLinkDestination.LOCK_MODES -> Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_LOCK_MODES, true)
            }
            PostLinkDestination.ENTER_SECURITY_KEY -> Intent(this, EnterSecurityKeyActivity::class.java)
            PostLinkDestination.ENTER_PASSWORD -> Intent(this, EnterPasswordActivity::class.java)
        }

        destinationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(destinationIntent)
        finish()
    }
}

internal enum class PostLinkDestination {
    SET_PASSWORD,
    LOCK_MODES,
    ENTER_PASSWORD,
    ENTER_SECURITY_KEY
}

internal fun resolvePostLinkDestination(purpose: String?, resetMode: String): PostLinkDestination {
    return when (purpose) {
        RecoveryEmailHelper.PURPOSE_SETUP_PASSWORD -> PostLinkDestination.SET_PASSWORD
        RecoveryEmailHelper.PURPOSE_SETUP_SECURITY_KEY -> PostLinkDestination.LOCK_MODES
        RecoveryEmailHelper.PURPOSE_RESET_SECURITY_KEY -> PostLinkDestination.ENTER_SECURITY_KEY
        RecoveryEmailHelper.PURPOSE_RESET_PASSWORD -> PostLinkDestination.ENTER_PASSWORD
        else -> if (resetMode == "security_key") {
            PostLinkDestination.ENTER_SECURITY_KEY
        } else {
            PostLinkDestination.ENTER_PASSWORD
        }
    }
}

@Composable
fun PasswordResetScreen(
    prefs: SharedPreferences,
    resetMode: String,
    onPasswordReset: () -> Unit
) {
    val isSecurityKeyReset = resetMode == "security_key"
    val title = if (isSecurityKeyReset) "Reset Security Key" else "Reset Password"

    var email by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var linkSent by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val recoveryEmail = if (isSecurityKeyReset) {
        prefs.getString("recovery_email_security_key", null)
    } else {
        prefs.getString("recovery_email", null)
    }
    val isRecoveryEmailVerified = if (isSecurityKeyReset) {
        prefs.getBoolean("recovery_email_security_key_verified", false)
    } else {
        prefs.getBoolean("recovery_email_password_verified", false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedContent(targetState = linkSent, label = "reset_step") { sent ->
            if (!sent) {
                // Step 1: Enter email and send link
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (!RecoveryResetPolicy.hasConfiguredRecoveryEmail(recoveryEmail)) {
                        Text(
                            text = "No recovery email has been set up. Please set one in Lock Mode settings first.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (!RecoveryResetPolicy.canStartResetFlow(recoveryEmail, isRecoveryEmailVerified)) {
                        Text(
                            text = "Recovery email is not verified. Verify it in Lock Mode settings before resetting.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            text = "Enter your recovery email address. We'll send a link to verify your identity.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Recovery Email") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        errorMessage?.let { err ->
                            Text(
                                text = err,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                if (email.isBlank()) {
                                    errorMessage = "Please enter your email"
                                    return@Button
                                }

                                if (!RecoveryResetPolicy.canSendResetLink(email, recoveryEmail, isRecoveryEmailVerified)) {
                                    errorMessage = "This email does not match the recovery email on file."
                                    return@Button
                                }

                                isSending = true
                                errorMessage = null

                                RecoveryEmailHelper.sendRecoveryLink(
                                    context = context,
                                    email = email,
                                    purpose = if (isSecurityKeyReset) {
                                        RecoveryEmailHelper.PURPOSE_RESET_SECURITY_KEY
                                    } else {
                                        RecoveryEmailHelper.PURPOSE_RESET_PASSWORD
                                    },
                                    onSuccess = {
                                        isSending = false
                                        linkSent = true
                                    },
                                    onError = { err ->
                                        isSending = false
                                        errorMessage = err
                                    }
                                )
                            },
                            enabled = !isSending,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Send Recovery Link")
                            }
                        }
                    }
                }
            } else {
                // Step 2: Link sent — waiting for user to click it
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Check Your Email",
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "We sent a recovery link to\n$email\n\nOpen the link in the email to reset your ${if (resetMode == "security_key") "security key" else "password"}.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "Didn't receive it? Check your spam folder or wait a moment.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SetupVerificationSuccessScreen(modeName: String, verifiedEmail: String, onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "$modeName Recovery Email Verified",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "$verifiedEmail is now verified. Return to Lock Mode setup and save.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("OK")
        }
    }
}

@Composable
fun ResetSuccessScreen(resetMode: String, onDone: () -> Unit) {
    val what = if (resetMode == "security_key") "Security key" else "Password"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "$what Reset Successfully",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Your $what has been cleared. You can now set a new one in Lock Mode settings.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("OK")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SetupVerificationSuccessScreenPreview() {
    AppTheme {
        SetupVerificationSuccessScreen(
            modeName = "Password",
            verifiedEmail = "julia@example.com",
            onDone = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ResetSuccessScreenPreview() {
    AppTheme {
        ResetSuccessScreen(
            resetMode = "password",
            onDone = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PasswordResetScreenPreview() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("password_reset_preview", android.content.Context.MODE_PRIVATE).apply {
        edit().putString("recovery_email", "julia@example.com").apply()
    }
    AppTheme {
        PasswordResetScreen(
            prefs = prefs,
            resetMode = "password",
            onPasswordReset = {}
        )
    }
}
