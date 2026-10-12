package com.example.data.unifiedpush

import android.content.Context
import com.example.NotifyrApplication
import com.example.data.local.ServerDao
import com.example.data.model.ServerEntity
import com.example.data.transport.InboxTransport
import com.example.data.worker.InboxSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import java.util.UUID

/**
 * Connector-backed UnifiedPush endpoint. The connector binds each callback to the `instance`
 * token the app registered with, which is the server profile's `connectionId`; that token is
 * the only thing tying a callback to a server, so an unknown instance is dropped.
 *
 * Trust boundary: the connector decrypts RFC8291 on the client's behalf and exposes no sender
 * verification hook, so delivery is trusted by endpoint token only. No sender UID or signature
 * is asserted here, and payload-level authorization stays with the server.
 */
class NotifyrPushService : PushService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        val url = endpoint.url
        val keySet = endpoint.pubKeySet
        if (instance.isBlank() || url.isBlank() || !url.startsWith("https://")) return
        if (keySet == null ||
            !EndpointValidation.isValidPublicKey(keySet.pubKey) ||
            !EndpointValidation.isValidAuthSecret(keySet.auth) ||
            !EndpointValidation.isValidEndpoint(url)
        ) return

        serviceScope.launch {
            val app = applicationContext as? NotifyrApplication ?: return@launch
            updateEndpoint(app, instance, url, keySet.pubKey, keySet.auth)
        }
    }

    override fun onMessage(message: PushMessage, instance: String) {
        if (instance.isBlank() || !message.decrypted) return
        val content = message.content.toString(Charsets.UTF_8)
        if (content.isBlank()) return

        serviceScope.launch {
            routeMessage(applicationContext, instance, content)
        }
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        if (instance.isBlank()) return
        serviceScope.launch {
            val app = applicationContext as? NotifyrApplication ?: return@launch
            val serverDao = app.database.serverDao()
            val server = serverDao.getServerByConnectionIdDirect(instance) ?: return@launch
            serverDao.updateUnifiedPushState(
                connectionId = instance,
                distributor = server.upDistributor,
                endpoint = null,
                status = "Registration Failed: ${reason.name}"
            )
        }
    }

    override fun onUnregistered(instance: String) {
        if (instance.isBlank()) return
        serviceScope.launch {
            val app = applicationContext as? NotifyrApplication ?: return@launch
            app.database.serverDao().updateUnifiedPushState(
                connectionId = instance,
                distributor = null,
                endpoint = null,
                status = UnifiedPushConstants.STATUS_UNREGISTERED
            )
        }
    }

    override fun onTempUnavailable(instance: String) {
        if (instance.isBlank()) return
        serviceScope.launch {
            val app = applicationContext as? NotifyrApplication ?: return@launch
            val serverDao = app.database.serverDao()
            val server = serverDao.getServerByConnectionIdDirect(instance) ?: return@launch
            serverDao.updateUnifiedPushState(
                connectionId = instance,
                distributor = server.upDistributor,
                endpoint = server.upEndpoint,
                status = UnifiedPushConstants.STATUS_TEMPORARILY_UNAVAILABLE
            )
        }
    }

    private suspend fun updateEndpoint(
        app: NotifyrApplication,
        instance: String,
        endpoint: String,
        pubKey: String,
        auth: String
    ) {
        val serverDao = app.database.serverDao()
        val server = serverDao.getServerByConnectionIdDirect(instance) ?: return
        val installationId = server.installationId ?: UUID.randomUUID().toString()
        val keyStore = KeyMaterialStore(app)
        if (!keyStore.storeKeyMaterial(instance, pubKey, auth)) return

        serverDao.updateUnifiedPushState(
            connectionId = instance,
            distributor = server.connectorPackage ?: server.upDistributor,
            endpoint = endpoint,
            status = UnifiedPushConstants.STATUS_REGISTERING
        )
        val transport: InboxTransport = app.inboxTransport
        val status = transport.registerSubscription(
            server = server,
            installationId = installationId,
            deliveryType = UnifiedPushConstants.DELIVERY_TYPE,
            endpoint = endpoint,
            p256dh = pubKey,
            auth = auth
        )

        serverDao.updateSubscriptionState(
            connectionId = instance,
            installationId = installationId,
            p256dh = pubKey,
            auth = auth,
            distributor = server.connectorPackage ?: server.upDistributor.orEmpty(),
            endpoint = endpoint,
            version = status?.version ?: server.subscriptionVersion,
            subscriptionStatus = status?.status ?: server.subscriptionStatus,
            status = status?.status ?: UnifiedPushConstants.STATUS_REGISTERED_AWAITING_SERVER
        )
        serverDao.updateUnifiedPushState(
            connectionId = instance,
            distributor = server.connectorPackage ?: server.upDistributor,
            endpoint = endpoint,
            status = status?.status ?: UnifiedPushConstants.STATUS_REGISTERED_AWAITING_SERVER
        )
    }

    private suspend fun routeMessage(context: Context, instance: String, content: String) {
        val app = context.applicationContext as? NotifyrApplication ?: return
        val serverDao = app.database.serverDao()
        val server = serverDao.getServerByConnectionIdDirect(instance) ?: return

        when (val decoded = UnifiedPushPayloadDecoder.decodePayload(
            rawBytes = null,
            rawString = content,
            server = server
        )) {
            is DecodeResult.Notification -> app.inboxRepository.applyInlineNotification(decoded)
            is DecodeResult.StateUpdate -> app.inboxRepository.applyStateUpdate(decoded)
            is DecodeResult.Reference -> app.inboxRepository.resolveReference(decoded)
            is DecodeResult.RegistrationChallenge ->
                app.unifiedPushManager.handleRegistrationChallenge(server, decoded)
            is DecodeResult.Ignored -> Unit
        }
        InboxSyncWorker.enqueue(context)
    }
}
