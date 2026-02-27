package com.juliacai.apptick.lockModes

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
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

class UsbSecurityKeySetupActivity : AppCompatActivity() {

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
                UsbSecurityKeySetupScreen(
                    onRegisterClick = { registerKeyAndFinish() },
                    onCancelClick = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }

    private fun registerKeyAndFinish() {
        val connected = UsbSecurityKey.connectedUsbDevices(usbManager)
        if (connected.isEmpty()) {
            Toast.makeText(this, "No USB key detected. Plug one in and try again.", Toast.LENGTH_SHORT).show()
            return
        }

        val key = UsbSecurityKey.fromUsbDevice(connected.first())
        prefs.edit {
            putString("security_usb_key_fingerprint", key.toPersistedString())
        }
        Toast.makeText(this, "USB security key saved", Toast.LENGTH_SHORT).show()
        setResult(Activity.RESULT_OK)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsbSecurityKeySetupScreen(
    onRegisterClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Setup USB Security Key",
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
                "Plug in the USB security key you want to use as an alternative unlock for Password mode.",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onRegisterClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Register Connected USB Key")
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
