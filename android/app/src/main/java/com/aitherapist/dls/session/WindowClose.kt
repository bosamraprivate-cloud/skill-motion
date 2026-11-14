package com.aitherapist.dls.session

/** Story 6.2: the attempt window opened at [openedAtMs] closed for [reason] (TIMEOUT = the step time ran out). */
data class WindowClose(val openedAtMs: Long, val reason: CloseReason)
