package com.example.data.repository

import com.example.data.local.ServerDao
import com.example.data.model.ConnectionStatus
import com.example.data.model.ServerEntity
import com.example.data.remote.ConnectionStateEvaluator
import com.example.data.remote.MeClient
import com.example.data.remote.MeError
import com.example.data.remote.MeResult
import com.example.data.remote.ServerInfoClient
import com.example.data.remote.ServerInfoResult
import com.example.data.security.KeystoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

class ServerRepository(
    private val serverDao: ServerDao,
    private val keystoreManager: KeystoreManager = KeystoreManager(),
    private val callFactory: Call.Factory = OkHttpClient()
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
            connectionStatus = ConnectionStatus.UNCONNECTED,
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

    /**
     * Probes the server for real: unauthenticated `/v1/server-info`, then bearer `/v1/me`.
     * [ConnectionStatus.CONNECTED] is only ever produced when both probes succeed.
     */
    suspend fun testConnection(server: ServerEntity): ConnectionStatus = withContext(Dispatchers.IO) {
        val status = probe(server)
        serverDao.updateConnectionStatus(
            connectionId = server.connectionId,
            timestamp = System.currentTimeMillis(),
            status = status.name
        )
        status
    }

    private fun probe(server: ServerEntity): ConnectionStatus {
        val baseUrl = server.baseUrl.toHttpUrlOrNull()?.toString()
            ?: return ConnectionStatus.UNREACHABLE
        val info = ServerInfoClient(callFactory, baseUrl).fetch()
        if (info !is ServerInfoResult.Success) {
            return ConnectionStateEvaluator.evaluate(info, MeResult.Failure(MeError.UNREACHABLE, "not probed"))
        }
        val token = keystoreManager.decrypt(server.encryptedAuthToken)
        val me = if (token.isNullOrBlank()) {
            MeResult.Failure(MeError.AUTH_REQUIRED, "No credential configured")
        } else {
            MeClient(callFactory, baseUrl).fetch(token)
        }
        return ConnectionStateEvaluator.evaluate(info, me)
    }

    fun hasEncryptedToken(server: ServerEntity): Boolean {
        return !server.encryptedAuthToken.isNullOrBlank()
    }
}
