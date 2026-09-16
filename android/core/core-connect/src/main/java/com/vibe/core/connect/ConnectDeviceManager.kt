package com.vibe.core.connect

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.vibe.core.model.Device
import com.vibe.core.model.DeviceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Discovers Spotify Connect devices (librespot, spotifyd, hardware receivers)
 * over mDNS / Android NSD and merges them with Web API devices, deduplicating by device ID.
 */
class ConnectDeviceManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val nsdManager: NsdManager? by lazy {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }

    private val serviceType = "_spotify-connect._tcp."
    private val discoveredMdnsDevices = mutableMapOf<String, Device>()

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {}

        override fun onServiceFound(service: NsdServiceInfo) {
            if (service.serviceType.contains("spotify-connect")) {
                nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val host = serviceInfo.host?.hostAddress
                        val port = serviceInfo.port
                        val deviceName = serviceInfo.serviceName

                        // Extract CName / Device ID if present or synthesize from name/host
                        val deviceId = "mdns_${serviceInfo.serviceName.hashCode()}"
                        val device = Device(
                            id = deviceId,
                            name = deviceName,
                            type = DeviceType.SPEAKER,
                            isLocal = false,
                            mdnsHost = host,
                            mdnsPort = port
                        )

                        synchronized(discoveredMdnsDevices) {
                            discoveredMdnsDevices[deviceId] = device
                            updateMergedDevices()
                        }
                    }
                })
            }
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            val deviceId = "mdns_${service.serviceName.hashCode()}"
            synchronized(discoveredMdnsDevices) {
                discoveredMdnsDevices.remove(deviceId)
                updateMergedDevices()
            }
        }

        override fun onDiscoveryStopped(serviceType: String) {}
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
    }

    fun startDiscovery() {
        runCatching {
            nsdManager?.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }
    }

    fun stopDiscovery() {
        runCatching {
            nsdManager?.stopServiceDiscovery(discoveryListener)
        }
    }

    /**
     * Combines responding mDNS receivers and Web API devices by device ID,
     * prioritizing active state and responsive receiver names.
     */
    fun syncWithWebApiDevices(webApiDevices: List<Device>) {
        scope.launch {
            val mergedMap = LinkedHashMap<String, Device>()

            // Put Web API devices
            for (dev in webApiDevices) {
                mergedMap[dev.id] = dev
            }

            // Merge / deduplicate mDNS devices
            synchronized(discoveredMdnsDevices) {
                for ((id, mdnsDev) in discoveredMdnsDevices) {
                    val existing = mergedMap[id]
                    if (existing != null) {
                        // Combine entries with the same device ID
                        mergedMap[id] = existing.copy(
                            mdnsHost = mdnsDev.mdnsHost,
                            mdnsPort = mdnsDev.mdnsPort
                        )
                    } else {
                        mergedMap[id] = mdnsDev
                    }
                }
            }

            _devices.value = mergedMap.values.toList()
        }
    }

    private fun updateMergedDevices() {
        val current = _devices.value.associateBy { it.id }.toMutableMap()
        for ((id, mdnsDev) in discoveredMdnsDevices) {
            if (!current.containsKey(id)) {
                current[id] = mdnsDev
            }
        }
        _devices.value = current.values.toList()
    }
}
