@file:OptIn(ExperimentalLayoutApi::class)

package com.autobook.ledger.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
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
import java.math.BigDecimal
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
    autoLedgerEnabled: Boolean,
    supabaseEndpoint: String,
    categories: List<String>,
    onAddManual: (LedgerType, String, String, String, String, String) -> Unit,
    onConfirm: (String) -> Unit,
    onIgnore: (String) -> Unit,
    onConfirmAll: (List<String>) -> Unit,
    onIgnoreAll: (List<String>) -> Unit,
    onDelete: (String) -> Unit,
    onUpdateEntry: (String, LedgerType, String, String, String, String, String, Boolean) -> Unit,
    onRefreshSources: () -> Unit,
    onSyncNow: () -> Unit,
    onExportCsv: () -> Unit,
    onUpdateSyncKey: (String) -> Unit,
) {
    AutoLedgerTheme {
        var tab by remember { mutableStateOf(MainTab.HOME) }
        var showManualDialog by remember { mutableStateOf(false) }
        var editingEntry by remember { mutableStateOf<ParsedBill?>(null) }
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
                    MainTab.HOME -> HomeScreen(
                        stats,
                        entries,
                        message,
                        onAdd = { showManualDialog = true },
                        onConfirm,
                        onIgnore,
                        onConfirmAll,
                        onIgnoreAll,
                        onEdit = { editingEntry = it },
                    )
                    MainTab.RECORDS -> RecordsScreen(entries, onConfirm, onIgnore, onConfirmAll, onIgnoreAll, onDelete, onEdit = { editingEntry = it })
                    MainTab.INSIGHTS -> InsightsScreen(entries, stats)
                    MainTab.SOURCES -> SourcesScreen(sources, autoLedgerEnabled, onRefreshSources)
                    MainTab.SETTINGS -> SettingsScreen(syncKey, supabaseEndpoint, message, onSyncNow, onExportCsv, onUpdateSyncKey)
                }
            }
        }
        if (showManualDialog) {
            ManualEntryDialog(
                categories = categories,
                onDismiss = { showManualDialog = false },
                onSave = { type, amount, merchant, category, account, note ->
                    onAddManual(type, amount, merchant, category, account, note)
                    showManualDialog = false
                },
            )
        }
        editingEntry?.let { entry ->
            EditEntryDialog(
                entry = entry,
                categories = categories,
                onDismiss = { editingEntry = null },
                onSave = { type, amount, merchant, category, account, note, confirm ->
                    onUpdateEntry(entry.id, type, amount, merchant, category, account, note, confirm)
                    editingEntry = null
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
    onConfirmAll: (List<String>) -> Unit,
    onIgnoreAll: (List<String>) -> Unit,
    onEdit: (ParsedBill) -> Unit,
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
            item {
                BatchActions(
                    count = pending.size,
                    confirmLabel = "全部确认",
                    ignoreLabel = "全部忽略",
                    onConfirmAll = { onConfirmAll(pending.map { it.id }) },
                    onIgnoreAll = { onIgnoreAll(pending.map { it.id }) },
                )
            }
            items(pending.take(5), key = { it.id }) { entry ->
                EntryRow(entry, showRaw = false, onConfirm = onConfirm, onIgnore = onIgnore, onDelete = null, onEdit = onEdit)
            }
        }
        item { SectionTitle("最近明细") }
        items(entries.take(12), key = { it.id }) { entry ->
            EntryRow(entry, showRaw = false, onConfirm = onConfirm, onIgnore = onIgnore, onDelete = null, onEdit = onEdit)
        }
    }
}

