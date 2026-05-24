package com.autobook.ledger.capture

import android.content.Context
import android.content.Intent
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
        val launcherApps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PackageManager.GET_META_DATA,
        ).map { it.activityInfo.applicationInfo }
        val installedApps = runCatching { pm.getInstalledApplications(PackageManager.GET_META_DATA) }.getOrDefault(emptyList())
        return (launcherApps + installedApps)
            .mapNotNull { appInfo -> toSpendingSource(pm, appInfo) }
            .distinctBy { it.packageName }
            .sortedWith(compareBy<SpendingSourceApp> { priority(it.reason) }.thenBy { it.label })
    }

    private fun toSpendingSource(pm: PackageManager, appInfo: ApplicationInfo): SpendingSourceApp? {
        val label = appInfo.loadLabel(pm)?.toString().orEmpty()
        val packageName = appInfo.packageName
        val reason = AppSourceMatcher.match(label, packageName) ?: return null
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

}
