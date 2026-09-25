package com.srideep.pocketforge.metrics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager
import android.provider.Settings
import java.io.File

/** What the phone itself reports, sampled alongside a generation. */
data class DeviceSnapshot(
    /** Peak resident memory of this process (VmHWM): model weights, KV cache and UI together. */
    val peakRamMb: Int,
    val ramMb: Int,
    val airplaneMode: Boolean,
    /** True when no network can reach the internet, whatever the airplane toggle says. */
    val offline: Boolean,
    /** PowerManager.THERMAL_STATUS_*; 0 is none, 3 and above means the SoC is throttling. */
    val thermalStatus: Int,
)

/**
 * Reads device state for the metrics bar and the benchmark.
 *
 * Everything here is a cheap, non-blocking read, so it can be sampled on every progress
 * update without disturbing the decode loop it is measuring.
 */
class DeviceMetrics(private val context: Context) {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)

    fun snapshot(): DeviceSnapshot {
        val status = readProcStatus()
        return DeviceSnapshot(
            peakRamMb = (status["VmHWM"] ?: 0L).div(1024).toInt(),
            ramMb = (status["VmRSS"] ?: 0L).div(1024).toInt(),
            airplaneMode = Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                0,
            ) != 0,
            offline = !hasInternetRoute(),
            thermalStatus = power?.currentThermalStatus ?: 0,
        )
    }

    /** A metrics read must never take the app down, so a refused query counts as online. */
    private fun hasInternetRoute(): Boolean = runCatching {
        val network = connectivity?.activeNetwork ?: return false
        val caps = connectivity.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)

    /** kB values from /proc/self/status, keyed by field name. */
    private fun readProcStatus(): Map<String, Long> = runCatching {
        File("/proc/self/status").readLines().mapNotNull { line ->
            val key = line.substringBefore(':', "")
            if (key != "VmHWM" && key != "VmRSS") return@mapNotNull null
            val kb = line.substringAfter(':').trim().substringBefore(' ').toLongOrNull()
            kb?.let { key to it }
        }.toMap()
    }.getOrDefault(emptyMap())
}
