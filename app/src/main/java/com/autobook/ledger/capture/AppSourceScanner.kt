package com.autobook.ledger.capture

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

data class SpendingSourceApp(
    val label: String,
    val packageName: String,
    val reason: String,
    val installed: Boolean = true,
)

class AppSourceScanner(
    private val context: Context,
) {
    fun likelySpendingApps(): List<SpendingSourceApp> {
        val pm = context.packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .mapNotNull { appInfo -> toSpendingSource(pm, appInfo) }
            .distinctBy { it.packageName }
            .sortedWith(compareBy<SpendingSourceApp> { priority(it.reason) }.thenBy { it.label })
    }

    private fun toSpendingSource(pm: PackageManager, appInfo: ApplicationInfo): SpendingSourceApp? {
        val label = appInfo.loadLabel(pm)?.toString().orEmpty()
        val packageName = appInfo.packageName
        val haystack = "$label $packageName".lowercase()
        val reason = rules.firstOrNull { rule ->
            rule.keywords.any { haystack.contains(it.lowercase()) }
        }?.reason ?: return null
        return SpendingSourceApp(label = label.ifBlank { packageName }, packageName = packageName, reason = reason)
    }

    private fun priority(reason: String): Int = when (reason) {
        "支付钱包" -> 0
        "银行信用卡" -> 1
        "电商购物" -> 2
        "外卖买菜" -> 3
        "交通出行" -> 4
        "生活服务" -> 5
        else -> 9
    }

    private data class Rule(val reason: String, val keywords: List<String>)

    companion object {
        private val rules = listOf(
            Rule("支付钱包", listOf("支付宝", "alipay", "微信", "tencent.mm", "云闪付", "unionpay")),
            Rule("银行信用卡", listOf("银行", "信用卡", "cmb", "icbc", "ccb", "boc", "abc", "bank", "招行", "工行", "建行", "农行", "交行", "平安口袋")),
            Rule("电商购物", listOf("淘宝", "天猫", "京东", "拼多多", "抖音", "快手", "唯品会", "小红书", "得物", "闲鱼")),
            Rule("外卖买菜", listOf("美团", "饿了么", "盒马", "叮咚", "朴朴", "京东到家")),
            Rule("交通出行", listOf("滴滴", "高德", "百度地图", "携程", "飞猪", "去哪儿", "12306", "航旅")),
            Rule("生活服务", listOf("电信", "移动", "联通", "水电", "燃气", "物业")),
            Rule("订阅内容", listOf("腾讯视频", "爱奇艺", "优酷", "网易云", "bilibili", "apple music", "spotify")),
        )
    }
}

