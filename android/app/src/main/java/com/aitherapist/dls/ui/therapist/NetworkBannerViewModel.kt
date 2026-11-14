package com.aitherapist.dls.ui.therapist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.data.NetworkStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** What the therapist network banner shows (Story 3.6); [NONE] hides it. */
enum class NetworkBanner { NONE, OFFLINE, PROXY_FAILED }

/** Offline wins over a failed proxy call; both clear when the device is back online. */
fun networkBanner(online: Boolean, proxyFailed: Boolean): NetworkBanner = when {
    !online -> NetworkBanner.OFFLINE
    proxyFailed -> NetworkBanner.PROXY_FAILED
    else -> NetworkBanner.NONE
}

@HiltViewModel
class NetworkBannerViewModel
@Inject
constructor(status: NetworkStatus) : ViewModel() {
    val banner: StateFlow<NetworkBanner> =
        combine(status.online, status.proxyFailed, ::networkBanner)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                networkBanner(status.online.value, status.proxyFailed.value)
            )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
