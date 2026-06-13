package com.autobook.ledger.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 开机自启接收器
 * 在设备启动完成后自动触发健康检查
 */
class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 立即触发一次健康检查
            scheduleImmediateHealthCheck(context)
        }
    }

    /**
     * 立即执行一次健康检查
     */
    private fun scheduleImmediateHealthCheck(context: Context) {
        val workManager = WorkManager.getInstance(context)
        
        // 创建一次性工作请求
        val oneTimeRequest = androidx.work.OneTimeWorkRequestBuilder<ListenerHealthWorker>()
            .build()
        
        // 立即执行
        workManager.enqueue(oneTimeRequest)
        
        // 同时确保定期任务已调度
        schedulePeriodicHealthCheck(context)
    }

    /**
     * 调度定期健康检查（每 15 分钟）
     */
    private fun schedulePeriodicHealthCheck(context: Context) {
        val workManager = WorkManager.getInstance(context)
        
        val periodicRequest = PeriodicWorkRequestBuilder<ListenerHealthWorker>(
            15, TimeUnit.MINUTES
        ).build()
        
        // 使用 KEEP 策略，如果已有任务则保留
        workManager.enqueueUniquePeriodicWork(
            ListenerHealthWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }
}
