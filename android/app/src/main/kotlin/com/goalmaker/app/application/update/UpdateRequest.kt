package com.goalmaker.app.application.update

/** What the update notification asked for when it was tapped. */
enum class UpdateRequest {
    /** Open Settings at the update. */
    SHOW,

    /** Open Settings at the update and start Install. */
    INSTALL,
}
