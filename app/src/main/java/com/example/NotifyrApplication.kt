package com.example

import android.app.Application
import com.example.data.local.NotifyrDatabase
import com.example.data.repository.NotificationRepository
import com.example.data.repository.OutboxRepository
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.ServerRepository
import com.example.data.transport.ApiResponseTransport
import com.example.data.transport.NotificationResponseTransport
import com.example.data.unifiedpush.UnifiedPushManager
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
    lateinit var preferencesRepository: PreferencesRepository
        private set
    lateinit var responseTransport: NotificationResponseTransport
        private set
    lateinit var unifiedPushManager: UnifiedPushManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = NotifyrDatabase.getInstance(this)
        serverRepository = ServerRepository(database.serverDao())
        notificationRepository = NotificationRepository(
            database.notificationDao(),
            database.serverDao()
        )
        outboxRepository = OutboxRepository(
            context = this,
            outboxDao = database.outboxDao(),
            notificationDao = database.notificationDao(),
            serverDao = database.serverDao()
        )
        preferencesRepository = PreferencesRepository(this)
        unifiedPushManager = UnifiedPushManager(this)
        responseTransport = ApiResponseTransport(debugLogging = BuildConfig.DEBUG)

        NotificationHelper.createNotificationChannels(this)
    }

    companion object {
        lateinit var instance: NotifyrApplication
            private set
    }
}
