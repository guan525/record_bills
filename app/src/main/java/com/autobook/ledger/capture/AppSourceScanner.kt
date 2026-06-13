package com.autobook.ledger.capture

import android.content.Context
import android.content.pm.PackageManager

data class SpendingSourceApp(
    val label: String,
    val packageName: String,
    val reason: String,
    val installed: Boolean = true,
)

/**
 * 白名单包名配置
 * 仅检测这些已安装应用的状态，避免使用 QUERY_ALL_PACKAGES 权限
 */
object WhitelistPackages {
    val PAYMENT = listOf(
        "com.eg.android.AlipayGphone" to "支付宝",
        "com.eg.android.AlipayGphoneHW" to "支付宝",
        "com.tencent.mm" to "微信",
        "com.unionpay" to "云闪付",
    )

    val BANKING = listOf(
        "cmb.pb" to "招商银行",
        "com.cmbchina.ccd.pluto" to "招商银行",
        "com.icbc" to "工商银行",
        "com.icbc.client" to "工商银行",
        "com.chinamworld.main" to "建设银行",
        "com.ccb.ccbhome" to "建设银行",
        "com.android.bankabc" to "农业银行",
        "com.bankcomm.Bankcomm" to "交通银行",
        "com.chinamworld.bocmbci" to "中国银行",
        "com.psbc.citizencard" to "邮储银行",
        "com.pingan.paces.ccb" to "平安银行",
        "com.spdb.mobilebank" to "浦发银行",
        "com.cmbc.mobilebank" to "民生银行",
        "com.ecnc.mobilebank" to "中信银行",
        "com.cebbank.mobilebank" to "光大银行",
    )

    val ECOMMERCE = listOf(
        "com.taobao.taobao" to "淘宝",
        "com.tmall.wireless" to "天猫",
        "com.jingdong.app.mall" to "京东",
        "com.xunmeng.pinduoduo" to "拼多多",
    )

    val FOOD_DELIVERY = listOf(
        "com.sankuai.meituan" to "美团",
        "me.ele" to "饿了么",
    )

    val SOCIAL = listOf(
        "com.ss.android.ugc.aweme" to "抖音",
        "com.smile.gifmaker" to "快手",
    )

    val TRAVEL = listOf(
        "com.sdu.didi.psnger" to "滴滴",
        "ctrip.android.view" to "携程",
        "com.taobao.trip" to "飞猪",
    )

    val ALL = PAYMENT + BANKING + ECOMMERCE + FOOD_DELIVERY + SOCIAL + TRAVEL

    fun getReason(packageName: String): String? = when {
        PAYMENT.any { it.first == packageName } -> "支付钱包"
        BANKING.any { it.first == packageName } -> "银行信用卡"
        ECOMMERCE.any { it.first == packageName } -> "电商购物"
        FOOD_DELIVERY.any { it.first == packageName } -> "外卖买菜"
        TRAVEL.any { it.first == packageName } -> "交通出行"
        SOCIAL.any { it.first == packageName } -> "生活服务"
        else -> null
    }
}

class AppSourceScanner(
    private val context: Context,
) {
    fun likelySpendingApps(): List<SpendingSourceApp> {
        val pm = context.packageManager
        return WhitelistPackages.ALL.mapNotNull { (packageName, label) ->
            toSpendingSource(pm, packageName, label)
        }.sortedWith(compareBy<SpendingSourceApp> { priority(it.reason) }.thenBy { it.label })
    }

    private fun toSpendingSource(
        pm: PackageManager,
        packageName: String,
        defaultLabel: String,
    ): SpendingSourceApp? {
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val label = appInfo.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: defaultLabel
            val reason = WhitelistPackages.getReason(packageName) ?: return null
            SpendingSourceApp(label = label, packageName = packageName, reason = reason)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
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
}
