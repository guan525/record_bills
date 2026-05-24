package com.autobook.ledger.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryClassifierTest {
    private val classifier = CategoryClassifier(CategoryCatalog.default())

    @Test
    fun classifiesCommonSpendingCategories() {
        assertEquals("餐饮/外卖/工作餐", classifier.classify("美团外卖", "美团外卖 支出28元"))
        assertEquals("交通/打车/网约车", classifier.classify("滴滴出行", "滴滴打车扣款"))
        assertEquals("购物/电商/综合电商", classifier.classify("京东商城", "京东商城消费"))
        assertEquals("居住/房租房贷/月租", classifier.classify("房东", "房租转账"))
        assertEquals("医疗健康/药品/药店", classifier.classify("益丰大药房", "药店消费"))
        assertEquals("教育学习/课程/线上课程", classifier.classify("得到", "课程购买"))
        assertEquals("休闲娱乐/电影演出/电影票", classifier.classify("猫眼电影", "电影票"))
        assertEquals("旅行/酒店住宿/酒店", classifier.classify("携程酒店", "酒店预订"))
        assertEquals("数字服务/会员订阅/音乐视频", classifier.classify("腾讯视频", "会员自动续费"))
        assertEquals("金融保险/保险/商业保险", classifier.classify("平安保险", "保费扣款"))
        assertEquals("收入/工资/工资", classifier.classify("公司", "工资到账"))
    }

    @Test
    fun fallsBackToUncategorized() {
        assertEquals("未分类/待确认/其他", classifier.classify("未知商户", "普通消费"))
    }
}

