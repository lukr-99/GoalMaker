package com.goalmaker.app.data.update

import com.goalmaker.app.application.update.SignatureVerifier
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * ECDSA P-256 with SHA-256 over the exact manifest bytes; the signature is DER, base64-encoded
 * (ADR 0004). Uses only platform crypto, available on every supported Android version.
 */
class EcdsaSignatureVerifier(publicKeyBase64: String) : SignatureVerifier {

    private val publicKey: PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)))

    override fun verify(data: ByteArray, signatureBase64: String): Boolean {
        val signature = try {
            Base64.getDecoder().decode(signatureBase64.trim())
        } catch (_: IllegalArgumentException) {
            return false
        }
        if (signature.isEmpty()) return false
        return try {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(publicKey)
                update(data)
                verify(signature)
            }
        } catch (_: GeneralSecurityException) {
            false
        }
    }
}
