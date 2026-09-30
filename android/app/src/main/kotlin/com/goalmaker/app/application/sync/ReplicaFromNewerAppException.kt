package com.goalmaker.app.application.sync

/**
 * The replica has a migration this app does not know, which is what a newer GoalMaker leaves behind
 * when an older one is installed over it (M6-06). Nothing is wrong with the data; updating the app
 * opens it again.
 */
class ReplicaFromNewerAppException(message: String) : Exception(message)
