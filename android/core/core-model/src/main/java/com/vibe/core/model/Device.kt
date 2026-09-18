package com.vibe.core.model

import android.content.Context
import android.provider.Settings
import kotlinx.serialization.Serializable

@Serializable
enum class DeviceType {
    COMPUTER,
    TABLET,
    SMARTPHONE,
    SPEAKER,
    CAST_VIDEO,
    CAST_AUDIO,
    AVR,
    STB,
    AUDIO_DONGLE,
    GAME_CONSOLE,
    UNKNOWN
}

object CurrentDeviceInfo {
    @Volatile
    var deviceFriendlyName: String? = null

    fun init(context: Context) {
        if (deviceFriendlyName == null) {
            deviceFriendlyName = try {
                Settings.Global.getString(context.contentResolver, "device_name")
                    ?: Settings.Secure.getString(context.contentResolver, "bluetooth_name")
            } catch (_: Exception) {
                null
            }
        }
    }
}

@Serializable
data class Device(
    val id: String,
    val name: String,
    val type: DeviceType = DeviceType.SPEAKER,
    val isActive: Boolean = false,
    val isLocal: Boolean = false,
    val isRestricted: Boolean = false,
    val volumePercent: Int = 100,
    val supportsVolume: Boolean = true,
    val mdnsHost: String? = null,
    val mdnsPort: Int? = null
)

fun Device.isCurrentDevice(allDevices: List<Device> = emptyList()): Boolean {
    if (type != DeviceType.SMARTPHONE && type != DeviceType.TABLET && type != DeviceType.UNKNOWN) return false
    
    val friendly = CurrentDeviceInfo.deviceFriendlyName
    if (!friendly.isNullOrBlank()) {
        if (name.equals(friendly, ignoreCase = true) ||
            name.contains(friendly, ignoreCase = true) ||
            friendly.contains(name, ignoreCase = true)
        ) {
            return true
        }
    }
    
    val model = android.os.Build.MODEL
    val device = android.os.Build.DEVICE
    val product = android.os.Build.PRODUCT
    if (name.equals(model, ignoreCase = true) ||
        name.contains(model, ignoreCase = true) ||
        model.contains(name, ignoreCase = true) ||
        name.contains(device, ignoreCase = true) ||
        name.contains(product, ignoreCase = true)
    ) {
        return true
    }
    val portableDevices = allDevices.filter { it.type == DeviceType.SMARTPHONE || it.type == DeviceType.TABLET }
    if (portableDevices.size == 1 && portableDevices.first().id == this.id) {
        return true
    }
    return false
}

val Device.isRemote: Boolean
    get() = type == DeviceType.COMPUTER ||
            type == DeviceType.SPEAKER ||
            type == DeviceType.AVR ||
            type == DeviceType.CAST_AUDIO ||
            type == DeviceType.CAST_VIDEO ||
            !isCurrentDevice()


