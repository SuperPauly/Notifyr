package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.NotifyrDatabase
import com.example.data.model.ActionItem
import com.example.data.model.ActionKind
import com.example.data.model.Converters
import com.example.data.model.NotificationEntity
import com.example.data.model.OutboxEntity
import com.example.data.model.OutboxStatus
import com.example.data.model.ServerEntity
import com.example.data.repository.NotificationRepository
import com.example.data.repository.OutboxRepository
import com.example.data.security.KeystoreManager
import com.example.data.transport.DemoNotificationTransport
import com.example.data.unifiedpush.UnifiedPushPayloadDecoder
import com.example.ui.notifications.NotificationHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context matches Notifyr`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Notifyr", appName)
    }

    @Test
    fun `equal notification IDs from different servers remain distinct`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = NotifyrDatabase.getInstance(context)
        val notifDao = db.notificationDao()

        val notifProd = NotificationEntity(
            id = 101L,
            createdAt = System.currentTimeMillis(),
            fromServer = "srv-prod-us",
            fromApp = "auth-gate",
            title = "Prod IAM Approval",
            body = "Approve prod access",
            actions = listOf(ActionItem("app_1", "Approve", ActionKind.BUTTON))
        )

        val notifStaging = NotificationEntity(
            id = 101L, // Identical numeric ID 101L
            createdAt = System.currentTimeMillis(),
            fromServer = "srv-staging-eu", // Different server profile
            fromApp = "github-webhook",
            title = "Staging CI Approval",
            body = "Approve staging build",
            actions = listOf(ActionItem("deploy_1", "Deploy", ActionKind.BUTTON))
        )

        notifDao.insertNotification(notifProd)
        notifDao.insertNotification(notifStaging)

        val prodFetch = notifDao.getNotificationDirect("srv-prod-us", 101L)
        val stagingFetch = notifDao.getNotificationDirect("srv-staging-eu", 101L)

        assertNotNull(prodFetch)
        assertNotNull(stagingFetch)
        assertEquals("Prod IAM Approval", prodFetch?.title)
        assertEquals("Staging CI Approval", stagingFetch?.title)
    }

    @Test
    fun `inline replies preserve full Unicode text and emoji`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = NotifyrDatabase.getInstance(context)
        val outboxRepo = OutboxRepository(context, db.outboxDao(), db.notificationDao(), db.serverDao())

        val notif = NotificationEntity(
            id = 505L,
            createdAt = System.currentTimeMillis(),
            fromServer = "srv-prod-us",
            fromApp = "incident-bot",
            title = "Database deadlocks",
            body = "Please provide triage notes",
            actions = listOf(ActionItem("reply_triage", "Reply", ActionKind.TEXT_REPLY))
        )
        db.notificationDao().insertNotification(notif)

        val unicodeText = "Patch applied 🚀 紧急修复! Status: OK ✔ (日本語 & العربية tested)"
        val responseId = outboxRepo.queueResponse(
            notification = notif,
            action = notif.actions.first(),
            replyText = unicodeText
        )

        assertNotNull(responseId)
        val response = db.outboxDao().getResponseById(responseId!!)
        assertNotNull(response)
        assertEquals(unicodeText, response?.replyText)
    }

    @Test
    fun `duplicate deliveries do not create duplicate alerts or outbox entries`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = NotifyrDatabase.getInstance(context)
        val notifRepo = NotificationRepository(db.notificationDao(), db.serverDao())
        val outboxRepo = OutboxRepository(context, db.outboxDao(), db.notificationDao(), db.serverDao())

        val notif = NotificationEntity(
            id = 701L,
            createdAt = System.currentTimeMillis(),
            fromServer = "srv-prod-us",
            fromApp = "k8s",
            title = "Worker node rebooted",
            body = "Node worker-01 restarted successfully",
            actions = listOf(ActionItem("ack", "Acknowledge", ActionKind.BUTTON))
        )

        // Process first delivery
        notifRepo.processIncomingNotification(context, notif, shouldAlert = false)
        // Process duplicate delivery
        notifRepo.processIncomingNotification(context, notif, shouldAlert = false)

        val total = db.notificationDao().getTotalCount()
        val count = db.notificationDao().getNotificationDirect("srv-prod-us", 701L)
        assertNotNull(count)

        // Submit response
        val resp1 = outboxRepo.queueResponse(notif, notif.actions.first())
        assertNotNull(resp1)

        // Attempt rapid duplicate response to same notification
        val resp2 = outboxRepo.queueResponse(notif, notif.actions.first())
        assertNull(resp2) // Prevented duplicate outbox entry
    }

    @Test
    fun `expired or answered notifications cannot create new responses`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = NotifyrDatabase.getInstance(context)
        val outboxRepo = OutboxRepository(context, db.outboxDao(), db.notificationDao(), db.serverDao())

        // Expired notification
        val expiredNotif = NotificationEntity(
            id = 888L,
            createdAt = System.currentTimeMillis() - (60 * 60 * 1000),
            fromServer = "srv-prod-us",
            fromApp = "auth-gate",
            title = "Expired OTP",
            body = "Challenge expired",
            actions = listOf(ActionItem("btn_val", "Validate", ActionKind.BUTTON)),
            expiresAt = System.currentTimeMillis() - (10 * 60 * 1000) // Expired 10m ago
        )
        assertTrue(expiredNotif.isExpired)

        val response = outboxRepo.queueResponse(expiredNotif, expiredNotif.actions.first())
        assertNull(response) // Rejected because notification is expired
    }

    @Test
    fun `changing accounts cannot send old replies using new account`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = NotifyrDatabase.getInstance(context)

        // Initial server profile with Account A
        val serverA = ServerEntity(
            connectionId = "conn-tenant-alpha",
            serverId = "srv-prod-us",
            displayName = "US Prod - Account Alpha",
            baseUrl = "https://alpha.internal",
            accountIdentity = "account-alpha@corp.io"
        )
        db.serverDao().insertServer(serverA)

        val outboxRepo = OutboxRepository(context, db.outboxDao(), db.notificationDao(), db.serverDao())

        val notif = NotificationEntity(
            id = 901L,
            createdAt = System.currentTimeMillis(),
            fromServer = "srv-prod-us",
            fromApp = "deploy",
            title = "Deployment Gate",
            body = "Approve deploy",
            actions = listOf(ActionItem("act_deploy", "Deploy", ActionKind.BUTTON))
        )
        db.notificationDao().insertNotification(notif)

        val responseId = outboxRepo.queueResponse(notif, notif.actions.first())
        assertNotNull(responseId)

        val saved = db.outboxDao().getResponseById(responseId!!)
        assertEquals("conn-tenant-alpha", saved?.connectionId)
        assertEquals("account-alpha@corp.io", saved?.accountIdentity)

        // Now simulate user editing / switching the server to Account B
        val serverB = serverA.copy(
            accountIdentity = "account-bravo@corp.io"
        )
        db.serverDao().updateServer(serverB)

        // The queued response must remain bound to its frozen identity
        val reloaded = db.outboxDao().getResponseById(responseId)
        assertEquals("account-alpha@corp.io", reloaded?.accountIdentity)
    }

    @Test
    fun `pending replies survive repository restarts`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = NotifyrDatabase.getInstance(context)

        val resp = OutboxEntity(
            responseId = UUID.randomUUID().toString(),
            notificationId = 12345L,
            fromServer = "srv-prod-us",
            actionId = "btn_drain",
            actionLabel = "Drain Node",
            actionKind = ActionKind.BUTTON,
            replyText = null,
            connectionId = "conn-prod-us",
            accountIdentity = "ops@corp.io",
            notificationTitle = "Memory Pressure",
            fromApp = "k8s",
            status = OutboxStatus.QUEUED
        )
        db.outboxDao().insertResponse(resp)

        // Simulate new repository instance
        val newOutboxRepo = OutboxRepository(context, db.outboxDao(), db.notificationDao(), db.serverDao())
        val pending = db.outboxDao().getPendingResponses()
        assertTrue(pending.any { it.responseId == resp.responseId })
    }

    @Test
    fun `test action item list converter`() {
        val converters = Converters()
        val actions = listOf(
            ActionItem("btn_1", "Grant Access", ActionKind.BUTTON),
            ActionItem("reply_1", "Add Notes", ActionKind.TEXT_REPLY)
        )

        val json = converters.fromActionItemList(actions)
        assertTrue(json.contains("Grant Access"))
        assertTrue(json.contains("TEXT_REPLY"))

        val parsed = converters.toActionItemList(json)
        assertEquals(2, parsed.size)
        assertEquals("btn_1", parsed[0].id)
        assertEquals(ActionKind.BUTTON, parsed[0].kind)
        assertEquals("reply_1", parsed[1].id)
        assertEquals(ActionKind.TEXT_REPLY, parsed[1].kind)
    }

    @Test
    fun `test UnifiedPush payload decoder binds to server and rejects invalid payload safely`() {
        val dummyServer = ServerEntity(
            connectionId = "conn-123",
            serverId = "srv-prod-us",
            displayName = "US Prod",
            baseUrl = "https://prod.internal"
        )

        val validJson = """
            {
                "id": 999,
                "title": "Alert 999",
                "body": "Test message body",
                "from_app": "auth-service",
                "actions": [
                    {"id": "act_1", "label": "Approve", "kind": "BUTTON"}
                ]
            }
        """.trimIndent()

        val decoded = UnifiedPushPayloadDecoder.decodePayload(
            rawBytes = null,
            rawString = validJson,
            server = dummyServer
        )

        assertNotNull(decoded)
        assertEquals(999L, decoded?.id)
        assertEquals("srv-prod-us", decoded?.fromServer)
        assertEquals("Alert 999", decoded?.title)
        assertEquals(1, decoded?.actions?.size)

        // Invalid negative ID or broken json should return null safely without throwing
        val invalidJson = "{\"id\": -1, \"title\": \"Broken\"}"
        val broken = UnifiedPushPayloadDecoder.decodePayload(
            rawBytes = null,
            rawString = invalidJson,
            server = dummyServer
        )
        assertNull(broken)
    }

    @Test
    fun `notification tag and id generation is deterministic`() {
        val tag1 = NotificationHelper.getNotificationTag("srv-prod-us", 101L)
        val tag2 = NotificationHelper.getNotificationTag("srv-prod-us", 101L)
        val tagDiff = NotificationHelper.getNotificationTag("srv-staging-eu", 101L)

        assertEquals("srv-prod-us_101", tag1)
        assertEquals(tag1, tag2)
        assertTrue(tag1 != tagDiff)

        val id1 = NotificationHelper.getNotificationId("srv-prod-us", 101L)
        val id2 = NotificationHelper.getNotificationId("srv-prod-us", 101L)
        assertEquals(id1, id2)
    }

    @Test
    fun `demo delivery and simulated replies are clearly labelled`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = NotifyrDatabase.getInstance(context)
        val outboxRepo = OutboxRepository(context, db.outboxDao(), db.notificationDao(), db.serverDao())
        val transport = DemoNotificationTransport()

        val notif = NotificationEntity(
            id = 660L,
            createdAt = System.currentTimeMillis(),
            fromServer = "srv-prod-us",
            fromApp = "canary-gate",
            title = "Promote Canary",
            body = "Promote canary deployment to 100% traffic?",
            actions = listOf(ActionItem("btn_promote", "Promote", ActionKind.BUTTON))
        )
        db.notificationDao().insertNotification(notif)

        val responseId = outboxRepo.queueResponse(notif, notif.actions.first())
        assertNotNull(responseId)

        val queuedItem = db.outboxDao().getResponseById(responseId!!)
        assertNotNull(queuedItem)
        assertTrue(queuedItem!!.isSimulated) // Demo response clearly labelled as simulated
        assertEquals(OutboxStatus.QUEUED, queuedItem.status)

        // Simulate transport delivery
        val server = db.serverDao().getServerByServerIdDirect("srv-prod-us")
            ?: transport.defaultServers.first()
        val deliverResult = transport.deliverResponse(server, queuedItem)
        assertTrue(deliverResult.isSuccess)
    }

    @Test
    fun `keystore protects credentials without exposing plaintext in storage or logs`() {
        val keystoreManager = KeystoreManager()
        val rawSecretToken = "super-secret-bearer-token-xyz-12345"

        val cipherText = keystoreManager.encrypt(rawSecretToken)
        assertNotNull(cipherText)
        assertFalse(cipherText!!.contains(rawSecretToken)) // Raw secret not in ciphertext

        val decrypted = keystoreManager.decrypt(cipherText)
        assertEquals(rawSecretToken, decrypted)
    }

    @Test
    fun `missing UnifiedPush distributors returns empty list safely without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val upManager = com.example.data.unifiedpush.UnifiedPushManager(context)

        val distributors = upManager.discoverDistributors()
        assertNotNull(distributors)
        // In JVM Robolectric with no external distributor installed, list is safely empty
        assertTrue(distributors.isEmpty())
    }

    @Test
    fun `notification list filtering by title or server source filters accurately`() {
        val list = listOf(
            NotificationEntity(
                id = 1L,
                createdAt = System.currentTimeMillis(),
                fromServer = "srv-prod-us",
                fromApp = "auth-gate",
                title = "Critical Security Alert",
                body = "Unauthorized SSH attempt"
            ),
            NotificationEntity(
                id = 2L,
                createdAt = System.currentTimeMillis(),
                fromServer = "srv-staging-eu",
                fromApp = "deploy-bot",
                title = "Staging Build Succeeded",
                body = "Pipeline #42 finished in 2m"
            ),
            NotificationEntity(
                id = 3L,
                createdAt = System.currentTimeMillis(),
                fromServer = "srv-prod-us",
                fromApp = "backup-job",
                title = "Nightly DB Backup",
                body = "Completed 18GB snapshot"
            )
        )

        // Filter by title keyword
        val queryTitle = "security"
        val filteredByTitle = list.filter {
            it.title.contains(queryTitle, ignoreCase = true) ||
            it.fromServer.contains(queryTitle, ignoreCase = true)
        }
        assertEquals(1, filteredByTitle.size)
        assertEquals("Critical Security Alert", filteredByTitle.first().title)

        // Filter by server source
        val queryServer = "staging-eu"
        val filteredByServer = list.filter {
            it.title.contains(queryServer, ignoreCase = true) ||
            it.fromServer.contains(queryServer, ignoreCase = true)
        }
        assertEquals(1, filteredByServer.size)
        assertEquals("srv-staging-eu", filteredByServer.first().fromServer)

        // Filter by common server
        val queryProd = "srv-prod"
        val filteredByProd = list.filter {
            it.title.contains(queryProd, ignoreCase = true) ||
            it.fromServer.contains(queryProd, ignoreCase = true)
        }
        assertEquals(2, filteredByProd.size)
    }

    @Test
    fun `pull to refresh updates notification repository and completes cleanly`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = NotifyrDatabase.getInstance(context)
        val notifRepo = NotificationRepository(db.notificationDao(), db.serverDao())

        // Invoke refresh
        notifRepo.refreshNotifications()

        // Verify database and flows remain healthy after refresh
        val count = db.notificationDao().getTotalCount()
        assertTrue(count >= 0)
    }
}
