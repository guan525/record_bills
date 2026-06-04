package com.autobook.ledger.data

import com.autobook.ledger.domain.LedgerStatus
import com.autobook.ledger.domain.LedgerType
import com.autobook.ledger.domain.ParsedBill
import com.autobook.ledger.domain.SourceKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LedgerRepositoryTest {
    @Test
    fun addsManualIncomeWithNoteAndTypeAwareDefaults() = runBlocking {
        val dao = FakeLedgerDao()
        val repository = LedgerRepository(dao)

        repository.addManualEntry(
            type = LedgerType.INCOME,
            amountCents = 8_888,
            merchant = "兼职收入",
            categoryPath = "",
            account = "银行卡",
            note = "6月项目尾款",
        )

        val entry = dao.entries.values.single().toParsedBill()
        assertEquals(LedgerType.INCOME, entry.type)
        assertEquals(LedgerStatus.CONFIRMED, entry.status)
        assertEquals(8_888L, entry.amountCents)
        assertEquals("兼职收入", entry.merchant)
        assertEquals("收入/其他/其他", entry.categoryPath)
        assertEquals("银行卡", entry.account)
        assertEquals("6月项目尾款", entry.note)
        assertEquals("手动", entry.sourceAppName)
    }

    @Test
    fun addsManualTransferWithTypeAwareDefaultCategory() = runBlocking {
        val dao = FakeLedgerDao()
        val repository = LedgerRepository(dao)

        repository.addManualEntry(
            type = LedgerType.TRANSFER,
            amountCents = 50_000,
            merchant = "",
            categoryPath = "",
            account = "",
            note = "",
        )

        val entry = dao.entries.values.single().toParsedBill()
        assertEquals(LedgerType.TRANSFER, entry.type)
        assertEquals("手动记录", entry.merchant)
        assertEquals("资金流转/账户互转/转出", entry.categoryPath)
        assertEquals("现金", entry.account)
    }

    @Test
    fun confirmsMultiplePendingEntriesTogether() = runBlocking {
        val dao = FakeLedgerDao()
        val repository = LedgerRepository(dao)
        val first = LedgerEntryEntity.fromParsedBill(ParsedBill.sample(status = LedgerStatus.PENDING, amountCents = 100).copy(id = "first"))
        val second = LedgerEntryEntity.fromParsedBill(ParsedBill.sample(status = LedgerStatus.PENDING, amountCents = 200).copy(id = "second"))
        val ignored = LedgerEntryEntity.fromParsedBill(ParsedBill.sample(status = LedgerStatus.PENDING, amountCents = 300).copy(id = "ignored"))
        dao.upsertAll(listOf(first, second, ignored))

        repository.confirmAll(listOf("first", "second"))

        assertEquals(LedgerStatus.CONFIRMED.name, dao.entries.getValue("first").status)
        assertEquals(LedgerStatus.CONFIRMED.name, dao.entries.getValue("second").status)
        assertEquals(LedgerStatus.PENDING.name, dao.entries.getValue("ignored").status)
    }

    @Test
    fun ignoresMultiplePendingEntriesTogether() = runBlocking {
        val dao = FakeLedgerDao()
        val repository = LedgerRepository(dao)
        val first = LedgerEntryEntity.fromParsedBill(ParsedBill.sample(type = LedgerType.EXPENSE, status = LedgerStatus.PENDING).copy(id = "first"))
        val second = LedgerEntryEntity.fromParsedBill(ParsedBill.sample(type = LedgerType.EXPENSE, status = LedgerStatus.PENDING).copy(id = "second"))
        dao.upsertAll(listOf(first, second))

        repository.ignoreAll(listOf("first", "second"))

        assertEquals(LedgerStatus.IGNORED.name, dao.entries.getValue("first").status)
        assertEquals(LedgerStatus.IGNORED.name, dao.entries.getValue("second").status)
    }

    @Test
    fun reusesExistingEntryForDuplicateCapturedTransactionInShortWindow() = runBlocking {
        val dao = FakeLedgerDao()
        val repository = LedgerRepository(dao)

        val first = repository.capture(
            sourceKind = SourceKind.NOTIFICATION,
            sourcePackage = "com.eg.android.AlipayGphone",
            sourceAppName = "支付宝",
            title = "支付宝付款",
            text = "你已成功付款 ¥36.80 给 星巴克咖啡",
            timestampMillis = 1_717_000_000_000,
        )
        val duplicate = repository.capture(
            sourceKind = SourceKind.NOTIFICATION,
            sourcePackage = "com.eg.android.AlipayGphone",
            sourceAppName = "支付宝",
            title = "支付宝付款",
            text = "你已成功付款 ¥36.80 给 星巴克咖啡",
            timestampMillis = 1_717_000_030_000,
        )

        requireNotNull(first)
        requireNotNull(duplicate)
        assertEquals(first.id, duplicate.id)
        assertEquals(1, dao.entries.size)
    }

    @Test
    fun keepsSimilarTransactionsSeparateOutsideDuplicateWindow() = runBlocking {
        val dao = FakeLedgerDao()
        val repository = LedgerRepository(dao)

        val first = repository.capture(
            sourceKind = SourceKind.NOTIFICATION,
            sourcePackage = "com.eg.android.AlipayGphone",
            sourceAppName = "支付宝",
            title = "支付宝付款",
            text = "你已成功付款 ¥36.80 给 星巴克咖啡",
            timestampMillis = 1_717_000_000_000,
        )
        val later = repository.capture(
            sourceKind = SourceKind.NOTIFICATION,
            sourcePackage = "com.eg.android.AlipayGphone",
            sourceAppName = "支付宝",
            title = "支付宝付款",
            text = "你已成功付款 ¥36.80 给 星巴克咖啡",
            timestampMillis = 1_717_000_240_000,
        )

        requireNotNull(first)
        requireNotNull(later)
        assertNotEquals(first.id, later.id)
        assertEquals(2, dao.entries.size)
    }

    @Test
    fun keepsSameMerchantAmountSeparateWhenRawTextDiffers() = runBlocking {
        val dao = FakeLedgerDao()
        val repository = LedgerRepository(dao)

        val first = repository.capture(
            sourceKind = SourceKind.NOTIFICATION,
            sourcePackage = "com.eg.android.AlipayGphone",
            sourceAppName = "支付宝",
            title = "支付宝付款",
            text = "你已成功付款 ¥36.80 给 星巴克咖啡 订单A123",
            timestampMillis = 1_717_000_000_000,
        )
        val second = repository.capture(
            sourceKind = SourceKind.NOTIFICATION,
            sourcePackage = "com.eg.android.AlipayGphone",
            sourceAppName = "支付宝",
            title = "支付宝付款",
            text = "你已成功付款 ¥36.80 给 星巴克咖啡 订单B456",
            timestampMillis = 1_717_000_030_000,
        )

        requireNotNull(first)
        requireNotNull(second)
        assertNotEquals(first.id, second.id)
        assertEquals(2, dao.entries.size)
    }
}

