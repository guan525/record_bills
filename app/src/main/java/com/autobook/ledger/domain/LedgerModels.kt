package com.autobook.ledger.domain

import java.util.UUID

enum class LedgerType {
    EXPENSE,
    INCOME,
    TRANSFER,
    REFUND,
    ADJUSTMENT,
}

enum class LedgerStatus {
    PENDING,
    CONFIRMED,
    IGNORED,
}

enum class SourceKind {
    MANUAL,
    NOTIFICATION,
    SMS,
    IMPORT,
    SYNC,
}

data class ParsedBill(
    val id: String = UUID.randomUUID().toString(),
    val type: LedgerType,
    val status: LedgerStatus = LedgerStatus.PENDING,
    val amountCents: Long,
    val currency: String = "CNY",
    val occurredAt: Long,
    val merchant: String,
    val title: String,
    val categoryPath: String,
    val account: String,
    val paymentMethod: String = "",
    val sourceKind: SourceKind,
    val sourcePackage: String? = null,
    val sourceAppName: String,
    val rawText: String,
    val confidence: Int,
    val note: String = "",
    val tags: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncedAt: Long? = null,
    val isDeleted: Boolean = false,
) {
    companion object {
        fun sample(
            type: LedgerType = LedgerType.EXPENSE,
            status: LedgerStatus = LedgerStatus.CONFIRMED,
            amountCents: Long = 0,
            categoryPath: String = "未分类/待确认/其他",
        ) = ParsedBill(
            type = type,
            status = status,
            amountCents = amountCents,
            occurredAt = 1_717_000_000_000,
            merchant = "测试商户",
            title = "测试",
            categoryPath = categoryPath,
            account = "现金",
            sourceKind = SourceKind.MANUAL,
            sourceAppName = "手动",
            rawText = "sample",
            confidence = 100,
        )
    }
}

