package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {

    @Query("SELECT * FROM servers ORDER BY display_name ASC")
    fun getAllServers(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers WHERE is_enabled = 1 ORDER BY display_name ASC")
    fun getEnabledServers(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers WHERE is_enabled = 1 ORDER BY display_name ASC")
    suspend fun getEnabledServersDirect(): List<ServerEntity>

    @Query("SELECT * FROM servers WHERE connection_id = :connectionId LIMIT 1")
    fun getServerByConnectionId(connectionId: String): Flow<ServerEntity?>

    @Query("SELECT * FROM servers WHERE connection_id = :connectionId LIMIT 1")
    suspend fun getServerByConnectionIdDirect(connectionId: String): ServerEntity?

    @Query("SELECT * FROM servers WHERE server_id = :serverId ORDER BY rowid DESC LIMIT 1")
    fun getServerByServerId(serverId: String): Flow<ServerEntity?>

    @Query("SELECT * FROM servers WHERE server_id = :serverId ORDER BY rowid DESC LIMIT 1")
    suspend fun getServerByServerIdDirect(serverId: String): ServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: ServerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServers(servers: List<ServerEntity>)

    @Update
    suspend fun updateServer(server: ServerEntity)

    @Delete
    suspend fun deleteServer(server: ServerEntity)

    @Query("DELETE FROM servers WHERE connection_id = :connectionId")
    suspend fun deleteServerByConnectionId(connectionId: String)

    @Query("UPDATE servers SET last_sync_time = :timestamp, connection_status = :status WHERE connection_id = :connectionId")
    suspend fun updateConnectionStatus(connectionId: String, timestamp: Long, status: String)

    @Query("UPDATE servers SET up_distributor = :distributor, up_endpoint = :endpoint, up_status = :status WHERE connection_id = :connectionId")
    suspend fun updateUnifiedPushState(connectionId: String, distributor: String?, endpoint: String?, status: String)

    @Query("UPDATE servers SET installation_id = :installationId, connector_package = :distributor, up_distributor = :distributor, up_endpoint = :endpoint, subscription_version = :version, subscription_status = :subscriptionStatus, up_status = :status WHERE connection_id = :connectionId")
    suspend fun updateSubscriptionState(
        connectionId: String,
        installationId: String,
        distributor: String,
        endpoint: String,
        version: Int?,
        subscriptionStatus: String?,
        status: String
    )

    @Query("UPDATE servers SET inbox_cursor = :cursor WHERE connection_id = :connectionId")
    suspend fun upsertCursor(connectionId: String, cursor: String)

    @Query("SELECT inbox_cursor FROM servers WHERE connection_id = :connectionId LIMIT 1")
    suspend fun getCursor(connectionId: String): String?

    @Query("SELECT COUNT(*) FROM servers")
    suspend fun getServerCount(): Int
}
