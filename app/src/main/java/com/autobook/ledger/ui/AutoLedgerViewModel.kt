package com.autobook.ledger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autobook.ledger.BuildConfig
import com.autobook.ledger.capture.AppSourceScanner
import com.autobook.ledger.capture.SpendingSourceApp
import com.autobook.ledger.data.LedgerDatabase
import com.autobook.ledger.data.LedgerRepository
import com.autobook.ledger.data.SyncPreferences
import com.autobook.ledger.domain.CategoryCatalog
import com.autobook.ledger.domain.LedgerStats
import com.autobook.ledger.domain.LedgerStatus
import com.autobook.ledger.domain.LedgerType
import com.autobook.ledger.domain.ParsedBill
import com.autobook.ledger.sync.CsvExporter
import com.autobook.ledger.sync.SupabaseSyncClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

class AutoLedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LedgerRepository(LedgerDatabase.get(application).ledgerDao())
    private val scanner = AppSourceScanner(application)
    private val syncPreferences = SyncPreferences(application)
    private val syncClient = SupabaseSyncClient(repository)
    private val exporter = CsvExporter(application)

    val entries: StateFlow<List<ParsedBill>> = repository.observeEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats: StateFlow<LedgerStats> = repository.observeStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerStats.from(emptyList()))

    private val _sources = MutableStateFlow<List<SpendingSourceApp>>(emptyList())
    val sources: StateFlow<List<SpendingSourceApp>> = _sources.asStateFlow()

    private val _message = MutableStateFlow("本地账本已准备好")
    val message: StateFlow<String> = _message.asStateFlow()

    private val _syncKey = MutableStateFlow(syncPreferences.getOrCreateOwnerKey())
    val syncKey: StateFlow<String> = _syncKey.asStateFlow()

    val categories: List<String> = CategoryCatalog.default().rules.map { it.path }.distinct()
    val supabaseEndpoint: String = BuildConfig.SUPABASE_URL.ifBlank { "未配置：请在本地 local.properties 设置 SUPABASE_URL" }

    init {
        refreshSources()
    }

    fun refreshSources() {
        viewModelScope.launch {
            _sources.value = runCatching { scanner.likelySpendingApps() }
                .onFailure { _message.value = "来源扫描失败：${it.message.orEmpty()}" }
                .getOrDefault(emptyList())
        }
    }

    fun addManual(amountText: String, merchant: String, categoryPath: String, account: String) {
        val cents = amountText.toCentsOrNull()
        if (cents == null || cents <= 0) {
            _message.value = "请输入有效金额"
            return
        }
        viewModelScope.launch {
            repository.addManualExpense(
                amountCents = cents,
                merchant = merchant,
                categoryPath = categoryPath,
                account = account,
            )
            _message.value = "已新增手动账单"
        }
    }

    fun confirm(id: String) {
        viewModelScope.launch {
            repository.confirm(id)
            _message.value = "已确认入账"
        }
    }

    fun ignore(id: String) {
        viewModelScope.launch {
            repository.ignore(id)
            _message.value = "已忽略该记录"
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            _message.value = "已删除记录"
        }
    }

    fun updateSyncKey(ownerKey: String) {
        if (ownerKey.length < 16) {
            _message.value = "同步密钥太短"
            return
        }
        syncPreferences.setOwnerKey(ownerKey)
        _syncKey.value = ownerKey
        _message.value = "已更新同步密钥"
    }

    fun syncNow() {
        viewModelScope.launch {
            _message.value = "正在同步 Supabase..."
            runCatching { syncClient.sync(syncPreferences.getOrCreateOwnerKey()) }
                .onSuccess { _message.value = it.message }
                .onFailure { _message.value = it.message ?: "同步失败，请检查本地 Supabase 配置和 SQL 建表" }
        }
    }

    fun exportCsv() {
        viewModelScope.launch {
            val file = exporter.export(repository.activeEntries())
            _message.value = "已导出：${file.absolutePath}"
        }
    }

    private fun String.toCentsOrNull(): Long? =
        runCatching {
            BigDecimal(trim())
                .multiply(BigDecimal(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        }.getOrNull()
}

data class EntryFilters(
    val status: LedgerStatus? = null,
    val query: String = "",
)

fun List<ParsedBill>.filtered(filters: EntryFilters): List<ParsedBill> =
    filter { entry ->
        val statusMatches = filters.status == null || entry.status == filters.status
        val query = filters.query.trim()
        val queryMatches = query.isBlank() ||
            entry.merchant.contains(query, ignoreCase = true) ||
            entry.categoryPath.contains(query, ignoreCase = true) ||
            entry.sourceAppName.contains(query, ignoreCase = true) ||
            entry.rawText.contains(query, ignoreCase = true)
        statusMatches && queryMatches
    }

fun List<ParsedBill>.confirmedExpenses(): List<ParsedBill> =
    filter { it.status == LedgerStatus.CONFIRMED && it.type == LedgerType.EXPENSE && !it.isDeleted }
