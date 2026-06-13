package com.autobook.ledger.domain

/**
 * 规则配置单例
 * 集中管理解析规则和包名白名单，支持未来云端下发更新
 */
object RulesConfig {
    
    /**
     * 支付类关键词
     */
    val PAYMENT_KEYWORDS = listOf(
        "成功付款", "付款成功", "已支付", "实付", "实际支付", 
        "支付成功", "支付金额", "付款金额", "支出", "消费", 
        "扣款", "自动续费"
    )
    
    /**
     * 退款类关键词
     */
    val REFUND_KEYWORDS = listOf(
        "退款", "退回", "返还"
    )
    
    /**
     * 转账类关键词
     */
    val TRANSFER_KEYWORDS = listOf(
        "转入", "转出", "转账", "提现", "零钱通"
    )
    
    /**
     * 收入类关键词
     */
    val INCOME_KEYWORDS = listOf(
        "工资", "薪资", "奖金", "收入"
    )
    
    /**
     * 营销类关键词（用于过滤纯营销消息）
     */
    val PROMOTIONAL_KEYWORDS = listOf(
        "优惠", "红包", "活动", "领取", "满", "立减", "折扣", "券"
    )
    
    /**
     * 金额提取正则表达式
     */
    val AMOUNT_PATTERNS = listOf(
        Regex("""(?:实付|已支付|实际支付|支付金额|付款金额|扣款金额)\s*[¥￥]?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*元?"""),
        Regex("""[¥￥]\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""),
        Regex("""人民币\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*元?"""),
        Regex("""(?:消费金额|消费|支出|付款|扣款)\s*[¥￥]?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*元"""),
        Regex("""([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*元"""),
    )
    
    /**
     * 商户提取正则表达式
     */
    val MERCHANT_PATTERNS = listOf(
        Regex("""给\s*([^\s，,。；;]+)"""),
        Regex("""收款方\s*([^\s，,。；;]+)"""),
        Regex("""付款方\s*([^\s，,。；;]+)"""),
        Regex("""商户\s*[:：]?\s*([^\s，,。；;可]+)"""),
        Regex("""在\s*([^\s，,。；;]+)\s*(?:消费|支付|付款)"""),
        Regex("""([^\s，,。；;]+)订单(?:金额|支付|付款)"""),
        Regex("""([^\s，,。；;]+)\s*(?:会员)?(?:自动续费|扣款|消费成功|付款成功)"""),
    )
    
    /**
     * 高可信度包名（通知渠道优先）
     */
    val HIGH_CONFIDENCE_PACKAGES = listOf(
        "com.eg.android.AlipayGphone",
        "com.eg.android.AlipayGphoneHW",
        "com.tencent.mm",
        "com.unionpay"
    )
    
    /**
     * 银行包名
     */
    val BANK_PACKAGES_KEYWORDS = listOf(
        "cmb", "icbc", "ccb", "boc", "abc", "bank", 
        "unionpay", "crossbank"
    )
    
    /**
     * 高可信度应用名称关键词
     */
    val HIGH_CONFIDENCE_APP_KEYWORDS = listOf(
        "支付宝", "微信", "银行", "招商", "工商", "建设", 
        "农业", "交通", "京东", "美团", "饿了么"
    )
    
    /**
     * 去重时间窗口（毫秒）
     */
    const val DEDUPLICATION_WINDOW_MS = 10 * 60 * 1000L // 10分钟
    
    /**
     * 渠道可信度优先级
     */
    fun getSourcePriority(sourceKind: SourceKind, sourcePackage: String?): Int {
        return when {
            // 通知渠道优先级更高
            sourceKind == SourceKind.NOTIFICATION && 
            sourcePackage in HIGH_CONFIDENCE_PACKAGES -> 3
            sourceKind == SourceKind.NOTIFICATION -> 2
            // 短信渠道
            sourceKind == SourceKind.SMS -> 1
            // 其他渠道
            else -> 0
        }
    }
    
    /**
     * 检查是否为高可信度来源
     */
    fun isHighConfidenceSource(sourcePackage: String?, sourceAppName: String, rawText: String): Boolean {
        val packageMatch = sourcePackage?.let { pkg ->
            HIGH_CONFIDENCE_PACKAGES.contains(pkg) || 
            BANK_PACKAGES_KEYWORDS.any { pkg.contains(it, ignoreCase = true) }
        } ?: false
        
        val appNameMatch = HIGH_CONFIDENCE_APP_KEYWORDS.any { 
            sourceAppName.contains(it) || rawText.contains(it) 
        }
        
        return packageMatch || appNameMatch
    }
}
