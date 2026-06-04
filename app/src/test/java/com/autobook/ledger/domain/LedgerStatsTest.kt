package com.autobook.ledger.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class LedgerStatsTest {
    private val zoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun ignoresPendingIgnoredTransfersAndRefundsForMonthlyExpense() {
        val now = 1_717_000_000_000
        val stats = LedgerStats.from(
            listOf(
                ParsedBill.sample(type = LedgerType.EXPENSE, status = LedgerStatus.CONFIRMED, amountCents = 1_000, categoryPath = "餐饮/早餐/早餐"),
                ParsedBill.sample(type = LedgerType.EXPENSE, status = LedgerStatus.PENDING, amountCents = 2_000, categoryPath = "交通/地铁公交/地铁"),
                ParsedBill.sample(type = LedgerType.TRANSFER, status = LedgerStatus.CONFIRMED, amountCents = 3_000, categoryPath = "资金流转/账户互转/转入"),
                ParsedBill.sample(type = LedgerType.REFUND, status = LedgerStatus.CONFIRMED, amountCents = 400, categoryPath = "购物/电商/退款"),
                ParsedBill.sample(type = LedgerType.INCOME, status = LedgerStatus.CONFIRMED, amountCents = 5_000, categoryPath = "收入/工资/工资"),
                ParsedBill.sample(type = LedgerType.EXPENSE, status = LedgerStatus.IGNORED, amountCents = 6_000, categoryPath = "购物/服饰/衣服"),
            ),
            now = now,
            zoneId = zoneId,
        )

        assertEquals(1_000L, stats.confirmedExpenseCents)
        assertEquals(5_400L, stats.incomeAndRefundCents)
        assertEquals(1, stats.pendingCount)
        assertEquals(1_000L, stats.categoryExpenseCents["餐饮"])
    }

    @Test
    fun excludesConfirmedExpensesOutsideCurrentMonthButKeepsAllPendingCount() {
        val june15 = millis(2024, 6, 15)
        val may31 = millis(2024, 5, 31)
        val stats = LedgerStats.from(
            listOf(
                ParsedBill.sample(status = LedgerStatus.CONFIRMED, amountCents = 1_000, categoryPath = "餐饮/咖啡/咖啡")
                    .copy(occurredAt = june15),
                ParsedBill.sample(status = LedgerStatus.CONFIRMED, amountCents = 2_000, categoryPath = "交通/打车/网约车")
                    .copy(occurredAt = may31),
                ParsedBill.sample(status = LedgerStatus.PENDING, amountCents = 3_000, categoryPath = "购物/电商/综合电商")
                    .copy(occurredAt = may31),
            ),
            now = june15,
            zoneId = zoneId,
        )

        assertEquals(1_000L, stats.confirmedExpenseCents)
        assertEquals(1, stats.pendingCount)
        assertEquals(1_000L, stats.categoryExpenseCents["餐饮"])
        assertEquals(null, stats.categoryExpenseCents["交通"])
    }

    private fun millis(year: Int, month: Int, day: Int): Long =
        LocalDateTime.of(year, month, day, 12, 0).atZone(zoneId).toInstant().toEpochMilli()
}
