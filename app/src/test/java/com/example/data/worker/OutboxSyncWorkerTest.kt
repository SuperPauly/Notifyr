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

@RunWith(RobolectricTestRunner::class)
class OutboxSyncWorkerTest {

    private val uniqueWorkName = "notifyr_outbox_sync"

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @Test
    fun `repeated enqueue coalesces into one network-constrained job`() {
        OutboxSyncWorker.enqueue(context)
        OutboxSyncWorker.enqueue(context)

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(uniqueWorkName)
            .get()

        assertEquals(1, workInfos.size)
        assertEquals(NetworkType.CONNECTED, workInfos.first().constraints.requiredNetworkType)
    }
}
