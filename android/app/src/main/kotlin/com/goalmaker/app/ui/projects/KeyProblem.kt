package com.goalmaker.app.ui.projects

/** Why the key typed in the project form can't be saved (docs/projects.md, "Item ids"). */
enum class KeyProblem {
    /** It is not 2 to 6 letters or digits starting with a letter. */
    NOT_VALID,

    /** Another of the owner's projects already reads by it. */
    TAKEN,
}
