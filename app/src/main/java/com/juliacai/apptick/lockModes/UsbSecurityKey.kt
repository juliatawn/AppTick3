package com.juliacai.apptick.lockModes

import android.content.SharedPreferences
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import org.json.JSONObject

data class UsbSecurityKey(
    val vendorId: Int,
    val productId: Int,
    val manufacturerName: String?,
    val productName: String?
) {
    fun matches(device: UsbDevice): Boolean {
        if (vendorId != device.vendorId || productId != device.productId) return false
        if (!manufacturerName.isNullOrBlank() && manufacturerName != device.manufacturerName) return false
        if (!productName.isNullOrBlank() && productName != device.productName) return false
        return true
    }

    fun displayName(): String {
        val label = listOfNotNull(
            manufacturerName?.takeIf { it.isNotBlank() },
            productName?.takeIf { it.isNotBlank() }
        ).joinToString(" ").ifBlank { "USB key" }
        return "$label ($vendorId:$productId)"
    }

    fun toPersistedString(): String {
        return JSONObject()
            .put("vendorId", vendorId)
            .put("productId", productId)
            .put("manufacturerName", manufacturerName)
            .put("productName", productName)
            .toString()
    }

    companion object {
        private const val PREF_USB_KEY_FINGERPRINT = "security_usb_key_fingerprint"

        fun fromUsbDevice(device: UsbDevice): UsbSecurityKey {
            return UsbSecurityKey(
                vendorId = device.vendorId,
                productId = device.productId,
                manufacturerName = device.manufacturerName,
                productName = device.productName
            )
        }

        fun connectedUsbDevices(usbManager: UsbManager): List<UsbDevice> {
            return usbManager.deviceList.values
                .sortedWith(compareBy<UsbDevice> { it.vendorId }
                    .thenBy { it.productId }
                    .thenBy { it.deviceName })
        }

        fun readRegisteredKey(prefs: SharedPreferences): UsbSecurityKey? {
            val raw = prefs.getString(PREF_USB_KEY_FINGERPRINT, null) ?: return null
            return runCatching {
                val json = JSONObject(raw)
                UsbSecurityKey(
                    vendorId = json.getInt("vendorId"),
                    productId = json.getInt("productId"),
                    manufacturerName = json.optString("manufacturerName").takeIf { it.isNotBlank() },
                    productName = json.optString("productName").takeIf { it.isNotBlank() }
                )
            }.getOrNull()
        }

        fun findMatchingConnectedDevice(usbManager: UsbManager, registered: UsbSecurityKey): UsbDevice? {
            return connectedUsbDevices(usbManager).firstOrNull { registered.matches(it) }
        }
    }
}
