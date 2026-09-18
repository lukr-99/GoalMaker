package com.goalmaker.app.application.update

/** Checks a detached signature over exact bytes against the key built into the app (ADR 0004). */
fun interface SignatureVerifier {
    fun verify(data: ByteArray, signatureBase64: String): Boolean
}