@Composable
private fun RecordsScreen(
    entries: List<ParsedBill>,
    onConfirm: (String) -> Unit,
    onIgnore: (String) -> Unit,
    onConfirmAll: (List<String>) -> Unit,
    onIgnoreAll: (List<String>) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (ParsedBill) -> Unit,
) {
    var filters by remember { mutableStateOf(EntryFilters()) }
    val filtered = entries.filtered(filters)
    val pendingFiltered = filtered.filter { it.status == LedgerStatus.PENDING }
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
        if (filters.status == LedgerStatus.PENDING && pendingFiltered.isNotEmpty()) {
            item {
                BatchActions(
                    count = pendingFiltered.size,
                    confirmLabel = "确认当前筛选",
                    ignoreLabel = "忽略当前筛选",
                    onConfirmAll = { onConfirmAll(pendingFiltered.map { it.id }) },
                    onIgnoreAll = { onIgnoreAll(pendingFiltered.map { it.id }) },
                )
            }
        }
        items(filtered, key = { it.id }) { entry ->
            EntryRow(entry, showRaw = true, onConfirm = onConfirm, onIgnore = onIgnore, onDelete = onDelete, onEdit = onEdit)
        }
    }
}

@Composable
private fun InsightsScreen(entries: List<ParsedBill>, stats: LedgerStats) {
    val expenses = entries.currentMonthConfirmedExpenses()
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
    autoLedgerEnabled: Boolean,
    onRefreshSources: () -> Unit,
) {
    val context = LocalContext.current
    val smsGranted = context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
        context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    val notificationEnabled = autoLedgerEnabled
    val isXiaomi = remember { isXiaomiDevice() }
    var showXiaomiGuide by remember { mutableStateOf(false) }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    LaunchedEffect(isXiaomi, notificationEnabled) {
        if (isXiaomi && notificationEnabled && !xiaomiGuideAlreadyShown(context)) {
            showXiaomiGuide = true
        }
    }

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
        if (isXiaomi) {
            item {
                XiaomiGuideHighlight(
                    notificationEnabled = notificationEnabled,
                    onOpenGuide = { showXiaomiGuide = true },
                )
            }
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

    if (showXiaomiGuide) {
        RedmiK50GuideDialog(
            autoLedgerEnabled = notificationEnabled,
            onDismiss = {
                if (notificationEnabled) markXiaomiGuideShown(context)
                showXiaomiGuide = false
            },
        )
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
    onEdit: (ParsedBill) -> Unit,
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
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(entry.rawText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
            if (entry.status == LedgerStatus.PENDING) {
                Button(onClick = { onConfirm(entry.id) }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) { Text("确认") }
                OutlinedButton(onClick = { onIgnore(entry.id) }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) { Text("忽略") }
            }
            OutlinedButton(onClick = { onEdit(entry) }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) { Text("纠错") }
            if (onDelete != null) {
                TextButton(onClick = { onDelete(entry.id) }) { Text("删除") }
            }
        }
    }
}

@Composable
private fun BatchActions(
    count: Int,
    confirmLabel: String,
    ignoreLabel: String,
    onConfirmAll: () -> Unit,
    onIgnoreAll: () -> Unit,
) {
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$count 条待处理", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConfirmAll, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) { Text(confirmLabel) }
                OutlinedButton(onClick = onIgnoreAll, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) { Text(ignoreLabel) }
            }
        }
    }
}

