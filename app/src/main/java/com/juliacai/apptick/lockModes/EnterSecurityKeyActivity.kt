package com.juliacai.apptick.lockModes

import android.content.Intent
import android.content.SharedPreferences
import android.content.Context
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.juliacai.apptick.AppTheme
import com.juliacai.apptick.MainActivity

class EnterSecurityKeyActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var usbManager: UsbManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        AppTheme.applyTheme(this)

        setContent {
            AppTheme {
                EnterSecurityKeyScreen(
                    onUnlockClick = { verifyConnectedUsbKey() },
                    onCancelClick = { finish() }
                )
            }
        }
    }

    private fun verifyConnectedUsbKey() {
        val registeredKey = UsbSecurityKey.readRegisteredKey(prefs)
        if (registeredKey == null) {
            Toast.makeText(this, "No USB security key configured", Toast.LENGTH_SHORT).show()
            return
        }

        val connectedDevice = UsbSecurityKey.findMatchingConnectedDevice(usbManager, registeredKey)
        if (connectedDevice == null) {
            Toast.makeText(this, "Registered USB key not detected", Toast.LENGTH_SHORT).show()
            return
        }

        prefs.edit {
            putBoolean("securityKeyUnlocked", true)
            putBoolean("passUnlocked", false)
        }
        Toast.makeText(this, "Unlocked", Toast.LENGTH_SHORT).show()
        val openLockModes = intent.getBooleanExtra(MainActivity.EXTRA_OPEN_LOCK_MODES, false)
        if (openLockModes) {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(MainActivity.EXTRA_OPEN_LOCK_MODES, true)
                }
            )
        }
        finish()
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun EnterSecurityKeyScreen(
    onUnlockClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Enter Security Key",
                        maxLines = 1,
                        softWrap = false
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Plug in your registered USB security key, then continue.",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onUnlockClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Unlock with USB Key")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onCancelClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }
}
