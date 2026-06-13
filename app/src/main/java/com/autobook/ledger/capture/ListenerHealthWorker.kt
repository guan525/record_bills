package com.autobook.ledger.capture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.autobook.ledger.MainActivity
import com.autobook.ledger.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 定期健康检查 Worker
 * 每 15 分钟检查一次 NotificationListenerService 是否正常运行
 * 如果检测到监听器断开，发送高优先级通知提醒用户
 */
class ListenerHealthWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Main) {
        try {
            val isListenerEnabled = checkNotificationListenerEnabled()
            
            if (!isListenerEnabled) {
                sendRecoveryNotification()
            }
            
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    /**
     * 检查通知监听器权限是否已启用
     */
    private fun checkNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            applicationContext.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        
        return flat.contains(applicationContext.packageName)
    }

    /**
     * 发送恢复通知
     */
    private fun sendRecoveryNotification() {
        val notificationManager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        // 创建通知渠道（Android 8.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "自动记账状态",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "自动记账功能状态提醒"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 创建点击通知后的跳转 Intent
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_settings", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 构建通知
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("自动记账已失效")
            .setContentText("点击修复通知监听权限")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(RECOVERY_NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "listener_health_channel"
        const val RECOVERY_NOTIFICATION_ID = 1002
        const val WORK_NAME = "listener_health_check"
    }
}
