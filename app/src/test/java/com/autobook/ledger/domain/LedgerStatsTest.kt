package com.autobook.ledger.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerStatsTest {
    @Test
    fun ignoresPendingIgnoredTransfersAndRefundsForMonthlyExpense() {
        val stats = LedgerStats.from(
            listOf(
                ParsedBill.sample(type = LedgerType.EXPENSE, status = LedgerStatus.CONFIRMED, amountCents = 1_000, categoryPath = "餐饮/早餐/早餐"),
                ParsedBill.sample(type = LedgerType.EXPENSE, status = LedgerStatus.PENDING, amountCents = 2_000, categoryPath = "交通/地铁公交/地铁"),
                ParsedBill.sample(type = LedgerType.TRANSFER, status = LedgerStatus.CONFIRMED, amountCents = 3_000, categoryPath = "资金流转/账户互转/转入"),
                ParsedBill.sample(type = LedgerType.REFUND, status = LedgerStatus.CONFIRMED, amountCents = 400, categoryPath = "购物/电商/退款"),
                ParsedBill.sample(type = LedgerType.INCOME, status = LedgerStatus.CONFIRMED, amountCents = 5_000, categoryPath = "收入/工资/工资"),
                ParsedBill.sample(type = LedgerType.EXPENSE, status = LedgerStatus.IGNORED, amountCents = 6_000, categoryPath = "购物/服饰/衣服"),
            )
        )

        assertEquals(1_000L, stats.confirmedExpenseCents)
        assertEquals(5_400L, stats.incomeAndRefundCents)
        assertEquals(1, stats.pendingCount)
        assertEquals(1_000L, stats.categoryExpenseCents["餐饮"])
    }
}

