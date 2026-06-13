package com.autobook.ledger.capture

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.autobook.ledger.AutoLedgerApp
import com.autobook.ledger.domain.SourceKind
import kotlinx.coroutines.launch

/**
 * 通知监听服务
 * 仅处理白名单应用的通知，避免无效计算和误判
 */
class AutoLedgerNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        
        // 白名单过滤：非白名单包名直接返回
        if (!isWhitelistedPackage(sbn.packageName)) return
        
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = listOfNotNull(
            extras.getCharSequence("android.text")?.toString(),
            extras.getCharSequence("android.bigText")?.toString(),
            extras.getCharSequence("android.subText")?.toString(),
        ).distinct().joinToString(" ")
        if (title.isBlank() && text.isBlank()) return

        val sourceAppName = runCatching {
            val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(sbn.packageName)

        val app = application as? AutoLedgerApp ?: return
        
        app.applicationScope.launch {
            try {
                val repository = app.ledgerRepository
                repository.capture(
                    sourceKind = SourceKind.NOTIFICATION,
                    sourcePackage = sbn.packageName,
                    sourceAppName = sourceAppName,
                    title = title,
                    text = text,
                    timestampMillis = sbn.postTime,
                )
            } catch (e: Exception) {
                // 静默处理异常，避免影响系统服务
            }
        }
    }

    /**
     * 检查是否为白名单包名
     */
    private fun isWhitelistedPackage(packageName: String): Boolean {
        return WhitelistPackages.ALL.any { it.first == packageName }
    }
}

