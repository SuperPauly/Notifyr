package com.example.data.remote

import com.example.data.model.ConnectionStatus

/**
 * Single source of truth mapping probe results to a [ConnectionStatus].
 * [ConnectionStatus.CONNECTED] is unreachable unless both probes succeed.
 */
object ConnectionStateEvaluator {
    fun evaluate(serverInfo: ServerInfoResult, me: MeResult): ConnectionStatus = when {
        serverInfo is ServerInfoResult.Success && me is MeResult.Success -> ConnectionStatus.CONNECTED
        serverInfo is ServerInfoResult.Failure -> when (serverInfo.error) {
            ServerInfoError.UNREACHABLE, ServerInfoError.HTTP -> ConnectionStatus.UNREACHABLE
            ServerInfoError.INVALID_SERVER_ID,
            ServerInfoError.INVALID_VAPID_KEY,
            ServerInfoError.CONTRACT_MISMATCH,
            ServerInfoError.MALFORMED -> ConnectionStatus.SERVER_MISMATCH
        }
        me is MeResult.Failure -> when (me.error) {
            MeError.AUTH_REQUIRED,
            MeError.INVALID_IDENTITY,
            MeError.SCOPE_NOT_RECIPIENT -> ConnectionStatus.AUTH_FAILED
            MeError.UNREACHABLE -> ConnectionStatus.UNREACHABLE
            MeError.HTTP, MeError.MALFORMED -> ConnectionStatus.SERVER_MISMATCH
        }
        else -> ConnectionStatus.UNCONNECTED
    }
}