private class FakeLedgerDao : LedgerDao {
    val entries = linkedMapOf<String, LedgerEntryEntity>()
    private val entriesFlow = MutableStateFlow<List<LedgerEntryEntity>>(emptyList())

    override fun observeActiveEntries(): Flow<List<LedgerEntryEntity>> = entriesFlow

    override suspend fun allEntries(): List<LedgerEntryEntity> =
        entries.values.sortedByDescending { it.updatedAt }

    override suspend fun activeEntries(): List<LedgerEntryEntity> =
        entries.values.filter { !it.isDeleted }.sortedWith(compareByDescending<LedgerEntryEntity> { it.occurredAt }.thenByDescending { it.updatedAt })

    override suspend fun unsyncedEntries(): List<LedgerEntryEntity> =
        entries.values.filter { it.syncedAt == null || it.updatedAt > it.syncedAt }.sortedBy { it.updatedAt }

    override suspend fun upsert(entry: LedgerEntryEntity) {
        entries[entry.id] = entry
        entriesFlow.value = activeEntries()
    }

    override suspend fun upsertAll(entries: List<LedgerEntryEntity>) {
        entries.forEach { upsert(it) }
    }

    override suspend fun updateStatus(id: String, status: String, updatedAt: Long) {
        entries[id]?.let {
            entries[id] = it.copy(status = status, updatedAt = updatedAt)
        }
        entriesFlow.value = activeEntries()
    }

    override suspend fun updateStatusForIds(ids: List<String>, status: String, updatedAt: Long) {
        ids.distinct().forEach { id ->
            entries[id]?.let {
                entries[id] = it.copy(status = status, updatedAt = updatedAt)
            }
        }
        entriesFlow.value = activeEntries()
    }

    override suspend fun softDelete(id: String, updatedAt: Long) {
        entries[id]?.let {
            entries[id] = it.copy(isDeleted = true, updatedAt = updatedAt)
        }
        entriesFlow.value = activeEntries()
    }

    override suspend fun markSynced(ids: List<String>, syncedAt: Long) {
        ids.forEach { id ->
            entries[id]?.let {
                entries[id] = it.copy(syncedAt = syncedAt)
            }
        }
        entriesFlow.value = activeEntries()
    }

    override suspend fun findDuplicateCapture(
        sourceKind: String,
        sourcePackage: String?,
        type: String,
        amountCents: Long,
        merchant: String,
        rawText: String,
        windowStart: Long,
        windowEnd: Long,
    ): LedgerEntryEntity? =
        entries.values
            .filter {
                !it.isDeleted &&
                    it.sourceKind == sourceKind &&
                    it.sourcePackage.orEmpty() == sourcePackage.orEmpty() &&
                    it.type == type &&
                    it.amountCents == amountCents &&
                    it.merchant == merchant &&
                    it.rawText == rawText &&
                    it.occurredAt in windowStart..windowEnd
            }
            .maxByOrNull { it.occurredAt }
}
