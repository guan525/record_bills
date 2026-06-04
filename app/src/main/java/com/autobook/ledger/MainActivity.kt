package com.autobook.ledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autobook.ledger.ui.AutoLedgerAppScreen
import com.autobook.ledger.ui.AutoLedgerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: AutoLedgerViewModel = viewModel()
            val entries by viewModel.entries.collectAsState()
            val stats by viewModel.stats.collectAsState()
            val sources by viewModel.sources.collectAsState()
            val message by viewModel.message.collectAsState()
            val syncKey by viewModel.syncKey.collectAsState()
            AutoLedgerAppScreen(
                entries = entries,
                stats = stats,
                sources = sources,
                message = message,
                syncKey = syncKey,
                supabaseEndpoint = viewModel.supabaseEndpoint,
                categories = viewModel.categories,
                onAddManual = viewModel::addManual,
                onConfirm = viewModel::confirm,
                onIgnore = viewModel::ignore,
                onDelete = viewModel::delete,
                onRefreshSources = viewModel::refreshSources,
                onSyncNow = viewModel::syncNow,
                onExportCsv = viewModel::exportCsv,
                onUpdateSyncKey = viewModel::updateSyncKey,
            )
        }
    }
}
