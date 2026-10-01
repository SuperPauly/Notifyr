package com.example.data.unifiedpush

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.example.data.model.ServerEntity

data class DistributorInfo(
    val packageName: String,
    val appName: String,
    val isInstalled: Boolean = true
)

class UnifiedPushManager(private val context: Context) {

    /**
     * Discovers all installed push distributors that respond to the UnifiedPush REGISTER broadcast.
     */
    fun discoverDistributors(): List<DistributorInfo> {
        val packageManager = context.packageManager
        val registerIntent = Intent(UnifiedPushConstants.ACTION_REGISTER)

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryBroadcastReceivers(
                registerIntent,
                PackageManager.ResolveInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryBroadcastReceivers(registerIntent, 0)
        }

        val list = mutableListOf<DistributorInfo>()
        for (info in resolveInfos) {
            val pkg = info.activityInfo.packageName
            val label = info.loadLabel(packageManager).toString()
            if (pkg != context.packageName) {
                list.add(DistributorInfo(packageName = pkg, appName = label))
            }
        }
        return list
    }

    /**
     * Registers a server profile with a selected distributor using its unique connectionId token.
     */
    fun register(server: ServerEntity, distributorPackage: String) {
        val registerIntent = Intent(UnifiedPushConstants.ACTION_REGISTER).apply {
            `package` = distributorPackage
            putExtra(UnifiedPushConstants.EXTRA_APPLICATION, context.packageName)
            putExtra(UnifiedPushConstants.EXTRA_TOKEN, server.connectionId)
        }
        context.sendBroadcast(registerIntent)
    }

    /**
     * Unregisters a server connection from its current distributor.
     */
    fun unregister(server: ServerEntity) {
        val distributorPackage = server.upDistributor ?: return
        val unregisterIntent = Intent(UnifiedPushConstants.ACTION_UNREGISTER).apply {
            `package` = distributorPackage
            putExtra(UnifiedPushConstants.EXTRA_APPLICATION, context.packageName)
            putExtra(UnifiedPushConstants.EXTRA_TOKEN, server.connectionId)
        }
        context.sendBroadcast(unregisterIntent)
    }
}
