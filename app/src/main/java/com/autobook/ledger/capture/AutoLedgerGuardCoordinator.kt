package com.autobook.ledger.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AutoLedgerGuardCoordinator {
    private const val HEALTH_CHECK_INTERVAL_MINUTES = 15L

    fun ensureBackgroundChecks(context: Context) {
        val request = PeriodicWorkRequestBuilder<ListenerHealthWorker>(
            HEALTH_CHECK_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        ).build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            ListenerHealthWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun enqueueImmediateHealthCheck(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .enqueue(OneTimeWorkRequestBuilder<ListenerHealthWorker>().build())
        ensureBackgroundChecks(context)
    }

    fun ensureGuardService(context: Context): GuardStartResult {
        ensureBackgroundChecks(context)

        val appContext = context.applicationContext
        if (!isAutoLedgerEnabled(appContext)) {
            LedgerGuardService.stop(appContext)
            return GuardStartResult.LISTENER_DISABLED
        }
        if (!canPostNotifications(appContext)) {
            LedgerGuardService.stop(appContext)
            return GuardStartResult.NOTIFICATION_PERMISSION_MISSING
        }

        return runCatching {
            LedgerGuardService.start(appContext)
            GuardStartResult.STARTED
        }.getOrDefault(GuardStartResult.START_FAILED)
    }

    fun isAutoLedgerEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.applicationContext.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        return flat.contains(context.applicationContext.packageName)
    }

    fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

enum class GuardStartResult {
    STARTED,
    LISTENER_DISABLED,
    NOTIFICATION_PERMISSION_MISSING,
    START_FAILED,
}
