package com.example.data.model

/**
 * Honest connection lifecycle for a configured server.
 *
 * Status is derived exclusively from real probe results
 * (`/v1/server-info` + `/v1/me`); nothing sets [CONNECTED] without both succeeding.
 */
enum class ConnectionStatus {
    UNCONNECTED,
    VERIFYING,
    CONNECTED,
    AUTH_FAILED,
    SERVER_MISMATCH,
    UNREACHABLE
}
