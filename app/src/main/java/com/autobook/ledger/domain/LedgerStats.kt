package com.autobook.ledger.domain

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class LedgerStats(
    val confirmedExpenseCents: Long,
    val incomeAndRefundCents: Long,
    val pendingCount: Int,
    val categoryExpenseCents: Map<String, Long>,
) {
    companion object {
        fun from(
            records: List<ParsedBill>,
            now: Long = System.currentTimeMillis(),
            zoneId: ZoneId = ZoneId.systemDefault(),
        ): LedgerStats {
            val currentMonth = YearMonth.from(Instant.ofEpochMilli(now).atZone(zoneId))
            val confirmed = records.filter { it.status == LedgerStatus.CONFIRMED && !it.isDeleted }
            val monthly = confirmed.filter { bill ->
                YearMonth.from(Instant.ofEpochMilli(bill.occurredAt).atZone(zoneId)) == currentMonth
            }
            val expenses = monthly.filter { it.type == LedgerType.EXPENSE }
            val incomeAndRefund = monthly.filter { it.type == LedgerType.INCOME || it.type == LedgerType.REFUND }
            return LedgerStats(
                confirmedExpenseCents = expenses.sumOf { it.amountCents },
                incomeAndRefundCents = incomeAndRefund.sumOf { it.amountCents },
                pendingCount = records.count { it.status == LedgerStatus.PENDING && !it.isDeleted },
                categoryExpenseCents = expenses
                    .groupBy { it.categoryPath.substringBefore("/") }
                    .mapValues { (_, bills) -> bills.sumOf { it.amountCents } },
            )
        }
    }
}
