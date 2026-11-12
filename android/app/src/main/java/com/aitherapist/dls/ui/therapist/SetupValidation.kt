package com.aitherapist.dls.ui.therapist

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** PIN rules: numeric, 4 to 6 digits. */
object PinRules {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 6

    fun isValid(pin: String): Boolean = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    /** Keeps digits only and caps the length, for text fields and pads. */
    fun sanitize(input: String): String = input.filter { it in '0'..'9' }.take(MAX_LENGTH)
}

/** The proxy URL must be an absolute https URL with a host. */
fun isValidProxyUrl(url: String): Boolean {
    val parsed = url.trim().toHttpUrlOrNull() ?: return false
    return parsed.isHttps && parsed.host.isNotBlank()
}
