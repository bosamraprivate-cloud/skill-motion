package com.aitherapist.dls.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Network state for the therapist banner (Story 3.6). [online] follows the device's validated default network;
 * [proxyFailed] is true while the last proxy call failed (network, timeout, refused, bad answer). Coming back online
 * clears it, a failure from a call started before that transition is ignored, and it expires after
 * [FAILURE_EXPIRY_MS] without new failures. Holds no child data and logs nothing.
 */
interface NetworkStatus {
    val online: StateFlow<Boolean>
    val proxyFailed: StateFlow<Boolean>

    /** Call when a proxy call starts; pass the returned generation to [reportProxy]. */
    fun beginProxyCall(): Long

    /** Records the outcome of one proxy call that started at [generation]. */
    fun reportProxy(generation: Long, failed: Boolean)

    companion object {
        const val FAILURE_EXPIRY_MS = 120_000L
    }
}

/** Pure state behind [NetworkStatus]; the platform monitor feeds [setOnline]. [scope] runs the failure expiry. */
open class NetworkStatusState(private val scope: CoroutineScope, initiallyOnline: Boolean = true) : NetworkStatus {
    private val onlineFlow = MutableStateFlow(initiallyOnline)
    private val failedFlow = MutableStateFlow(false)
    override val online: StateFlow<Boolean> = onlineFlow.asStateFlow()
    override val proxyFailed: StateFlow<Boolean> = failedFlow.asStateFlow()

    /** Bumped on every offline -> online transition. */
    @Volatile private var generation = 0L
    private var expiry: Job? = null

    override fun beginProxyCall(): Long = generation

    @Synchronized
    override fun reportProxy(generation: Long, failed: Boolean) {
        if (generation != this.generation) return
        expiry?.cancel()
        failedFlow.value = failed
        if (failed) {
            expiry =
                scope.launch {
                    delay(NetworkStatus.FAILURE_EXPIRY_MS)
                    failedFlow.value = false
                }
        }
    }

    @Synchronized
    fun setOnline(value: Boolean) {
        if (value && !onlineFlow.value) {
            generation++
            expiry?.cancel()
            failedFlow.value = false
        }
        onlineFlow.value = value
    }
}

/** [NetworkStatus] fed by a [ConnectivityManager] default-network callback for the app's lifetime. */
class AndroidNetworkStatus(context: Context, scope: CoroutineScope) : NetworkStatusState(scope, initiallyOnline = isOnline(context)) {
    init {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        manager?.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    setOnline(caps.isValidatedInternet())
                }

                override fun onLost(network: Network) {
                    setOnline(false)
                }
            }
        )
    }

    private companion object {
        fun isOnline(context: Context): Boolean {
            val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
            val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
            return caps.isValidatedInternet()
        }

        /** Online means internet capability that the system has validated (not a captive or dead Wi-Fi). */
        fun NetworkCapabilities.isValidatedInternet(): Boolean = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
