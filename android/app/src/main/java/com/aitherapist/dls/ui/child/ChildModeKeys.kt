package com.aitherapist.dls.ui.child

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Hardware key presses (Bluetooth clicker or any key) from the activity to the step screen, which treats them as Done. */
@Singleton
class ChildModeKeys
@Inject
constructor() {
    private val presses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = presses.asSharedFlow()

    fun press() {
        presses.tryEmit(Unit)
    }
}
