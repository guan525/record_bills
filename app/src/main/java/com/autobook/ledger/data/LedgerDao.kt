package com.autobook.ledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledger_entries WHERE is_deleted = 0 ORDER BY occurred_at DESC, updated_at DESC")
    fun observeActiveEntries(): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entries ORDER BY updated_at DESC")
    suspend fun allEntries(): List<LedgerEntryEntity>

    @Query("SELECT * FROM ledger_entries WHERE is_deleted = 0 ORDER BY occurred_at DESC, updated_at DESC")
    suspend fun activeEntries(): List<LedgerEntryEntity>

    @Query("SELECT * FROM ledger_entries WHERE synced_at IS NULL OR updated_at > synced_at ORDER BY updated_at ASC")
    suspend fun unsyncedEntries(): List<LedgerEntryEntity>

    @Query(
        """
        SELECT * FROM ledger_entries
        WHERE is_deleted = 0
            AND source_kind = :sourceKind
            AND COALESCE(source_package, '') = COALESCE(:sourcePackage, '')
            AND type = :type
            AND amount_cents = :amountCents
            AND merchant = :merchant
            AND raw_text = :rawText
            AND occurred_at BETWEEN :windowStart AND :windowEnd
        ORDER BY occurred_at DESC
        LIMIT 1
        """
    )
    suspend fun findDuplicateCapture(
        sourceKind: String,
        sourcePackage: String?,
        type: String,
        amountCents: Long,
        merchant: String,
        rawText: String,
        windowStart: Long,
        windowEnd: Long,
    ): LedgerEntryEntity?

    /**
     * 跨渠道智能去重查询
     * 匹配条件：时间窗口内、金额相同、类型相同（不要求 rawText 完全一致）
     */
    @Query(
        """
        SELECT * FROM ledger_entries
        WHERE is_deleted = 0
            AND type = :type
            AND amount_cents = :amountCents
            AND occurred_at BETWEEN :windowStart AND :windowEnd
        ORDER BY 
            CASE source_kind 
                WHEN 'NOTIFICATION' THEN 0 
                WHEN 'SMS' THEN 1 
                ELSE 2 
            END,
            confidence DESC
        LIMIT 1
        """
    )
    suspend fun findCrossChannelDuplicate(
        type: String,
        amountCents: Long,
        windowStart: Long,
        windowEnd: Long,
    ): LedgerEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: LedgerEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<LedgerEntryEntity>)

    @Query("UPDATE ledger_entries SET status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long)

    @Query("UPDATE ledger_entries SET status = :status, updated_at = :updatedAt WHERE id IN (:ids)")
    suspend fun updateStatusForIds(ids: List<String>, status: String, updatedAt: Long)

    @Query(
        """
        UPDATE ledger_entries
        SET type = :type,
            status = COALESCE(:status, status),
            amount_cents = :amountCents,
            merchant = :merchant,
            title = :merchant,
            category_path = :categoryPath,
            account = :account,
            note = :note,
            updated_at = :updatedAt,
            synced_at = NULL
        WHERE id = :id AND is_deleted = 0
        """
    )
    suspend fun updateEntryDetails(
        id: String,
        type: String,
        status: String?,
        amountCents: Long,
        merchant: String,
        categoryPath: String,
        account: String,
        note: String,
        updatedAt: Long,
    ): Int

    @Query("UPDATE ledger_entries SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: Long)

    @Query("UPDATE ledger_entries SET synced_at = :syncedAt WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>, syncedAt: Long)
}
