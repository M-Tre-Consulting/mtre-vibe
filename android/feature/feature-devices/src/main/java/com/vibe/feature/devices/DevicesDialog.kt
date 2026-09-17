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
import androidx.compose.ui.unit.dp
import com.vibe.core.model.Device
import com.vibe.core.model.DeviceType

@Composable
fun DevicesDialog(
    devices: List<Device>,
    isLocalPlaybackActive: Boolean = true,
    onSelectLocalPlayback: () -> Unit = {},
    onSelectDevice: (Device) -> Unit,
    onVolumeChange: (Device, Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.devices_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                // 1. This Phone (Local playback option)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isLocalPlaybackActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
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
                            tint = if (isLocalPlaybackActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${Build.MODEL} (${androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.devices_this_phone)})",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isLocalPlaybackActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isLocalPlaybackActive) {
                                    androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.devices_this_phone_subtitle_playing)
                                } else {
                                    androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.devices_this_phone_subtitle_tap)
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
                    androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.devices_available_speakers),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                val remoteDevices = devices.filter { it.name != Build.MODEL }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(remoteDevices, key = { it.id }) { device ->
                        DeviceItemRow(
                            device = device,
                            isCurrentlyActive = !isLocalPlaybackActive && device.isActive,
                            onSelect = { onSelectDevice(device) },
                            onVolumeChange = { vol -> onVolumeChange(device, vol) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.devices_close))
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
