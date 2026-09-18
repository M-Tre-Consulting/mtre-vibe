package com.vibe.feature.devices

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vibe.core.model.Device
import com.vibe.core.model.DeviceType
import com.vibe.core.model.isCurrentDevice

@Composable
fun DevicesDialog(
    devices: List<Device>,
    isLocalPlaybackActive: Boolean = true,
    activeDeviceId: String? = null,
    isRefreshing: Boolean = false,
    isSpotifyAppInstalled: Boolean = true,
    onSelectLocalPlayback: () -> Unit = {},
    onSelectDevice: (Device) -> Unit,
    onVolumeChange: (Device, Int) -> Unit,
    onWakeSpotify: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val phoneConnectDevice = remember(devices) {
        devices.firstOrNull { it.isCurrentDevice(devices) }
    }
    val otherDevices = remember(devices) {
        devices.filter { !it.isCurrentDevice(devices) }
    }

    val isPhoneConnectActive = phoneConnectDevice != null &&
        (phoneConnectDevice.isActive || (activeDeviceId != null && phoneConnectDevice.id == activeDeviceId))
    val isInternalLocalActive = isLocalPlaybackActive && !isPhoneConnectActive &&
        !otherDevices.any { it.isActive || (activeDeviceId != null && it.id == activeDeviceId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(com.vibe.core.ui.R.string.devices_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(com.vibe.core.ui.R.string.devices_refresh),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                // SECTION 1: This Device
                Text(
                    text = stringResource(com.vibe.core.ui.R.string.devices_this_device_model, Build.MODEL),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (phoneConnectDevice != null) {
                    // Phone Spotify Connect endpoint is online
                    DeviceItemRow(
                        device = phoneConnectDevice,
                        isCurrentlyActive = isPhoneConnectActive,
                        subtitle = stringResource(com.vibe.core.ui.R.string.devices_local_spotify_app),
                        onSelect = { onSelectDevice(phoneConnectDevice) },
                        onVolumeChange = { vol -> onVolumeChange(phoneConnectDevice, vol) }
                    )
                } else {
                    // Phone Spotify Connect is not detected yet
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isInternalLocalActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        onClick = onSelectLocalPlayback
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Smartphone,
                                contentDescription = null,
                                tint = if (isInternalLocalActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(com.vibe.core.ui.R.string.devices_vibe_player_model, Build.MODEL),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isInternalLocalActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isInternalLocalActive) stringResource(com.vibe.core.ui.R.string.devices_this_phone_subtitle_playing)
                                    else stringResource(com.vibe.core.ui.R.string.devices_this_phone_subtitle_tap),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (isSpotifyAppInstalled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onWakeSpotify
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(com.vibe.core.ui.R.string.devices_wake_spotify),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = stringResource(com.vibe.core.ui.R.string.devices_wake_spotify_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                // SECTION 2: Other Devices
                Text(
                    text = stringResource(com.vibe.core.ui.R.string.devices_other_connect_devices),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (otherDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isRefreshing) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(
                                    stringResource(com.vibe.core.ui.R.string.devices_searching),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text(
                                    stringResource(com.vibe.core.ui.R.string.devices_no_devices),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    stringResource(com.vibe.core.ui.R.string.devices_no_devices_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(otherDevices, key = { it.id }) { device ->
                            val isItemActive = device.isActive || (activeDeviceId != null && device.id == activeDeviceId)
                            DeviceItemRow(
                                device = device,
                                isCurrentlyActive = isItemActive,
                                onSelect = { onSelectDevice(device) },
                                onVolumeChange = { vol -> onVolumeChange(device, vol) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(com.vibe.core.ui.R.string.devices_close))
            }
        }
    )
}

@Composable
fun DeviceItemRow(
    device: Device,
    isCurrentlyActive: Boolean,
    subtitle: String? = null,
    onSelect: () -> Unit,
    onVolumeChange: (Int) -> Unit
) {
    var volume by remember(device.volumePercent) { mutableIntStateOf(device.volumePercent) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentlyActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        onClick = onSelect
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = when (device.type) {
                        DeviceType.SMARTPHONE -> Icons.Default.PhoneAndroid
                        DeviceType.COMPUTER -> Icons.Default.Computer
                        else -> Icons.Default.Speaker
                    },
                    contentDescription = null,
                    tint = if (isCurrentlyActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isCurrentlyActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle ?: if (isCurrentlyActive) stringResource(com.vibe.core.ui.R.string.devices_remote_playing)
                               else stringResource(com.vibe.core.ui.R.string.devices_spotify_connect),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isCurrentlyActive && device.supportsVolume) {
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = volume.toFloat(),
                    onValueChange = { volume = it.toInt() },
                    onValueChangeFinished = { onVolumeChange(volume) },
                    valueRange = 0f..100f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
