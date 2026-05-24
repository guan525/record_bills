package com.autobook.ledger.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppSourceMatcherTest {
    @Test
    fun identifiesCommonSpendingSourcesFromLabelsAndPackages() {
        assertEquals("支付钱包", AppSourceMatcher.match("支付宝", "com.eg.android.AlipayGphone"))
        assertEquals("支付钱包", AppSourceMatcher.match("微信", "com.tencent.mm"))
        assertEquals("银行信用卡", AppSourceMatcher.match("国际港澳跨境银行", "com.gangao.crossbank.debug"))
        assertEquals("电商购物", AppSourceMatcher.match("京东", "com.jingdong.app.mall"))
        assertEquals("外卖买菜", AppSourceMatcher.match("美团", "com.sankuai.meituan"))
        assertEquals("交通出行", AppSourceMatcher.match("滴滴出行", "com.sdu.didi.psnger"))
    }

    @Test
    fun ignoresUnrelatedApps() {
        assertNull(AppSourceMatcher.match("计算器", "com.android.calculator2"))
    }
}

