package com.pedroaba.spix.download

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

internal class DownloadNetwork(private val context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    fun isOnline(): Boolean {
        if (power.isDeviceIdleMode && !power.isIgnoringBatteryOptimizations(context.packageName)) return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    val online = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(isOnline()) }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { trySend(isOnline()) }
            override fun onLost(network: Network) { trySend(isOnline()) }
            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) { trySend(!blocked && isOnline()) }
        }
        val idleReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { trySend(isOnline()) }
        }
        androidx.core.content.ContextCompat.registerReceiver(context, idleReceiver,
            IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        manager.registerDefaultNetworkCallback(callback)
        trySend(isOnline())
        awaitClose { manager.unregisterNetworkCallback(callback); context.unregisterReceiver(idleReceiver) }
    }.distinctUntilChanged()
}
