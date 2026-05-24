package com.autobook.ledger.domain

data class CategoryRule(
    val path: String,
    val keywords: List<String>,
)

class CategoryCatalog private constructor(
    val rules: List<CategoryRule>,
) {
    companion object {
        fun default() = CategoryCatalog(
            listOf(
                CategoryRule("餐饮/外卖/工作餐", listOf("美团外卖", "饿了么", "外卖", "工作餐", "盒马", "叮咚买菜")),
                CategoryRule("餐饮/咖啡饮品/咖啡", listOf("星巴克", "瑞幸", "库迪", "咖啡", "拿铁", "茶百道", "喜茶", "奈雪", "霸王茶姬", "奶茶")),
                CategoryRule("餐饮/正餐/餐厅", listOf("餐厅", "饭店", "火锅", "烧烤", "小吃", "面馆", "肯德基", "麦当劳")),
                CategoryRule("交通/打车/网约车", listOf("滴滴", "高德打车", "曹操出行", "T3出行", "网约车", "打车")),
                CategoryRule("交通/地铁公交/地铁", listOf("地铁", "公交", "公交卡", "交通卡")),
                CategoryRule("交通/火车飞机/机票火车票", listOf("机票", "火车票", "高铁", "铁路", "航旅纵横", "12306")),
                CategoryRule("购物/电商/退款", listOf("退款", "退回", "退货")),
                CategoryRule("购物/电商/综合电商", listOf("京东", "淘宝", "天猫", "拼多多", "抖音商城", "快手小店", "唯品会", "电商")),
                CategoryRule("购物/服饰/衣服", listOf("优衣库", "衣服", "服饰", "鞋", "帽", "ZARA", "耐克", "阿迪")),
                CategoryRule("居住/房租房贷/月租", listOf("房租", "租金", "房东", "月租", "房贷")),
                CategoryRule("居住/水电燃气/生活缴费", listOf("水费", "电费", "燃气", "物业", "生活缴费", "宽带")),
                CategoryRule("医疗健康/药品/药店", listOf("药房", "药店", "大药房", "买药", "药品", "医院", "挂号")),
                CategoryRule("教育学习/课程/线上课程", listOf("课程", "得到", "极客时间", "知识星球", "学费", "培训")),
                CategoryRule("休闲娱乐/电影演出/电影票", listOf("电影", "猫眼", "淘票票", "演出", "票务", "KTV")),
                CategoryRule("旅行/酒店住宿/酒店", listOf("携程", "飞猪", "去哪儿", "酒店", "民宿", "住宿")),
                CategoryRule("数字服务/会员订阅/音乐视频", listOf("会员", "订阅", "自动续费", "腾讯视频", "爱奇艺", "优酷", "网易云", "Apple Music", "Spotify", "云服务")),
                CategoryRule("家庭人情/人情往来/红包转账", listOf("红包", "礼金", "人情", "请客")),
                CategoryRule("金融保险/保险/商业保险", listOf("保险", "保费", "平安保险", "太平洋保险", "众安", "医保")),
                CategoryRule("收入/工资/工资", listOf("工资", "薪资", "奖金", "绩效", "报销到账")),
                CategoryRule("资金流转/账户互转/转入", listOf("转入", "转出", "转账", "提现", "充值", "零钱通")),
            )
        )
    }
}

class CategoryClassifier(
    private val catalog: CategoryCatalog,
) {
    fun classify(merchant: String, text: String): String {
        val haystack = "$merchant $text".lowercase()
        return catalog.rules.firstOrNull { rule ->
            rule.keywords.any { keyword -> haystack.contains(keyword.lowercase()) }
        }?.path ?: "未分类/待确认/其他"
    }
}

