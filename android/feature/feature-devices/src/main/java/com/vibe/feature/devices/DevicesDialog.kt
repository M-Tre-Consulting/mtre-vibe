package com.vibe.feature.devices

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
    onSelectDevice: (Device) -> Unit,
    onVolumeChange: (Device, Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Connect to a device", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                Text(
                    "Listening On / Available Speakers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Long device lists scroll
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(devices, key = { it.id }) { device ->
                        DeviceItemRow(
                            device = device,
                            onSelect = { onSelectDevice(device) },
                            onVolumeChange = { vol -> onVolumeChange(device, vol) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun DeviceItemRow(
    device: Device,
    onSelect: () -> Unit,
    onVolumeChange: (Int) -> Unit
) {
    var volume by remember(device.volumePercent) { mutableIntStateOf(device.volumePercent) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (device.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
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
                    tint = if (device.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (device.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (device.isActive) "This Device (Playing)" else "Spotify Connect",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (device.isActive && device.supportsVolume) {
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
