package com.example.data.remote

import com.example.data.model.ConnectionStatus
import com.example.data.remote.model.MeDto
import com.example.data.remote.model.ServerInfoDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ConnectionStateTest {

    private val successInfo = ServerInfoResult.Success(
        ServerInfoDto("11111111-2222-4333-8444-555555555555", "key", "notifications.v2")
    )
    private val successMe = MeResult.Success(MeDto("recipient", userId = "alice"))

    private val infoResults = buildList {
        add(successInfo)
        ServerInfoError.entries.forEach { add(ServerInfoResult.Failure(it, "info failure")) }
    }
    private val meResults = buildList {
        add(successMe)
        MeError.entries.forEach { add(MeResult.Failure(it, "me failure")) }
    }

    @Test
    fun `connected requires both probes to succeed`() {
        for (info in infoResults) {
            for (me in meResults) {
                val status = ConnectionStateEvaluator.evaluate(info, me)
                val bothSucceeded = info === successInfo && me === successMe
                if (bothSucceeded) {
                    assertEquals(ConnectionStatus.CONNECTED, status)
                } else {
                    assertNotEquals(
                        "CONNECTED leaked from info=${info::class.simpleName} me=${me::class.simpleName}",
                        ConnectionStatus.CONNECTED,
                        status
                    )
                }
            }
        }
    }

    @Test
    fun `auth failures map to auth failed`() {
        assertEquals(
            ConnectionStatus.AUTH_FAILED,
            ConnectionStateEvaluator.evaluate(successInfo, MeResult.Failure(MeError.AUTH_REQUIRED, "x"))
        )
        assertEquals(
            ConnectionStatus.AUTH_FAILED,
            ConnectionStateEvaluator.evaluate(successInfo, MeResult.Failure(MeError.SCOPE_NOT_RECIPIENT, "x"))
        )
    }

    @Test
    fun `discovery mismatches map to server mismatch`() {
        assertEquals(
            ConnectionStatus.SERVER_MISMATCH,
            ConnectionStateEvaluator.evaluate(ServerInfoResult.Failure(ServerInfoError.CONTRACT_MISMATCH, "x"), successMe)
        )
    }

    @Test
    fun `network failures map to unreachable`() {
        assertEquals(
            ConnectionStatus.UNREACHABLE,
            ConnectionStateEvaluator.evaluate(ServerInfoResult.Failure(ServerInfoError.UNREACHABLE, "x"), successMe)
        )
    }
}