@Composable
private fun ManualEntryDialog(
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (LedgerType, String, String, String, String, String) -> Unit,
) {
    var type by remember { mutableStateOf(LedgerType.EXPENSE) }
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(defaultManualCategory(type, categories)) }
    var account by remember { mutableStateOf("现金") }
    var note by remember { mutableStateOf("") }
    var showAmountError by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动记一笔") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    manualTypes.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = {
                                type = option
                                category = defaultManualCategory(option, categories)
                            },
                            label = { Text(manualTypeLabel(option)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it
                        showAmountError = false
                    },
                    label = { Text("金额") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = showAmountError,
                    supportingText = {
                        if (showAmountError) Text("请输入大于 0 的金额")
                    },
                )
                OutlinedTextField(value = merchant, onValueChange = { merchant = it }, label = { Text("商户/来源") }, singleLine = true)
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("分类路径") }, minLines = 2)
                OutlinedTextField(value = account, onValueChange = { account = it }, label = { Text("账户") }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注") }, minLines = 2)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.take(8).forEach { option ->
                        FilterChip(selected = category == option, onClick = { category = option }, label = { Text(option.substringAfter("/")) })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!amount.isPositiveAmountText()) {
                        showAmountError = true
                    } else {
                        onSave(type, amount, merchant, category, account, note)
                    }
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun EditEntryDialog(
    entry: ParsedBill,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (LedgerType, String, String, String, String, String, Boolean) -> Unit,
) {
    var type by remember(entry.id) { mutableStateOf(entry.type) }
    var amount by remember(entry.id) { mutableStateOf(entry.amountCents.toAmountText()) }
    var merchant by remember(entry.id) { mutableStateOf(entry.merchant) }
    var category by remember(entry.id) { mutableStateOf(entry.categoryPath) }
    var account by remember(entry.id) { mutableStateOf(entry.account) }
    var note by remember(entry.id) { mutableStateOf(entry.note) }
    var showAmountError by remember(entry.id) { mutableStateOf(false) }
    val canConfirm = entry.status == LedgerStatus.PENDING
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (canConfirm) "纠错待确认账单" else "修改账单") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    manualTypes.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = {
                                type = option
                                if (category.isBlank() || category.startsWith("未分类/")) {
                                    category = defaultManualCategory(option, categories)
                                }
                            },
                            label = { Text(manualTypeLabel(option)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it
                        showAmountError = false
                    },
                    label = { Text("金额") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = showAmountError,
                    supportingText = {
                        if (showAmountError) Text("请输入大于 0 的金额")
                    },
                )
                OutlinedTextField(value = merchant, onValueChange = { merchant = it }, label = { Text("商户/来源") }, singleLine = true)
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("分类路径") }, minLines = 2)
                OutlinedTextField(value = account, onValueChange = { account = it }, label = { Text("账户") }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注") }, minLines = 2)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.take(10).forEach { option ->
                        FilterChip(selected = category == option, onClick = { category = option }, label = { Text(option.substringAfter("/")) })
                    }
                }
            }
        },
        confirmButton = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canConfirm) {
                    Button(
                        onClick = {
                            if (!amount.isPositiveAmountText()) {
                                showAmountError = true
                            } else {
                                onSave(type, amount, merchant, category, account, note, true)
                            }
                        },
                    ) {
                        Text("保存并确认")
                    }
                }
                OutlinedButton(
                    onClick = {
                        if (!amount.isPositiveAmountText()) {
                            showAmountError = true
                        } else {
                            onSave(type, amount, merchant, category, account, note, false)
                        }
                    },
                ) {
                    Text("保存")
                }
            }
        },
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
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
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
private fun XiaomiGuideHighlight(
    notificationEnabled: Boolean,
    onOpenGuide: () -> Unit,
) {
    Panel(background = Color(0xFFFFF3D6), contentColor = Color(0xFF3D2C00)) {
        Text("红米 K50 / 小米系统专属防杀设置", fontWeight = FontWeight.SemiBold)
        Text(
            if (notificationEnabled) {
                "已检测到自动记账开启。建议完成自启动、无限制省电、后台锁定三项设置，降低系统清理导致漏记的概率。"
            } else {
                "检测到小米设备。开启自动记账后，请按专属步骤关闭系统防杀限制。"
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onOpenGuide) { Text("查看防杀设置") }
    }
}

@Composable
private fun RedmiK50GuideDialog(
    autoLedgerEnabled: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("红米 K50 专属防杀设置") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = Color(0xFFFFF3D6), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (autoLedgerEnabled) {
                            "已检测到自动记账开启。请继续完成下面 3 项小米系统设置。"
                        } else {
                            "检测到小米设备。开启自动记账后，请完成下面 3 项设置。"
                        },
                        modifier = Modifier.padding(12.dp),
                        color = Color(0xFF5F4320),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                XiaomiGuideStep(
                    title = "1. 自启动权限",
                    body = "打开 MIUI/HyperOS 自启动管理，找到“自动账本”，开启自启动。",
                    button = "打开自启动管理",
                    onClick = { context.openXiaomiAutostartSettings() },
                )
                XiaomiGuideStep(
                    title = "2. 无限制省电策略",
                    body = "进入小米省电策略，把“自动账本”的后台省电策略改为“无限制”。如果系统没有直接打开本 App，请在列表中手动找到“自动账本”。",
                    button = "打开省电策略",
                    onClick = { context.openXiaomiBatterySettings() },
                )
                XiaomiGuideStep(
                    title = "3. 后台锁定任务",
                    body = "返回桌面后打开最近任务界面，下拉“自动账本”卡片或长按图标，给 App 加小锁，避免一键清理时被结束。",
                    button = "回到桌面",
                    onClick = { context.openHomeScreen() },
                )
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("我已设置") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } },
    )
}

