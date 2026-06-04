@file:OptIn(ExperimentalLayoutApi::class)

package com.autobook.ledger.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autobook.ledger.capture.SpendingSourceApp
import com.autobook.ledger.domain.LedgerStats
import com.autobook.ledger.domain.LedgerStatus
import com.autobook.ledger.domain.LedgerType
import com.autobook.ledger.domain.ParsedBill
import java.util.Locale

private enum class MainTab(val label: String) {
    HOME("首页"),
    RECORDS("明细"),
    INSIGHTS("统计"),
    SOURCES("来源"),
    SETTINGS("设置"),
}

@Composable
fun AutoLedgerAppScreen(
    entries: List<ParsedBill>,
    stats: LedgerStats,
    sources: List<SpendingSourceApp>,
    message: String,
    syncKey: String,
    supabaseEndpoint: String,
    categories: List<String>,
    onAddManual: (String, String, String, String) -> Unit,
    onConfirm: (String) -> Unit,
    onIgnore: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRefreshSources: () -> Unit,
    onSyncNow: () -> Unit,
    onExportCsv: () -> Unit,
    onUpdateSyncKey: (String) -> Unit,
) {
    AutoLedgerTheme {
        var tab by remember { mutableStateOf(MainTab.HOME) }
        var showManualDialog by remember { mutableStateOf(false) }
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    MainTab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Text(item.label.take(1), fontWeight = FontWeight.Bold) },
                            label = { Text(item.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding),
            ) {
                when (tab) {
                    MainTab.HOME -> HomeScreen(stats, entries, message, onAdd = { showManualDialog = true }, onConfirm, onIgnore)
                    MainTab.RECORDS -> RecordsScreen(entries, onConfirm, onIgnore, onDelete)
                    MainTab.INSIGHTS -> InsightsScreen(entries, stats)
                    MainTab.SOURCES -> SourcesScreen(sources, onRefreshSources)
                    MainTab.SETTINGS -> SettingsScreen(syncKey, supabaseEndpoint, message, onSyncNow, onExportCsv, onUpdateSyncKey)
                }
            }
        }
        if (showManualDialog) {
            ManualEntryDialog(
                categories = categories,
                onDismiss = { showManualDialog = false },
                onSave = { amount, merchant, category, account ->
                    onAddManual(amount, merchant, category, account)
                    showManualDialog = false
                },
            )
        }
    }
}

@Composable
private fun HomeScreen(
    stats: LedgerStats,
    entries: List<ParsedBill>,
    message: String,
    onAdd: () -> Unit,
    onConfirm: (String) -> Unit,
    onIgnore: (String) -> Unit,
) {
    val pending = entries.filter { it.status == LedgerStatus.PENDING }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader("自动账本", "本月确认支出 ${stats.confirmedExpenseCents.cny()}，待确认 ${stats.pendingCount} 条")
            SummaryBand(stats)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onAdd, modifier = Modifier.weight(1f)) { Text("手动记一笔") }
                OutlinedButton(onClick = {}, modifier = Modifier.weight(1f), enabled = false) { Text("自动监听中") }
            }
        }
        item { StatusMessage(message) }
        if (pending.isNotEmpty()) {
            item { SectionTitle("待确认账单") }
            items(pending.take(5), key = { it.id }) { entry ->
                EntryRow(entry, showRaw = false, onConfirm = onConfirm, onIgnore = onIgnore, onDelete = null)
            }
        }
        item { SectionTitle("最近明细") }
        items(entries.take(12), key = { it.id }) { entry ->
            EntryRow(entry, showRaw = false, onConfirm = onConfirm, onIgnore = onIgnore, onDelete = null)
        }
    }
}

@Composable
private fun RecordsScreen(
    entries: List<ParsedBill>,
    onConfirm: (String) -> Unit,
    onIgnore: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var filters by remember { mutableStateOf(EntryFilters()) }
    val filtered = entries.filtered(filters)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SectionHeader("明细", "搜索、筛选、确认和删除账单") }
        item {
            OutlinedTextField(
                value = filters.query,
                onValueChange = { filters = filters.copy(query = it) },
                label = { Text("搜索商户、分类、来源或原文") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filters.status == null, onClick = { filters = filters.copy(status = null) }, label = { Text("全部") })
                FilterChip(selected = filters.status == LedgerStatus.PENDING, onClick = { filters = filters.copy(status = LedgerStatus.PENDING) }, label = { Text("待确认") })
                FilterChip(selected = filters.status == LedgerStatus.CONFIRMED, onClick = { filters = filters.copy(status = LedgerStatus.CONFIRMED) }, label = { Text("已确认") })
                FilterChip(selected = filters.status == LedgerStatus.IGNORED, onClick = { filters = filters.copy(status = LedgerStatus.IGNORED) }, label = { Text("已忽略") })
            }
        }
        items(filtered, key = { it.id }) { entry ->
            EntryRow(entry, showRaw = true, onConfirm = onConfirm, onIgnore = onIgnore, onDelete = onDelete)
        }
    }
}

