//package com.juliacai.apptick.lockModes
//
//import android.app.Activity
//import android.app.admin.DevicePolicyManager
//import android.content.ComponentName
//import android.content.Intent
//import android.content.SharedPreferences
//import android.os.Bundle
//import android.util.Log
//import android.widget.Toast
//import androidx.activity.compose.setContent
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.appcompat.app.AppCompatActivity
//import androidx.credentials.CreatePublicKeyCredentialRequest
//import androidx.credentials.CredentialManager
//import androidx.credentials.PublicKeyCredential
//import androidx.core.content.edit
//import com.google.android.material.dialog.MaterialAlertDialogBuilder
//import com.juliacai.apptick.premiumMode.DeviceAdmin
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//
//class SecurityKeySettingsActivity : AppCompatActivity() {
//
//    private lateinit var devicePolicyManager: DevicePolicyManager
//    private lateinit var adminComponentName: ComponentName
//    private lateinit var prefs: SharedPreferences
//    private lateinit var credentialManager: CredentialManager
//
//    private val deviceAdminLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
//        if (result.resultCode == Activity.RESULT_OK) {
//            Toast.makeText(this, "Device admin enabled", Toast.LENGTH_SHORT).show()
//        } else {
//            Toast.makeText(this, "Device admin not enabled", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
//        adminComponentName = ComponentName(this, DeviceAdmin::class.java)
//        prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
//        credentialManager = CredentialManager.create(this)
//
//        setContent {
//            SecurityKeySettingsScreen(
//                onSaveClick = { recoveryEmail, enableSettingsLock ->
//                    createPasskey(recoveryEmail, enableSettingsLock)
//                },
//                onCancelClick = { finish() },
//                onGoToSettingsClick = {
//                    if (isAdminGranted) {
//                        showInfoDialog("Device Admin", "Device admin permissions are already enabled.")
//                    } else {
//                        requestDeviceAdmin()
//                    }
//                }
//            )
//        }
//    }
//
//    private fun createPasskey(recoveryEmail: String, enableSettingsLock: Boolean) {
//        val requestJson = "{\"challenge\":\"someChallenge\",\"rp\":{\"name\":\"AppTick\",\"id\":\"apptick.juliacai.com\"},\"user\":{\"id\":\"someUserId\",\"name\":\"user\",\"displayName\":\"user\"},\"pubKeyCredParams\":[{\"type\":\"public-key\",\"alg\":-7}],\"authenticatorSelection\":{\"authenticatorAttachment\":\"platform\"},\"timeout\":1800000,\"attestation\":\"none\"}"
//        val request = CreatePublicKeyCredentialRequest(requestJson)
//        CoroutineScope(Dispatchers.Main).launch {
//            try {
//                val result = credentialManager.createCredential(this@SecurityKeySettingsActivity, request) as PublicKeyCredential
//                val credentialId = result.data.getString("credentialId")
//                saveSettingsAndFinish(recoveryEmail, enableSettingsLock, credentialId!!)
//            } catch (e: Exception) {
//                Log.e("PasskeyCreation", "Error creating passkey", e)
//                Toast.makeText(this@SecurityKeySettingsActivity, "Error creating passkey", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }
//
//    private fun saveSettingsAndFinish(
//        recoveryEmail: String,
//        enableSettingsLock: Boolean,
//        credentialId: String
//    ) {
//        if (enableSettingsLock && !isAdminGranted) {
//            showInfoDialog(
//                "Device Admin Required",
//                "To prevent AppTick from being uninstalled, please grant Device Admin permissions using the \"GO TO SETTINGS\" button."
//            )
//            return
//        }
//
//        prefs.edit {
//            putBoolean("security_key_enabled", true)
//            putString("recovery_email_security_key", recoveryEmail)
//            putBoolean("blockSettings", enableSettingsLock)
//            putBoolean("locked", true)
//            putString("credentialId", credentialId)
//        }
//
//        Toast.makeText(this, "Security Key lock enabled", Toast.LENGTH_SHORT).show()
//        finish()
//    }
//
//    private fun requestDeviceAdmin() {
//        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
//            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponentName)
//            putExtra(
//                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
//                "Granting this permission prevents AppTick from being uninstalled, ensuring your app limits remain active."
//            )
//        }
//        deviceAdminLauncher.launch(intent)
//    }
//
//    private val isAdminGranted: Boolean
//        get() = devicePolicyManager.isAdminActive(adminComponentName)
//
//    private fun showInfoDialog(title: String, message: String) {
//        MaterialAlertDialogBuilder(this)
//            .setTitle(title)
//            .setMessage(message)
//            .setPositiveButton(android.R.string.ok, null)
//            .show()
//    }
//}
