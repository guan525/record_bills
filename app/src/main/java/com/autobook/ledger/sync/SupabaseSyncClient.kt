package com.autobook.ledger.sync

import com.autobook.ledger.BuildConfig
import com.autobook.ledger.data.LedgerEntryEntity
import com.autobook.ledger.data.LedgerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

data class SyncResult(
    val pushed: Int,
    val pulled: Int,
    val message: String,
)

class SupabaseSyncClient(
    private val repository: LedgerRepository,
) {
    suspend fun sync(ownerKey: String): SyncResult = withContext(Dispatchers.IO) {
        val unsynced = repository.unsyncedEntries()
        if (unsynced.isNotEmpty()) push(ownerKey, unsynced)
        val remote = pull(ownerKey)
        if (remote.isNotEmpty()) repository.upsertEntries(remote)
        repository.markSynced((unsynced.map { it.id } + remote.map { it.id }).distinct())
        SyncResult(
            pushed = unsynced.size,
            pulled = remote.size,
            message = "同步完成：上传 ${unsynced.size} 条，拉取 ${remote.size} 条",
        )
    }

    private fun push(ownerKey: String, entries: List<LedgerEntryEntity>) {
        val url = URL("${BuildConfig.SUPABASE_URL}/rest/v1/ledger_entries?on_conflict=id")
        val body = JSONArray(entries.map { it.toJson(ownerKey) }).toString()
        open(url, "POST", ownerKey).useConnection { connection ->
            connection.setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            connection.requireSuccess("上传")
        }
    }

    private fun pull(ownerKey: String): List<LedgerEntryEntity> {
        val url = URL("${BuildConfig.SUPABASE_URL}/rest/v1/ledger_entries?select=*&is_deleted=eq.false&order=updated_at.desc")
        return open(url, "GET", ownerKey).useConnection { connection ->
            connection.requireSuccess("拉取")
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val array = JSONArray(text)
            (0 until array.length()).map { index -> array.getJSONObject(index).toEntity() }
        }
    }

    private fun open(url: URL, method: String, ownerKey: String): HttpURLConnection {
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            doInput = true
            doOutput = method == "POST" || method == "PATCH"
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_PUBLISHABLE_KEY}")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-owner-key", ownerKey)
        }
    }

    private fun HttpURLConnection.requireSuccess(action: String) {
        if (responseCode !in 200..299) {
            val error = errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw IllegalStateException("$action 失败：HTTP $responseCode $error")
        }
    }

    private inline fun <T> HttpURLConnection.useConnection(block: (HttpURLConnection) -> T): T {
        return try {
            block(this)
        } finally {
            disconnect()
        }
    }

    private fun LedgerEntryEntity.toJson(ownerKey: String): JSONObject = JSONObject().apply {
        put("id", id)
        put("owner_key", ownerKey)
        put("type", type)
        put("status", status)
        put("amount_cents", amountCents)
        put("currency", currency)
        put("occurred_at", occurredAt)
        put("merchant", merchant)
        put("title", title)
        put("category_path", categoryPath)
        put("account", account)
        put("payment_method", paymentMethod)
        put("source_kind", sourceKind)
        put("source_package", sourcePackage)
        put("source_app_name", sourceAppName)
        put("raw_text", rawText)
        put("confidence", confidence)
        put("note", note)
        put("tags", tags)
        put("created_at_ms", createdAt)
        put("updated_at_ms", updatedAt)
        put("synced_at_ms", syncedAt ?: JSONObject.NULL)
        put("is_deleted", isDeleted)
        put("updated_at", Instant.ofEpochMilli(updatedAt).toString())
    }

    private fun JSONObject.toEntity() = LedgerEntryEntity(
        id = getString("id"),
        type = getString("type"),
        status = getString("status"),
        amountCents = getLong("amount_cents"),
        currency = optString("currency", "CNY"),
        occurredAt = getLong("occurred_at"),
        merchant = optString("merchant", ""),
        title = optString("title", ""),
        categoryPath = optString("category_path", "未分类/待确认/其他"),
        account = optString("account", "待确认账户"),
        paymentMethod = optString("payment_method", ""),
        sourceKind = optString("source_kind", "SYNC"),
        sourcePackage = optString("source_package").takeIf { it.isNotBlank() && it != "null" },
        sourceAppName = optString("source_app_name", "Supabase"),
        rawText = optString("raw_text", ""),
        confidence = optInt("confidence", 50),
        note = optString("note", ""),
        tags = optString("tags", ""),
        createdAt = optLong("created_at_ms", System.currentTimeMillis()),
        updatedAt = optLong("updated_at_ms", System.currentTimeMillis()),
        syncedAt = System.currentTimeMillis(),
        isDeleted = optBoolean("is_deleted", false),
    )
}

