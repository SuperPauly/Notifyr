package com.example.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the sync worker's scheduling contract: a single unique, network-constrained job
 * so repeated enqueues (startup, resume, network, push) coalesce instead of stacking.
 */
@RunWith(RobolectricTestRunner::class)
class InboxSyncWorkerTest {

    private val uniqueWorkName = "notifyr_inbox_sync"

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @Test
    fun `repeated enqueue coalesces into one network-constrained job`() {
        InboxSyncWorker.enqueue(context)
        InboxSyncWorker.enqueue(context)

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(uniqueWorkName)
            .get()

        assertEquals(1, workInfos.size)
        assertEquals(NetworkType.CONNECTED, workInfos.first().constraints.requiredNetworkType)
    }
}
