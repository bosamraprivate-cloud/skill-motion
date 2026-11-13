package com.aitherapist.dls.ui.child

import android.os.SystemClock
import com.aitherapist.dls.audio.AssetAudioPlayer
import com.aitherapist.dls.audio.AudioPlayer
import com.aitherapist.dls.data.FrameStore
import com.aitherapist.dls.data.SessionRepository
import com.aitherapist.dls.data.TherapistSettings
import com.aitherapist.dls.data.ZoneStore
import com.aitherapist.dls.session.AssessmentRunnerFactory
import com.aitherapist.dls.session.Clock
import com.aitherapist.dls.session.FrameStoreEvidence
import com.aitherapist.dls.vision.AttemptCapture
import com.aitherapist.dls.vision.BitmapJpegEncoder
import com.aitherapist.dls.vision.CloudVerifier
import com.aitherapist.dls.vision.HandVerifier
import com.aitherapist.dls.vision.LiveCapture
import com.aitherapist.dls.vision.PoseVerifier
import com.aitherapist.dls.vision.VerifierRouter
import com.aitherapist.dls.vision.Zone
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

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

    @Provides
    fun attemptCapture(impl: LiveCapture): AttemptCapture = impl

    /** One HTTP client for cloud judging (Story 3.6 hardens network handling). */
    @Provides
    @Singleton
    fun cloudHttpClient(): CloudHttp = CloudHttp(OkHttpClient())

    /**
     * Story 3.5: the router picks the verifier by method; the cloud verifier is built from the current proxy settings
     * per judgement (none set: cannot tell), zones come from SecureSettings.
     */
    @Provides
    fun verifierRouter(settings: TherapistSettings, zones: ZoneStore, http: CloudHttp): VerifierRouter = VerifierRouter(
        pose = PoseVerifier(),
        hand = HandVerifier(),
        cloud = {
            val url = settings.proxyUrl()
            val secret = settings.sharedSecret()
            if (url.isNullOrBlank() || secret.isNullOrBlank()) null else CloudVerifier(url, secret, BitmapJpegEncoder, http.client)
        },
        zones = { zones.zones().mapValues { (_, z) -> Zone(z.left, z.top, z.right, z.bottom) } }
    )

    @Provides
    fun runnerFactory(sessions: SessionRepository, clock: Clock, router: VerifierRouter, frames: FrameStore): AssessmentRunnerFactory =
        AssessmentRunnerFactory(
            store = sessions,
            verifier = router,
            clock = clock,
            dispatcher = Dispatchers.IO,
            wallClock = { System.currentTimeMillis() },
            evidence = FrameStoreEvidence(frames, BitmapJpegEncoder)
        )
}

/** Wrapper so the cloud client does not collide with any other OkHttpClient binding. */
class CloudHttp(val client: OkHttpClient)
