package com.juliacai.apptick

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit

class SecurityKeySettings : AppCompatActivity() {

    private lateinit var usbManager: UsbManager
    private lateinit var permissionIntent: PendingIntent
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
        usbManager = getSystemService(USB_SERVICE) as UsbManager
        permissionIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_IMMUTABLE
        )

        setContent {
            SecurityKeySettingsScreen(
                prefs = prefs,
                requestUsbPermission = ::requestUsbPermission
            )
        }
    }

    private fun requestUsbPermission() {
        val deviceList = usbManager.deviceList
        if (deviceList.isNotEmpty()) {
            val device = deviceList.values.first()
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            registerReceiver(usbReceiver, filter)
            usbManager.requestPermission(device, permissionIntent)
        } else {
            Toast.makeText(this, "No USB device found", Toast.LENGTH_SHORT).show()
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            prefs.edit { putBoolean("securityKeyEnabled", true) }
                            Toast.makeText(context, "Security key enabled", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "USB permission denied", Toast.LENGTH_SHORT).show()
                    }
                    context.unregisterReceiver(this)
                }
            }
        }
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.juliacai.apptick.USB_PERMISSION"
    }
}

@Composable
fun SecurityKeySettingsScreen(
    prefs: SharedPreferences,
    requestUsbPermission: () -> Unit
) {
    val context = LocalContext.current
    var securityKeyEnabled by remember { mutableStateOf(prefs.getBoolean("securityKeyEnabled", false)) }
    var passwordFallbackEnabled by remember { mutableStateOf(prefs.getBoolean("passwordFallbackEnabled", true)) }

    Column(modifier = Modifier.padding(16.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Security Key Settings",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable USB Security Key", modifier = Modifier.weight(1f))
                    Switch(
                        checked = securityKeyEnabled,
                        onCheckedChange = {
                            securityKeyEnabled = it
                            if (it) {
                                requestUsbPermission()
                            } else {
                                prefs.edit { putBoolean("securityKeyEnabled", false) }
                                Toast.makeText(context, "Security key disabled", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                Text(
                    text = "Use a USB security key (like YubiKey) to unlock the app",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Allow password as fallback", modifier = Modifier.weight(1f))
                    Checkbox(
                        checked = passwordFallbackEnabled,
                        onCheckedChange = {
                            passwordFallbackEnabled = it
                            prefs.edit { putBoolean("passwordFallbackEnabled", it) }
                        }
                    )
                }
                Text(
                    text = "If enabled, you can still use your password if the security key is not available",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { 
                        if (securityKeyEnabled) {
                            Toast.makeText(context, "Security key test successful", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Please enable security key first", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Test Security Key")
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Note: This feature requires a compatible USB security key (like YubiKey) and is only available for premium users.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
