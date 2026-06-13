package com.autobook.ledger.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 账单解析器
 * 使用 RulesConfig 中的规则进行解析，支持未来云端下发更新
 */
class BillParser(
    categoryCatalog: CategoryCatalog,
) {
    private val classifier = CategoryClassifier(categoryCatalog)

    fun parse(
        sourceKind: SourceKind,
        sourcePackage: String?,
        sourceAppName: String,
        title: String,
        text: String,
        timestampMillis: Long,
    ): ParsedBill? {
        val raw = listOf(title, text).joinToString(" ").trim()
        if (raw.isBlank() || isPromotionOnly(raw)) return null

        val amountCents = extractAmountCents(raw) ?: return null
        val type = detectType(raw)
        val merchant = extractMerchant(raw, sourceAppName)
        val categoryPath = when (type) {
            LedgerType.REFUND -> "购物/电商/退款"
            LedgerType.TRANSFER -> if (raw.contains("转入")) "资金流转/账户互转/转入" else "资金流转/账户互转/转出"
            else -> classifier.classify(merchant, raw)
        }
        val confidence = confidenceFor(sourcePackage, sourceAppName, merchant, raw)

        return ParsedBill(
            type = type,
            status = LedgerStatus.PENDING,
            amountCents = amountCents,
            occurredAt = timestampMillis,
            merchant = merchant,
            title = title.ifBlank { merchant },
            categoryPath = categoryPath,
            account = detectAccount(sourceKind, sourcePackage, sourceAppName, raw),
            paymentMethod = detectPaymentMethod(sourcePackage, sourceAppName, raw),
            sourceKind = sourceKind,
            sourcePackage = sourcePackage,
            sourceAppName = sourceAppName,
            rawText = raw,
            confidence = confidence,
        )
    }

    private fun isPromotionOnly(raw: String): Boolean {
        val hasPromotional = RulesConfig.PROMOTIONAL_KEYWORDS.any(raw::contains)
        val hasTransactional = (RulesConfig.PAYMENT_KEYWORDS + 
                               RulesConfig.REFUND_KEYWORDS + 
                               RulesConfig.TRANSFER_KEYWORDS + 
                               listOf("收款")).any(raw::contains)
        return hasPromotional && !hasTransactional
    }

    private fun extractAmountCents(raw: String): Long? {
        val amountText = RulesConfig.AMOUNT_PATTERNS.firstNotNullOfOrNull { pattern ->
            pattern.find(raw)?.groupValues?.getOrNull(1)
        } ?: return null
        return BigDecimal(amountText.replace(",", ""))
            .multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    private fun detectType(raw: String): LedgerType = when {
        RulesConfig.REFUND_KEYWORDS.any(raw::contains) -> LedgerType.REFUND
        RulesConfig.TRANSFER_KEYWORDS.any(raw::contains) -> LedgerType.TRANSFER
        RulesConfig.INCOME_KEYWORDS.any(raw::contains) -> LedgerType.INCOME
        else -> LedgerType.EXPENSE
    }

    private fun extractMerchant(raw: String, sourceAppName: String): String {
        val candidate = RulesConfig.MERCHANT_PATTERNS.firstNotNullOfOrNull { pattern ->
            pattern.find(raw)?.groupValues?.getOrNull(1)
        }
        return candidate
            ?.trim(' ', '，', ',', '。', '；', ';', ':', '：')
            ?.takeIf { it.isNotBlank() }
            ?: sourceAppName.ifBlank { "未知商户" }
    }

    private fun detectAccount(
        sourceKind: SourceKind,
        sourcePackage: String?,
        sourceAppName: String,
        raw: String,
    ): String {
        val source = "${sourcePackage.orEmpty()} $sourceAppName $raw"
        return when {
            source.contains("Alipay", ignoreCase = true) || source.contains("支付宝") -> "支付宝"
            source.contains("tencent.mm", ignoreCase = true) || source.contains("微信") -> "微信支付"
            source.contains("信用卡") -> "信用卡"
            sourceKind == SourceKind.SMS || source.contains("银行") || source.contains("储蓄卡") -> "银行卡"
            else -> "待确认账户"
        }
    }

    private fun detectPaymentMethod(sourcePackage: String?, sourceAppName: String, raw: String): String {
        val source = "${sourcePackage.orEmpty()} $sourceAppName $raw"
        return when {
            source.contains("信用卡") -> "信用卡"
            source.contains("支付宝") || source.contains("Alipay", ignoreCase = true) -> "支付宝"
            source.contains("微信") || source.contains("tencent.mm", ignoreCase = true) -> "微信支付"
            source.contains("银行") -> "银行卡"
            else -> ""
        }
    }

    private fun confidenceFor(sourcePackage: String?, sourceAppName: String, merchant: String, raw: String): Int {
        val highConfidence = RulesConfig.isHighConfidenceSource(sourcePackage, sourceAppName, raw)
        val merchantKnown = merchant != sourceAppName && merchant != "未知商户"
        var score = 55
        if (highConfidence) score += 20
        if (merchantKnown) score += 15
        if (raw.contains("付款") || raw.contains("支出") || raw.contains("扣款") || raw.contains("消费")) score += 10
        return score.coerceIn(35, 95)
    }
}