@Composable
private fun XiaomiGuideStep(
    title: String,
    body: String,
    button: String,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onClick) { Text(button) }
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
                            progress = { (item.value.toFloat() / max).coerceIn(0f, 1f) },
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

private val manualTypes = listOf(
    LedgerType.EXPENSE,
    LedgerType.INCOME,
    LedgerType.REFUND,
    LedgerType.TRANSFER,
)

private fun manualTypeLabel(type: LedgerType): String = when (type) {
    LedgerType.EXPENSE -> "支出"
    LedgerType.INCOME -> "收入"
    LedgerType.REFUND -> "退款"
    LedgerType.TRANSFER -> "转账"
    LedgerType.ADJUSTMENT -> "调整"
}

private fun defaultManualCategory(type: LedgerType, categories: List<String>): String {
    val fallback = when (type) {
        LedgerType.EXPENSE -> "未分类/待确认/其他"
        LedgerType.INCOME -> "收入/其他/其他"
        LedgerType.REFUND -> "购物/电商/退款"
        LedgerType.TRANSFER -> "资金流转/账户互转/转出"
        LedgerType.ADJUSTMENT -> "调整/手动调整/其他"
    }
    return categories.firstOrNull { it.substringBefore("/") == fallback.substringBefore("/") } ?: fallback
}

private fun String.isPositiveAmountText(): Boolean =
    runCatching { BigDecimal(trim()) > BigDecimal.ZERO }.getOrDefault(false)

private fun Long.toAmountText(): String =
    BigDecimal(this).movePointLeft(2).stripTrailingZeros().toPlainString()

private fun amountColor(type: LedgerType): Color = when (type) {
    LedgerType.EXPENSE -> Color(0xFF8A3B2E)
    LedgerType.INCOME, LedgerType.REFUND -> Color(0xFF276749)
    LedgerType.TRANSFER, LedgerType.ADJUSTMENT -> Color(0xFF52606D)
}

private fun notificationListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
    return flat.lowercase(Locale.US).contains(context.packageName.lowercase(Locale.US))
}

private fun isXiaomiDevice(): Boolean =
    Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)

private fun xiaomiGuideAlreadyShown(context: Context): Boolean =
    context.getSharedPreferences(XIAOMI_GUIDE_PREFS, Context.MODE_PRIVATE)
        .getBoolean(XIAOMI_GUIDE_SHOWN_KEY, false)

private fun markXiaomiGuideShown(context: Context) {
    context.getSharedPreferences(XIAOMI_GUIDE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(XIAOMI_GUIDE_SHOWN_KEY, true)
        .apply()
}

private fun Context.openXiaomiAutostartSettings() {
    startFirstAvailableActivity(
        Intent().setComponent(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
        ),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
    )
}

private fun Context.openXiaomiBatterySettings() {
    val appLabel = runCatching {
        packageManager.getApplicationLabel(applicationInfo).toString()
    }.getOrDefault("自动账本")
    startFirstAvailableActivity(
        Intent("miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY")
            .setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
            .putExtra("package_name", packageName)
            .putExtra("package_label", appLabel),
        Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST")
            .setPackage("com.miui.powerkeeper"),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
    )
}

private fun Context.openHomeScreen() {
    startFirstAvailableActivity(
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun Context.startFirstAvailableActivity(vararg intents: Intent): Boolean {
    intents.forEach { intent ->
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val canHandle = intent.resolveActivity(packageManager) != null
        if (canHandle && runCatching { startActivity(intent) }.isSuccess) {
            return true
        }
    }
    return false
}

private const val XIAOMI_GUIDE_PREFS = "xiaomi_auto_ledger_guide"
private const val XIAOMI_GUIDE_SHOWN_KEY = "redmi_k50_guide_shown"
