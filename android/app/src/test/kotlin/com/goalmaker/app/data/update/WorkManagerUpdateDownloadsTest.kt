package com.goalmaker.app.data.update

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The background download never costs mobile data or a flat battery. */
class WorkManagerUpdateDownloadsTest {
    @Test
    fun `the download waits for an unmetered network and a battery that is not low`() {
        val request = WorkManagerUpdateDownloads.request()
        val constraints = request.workSpec.constraints

        assertEquals(NetworkType.UNMETERED, constraints.requiredNetworkType)
        assertTrue(constraints.requiresBatteryNotLow())
        assertEquals(UpdateDownloadWorker::class.java.name, request.workSpec.workerClassName)
    }
}
