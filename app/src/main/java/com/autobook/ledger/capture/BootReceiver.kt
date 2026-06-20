package com.autobook.ledger.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机自启接收器
 * 在设备启动完成后自动触发健康检查
 */
class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AutoLedgerGuardCoordinator.enqueueImmediateHealthCheck(context)
        }
    }
}
