package com.xrc.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Network utilities for device information, connectivity checks,
 * and IP address resolution.
 */
object NetworkUtil {

    private const val TAG = "NetworkUtil"

    /**
     * Check if the device has an active internet connection.
     */
    fun isConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if connected via Wi-Fi.
     */
    fun isWifiConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if connected via mobile data.
     */
    fun isMobileConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get the device's local IP address.
     */
    fun getLocalIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress ?: continue
                    }
                }
            }
            "127.0.0.1"
        } catch (e: Exception) {
            "127.0.0.1"
        }
    }

    /**
     * Get the MAC address of the Wi-Fi interface.
     * On Android 10+, returns a randomized MAC unless using specific APIs.
     */
    fun getWifiMacAddress(context: Context): String {
        return try {
            val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val wifiInfo = wm.connectionInfo
                wifiInfo.macAddress ?: "02:00:00:00:00:00"
            } else {
                @Suppress("DEPRECATION")
                wm.connectionInfo.macAddress ?: "02:00:00:00:00:00"
            }
        } catch (e: Exception) {
            "02:00:00:00:00:00"
        }
    }

    /**
     * Get the SSID of the connected Wi-Fi network.
     */
    fun getWifiSsid(context: Context): String {
        return try {
            val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val wifiInfo = wm.connectionInfo
                wifiInfo.ssid ?: ""
            } else {
                @Suppress("DEPRECATION")
                wm.connectionInfo.ssid ?: ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Get the network type as a human-readable string.
     */
    fun getNetworkType(context: Context): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            when (tm.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
                TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
                TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
                TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
                TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
                TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
                TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
                TelephonyManager.NETWORK_TYPE_EVDO_0 -> "EVDO"
                TelephonyManager.NETWORK_TYPE_EVDO_A -> "EVDO_A"
                TelephonyManager.NETWORK_TYPE_1xRTT -> "1xRTT"
                TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                else -> {
                    when {
                        isWifiConnected(context) -> "WiFi"
                        else -> "Unknown"
                    }
                }
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * Get the carrier/operator name.
     */
    fun getCarrierName(context: Context): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.networkOperatorName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Ping a host to check reachability.
     */
    fun pingHost(host: String, timeoutMs: Int = 2000): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(
                arrayOf("ping", "-c", "1", "-W", (timeoutMs / 1000).toString(), host)
            )
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Test C2 server connectivity.
     */
    fun testC2Connection(host: String, port: Int): Boolean {
        return try {
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress(host, port), 3000)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get the device's public IP via external API.
     * Only use as last resort; prefer local IP.
     */
    fun getPublicIpAddress(): String? {
        return try {
            val url = java.net.URL("http://checkip.amazonaws.com")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val result = conn.inputStream.bufferedReader().readText().trim()
            conn.disconnect()
            result.ifBlank { null }
        } catch (e: Exception) {
            try {
                val url = java.net.URL("https://api.ipify.org")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val result = conn.inputStream.bufferedReader().readText().trim()
                conn.disconnect()
                result.ifBlank { null }
            } catch (e2: Exception) {
                null
            }
        }
    }

    /**
     * Get network capabilities as a string list.
     */
    fun getNetworkCapabilities(context: Context): List<String> {
        val caps = mutableListOf<String>()
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return caps
            val nc = cm.getNetworkCapabilities(network) ?: return caps

            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) caps.add("WiFi")
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) caps.add("Cellular")
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) caps.add("Ethernet")
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) caps.add("Bluetooth")
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) caps.add("VPN")
            if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) caps.add("Internet")
            if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) caps.add("Unmetered")
            caps
        } catch (e: Exception) {
            caps
        }
    }
}
