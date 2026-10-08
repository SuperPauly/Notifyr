package com.example.data.transport

import com.example.data.model.ActionItem
import com.example.data.model.ActionKind
import com.example.data.model.ConnectionStatus
import com.example.data.model.NotificationEntity
import com.example.data.model.OutboxEntity
import com.example.data.model.ServerEntity
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong

class DemoNotificationTransport : NotificationDeliveryTransport, NotificationResponseTransport {

    private val idCounter = AtomicLong(500)

    val defaultServers: List<ServerEntity> = listOf(
        ServerEntity(
            connectionId = "conn-prod-us",
            serverId = "srv-prod-us",
            displayName = "US-East Production Cloud",
            baseUrl = "https://notifyr-gateway.us-east.prod.internal",
            accountIdentity = "alex.ops@company.io",
            encryptedAuthToken = com.example.data.security.KeystoreManager().encrypt("tok_prod_sec_9941a87e2b"),
            colorHex = 0xFF4F46E5, // Indigo
            isEnabled = true,
            connectionStatus = ConnectionStatus.CONNECTED
        ),
        ServerEntity(
            connectionId = "conn-staging-eu",
            serverId = "srv-staging-eu",
            displayName = "EU-West Staging & CI/CD",
            baseUrl = "https://notifyr.eu-west.staging.cloud",
            accountIdentity = "ci.automation@company.io",
            encryptedAuthToken = com.example.data.security.KeystoreManager().encrypt("tok_stg_dev_33890c21"),
            colorHex = 0xFF059669, // Emerald
            isEnabled = true,
            connectionStatus = ConnectionStatus.CONNECTED
        )
    )

    fun getInitialDemoNotifications(): List<NotificationEntity> {
        val now = System.currentTimeMillis()
        return listOf(
            // --- SERVER 1: srv-prod-us ---
            NotificationEntity(
                id = 101L, // Positive Kotlin Long (protobuf int64)
                createdAt = now - (8 * 60 * 1000), // 8 mins ago
                fromServer = "srv-prod-us",
                fromApp = "auth-gate",
                title = "Elevated IAM Admin Role Requested",
                body = "Staff Engineer alex@company.io has requested 15-minute temporary break-glass access to Production Aurora PG Cluster. Reason: Live deadlock incident mitigation #992.",
                actions = listOf(
                    ActionItem("approve_15m", "Grant Access (15m)", ActionKind.BUTTON),
                    ActionItem("deny_access", "Deny Request", ActionKind.BUTTON)
                ),
                expiresAt = now + (45 * 60 * 1000), // expires in 45m
                openPath = "/security/iam/requests/req-8821",
                isRead = false,
                isResponded = false
            ),
            NotificationEntity(
                id = 102L,
                createdAt = now - (24 * 60 * 1000), // 24 mins ago
                fromServer = "srv-prod-us",
                fromApp = "incident-bot",
                title = "Sev-1 Alert: Payment Gateway 504 Timeouts",
                body = "Elevated 504 Gateway Timeout rate on checkout /v2/charge (8.4% error rate in us-east-1). On-call engineer: please enter triage assessment notes or runbook dispatch code.",
                actions = listOf(
                    ActionItem("triage_notes", "Submit Triage Note", ActionKind.TEXT_REPLY)
                ),
                expiresAt = null,
                openPath = "/incidents/sev1-992/war-room",
                isRead = false,
                isResponded = false
            ),
            NotificationEntity(
                id = 103L,
                createdAt = now - (52 * 60 * 1000), // 52 mins ago
                fromServer = "srv-prod-us",
                fromApp = "k8s-sentinel",
                title = "Memory Pressure Alert: worker-node-07",
                body = "cgroup memory allocation exceeded 94% on worker-node-07 (Zone: us-east-1a). Node condition 'MemoryPressure' is active.",
                actions = listOf(
                    ActionItem("drain_node", "Drain Node", ActionKind.BUTTON),
                    ActionItem("restart_pods", "Restart Heavy Pods", ActionKind.BUTTON),
                    ActionItem("ack_node", "Acknowledge", ActionKind.BUTTON)
                ),
                expiresAt = null,
                openPath = "/k8s/clusters/prod-us/nodes/worker-node-07",
                isRead = true,
                isResponded = false
            ),
            NotificationEntity(
                id = 104L,
                createdAt = now - (4 * 3600 * 1000), // 4 hours ago
                fromServer = "srv-prod-us",
                fromApp = "billing-service",
                title = "Monthly Cloud Spend Invoice Ready",
                body = "Consolidated cloud billing statement for previous month finalized at $14,280.40 USD. No budgetary anomalies detected.",
                actions = emptyList(), // Ordinary notification
                expiresAt = null,
                openPath = "/finance/invoices/2026-09",
                isRead = true,
                isResponded = false
            ),

            // --- SERVER 2: srv-staging-eu ---
            // Note: id = 101L on srv-staging-eu intentionally shares the numeric ID with srv-prod-us
            // to verify that composite partitioning (from_server, id) isolates records properly!
            NotificationEntity(
                id = 101L,
                createdAt = now - (12 * 60 * 1000), // 12 mins ago
                fromServer = "srv-staging-eu",
                fromApp = "github-webhook",
                title = "PR #342: Staging Deployment Gate",
                body = "Release build v2.14.0-rc3 passed all 482 integration tests. Target environment 'eu-west-staging' is awaiting manual deployment gate approval.",
                actions = listOf(
                    ActionItem("deploy_staging", "Deploy to Staging", ActionKind.BUTTON),
                    ActionItem("reject_build", "Reject Build", ActionKind.BUTTON)
                ),
                expiresAt = now + (2 * 3600 * 1000),
                openPath = "/ci/pipelines/staging-gate-342",
                isRead = false,
                isResponded = false
            ),
            NotificationEntity(
                id = 202L,
                createdAt = now - (35 * 60 * 1000),
                fromServer = "srv-staging-eu",
                fromApp = "customer-desk",
                title = "Tier-1 VIP Ticket Escalation #4029",
                body = "Customer 'Acquired Logistics' reported an ingest rate disparity on the EU webhook channel. Escalated to Tier-1 support. Please submit a direct client response.",
                actions = listOf(
                    ActionItem("customer_reply", "Send VIP Customer Reply", ActionKind.TEXT_REPLY)
                ),
                expiresAt = now + (30 * 60 * 1000),
                openPath = "/support/tickets/4029",
                isRead = false,
                isResponded = false
            ),
            NotificationEntity(
                id = 203L,
                createdAt = now - (90 * 60 * 1000),
                fromServer = "srv-staging-eu",
                fromApp = "feature-flags",
                title = "Flag 'checkout_redesign_v2' Enabled",
                body = "Audit log: Engineer sophie@test.eu enabled flag 'checkout_redesign_v2' for 50% canary traffic in EU staging cluster.",
                actions = emptyList(), // Ordinary notification
                expiresAt = null,
                openPath = "/feature-flags/env/staging/flags/checkout_redesign_v2",
                isRead = true,
                isResponded = false
            )
        )
    }

