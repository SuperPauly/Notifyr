package com.example.data.unifiedpush

import android.content.Context
import com.example.data.model.ServerEntity
import com.example.data.transport.InboxTransport
import com.example.ui.notifications.NotificationHelper
import org.unifiedpush.android.connector.UnifiedPush

data class DistributorInfo(
    val packageName: String,
    val appName: String,
    val isInstalled: Boolean = true
)

class UnifiedPushManager(
    private val context: Context,
    private val inboxTransport: InboxTransport? = null
) {

    /**
     * Discovers all installed push distributors through the connector's own resolver.
     */
    fun discoverDistributors(): List<DistributorInfo> =
        UnifiedPush.getDistributors(context)
            .filter { it != context.packageName }
            .map { pkg -> DistributorInfo(packageName = pkg, appName = appLabel(pkg)) }

    /**
     * Registers [server.connectionId] as the connector instance. The connectionId is the token
     * that binds every subsequent distributor callback back to this server profile.
     */
    fun register(server: ServerEntity, distributorPackage: String) {
        UnifiedPush.saveDistributor(context, distributorPackage)
        registerInstance(server, server.vapidPublicKey)
    }

    /** Re-registers an already-configured instance, e.g. after the endpoint went temporarily away. */
    fun reRegister(server: ServerEntity) {
        registerInstance(server, server.vapidPublicKey)
    }

    /** Unregisters the server's connector instance from its saved distributor. */
    suspend fun unregister(server: ServerEntity) {
        // Server-first deletion: call DELETE /v1/subscriptions before connector unregister
        inboxTransport?.deleteSubscription(server)
        
        UnifiedPush.unregister(context, server.connectionId)
        KeyMaterialStore(context).clearKeyMaterial(server.connectionId)
    }

    private fun registerInstance(server: ServerEntity, vapid: String?) {
        val messageForDistributor = server.displayName.ifBlank { server.serverId }
        try {
            UnifiedPush.register(
                context = context,
                instance = server.connectionId,
                messageForDistributor = messageForDistributor,
                vapid = vapid
            )
        } catch (_: UnifiedPush.VapidNotValidException) {
            // The stored VAPID key is not a valid base64url uncompressed P-256 point; register
            // without it rather than crash, and let the distributor decide whether it is required.
            UnifiedPush.register(
                context = context,
                instance = server.connectionId,
                messageForDistributor = messageForDistributor,
                vapid = null
            )
        }
    }

    private fun appLabel(packageName: String): String = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)

    /**
     * Handles a `registration_challenge` envelope. The visible notification is posted first and
     * synchronously, because the UnifiedPush `userVisibleOnly` contract requires every received
     * message to surface a user-visible notification; the server confirmation follows.
     */
    suspend fun handleRegistrationChallenge(
        server: ServerEntity,
        challenge: DecodeResult.RegistrationChallenge
    ): Boolean {
        if (challenge.installationId != server.installationId ||
            challenge.subscriptionVersion != server.subscriptionVersion ||
            server.subscriptionStatus == "active"
        ) return false

        NotificationHelper.postVisibleMessage(
            context = context,
            title = challenge.visibleTitle,
            body = challenge.visibleBody,
            tag = "up_challenge_${challenge.installationId}"
        )
        val confirmed = inboxTransport?.confirmSubscription(
            server = server,
            installationId = challenge.installationId,
            version = challenge.subscriptionVersion,
            challenge = challenge.challenge
        ) ?: false
        if (confirmed) {
            val app = context.applicationContext as? com.example.NotifyrApplication ?: return false
            app.database.serverDao().updateServer(
                server.copy(subscriptionStatus = "active")
            )
        }
        return confirmed
    }
}
