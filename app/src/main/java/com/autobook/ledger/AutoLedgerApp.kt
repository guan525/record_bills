package com.autobook.ledger

import android.app.Application
import com.autobook.ledger.data.LedgerDatabase
import com.autobook.ledger.data.LedgerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application 类，提供全局单例和 CoroutineScope
 */
class AutoLedgerApp : Application() {

    /**
     * 全局 CoroutineScope，用于在 Service/Receiver 中执行后台任务
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 全局 LedgerRepository 单例
     */
    val ledgerRepository: LedgerRepository by lazy {
        LedgerRepository(LedgerDatabase.get(this).ledgerDao())
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        @Volatile
        private var instance: AutoLedgerApp? = null

        fun getInstance(): AutoLedgerApp {
            return instance ?: throw IllegalStateException(
                "AutoLedgerApp not initialized. Ensure the Application class is declared in AndroidManifest.xml"
            )
        }
    }
}

