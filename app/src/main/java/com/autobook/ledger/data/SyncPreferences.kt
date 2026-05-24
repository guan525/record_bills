package com.autobook.ledger.data

import android.content.Context
import java.security.SecureRandom

class SyncPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("auto-ledger-sync", Context.MODE_PRIVATE)

    fun getOrCreateOwnerKey(): String {
        val existing = prefs.getString(KEY_OWNER, null)
        if (!existing.isNullOrBlank()) return existing
        val generated = ByteArray(24).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        prefs.edit().putString(KEY_OWNER, generated).apply()
        return generated
    }

    fun setOwnerKey(ownerKey: String) {
        prefs.edit().putString(KEY_OWNER, ownerKey.trim()).apply()
    }

    companion object {
        private const val KEY_OWNER = "owner_key"
    }
}

