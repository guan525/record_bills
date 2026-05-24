package com.autobook.ledger.data

import com.autobook.ledger.domain.BillParser
import com.autobook.ledger.domain.CategoryCatalog
import com.autobook.ledger.domain.LedgerStatus
import com.autobook.ledger.domain.LedgerStats
import com.autobook.ledger.domain.LedgerType
import com.autobook.ledger.domain.ParsedBill
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
    ) {
        val now = System.currentTimeMillis()
        val bill = ParsedBill(
            id = UUID.randomUUID().toString(),
            type = LedgerType.EXPENSE,
            status = LedgerStatus.CONFIRMED,
            amountCents = amountCents,
            occurredAt = occurredAt,
            merchant = merchant.ifBlank { "手动记录" },
            title = merchant.ifBlank { "手动记录" },
            categoryPath = categoryPath.ifBlank { "未分类/待确认/其他" },
            account = account.ifBlank { "现金" },
            sourceKind = SourceKind.MANUAL,
            sourceAppName = "手动",
            rawText = "manual",
            confidence = 100,
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
        dao.upsert(LedgerEntryEntity.fromParsedBill(parsed))
        return parsed
    }

    suspend fun confirm(id: String) = dao.updateStatus(id, LedgerStatus.CONFIRMED.name, System.currentTimeMillis())

    suspend fun ignore(id: String) = dao.updateStatus(id, LedgerStatus.IGNORED.name, System.currentTimeMillis())

    suspend fun delete(id: String) = dao.softDelete(id, System.currentTimeMillis())
}

