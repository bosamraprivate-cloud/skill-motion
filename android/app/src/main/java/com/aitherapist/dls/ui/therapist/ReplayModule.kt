package com.aitherapist.dls.ui.therapist

import android.content.Context
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.data.ClipFiles
import com.aitherapist.dls.data.FrameStore
import com.aitherapist.dls.data.ReplayStore
import com.aitherapist.dls.data.SessionRepository
import com.aitherapist.dls.data.TherapistSettings
import com.aitherapist.dls.replay.AndroidClipDecoder
import com.aitherapist.dls.replay.ReplayService
import com.aitherapist.dls.replay.ReplaySource
import com.aitherapist.dls.replay.VideoDetector
import com.aitherapist.dls.replay.VideoLandmarkEngine
import com.aitherapist.dls.replay.replayRouter
import com.aitherapist.dls.session.AssessmentRunnerFactory
import com.aitherapist.dls.session.Clock
import com.aitherapist.dls.session.FrameStoreEvidence
import com.aitherapist.dls.ui.child.CloudHttp
import com.aitherapist.dls.vision.AttemptImage
import com.aitherapist.dls.vision.BitmapJpegEncoder
import com.aitherapist.dls.vision.CloudVerifier
import com.aitherapist.dls.vision.VideoLandmarkers
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers

/**
 * Wiring for replay mode (Story 3.7, debug builds only in the UI): its own router (no live zones, cloud calls never feed
 * the live network banner), the shared runner and evidence code, VIDEO-mode landmarkers.
 */
@Module
@InstallIn(SingletonComponent::class)
object ReplayModule {
    @Provides
    fun replayService(
        @ApplicationContext context: Context,
        catalog: CatalogRepository,
        store: ReplayStore,
        clips: ClipFiles,
        sessions: SessionRepository,
        frames: FrameStore,
        clock: Clock,
        settings: TherapistSettings,
        http: CloudHttp,
        decoder: AndroidClipDecoder
    ): ReplayService {
        val router =
            replayRouter {
                val url = settings.proxyUrl()
                val secret = settings.sharedSecret()
                if (url.isNullOrBlank() || secret.isNullOrBlank()) null else CloudVerifier(url, secret, BitmapJpegEncoder, http.client)
            }
        val runners =
            AssessmentRunnerFactory(
                store = sessions,
                verifier = router,
                clock = clock,
                dispatcher = Dispatchers.IO,
                wallClock = { System.currentTimeMillis() },
                evidence = FrameStoreEvidence(frames, BitmapJpegEncoder)
            )
        val engine =
            VideoLandmarkEngine { pose, hand ->
                val landmarkers = VideoLandmarkers(context, pose, hand)
                object : VideoDetector {
                    override fun detect(image: AttemptImage, timestampMs: Long) = landmarkers.detect(image, timestampMs)

                    override fun close() = landmarkers.close()
                }
            }
        return ReplayService(catalog, store, clips, runners, ReplaySource(decoder, engine))
    }
}