    override suspend fun fetchNotifications(server: ServerEntity): Result<List<NotificationEntity>> {
        // Simulate network latency
        delay(400)
        val filtered = getInitialDemoNotifications().filter { it.fromServer == server.serverId }
        return Result.success(filtered)
    }

    override suspend fun testConnection(server: ServerEntity): Result<String> {
        delay(500)
        return if (server.baseUrl.isNotBlank()) {
            Result.success("Connection verified (HTTP 200 OK, latency 42ms)")
        } else {
            Result.failure(IllegalArgumentException("Server URL cannot be empty"))
        }
    }

    override suspend fun deliverResponse(server: ServerEntity, response: OutboxEntity): Result<Unit> {
        // Simulate remote response dispatch
        delay(600)
        return Result.success(Unit)
    }

    fun generateSimulatedNotification(server: ServerEntity): NotificationEntity {
        val nextId = idCounter.incrementAndGet()
        val now = System.currentTimeMillis()
        val appOptions = listOf("auth-gate", "incident-bot", "k8s-sentinel", "github-webhook", "system-monitor")
        val app = appOptions.random()

        return when ((1..3).random()) {
            1 -> NotificationEntity(
                id = nextId,
                createdAt = now,
                fromServer = server.serverId,
                fromApp = app,
                title = "Interactive Approval Needed on ${server.displayName}",
                body = "Action item #$nextId dispatched by $app. Approval required for operational routine.",
                actions = listOf(
                    ActionItem("btn_accept_$nextId", "Approve", ActionKind.BUTTON),
                    ActionItem("btn_deny_$nextId", "Decline", ActionKind.BUTTON)
                ),
                expiresAt = now + (20 * 60 * 1000),
                openPath = "/tasks/$nextId",
                isRead = false,
                isResponded = false
            )
            2 -> NotificationEntity(
                id = nextId,
                createdAt = now,
                fromServer = server.serverId,
                fromApp = app,
                title = "Input Requested by $app",
                body = "Automated prompt #$nextId: Please provide verification code or triage comments.",
                actions = listOf(
                    ActionItem("reply_$nextId", "Send Reply", ActionKind.TEXT_REPLY)
                ),
                expiresAt = now + (30 * 60 * 1000),
                openPath = "/prompts/$nextId",
                isRead = false,
                isResponded = false
            )
            else -> NotificationEntity(
                id = nextId,
                createdAt = now,
                fromServer = server.serverId,
                fromApp = app,
                title = "Status Update from $app",
                body = "Routine health checkpoint passed with zero regressions for $app on ${server.displayName}.",
                actions = emptyList(),
                expiresAt = null,
                openPath = "/dashboard/$app",
                isRead = false,
                isResponded = false
            )
        }
    }
}
