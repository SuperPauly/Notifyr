package com.example

import android.app.Application
import com.example.data.local.NotifyrDatabase
import com.example.data.repository.NotificationRepository
import com.example.data.repository.OutboxRepository
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.ServerRepository
import com.example.data.transport.DemoNotificationTransport
import com.example.data.unifiedpush.UnifiedPushManager
import com.example.ui.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotifyrApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: NotifyrDatabase
        private set
    lateinit var transport: DemoNotificationTransport
        private set
    lateinit var serverRepository: ServerRepository
        private set
    lateinit var notificationRepository: NotificationRepository
        private set
    lateinit var outboxRepository: OutboxRepository
        private set
    lateinit var preferencesRepository: PreferencesRepository
        private set
    lateinit var unifiedPushManager: UnifiedPushManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = NotifyrDatabase.getInstance(this)
        transport = DemoNotificationTransport()
        serverRepository = ServerRepository(database.serverDao(), transport)
        notificationRepository = NotificationRepository(
            database.notificationDao(),
            database.serverDao(),
            transport
        )
        outboxRepository = OutboxRepository(
            context = this,
            outboxDao = database.outboxDao(),
            notificationDao = database.notificationDao(),
            serverDao = database.serverDao()
        )
        preferencesRepository = PreferencesRepository(this)
        unifiedPushManager = UnifiedPushManager(this)

        NotificationHelper.createNotificationChannels(this)

        applicationScope.launch {
            serverRepository.ensureDefaultServers()
            notificationRepository.ensureInitialData()
        }
    }

    companion object {
        lateinit var instance: NotifyrApplication
            private set
    }
}
