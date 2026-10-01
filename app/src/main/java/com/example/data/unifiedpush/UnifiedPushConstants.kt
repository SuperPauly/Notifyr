package com.example.data.unifiedpush

object UnifiedPushConstants {
    // Actions sent from connector to distributor
    const val ACTION_REGISTER = "org.unifiedpush.android.distributor.REGISTER"
    const val ACTION_UNREGISTER = "org.unifiedpush.android.distributor.UNREGISTER"

    // Actions sent from distributor to connector
    const val ACTION_NEW_ENDPOINT = "org.unifiedpush.android.connector.NEW_ENDPOINT"
    const val ACTION_UNREGISTERED = "org.unifiedpush.android.connector.UNREGISTERED"
    const val ACTION_MESSAGE = "org.unifiedpush.android.connector.MESSAGE"
    const val ACTION_REGISTRATION_FAILED = "org.unifiedpush.android.connector.REGISTRATION_FAILED"

    // Common extras
    const val EXTRA_APPLICATION = "application"
    const val EXTRA_TOKEN = "token"
    const val EXTRA_ENDPOINT = "endpoint"
    const val EXTRA_MESSAGE = "message"
    const val EXTRA_BYTES_MESSAGE = "bytesMessage"
    const val EXTRA_REASON = "reason"

    // Registration statuses
    const val STATUS_UNREGISTERED = "Not Registered"
    const val STATUS_REGISTERING = "Registering with distributor..."
    const val STATUS_REGISTERED_AWAITING_SERVER = "Registered with distributor (Awaiting server integration)"
    const val STATUS_FAILED = "Distributor Registration Failed"
}