@Composable
private fun InsightsScreen(entries: List<ParsedBill>, stats: LedgerStats) {
    val expenses = entries.confirmedExpenses()
    val category = expenses.groupBy { it.categoryPath.topCategory() }.mapValues { it.value.sumOf { entry -> entry.amountCents } }
    val merchant = expenses.groupBy { it.merchant }.mapValues { it.value.sumOf { entry -> entry.amountCents } }
    val account = expenses.groupBy { it.account }.mapValues { it.value.sumOf { entry -> entry.amountCents } }
    val source = expenses.groupBy { it.sourceAppName }.mapValues { it.value.sumOf { entry -> entry.amountCents } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeader("统计", "按分类、商户、账户、来源拆解支出") }
        item { SummaryBand(stats) }
        item { Breakdown("分类支出", category) }
        item { Breakdown("商户排行", merchant) }
        item { Breakdown("账户支出", account) }
        item { Breakdown("来源 App", source) }
    }
}

@Composable
private fun SourcesScreen(
    sources: List<SpendingSourceApp>,
    onRefreshSources: () -> Unit,
) {
    val context = LocalContext.current
    val smsGranted = context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
        context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    val notificationEnabled = notificationListenerEnabled(context)
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeader("来源", "扫描手机上可能产生消费的 App，并检查监听权限") }
        item {
            PermissionPanel(
                notificationEnabled = notificationEnabled,
                smsGranted = smsGranted,
                onOpenNotificationSettings = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                onRequestSms = { smsLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)) },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRefreshSources) { Text("重新扫描") }
                Text("${sources.size} 个可能来源", modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
        items(sources, key = { it.packageName }) { source ->
            Panel {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(source.label, fontWeight = FontWeight.SemiBold)
                        Text(source.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Badge(source.reason)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    syncKey: String,
    supabaseEndpoint: String,
    message: String,
    onSyncNow: () -> Unit,
    onExportCsv: () -> Unit,
    onUpdateSyncKey: (String) -> Unit,
) {
    var editingKey by remember(syncKey) { mutableStateOf(syncKey) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeader("设置", "Supabase 同步、换机密钥和备份") }
        item { StatusMessage(message) }
        item {
            Panel {
                Text("Supabase 项目", fontWeight = FontWeight.SemiBold)
                Text(supabaseEndpoint, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = editingKey,
                    onValueChange = { editingKey = it },
                    label = { Text("换机同步密钥") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onUpdateSyncKey(editingKey) }) { Text("保存密钥") }
                    OutlinedButton(onClick = onSyncNow) { Text("立即同步") }
                }
            }
        }
        item {
            Panel {
                Text("Supabase 建表", fontWeight = FontWeight.SemiBold)
                Text("先在 Supabase SQL Editor 执行项目内 supabase/schema.sql，再点立即同步。")
            }
        }
        item {
            Panel {
                Text("本地备份", fontWeight = FontWeight.SemiBold)
                Text("导出 CSV 到 App 外部文件目录，作为长期兜底备份。")
                Spacer(Modifier.height(10.dp))
                Button(onClick = onExportCsv) { Text("导出 CSV") }
            }
        }
    }
}

@Composable
private fun SummaryBand(stats: LedgerStats) {
    Panel(background = Color(0xFF243B53), contentColor = Color.White) {
        Text("确认支出", color = Color(0xFFD9E8F5))
        Text(stats.confirmedExpenseCents.cny(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Metric("收入/退款", stats.incomeAndRefundCents.cny(), Modifier.weight(1f))
            Metric("待确认", "${stats.pendingCount} 条", Modifier.weight(1f))
        }
    }
}

@Composable
private fun EntryRow(
    entry: ParsedBill,
    showRaw: Boolean,
    onConfirm: (String) -> Unit,
    onIgnore: (String) -> Unit,
    onDelete: ((String) -> Unit)?,
) {
    Panel {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.merchant, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Badge(statusLabel(entry.status))
                }
                Text(entry.categoryPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${entry.sourceAppName} · ${entry.account} · ${entry.occurredAt.formatDateTime()}", style = MaterialTheme.typography.bodySmall)
            }
            Text(entry.amountCents.cny(), fontWeight = FontWeight.Bold, color = amountColor(entry.type))
        }
        if (showRaw && entry.rawText.isNotBlank() && entry.rawText != "manual") {
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Text(entry.rawText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
            if (entry.status == LedgerStatus.PENDING) {
                Button(onClick = { onConfirm(entry.id) }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) { Text("确认") }
                OutlinedButton(onClick = { onIgnore(entry.id) }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) { Text("忽略") }
            }
            if (onDelete != null) {
                TextButton(onClick = { onDelete(entry.id) }) { Text("删除") }
            }
        }
    }
}

@Composable
private fun ManualEntryDialog(
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(categories.firstOrNull() ?: "未分类/待确认/其他") }
    var account by remember { mutableStateOf("现金") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动记一笔") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("金额") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(value = merchant, onValueChange = { merchant = it }, label = { Text("商户/备注") }, singleLine = true)
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("分类路径") }, minLines = 2)
                OutlinedTextField(value = account, onValueChange = { account = it }, label = { Text("账户") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.take(8).forEach { option ->
                        FilterChip(selected = category == option, onClick = { category = option }, label = { Text(option.substringAfter("/")) })
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(amount, merchant, category, account) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun PermissionPanel(
    notificationEnabled: Boolean,
    smsGranted: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onRequestSms: () -> Unit,
) {
    Panel {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("通知监听", fontWeight = FontWeight.SemiBold)
                Text(if (notificationEnabled) "已开启，可自动读取支付通知" else "未开启，需要授权自动识别通知")
            }
            Button(onClick = onOpenNotificationSettings) { Text(if (notificationEnabled) "设置" else "去开启") }
        }
        Divider(modifier = Modifier.padding(vertical = 12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("短信账单", fontWeight = FontWeight.SemiBold)
                Text(if (smsGranted) "已授权，可识别银行/信用卡短信" else "未授权，只使用通知识别")
            }
            Button(onClick = onRequestSms) { Text(if (smsGranted) "已授权" else "授权") }
        }
    }
}

@Composable
private fun Breakdown(title: String, values: Map<String, Long>) {
    val top = values.entries.sortedByDescending { it.value }.take(8)
    val max = top.maxOfOrNull { it.value } ?: 1L
    Panel {
        Text(title, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (top.isEmpty()) {
            Text("确认账单后显示统计。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                top.forEach { item ->
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.key, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(item.value.cny(), fontWeight = FontWeight.SemiBold)
                        }
                        LinearProgressIndicator(
                            progress = (item.value.toFloat() / max).coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun StatusMessage(message: String) {
    Surface(color = Color(0xFFE8F4EA), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.padding(12.dp), color = Color(0xFF24513B))
    }
}

@Composable
private fun Panel(
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable () -> Unit,
) {
    Surface(
        color = background,
        contentColor = contentColor,
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            content()
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(Color(0x1AFFFFFF), RoundedCornerShape(8.dp)).padding(10.dp)) {
        Text(label, color = Color(0xFFD9E8F5), style = MaterialTheme.typography.bodySmall)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Badge(text: String) {
    Surface(color = Color(0xFFF1E8D9), shape = RoundedCornerShape(6.dp)) {
        Text(text, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF5F4320))
    }
}

@Composable
private fun AutoLedgerTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = Color(0xFF245E71),
        secondary = Color(0xFF8A5A44),
        tertiary = Color(0xFF627D3A),
        background = Color(0xFFF7F7F2),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1F2933),
        onSurfaceVariant = Color(0xFF52606D),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

private fun statusLabel(status: LedgerStatus): String = when (status) {
    LedgerStatus.PENDING -> "待确认"
    LedgerStatus.CONFIRMED -> "已确认"
    LedgerStatus.IGNORED -> "已忽略"
}

private fun amountColor(type: LedgerType): Color = when (type) {
    LedgerType.EXPENSE -> Color(0xFF8A3B2E)
    LedgerType.INCOME, LedgerType.REFUND -> Color(0xFF276749)
    LedgerType.TRANSFER, LedgerType.ADJUSTMENT -> Color(0xFF52606D)
}

private fun notificationListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
    return flat.lowercase(Locale.US).contains(context.packageName.lowercase(Locale.US))
}
