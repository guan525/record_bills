package com.autobook.ledger.domain

import java.math.BigDecimal
import java.math.RoundingMode

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
        val promotional = listOf("优惠", "红包", "活动", "领取", "满", "立减", "折扣", "券")
        val transactional = listOf("付款", "支付", "支出", "消费", "扣款", "退款", "转入", "转出", "到账", "自动续费", "收款")
        return promotional.any(raw::contains) && transactional.none(raw::contains)
    }

    private fun extractAmountCents(raw: String): Long? {
        val patterns = listOf(
            Regex("""(?:实付|实际支付|支付金额|付款金额|扣款金额)\s*[¥￥]?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*元?"""),
            Regex("""[¥￥]\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""),
            Regex("""人民币\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*元?"""),
            Regex("""(?:消费金额|消费|支出|付款|扣款)\s*[¥￥]?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*元"""),
            Regex("""([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*元"""),
        )
        val amountText = patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(raw)?.groupValues?.getOrNull(1)
        } ?: return null
        return BigDecimal(amountText.replace(",", ""))
            .multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    private fun detectType(raw: String): LedgerType = when {
        raw.contains("退款") || raw.contains("退回") || raw.contains("返还") -> LedgerType.REFUND
        raw.contains("转入") || raw.contains("转出") || raw.contains("转账") || raw.contains("提现") || raw.contains("零钱通") -> LedgerType.TRANSFER
        raw.contains("工资") || raw.contains("薪资") || raw.contains("奖金") || raw.contains("收入") -> LedgerType.INCOME
        else -> LedgerType.EXPENSE
    }

    private fun extractMerchant(raw: String, sourceAppName: String): String {
        val patterns = listOf(
            Regex("""给\s*([^\s，,。；;]+)"""),
            Regex("""收款方\s*([^\s，,。；;]+)"""),
            Regex("""付款方\s*([^\s，,。；;]+)"""),
            Regex("""商户\s*[:：]?\s*([^\s，,。；;可]+)"""),
            Regex("""在\s*([^\s，,。；;]+)\s*(?:消费|支付|付款)"""),
            Regex("""([^\s，,。；;]+)订单(?:金额|支付|付款)"""),
            Regex("""([^\s，,。；;]+)\s*(?:会员)?(?:自动续费|扣款|消费成功|付款成功)"""),
        )
        val candidate = patterns.firstNotNullOfOrNull { pattern ->
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
        val knownSource = listOf("支付宝", "微信", "银行", "招商", "工商", "建设", "农业", "交通", "京东", "美团", "饿了么")
            .any { sourceAppName.contains(it) || raw.contains(it) }
        val knownPackage = sourcePackage?.let { packageName ->
            listOf("alipay", "tencent.mm", "bank", "cmb", "unionpay").any { packageName.contains(it, ignoreCase = true) }
        } ?: false
        val merchantKnown = merchant != sourceAppName && merchant != "未知商户"
        var score = 55
        if (knownSource || knownPackage) score += 20
        if (merchantKnown) score += 15
        if (raw.contains("付款") || raw.contains("支出") || raw.contains("扣款") || raw.contains("消费")) score += 10
        return score.coerceIn(35, 95)
    }
}
