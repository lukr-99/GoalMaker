package com.goalmaker.app.application.update

/** What the update channel currently publishes, unverified. */
class ChannelSnapshot(
    val manifestBytes: ByteArray,
    val signatureBase64: String,
)
