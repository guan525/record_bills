package com.autobook.ledger.domain

data class LedgerStats(
    val confirmedExpenseCents: Long,
    val incomeAndRefundCents: Long,
    val pendingCount: Int,
    val categoryExpenseCents: Map<String, Long>,
) {
    companion object {
        fun from(records: List<ParsedBill>): LedgerStats {
            val confirmed = records.filter { it.status == LedgerStatus.CONFIRMED && !it.isDeleted }
            val expenses = confirmed.filter { it.type == LedgerType.EXPENSE }
            val incomeAndRefund = confirmed.filter { it.type == LedgerType.INCOME || it.type == LedgerType.REFUND }
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

