package com.autobook.ledger.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.autobook.ledger.data.LedgerDatabase
import com.autobook.ledger.data.LedgerRepository
import com.autobook.ledger.domain.SourceKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsBillReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val body = messages.joinToString("") { it.messageBody.orEmpty() }
        val sender = messages.firstOrNull()?.originatingAddress.orEmpty()
        if (body.isBlank()) return

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val repository = LedgerRepository(LedgerDatabase.get(context).ledgerDao())
            repository.capture(
                sourceKind = SourceKind.SMS,
                sourcePackage = null,
                sourceAppName = sender.ifBlank { "短信" },
                title = sender.ifBlank { "短信账单" },
                text = body,
                timestampMillis = System.currentTimeMillis(),
            )
        }
    }
}

