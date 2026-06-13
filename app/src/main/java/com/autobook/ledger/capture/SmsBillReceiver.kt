package com.autobook.ledger.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.autobook.ledger.AutoLedgerApp
import com.autobook.ledger.domain.SourceKind
import kotlinx.coroutines.launch

/**
 * 短信账单接收器
 * 使用 goAsync() 确保数据库写入完成后再调用 finish()，
 * 防止系统因 BroadcastReceiver 超时而杀死进程导致账单丢失。
 */
class SmsBillReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // 使用 goAsync 延长 BroadcastReceiver 生命周期
        val pendingResult = goAsync()

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val body = messages.joinToString("") { it.messageBody.orEmpty() }
        val sender = messages.firstOrNull()?.originatingAddress.orEmpty()
        if (body.isBlank()) {
            pendingResult.finish()
            return
        }

        // 使用全局 ApplicationScope 执行数据库写入
        val app = context.applicationContext as? AutoLedgerApp
        if (app == null) {
            pendingResult.finish()
            return
        }

        app.applicationScope.launch {
            try {
                val repository = app.ledgerRepository
                repository.capture(
                    sourceKind = SourceKind.SMS,
                    sourcePackage = null,
                    sourceAppName = sender.ifBlank { "短信" },
                    title = sender.ifBlank { "短信账单" },
                    text = body,
                    timestampMillis = System.currentTimeMillis(),
                )
            } finally {
                // 确保无论成功还是失败都调用 finish()
                pendingResult.finish()
            }
        }
    }
}

