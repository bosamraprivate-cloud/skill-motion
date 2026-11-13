package com.aitherapist.dls.data

/** Setup zones (normalised, un-mirrored rectangles keyed by zone name), implemented by [SecureSettings]. */
interface ZoneStore {
    suspend fun zones(): Map<String, Zone>

    /** Replaces all zones; every zone must lie in 0..1 with left < right and top < bottom. */
    suspend fun setZones(zones: Map<String, Zone>)
}
