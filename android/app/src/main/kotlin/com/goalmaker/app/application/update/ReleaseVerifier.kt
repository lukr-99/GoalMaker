package com.goalmaker.app.application.update

import com.goalmaker.app.domain.update.ManifestCheck
import com.goalmaker.app.domain.update.ReleaseManifestParser

/**
 * Signature first, content second: unsigned bytes are never parsed. The combined behavior is the
 * contract in contracts/vectors/release-manifest.json.
 */
class ReleaseVerifier(private val signatures: SignatureVerifier) {
    fun check(snapshot: ChannelSnapshot): ManifestCheck {
        val signed = try {
            signatures.verify(snapshot.manifestBytes, snapshot.signatureBase64)
        } catch (_: IllegalArgumentException) {
            false
        }
        return if (signed) ReleaseManifestParser.parse(snapshot.manifestBytes) else ManifestCheck.BadSignature
    }
}
