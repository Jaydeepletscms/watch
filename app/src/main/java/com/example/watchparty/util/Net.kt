package com.example.watchparty.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.StatFs
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

data class DeviceHealthStatus(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val temperatureCelsius: Float
)

object Net {
    const val DEFAULT_PORT = 8080

    /**
     * Resolves the local Wi-Fi or Hotspot IPv4 address (e.g. 192.168.1.x or 192.168.43.1).
     */
    fun getLocalIpAddress(context: Context): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            // Prioritize wlan / ap / swlan interfaces for Wi-Fi or Hotspot
            val prioritized = interfaces.sortedBy { iface ->
                val name = iface.name.lowercase(Locale.ROOT)
                when {
                    name.startsWith("wlan") -> 0
                    name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("rndis") -> 1
                    name.startsWith("eth") -> 2
                    else -> 3
                }
            }
            for (iface in prioritized) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip
                        }
                    }
                }
            }
            // Fallback to WifiManager connectionInfo
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                return String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }
        } catch (_: Exception) {
        }
        return "127.0.0.1"
    }

    fun getAvailableCacheBytes(cacheDir: File): Long {
        return try {
            val stat = StatFs(cacheDir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }

    fun hasEnoughStorage(cacheDir: File, minimumBytes: Long = 100L * 1024L * 1024L): Boolean {
        return getAvailableCacheBytes(cacheDir) >= minimumBytes
    }

    fun getDeviceHealth(context: Context): DeviceHealthStatus {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 100
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 300) ?: 300
            DeviceHealthStatus(
                batteryPercent = pct,
                isCharging = isCharging,
                temperatureCelsius = tempTenths / 10f
            )
        } catch (_: Exception) {
            DeviceHealthStatus(100, true, 30.0f)
        }
    }

    fun generateRoomCode(): String {
        val chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        return (1..3).map { chars.random() }.joinToString("")
    }
}
