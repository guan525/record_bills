package com.autobook.ledger

import android.Manifest
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autobook.ledger.capture.AutoLedgerGuardCoordinator
import com.autobook.ledger.ui.AutoLedgerAppScreen
import com.autobook.ledger.ui.AutoLedgerViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val autoLedgerEnabled = MutableStateFlow(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            ensureAutoLedgerGuard()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshAutoLedgerEnabled()
        setContent {
            val viewModel: AutoLedgerViewModel = viewModel()
            val entries by viewModel.entries.collectAsState()
            val stats by viewModel.stats.collectAsState()
            val sources by viewModel.sources.collectAsState()
            val message by viewModel.message.collectAsState()
            val syncKey by viewModel.syncKey.collectAsState()
            val autoLedgerEnabled by autoLedgerEnabled.collectAsState()
            AutoLedgerAppScreen(
                entries = entries,
                stats = stats,
                sources = sources,
                message = message,
                syncKey = syncKey,
                autoLedgerEnabled = autoLedgerEnabled,
                supabaseEndpoint = viewModel.supabaseEndpoint,
                categories = viewModel.categories,
                onAddManual = viewModel::addManual,
                onConfirm = viewModel::confirm,
                onIgnore = viewModel::ignore,
                onConfirmAll = viewModel::confirmAll,
                onIgnoreAll = viewModel::ignoreAll,
                onDelete = viewModel::delete,
                onUpdateEntry = viewModel::updateEntry,
                onRefreshSources = viewModel::refreshSources,
                onSyncNow = viewModel::syncNow,
                onExportCsv = viewModel::exportCsv,
                onUpdateSyncKey = viewModel::updateSyncKey,
            )
        }
        requestNotificationPermissionIfNeeded()
        ensureAutoLedgerGuard()
    }

    override fun onResume() {
        super.onResume()
        refreshAutoLedgerEnabled()
        ensureAutoLedgerGuard()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !AutoLedgerGuardCoordinator.canPostNotifications(this)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun ensureAutoLedgerGuard() {
        AutoLedgerGuardCoordinator.ensureGuardService(this)
    }

    private fun refreshAutoLedgerEnabled() {
        autoLedgerEnabled.value = AutoLedgerGuardCoordinator.isAutoLedgerEnabled(this)
    }
}
