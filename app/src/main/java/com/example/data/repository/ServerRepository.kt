package com.example.data.repository

import com.example.data.local.ServerDao
import com.example.data.model.ServerEntity
import com.example.data.security.KeystoreManager
import com.example.data.transport.DemoNotificationTransport
import kotlinx.coroutines.flow.Flow

class ServerRepository(
    private val serverDao: ServerDao,
    private val transport: DemoNotificationTransport,
    private val keystoreManager: KeystoreManager = KeystoreManager()
) {
    val allServers: Flow<List<ServerEntity>> = serverDao.getAllServers()
    val enabledServers: Flow<List<ServerEntity>> = serverDao.getEnabledServers()

    fun getServer(serverId: String): Flow<ServerEntity?> = serverDao.getServerByServerId(serverId)

    fun getServerByConnectionId(connectionId: String): Flow<ServerEntity?> =
        serverDao.getServerByConnectionId(connectionId)

    suspend fun getServerDirect(serverId: String): ServerEntity? =
        serverDao.getServerByServerIdDirect(serverId)

    suspend fun getServerByConnectionIdDirect(connectionId: String): ServerEntity? =
        serverDao.getServerByConnectionIdDirect(connectionId)

    suspend fun ensureDefaultServers() {
        if (serverDao.getServerCount() == 0) {
            serverDao.insertServers(transport.defaultServers)
        }
    }

    suspend fun addServer(
        connectionId: String,
        serverId: String,
        displayName: String,
        baseUrl: String,
        accountIdentity: String?,
        rawAuthToken: String?,
        colorHex: Long,
        isEnabled: Boolean
    ) {
        val encryptedToken = keystoreManager.encrypt(rawAuthToken)
        val server = ServerEntity(
            connectionId = connectionId,
            serverId = serverId,
            displayName = displayName,
            baseUrl = baseUrl,
            accountIdentity = accountIdentity,
            encryptedAuthToken = encryptedToken,
            colorHex = colorHex,
            isEnabled = isEnabled,
            connectionStatus = "Unconnected",
            lastSyncTime = System.currentTimeMillis()
        )
        serverDao.insertServer(server)
    }

    suspend fun updateServer(server: ServerEntity) {
        serverDao.updateServer(server)
    }

    suspend fun deleteServer(connectionId: String) {
        serverDao.deleteServerByConnectionId(connectionId)
    }

    suspend fun testConnection(server: ServerEntity): Result<String> {
        val res = transport.testConnection(server)
        if (res.isSuccess) {
            serverDao.updateConnectionStatus(
                connectionId = server.connectionId,
                timestamp = System.currentTimeMillis(),
                status = "Connected (OK)"
            )
        } else {
            val errMsg = res.exceptionOrNull()?.message ?: "Unreachable"
            serverDao.updateConnectionStatus(
                connectionId = server.connectionId,
                timestamp = System.currentTimeMillis(),
                status = "Connection Failed: $errMsg"
            )
        }
        return res
    }

    fun hasEncryptedToken(server: ServerEntity): Boolean {
        return !server.encryptedAuthToken.isNullOrBlank()
    }
}
