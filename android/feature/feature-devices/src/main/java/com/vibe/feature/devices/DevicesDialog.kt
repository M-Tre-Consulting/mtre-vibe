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
import androidx.compose.ui.unit.dp
import com.vibe.core.model.Device
import com.vibe.core.model.DeviceType
import com.vibe.core.model.isRemote

@Composable
fun DevicesDialog(
    devices: List<Device>,
    isLocalPlaybackActive: Boolean = true,
    activeDeviceId: String? = null,
    isRefreshing: Boolean = false,
    onSelectLocalPlayback: () -> Unit = {},
    onSelectDevice: (Device) -> Unit,
    onVolumeChange: (Device, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val remoteDevices = remember(devices) {
        devices.filter { it.isRemote }
    }
    val anyRemoteActive = remoteDevices.any { it.isActive || (activeDeviceId != null && it.id == activeDeviceId) }
    val effectiveLocalActive = isLocalPlaybackActive && !anyRemoteActive

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
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                // 1. This Phone (Local playback option)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (effectiveLocalActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
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
                            tint = if (effectiveLocalActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${Build.MODEL} (${stringResource(com.vibe.core.ui.R.string.devices_this_phone)})",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (effectiveLocalActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (effectiveLocalActive) {
                                    stringResource(com.vibe.core.ui.R.string.devices_this_phone_subtitle_playing)
                                } else {
                                    stringResource(com.vibe.core.ui.R.string.devices_this_phone_subtitle_tap)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    stringResource(com.vibe.core.ui.R.string.devices_available_speakers),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (remoteDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
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
                        items(remoteDevices, key = { it.id }) { device ->
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
                        text = if (isCurrentlyActive) androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.devices_remote_playing)
                               else androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.devices_spotify_connect),
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
