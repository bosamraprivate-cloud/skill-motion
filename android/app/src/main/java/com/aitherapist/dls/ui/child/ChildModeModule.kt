package com.aitherapist.dls.ui.child

import android.os.SystemClock
import com.aitherapist.dls.audio.AssetAudioPlayer
import com.aitherapist.dls.audio.AudioPlayer
import com.aitherapist.dls.data.SessionRepository
import com.aitherapist.dls.session.AssessmentRunnerFactory
import com.aitherapist.dls.session.Clock
import com.aitherapist.dls.vision.NoneVerifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Wiring for child mode (Story 2.14). */
@Module
@InstallIn(SingletonComponent::class)
object ChildModeModule {
    /** Monotonic: debounce and attempt windows must not jump with the wall clock. */
    @Provides
    fun clock(): Clock = Clock { SystemClock.elapsedRealtime() }

    /** Outlives a step screen so leaving child mode can still mark the session abandoned. */
    @Provides
    @Singleton
    fun childModeScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    fun audioPlayer(impl: AssetAudioPlayer): AudioPlayer = impl

    /** Every step is `none` in Epic 2; the camera verifiers replace [NoneVerifier] in Epic 3. */
    @Provides
    fun runnerFactory(sessions: SessionRepository, clock: Clock): AssessmentRunnerFactory = AssessmentRunnerFactory(
        store = sessions,
        verifier = NoneVerifier,
        clock = clock,
        dispatcher = Dispatchers.IO,
        wallClock = { System.currentTimeMillis() }
    )
}
