package com.aitherapist.dls.session

import com.aitherapist.dls.catalog.Step
import com.aitherapist.dls.data.SessionStore
import com.aitherapist.dls.vision.Verifier
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher

/** Hilt-free factory for [AssessmentRunner]: one runner (one new session) per child-mode run. */
class AssessmentRunnerFactory(
    private val store: SessionStore,
    private val verifier: Verifier,
    private val clock: Clock,
    private val dispatcher: CoroutineDispatcher,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val wallClock: Clock = clock
) {
    fun create(childId: String, taskId: String, catalogVersion: String, steps: List<Step>): AssessmentRunner = AssessmentRunner(
        sessionId = newId(),
        childId = childId,
        taskId = taskId,
        catalogVersion = catalogVersion,
        steps = steps,
        store = store,
        verifier = verifier,
        clock = clock,
        dispatcher = dispatcher,
        newId = newId,
        wallClock = wallClock
    )
}
