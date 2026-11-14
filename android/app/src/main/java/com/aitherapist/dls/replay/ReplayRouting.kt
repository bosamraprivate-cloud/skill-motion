package com.aitherapist.dls.replay

import com.aitherapist.dls.vision.HandVerifier
import com.aitherapist.dls.vision.PoseVerifier
import com.aitherapist.dls.vision.Verifier
import com.aitherapist.dls.vision.VerifierRouter

/**
 * The replay router (Story 3.7 review): the same router and verifiers as live, but with no setup zones (clips were not
 * filmed in this room, so zone steps become cannot tell `no_zone`, shown as the reason in the results) and a cloud
 * verifier that does not report to the live network banner.
 */
fun replayRouter(cloud: suspend () -> Verifier?): VerifierRouter =
    VerifierRouter(pose = PoseVerifier(), hand = HandVerifier(), cloud = cloud, zones = { emptyMap() })
