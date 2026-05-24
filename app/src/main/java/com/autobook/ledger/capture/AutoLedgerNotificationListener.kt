package com.autobook.ledger.capture

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.autobook.ledger.data.LedgerDatabase
import com.autobook.ledger.data.LedgerRepository
import com.autobook.ledger.domain.SourceKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AutoLedgerNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
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

        scope.launch {
            val repository = LedgerRepository(LedgerDatabase.get(applicationContext).ledgerDao())
            repository.capture(
                sourceKind = SourceKind.NOTIFICATION,
                sourcePackage = sbn.packageName,
                sourceAppName = sourceAppName,
                title = title,
                text = text,
                timestampMillis = sbn.postTime,
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

