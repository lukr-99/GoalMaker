package com.goalmaker.app.application.sync

/**
 * No session the server accepts: nobody is signed in, or the server ended the session (a refresh it
 * refused, a token it turned away even after a refresh). Retrying cannot help until someone signs
 * in; everything stays queued for then (docs/sign-in.md).
 */
class NotSignedInException(message: String) : RemoteUnavailableException(message)
