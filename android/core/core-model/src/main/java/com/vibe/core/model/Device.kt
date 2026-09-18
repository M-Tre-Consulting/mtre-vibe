package com.vibe.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class DeviceType {
    COMPUTER,
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

val Device.isRemote: Boolean
    get() = type == DeviceType.COMPUTER ||
            type == DeviceType.SPEAKER ||
            type == DeviceType.AVR ||
            type == DeviceType.CAST_AUDIO ||
            type == DeviceType.CAST_VIDEO ||
            (!isLocal && !name.equals(android.os.Build.MODEL, ignoreCase = true))

