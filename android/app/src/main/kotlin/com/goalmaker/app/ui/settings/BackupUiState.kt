package com.goalmaker.app.ui.settings

import com.goalmaker.app.application.backup.RestoreReport
import com.goalmaker.app.domain.backup.BackupProblem

/**
 * What the Your data card is doing (docs/backup.md). [pending] is a file that has been read and
 * checked but not restored yet: the card shows [preview] and waits for the owner. [problem] is why
 * a file was refused, and nothing was written.
 */
data class BackupUiState(
    val pending: String? = null,
    val preview: RestoreReport? = null,
    val report: RestoreReport? = null,
    val problem: BackupProblem? = null,
    val outcome: BackupOutcome? = null,
) {
    val isAsking: Boolean get() = pending != null && preview != null

    val isSaying: Boolean get() = problem != null || outcome != null
}
