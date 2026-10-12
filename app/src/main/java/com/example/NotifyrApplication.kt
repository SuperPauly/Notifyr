package com.example

import android.app.Application
import com.example.data.local.NotifyrDatabase
import com.example.data.repository.InboxRepository
import com.example.data.repository.NotificationRepository
import com.example.data.repository.OutboxRepository
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.ServerRepository
import com.example.data.transport.ApiInboxTransport
import com.example.data.transport.ApiResponseTransport
import com.example.data.transport.InboxTransport
import com.example.data.transport.NotificationResponseTransport
import com.example.data.unifiedpush.UnifiedPushManager
import com.example.data.worker.InboxSyncWorker
import com.example.ui.notifications.NotificationHelper

class NotifyrApplication : Application() {

    lateinit var database: NotifyrDatabase
        private set
    lateinit var serverRepository: ServerRepository
        private set
    lateinit var notificationRepository: NotificationRepository
        private set
    lateinit var outboxRepository: OutboxRepository
        private set
    lateinit var inboxRepository: InboxRepository
        private set
    lateinit var preferencesRepository: PreferencesRepository
        private set
    lateinit var responseTransport: NotificationResponseTransport
        private set
    lateinit var unifiedPushManager: UnifiedPushManager
        private set
    lateinit var inboxTransport: InboxTransport
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = NotifyrDatabase.getInstance(this)
        serverRepository = ServerRepository(database.serverDao())
        val inboxTransport = ApiInboxTransport(debugLogging = BuildConfig.DEBUG)
        this.inboxTransport = inboxTransport
        inboxRepository = InboxRepository(
            context = this,
            notificationDao = database.notificationDao(),
            serverDao = database.serverDao(),
            transport = inboxTransport
        )
        notificationRepository = NotificationRepository(
            database.notificationDao(),
            database.serverDao(),
            inboxRepository
        )
        outboxRepository = OutboxRepository(
            context = this,
            outboxDao = database.outboxDao(),
            notificationDao = database.notificationDao(),
            serverDao = database.serverDao()
        )
        preferencesRepository = PreferencesRepository(this)
        unifiedPushManager = UnifiedPushManager(this, inboxTransport)
        responseTransport = ApiResponseTransport(debugLogging = BuildConfig.DEBUG)

        NotificationHelper.createNotificationChannels(this)
        InboxSyncWorker.enqueue(this)
    }

    companion object {
        lateinit var instance: NotifyrApplication
            private set
    }
}
