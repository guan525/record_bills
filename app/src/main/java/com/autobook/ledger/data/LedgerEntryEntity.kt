package com.autobook.ledger.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.autobook.ledger.domain.LedgerStatus
import com.autobook.ledger.domain.LedgerType
import com.autobook.ledger.domain.ParsedBill
import com.autobook.ledger.domain.SourceKind

@Entity(tableName = "ledger_entries")
data class LedgerEntryEntity(
    @PrimaryKey val id: String,
    val type: String,
    val status: String,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    val currency: String,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    val merchant: String,
    val title: String,
    @ColumnInfo(name = "category_path") val categoryPath: String,
    val account: String,
    @ColumnInfo(name = "payment_method") val paymentMethod: String,
    @ColumnInfo(name = "source_kind") val sourceKind: String,
    @ColumnInfo(name = "source_package") val sourcePackage: String?,
    @ColumnInfo(name = "source_app_name") val sourceAppName: String,
    @ColumnInfo(name = "raw_text") val rawText: String,
    val confidence: Int,
    val note: String,
    val tags: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "synced_at") val syncedAt: Long?,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean,
) {
    fun toParsedBill() = ParsedBill(
        id = id,
        type = LedgerType.valueOf(type),
        status = LedgerStatus.valueOf(status),
        amountCents = amountCents,
        currency = currency,
        occurredAt = occurredAt,
        merchant = merchant,
        title = title,
        categoryPath = categoryPath,
        account = account,
        paymentMethod = paymentMethod,
        sourceKind = SourceKind.valueOf(sourceKind),
        sourcePackage = sourcePackage,
        sourceAppName = sourceAppName,
        rawText = rawText,
        confidence = confidence,
        note = note,
        tags = tags,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncedAt = syncedAt,
        isDeleted = isDeleted,
    )

    companion object {
        fun fromParsedBill(bill: ParsedBill, updatedAt: Long = System.currentTimeMillis()) = LedgerEntryEntity(
            id = bill.id,
            type = bill.type.name,
            status = bill.status.name,
            amountCents = bill.amountCents,
            currency = bill.currency,
            occurredAt = bill.occurredAt,
            merchant = bill.merchant,
            title = bill.title,
            categoryPath = bill.categoryPath,
            account = bill.account,
            paymentMethod = bill.paymentMethod,
            sourceKind = bill.sourceKind.name,
            sourcePackage = bill.sourcePackage,
            sourceAppName = bill.sourceAppName,
            rawText = bill.rawText,
            confidence = bill.confidence,
            note = bill.note,
            tags = bill.tags,
            createdAt = bill.createdAt,
            updatedAt = updatedAt,
            syncedAt = bill.syncedAt,
            isDeleted = bill.isDeleted,
        )
    }
}

