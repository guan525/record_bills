package com.autobook.ledger.data

import com.autobook.ledger.domain.BillParser
import com.autobook.ledger.domain.CategoryCatalog
import com.autobook.ledger.domain.LedgerStatus
import com.autobook.ledger.domain.LedgerStats
import com.autobook.ledger.domain.LedgerType
import com.autobook.ledger.domain.ParsedBill
import com.autobook.ledger.domain.RulesConfig
import com.autobook.ledger.domain.SourceKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class LedgerRepository(
    private val dao: LedgerDao,
    private val parser: BillParser = BillParser(CategoryCatalog.default()),
) {
    fun observeEntries(): Flow<List<ParsedBill>> =
        dao.observeActiveEntries().map { entries -> entries.map { it.toParsedBill() } }

    fun observeStats(): Flow<LedgerStats> =
        observeEntries().map(LedgerStats.Companion::from)

    suspend fun activeEntries(): List<ParsedBill> =
        dao.activeEntries().map { it.toParsedBill() }

    suspend fun unsyncedEntries(): List<LedgerEntryEntity> = dao.unsyncedEntries()

    suspend fun upsertEntry(entry: LedgerEntryEntity) = dao.upsert(entry)

    suspend fun upsertEntries(entries: List<LedgerEntryEntity>) = dao.upsertAll(entries)

    suspend fun markSynced(ids: List<String>, syncedAt: Long = System.currentTimeMillis()) {
        if (ids.isNotEmpty()) dao.markSynced(ids, syncedAt)
    }

    suspend fun addManualExpense(
        amountCents: Long,
        merchant: String,
        categoryPath: String,
        account: String,
        occurredAt: Long = System.currentTimeMillis(),
    ) = addManualEntry(
        type = LedgerType.EXPENSE,
        amountCents = amountCents,
        merchant = merchant,
        categoryPath = categoryPath,
        account = account,
        note = "",
        occurredAt = occurredAt,
    )

    suspend fun addManualEntry(
        type: LedgerType,
        amountCents: Long,
        merchant: String,
        categoryPath: String,
        account: String,
        note: String,
        occurredAt: Long = System.currentTimeMillis(),
    ) {
        val now = System.currentTimeMillis()
        val normalizedMerchant = merchant.ifBlank { "手动记录" }
        val bill = ParsedBill(
            id = UUID.randomUUID().toString(),
            type = type,
            status = LedgerStatus.CONFIRMED,
            amountCents = amountCents,
            occurredAt = occurredAt,
            merchant = normalizedMerchant,
            title = normalizedMerchant,
            categoryPath = categoryPath.ifBlank { defaultManualCategory(type) },
            account = account.ifBlank { "现金" },
            sourceKind = SourceKind.MANUAL,
            sourceAppName = "手动",
            rawText = "manual",
            confidence = 100,
            note = note.trim(),
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(LedgerEntryEntity.fromParsedBill(bill, now))
    }

    suspend fun capture(
        sourceKind: SourceKind,
        sourcePackage: String?,
        sourceAppName: String,
        title: String,
        text: String,
        timestampMillis: Long,
    ): ParsedBill? {
        val parsed = parser.parse(sourceKind, sourcePackage, sourceAppName, title, text, timestampMillis) ?: return null
        
        // 跨渠道智能去重：时间窗口内、金额相同、类型相同
        val duplicate = dao.findCrossChannelDuplicate(
            type = parsed.type.name,
            amountCents = parsed.amountCents,
            windowStart = parsed.occurredAt - RulesConfig.DEDUPLICATION_WINDOW_MS,
            windowEnd = parsed.occurredAt + RulesConfig.DEDUPLICATION_WINDOW_MS,
        )
        
        if (duplicate != null) {
            // 如果已存在的记录可信度更低，则替换它
            val existingPriority = RulesConfig.getSourcePriority(
                SourceKind.valueOf(duplicate.sourceKind),
                duplicate.sourcePackage
            )
            val newPriority = RulesConfig.getSourcePriority(sourceKind, sourcePackage)
            
            if (newPriority > existingPriority) {
                // 新记录可信度更高，替换旧记录
                dao.upsert(LedgerEntryEntity.fromParsedBill(parsed))
                return parsed
            }
            // 已存在记录可信度更高或相同，返回已存在的记录
            return duplicate.toParsedBill()
        }

        dao.upsert(LedgerEntryEntity.fromParsedBill(parsed))
        return parsed
    }

    suspend fun confirm(id: String) = dao.updateStatus(id, LedgerStatus.CONFIRMED.name, System.currentTimeMillis())

    suspend fun ignore(id: String) = dao.updateStatus(id, LedgerStatus.IGNORED.name, System.currentTimeMillis())

    suspend fun confirmAll(ids: List<String>) = updateStatusForIds(ids, LedgerStatus.CONFIRMED)

    suspend fun ignoreAll(ids: List<String>) = updateStatusForIds(ids, LedgerStatus.IGNORED)

    suspend fun updateEntry(
        id: String,
        type: LedgerType,
        amountCents: Long,
        merchant: String,
        categoryPath: String,
        account: String,
        note: String,
        confirm: Boolean,
    ): Boolean {
        val normalizedMerchant = merchant.trim().ifBlank { "待确认商户" }
        val updatedCount = dao.updateEntryDetails(
            id = id,
            type = type.name,
            status = if (confirm) LedgerStatus.CONFIRMED.name else null,
            amountCents = amountCents,
            merchant = normalizedMerchant,
            categoryPath = categoryPath.trim().ifBlank { defaultManualCategory(type) },
            account = account.trim().ifBlank { "待确认账户" },
            note = note.trim(),
            updatedAt = System.currentTimeMillis(),
        )
        return updatedCount > 0
    }

    suspend fun delete(id: String) = dao.softDelete(id, System.currentTimeMillis())

    private suspend fun updateStatusForIds(ids: List<String>, status: LedgerStatus) {
        val uniqueIds = ids.distinct()
        if (uniqueIds.isNotEmpty()) {
            dao.updateStatusForIds(uniqueIds, status.name, System.currentTimeMillis())
        }
    }

    private companion object {
        const val DUPLICATE_CAPTURE_WINDOW_MS = 2 * 60 * 1000L

        fun defaultManualCategory(type: LedgerType): String = when (type) {
            LedgerType.EXPENSE -> "未分类/待确认/其他"
            LedgerType.INCOME -> "收入/其他/其他"
            LedgerType.REFUND -> "购物/电商/退款"
            LedgerType.TRANSFER -> "资金流转/账户互转/转出"
            LedgerType.ADJUSTMENT -> "调整/手动调整/其他"
        }
    }
}
