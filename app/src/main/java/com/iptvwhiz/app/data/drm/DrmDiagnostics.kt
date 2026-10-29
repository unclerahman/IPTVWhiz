package com.iptvwhiz.app.data.drm

/**
 * The one-line reason under the copy-protection message. It names the DRM system the playlist
 * asked for, so "wrong provider key" becomes something the owner can act on. Key material is
 * never included, only IDs and server responses.
 */
fun drmMissingDetail(drm: DrmInfo): String {
    val system = drm.type.storage
    return if (drm.type == DrmType.CLEAR_KEY) {
        "$system · no key in the playlist"
    } else {
        "$system · the playlist has no license link"
    }
}

/**
 * True when the playlist's ClearKey IDs include one the device asked for. Also true when the
 * question does not apply (no request yet, or not ClearKey), so callers can use `!matches`.
 */
fun DrmInfo.clearKeyMatches(requestedKeyIds: Set<String>): Boolean =
    type != DrmType.CLEAR_KEY ||
        requestedKeyIds.isEmpty() ||
        clearKeyKeyIds.intersect(requestedKeyIds).isNotEmpty()

/**
 * Explains a DRM failure that happened while playing.
 *
 * @param drm what the playlist said, or null when it said nothing at all.
 * @param requestedKeyIds key IDs the device asked for (ClearKey only), from [DrmKeyIds].
 * @param httpStatus the license server's answer, when it refused.
 * @param deviceUnsupported the device has no module for this DRM system.
 * @param streamLacksScheme the stream does not offer the system the playlist asked for.
 */
fun drmFailureDetail(
    drm: DrmInfo?,
    requestedKeyIds: Set<String> = emptySet(),
    httpStatus: Int? = null,
    deviceUnsupported: Boolean = false,
    streamLacksScheme: Boolean = false,
): String {
    if (drm == null) return "the stream is copy-protected, and the playlist has no key for it"
    // A half-written #KODIPROP line (type but no usable key/license) is a playlist problem, not a
    // device or license-server problem, so say that before anything else.
    if (!drm.isValid) return drmMissingDetail(drm)
    val system = drm.type.storage
    val keyIdMismatch = !drm.clearKeyMatches(requestedKeyIds)
    return when {
        deviceUnsupported -> "$system · this device has no $system module"
        streamLacksScheme -> "$system · the stream doesn't offer $system"
        keyIdMismatch -> "$system · the playlist key doesn't match this stream's key ID"
        httpStatus != null -> "$system · the license server said no (HTTP $httpStatus)"
        drm.licenseUrl != null -> "$system · the license request failed"
        drm.type == DrmType.CLEAR_KEY -> "$system · the device rejected the playlist key"
        else -> "$system · the license link didn't work"
    }
}
