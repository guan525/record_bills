package com.autobook.ledger.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillParserTest {
    private val parser = BillParser(CategoryCatalog.default())

    @Test
    fun parsesAlipayExpenseNotification() {
        val bill = parser.parse(
            sourceKind = SourceKind.NOTIFICATION,
            sourcePackage = "com.eg.android.AlipayGphone",
            sourceAppName = "支付宝",
            title = "支付宝付款",
            text = "你已成功付款 ¥36.80 给 星巴克咖啡",
            timestampMillis = 1_717_000_000_000,
        )

        requireNotNull(bill)
        assertEquals(LedgerType.EXPENSE, bill.type)
        assertEquals(3_680L, bill.amountCents)
        assertEquals("星巴克咖啡", bill.merchant)
        assertEquals("餐饮/咖啡饮品/咖啡", bill.categoryPath)
        assertEquals("支付宝", bill.account)
        assertTrue(bill.confidence >= 80)
    }

    @Test
    fun parsesWechatPayExpenseNotification() {
        val bill = parser.parse(
            sourceKind = SourceKind.NOTIFICATION,
            sourcePackage = "com.tencent.mm",
            sourceAppName = "微信",
            title = "微信支付",
            text = "微信支付收款方 美团外卖 支出 28.50元",
            timestampMillis = 1_717_000_000_000,
        )

        requireNotNull(bill)
        assertEquals(LedgerType.EXPENSE, bill.type)
        assertEquals(2_850L, bill.amountCents)
        assertEquals("美团外卖", bill.merchant)
        assertEquals("餐饮/外卖/工作餐", bill.categoryPath)
        assertEquals("微信支付", bill.account)
    }

    @Test
    fun parsesBankCardSmsExpense() {
        val bill = parser.parse(
            sourceKind = SourceKind.SMS,
            sourcePackage = null,
            sourceAppName = "招商银行",
            title = "招商银行",
            text = "您尾号1234的信用卡05月24日消费人民币199.00元，商户京东商城，可用额度充足。",
            timestampMillis = 1_717_000_000_000,
        )

        requireNotNull(bill)
        assertEquals(LedgerType.EXPENSE, bill.type)
        assertEquals(19_900L, bill.amountCents)
        assertEquals("京东商城", bill.merchant)
        assertEquals("购物/电商/综合电商", bill.categoryPath)
        assertEquals("信用卡", bill.account)
    }

    @Test
    fun parsesRefundAsIncomeLikeRecord() {
        val bill = parser.parse(
            sourceKind = SourceKind.NOTIFICATION,
            sourcePackage = "com.eg.android.AlipayGphone",
            sourceAppName = "支付宝",
            title = "退款到账",
            text = "淘宝订单退款成功，退款 ¥58.20 已退回支付宝余额",
            timestampMillis = 1_717_000_000_000,
        )

        requireNotNull(bill)
        assertEquals(LedgerType.REFUND, bill.type)
        assertEquals(5_820L, bill.amountCents)
        assertEquals("购物/电商/退款", bill.categoryPath)
    }

    @Test
    fun parsesTransferWithoutCountingAsExpense() {
        val bill = parser.parse(
            sourceKind = SourceKind.NOTIFICATION,
            sourcePackage = "com.tencent.mm",
            sourceAppName = "微信",
            title = "零钱转入",
            text = "零钱通转入 500.00元 成功",
            timestampMillis = 1_717_000_000_000,
        )

        requireNotNull(bill)
        assertEquals(LedgerType.TRANSFER, bill.type)
        assertEquals(50_000L, bill.amountCents)
        assertEquals("资金流转/账户互转/转入", bill.categoryPath)
    }

    @Test
    fun parsesSubscriptionExpense() {
        val bill = parser.parse(
            sourceKind = SourceKind.NOTIFICATION,
            sourcePackage = "com.apple.android.music",
            sourceAppName = "Apple Music",
            title = "订阅扣款",
            text = "Apple Music 会员自动续费成功，扣款15元",
            timestampMillis = 1_717_000_000_000,
        )

        requireNotNull(bill)
        assertEquals(LedgerType.EXPENSE, bill.type)
        assertEquals(1_500L, bill.amountCents)
        assertEquals("数字服务/会员订阅/音乐视频", bill.categoryPath)
    }

    @Test
    fun ignoresPromotionWithoutTransaction() {
        val bill = parser.parse(
            sourceKind = SourceKind.NOTIFICATION,
            sourcePackage = "com.taobao.taobao",
            sourceAppName = "淘宝",
            title = "限时优惠",
            text = "今晚8点满200减30，红包限时领取",
            timestampMillis = 1_717_000_000_000,
        )

        assertNull(bill)
    }

    @Test
    fun marksUncertainAmountAsPending() {
        val bill = parser.parse(
            sourceKind = SourceKind.NOTIFICATION,
            sourcePackage = "com.unknown.pay",
            sourceAppName = "未知支付",
            title = "支付提醒",
            text = "消费 12.34 元",
            timestampMillis = 1_717_000_000_000,
        )

        requireNotNull(bill)
        assertEquals(LedgerStatus.PENDING, bill.status)
        assertFalse(bill.confidence >= 80)
    }
}

