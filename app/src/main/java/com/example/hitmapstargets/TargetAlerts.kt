package com.example.hitmapstargets

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.time.Instant
import java.util.concurrent.TimeUnit

object TargetAlerts {
    private const val KEY_ENABLED = "notifications_enabled"
    private const val KEY_BASELINED = "initial_snapshot_seen"
    private const val KEY_NOTIFIED = "notified_target_ids"
    private const val PERIODIC_WORK = "hitmaps-periodic-check"
    private const val START_WORK_PREFIX = "hitmaps-target-start-"
    private const val WORK_TAG = "hitmaps-target-alerts"
    private const val CHANNEL_ID = "elusive_targets_active"

    private fun prefs(context: Context) = context.getSharedPreferences(
        TargetsRepository.PREFS_NAME, Context.MODE_PRIVATE
    )

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, false)

    fun hasPermission(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        val manager = WorkManager.getInstance(context)
        if (enabled) {
            createChannel(context)
            val schedule = PeriodicWorkRequestBuilder<TargetSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag(WORK_TAG)
                .build()
            manager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, schedule)
            syncSchedules(context, TargetsRepository(context).cached())
            val initial = OneTimeWorkRequestBuilder<TargetSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag(WORK_TAG)
                .build()
            manager.enqueueUniqueWork("hitmaps-initial-check", ExistingWorkPolicy.REPLACE, initial)
        } else {
            manager.cancelAllWorkByTag(WORK_TAG)
        }
    }

    /** Adds alerts for known future beginnings, even if the app is subsequently closed. */
    fun syncSchedules(context: Context, targets: List<ElusiveTarget>) {
        if (!isEnabled(context)) return
        val prefs = prefs(context)
        val now = Instant.now()
        // Don't notify about a target that was already active when alerts were first enabled.
        // An empty cache isn't a real baseline; wait for the first successful API fetch.
        if (!prefs.getBoolean(KEY_BASELINED, false) &&
            (targets.isNotEmpty() || TargetsRepository(context).lastSync() > 0L)
        ) {
            val activeIds = targets.filter { it.status(now) == TargetStatus.ACTIVE }
                .map { it.id.toString() }.toSet()
            prefs.edit()
                .putStringSet(KEY_NOTIFIED, activeIds)
                .putBoolean(KEY_BASELINED, true)
                .apply()
        }

        val manager = WorkManager.getInstance(context)
        for (target in targets) {
            if (target.status(now) != TargetStatus.UPCOMING) continue
            val delayMs = (target.startsAt.toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0)
            val job = OneTimeWorkRequestBuilder<TargetSyncWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag(WORK_TAG)
                .build()
            // KEEP prevents repeated refreshes from pushing back a scheduled start.
            manager.enqueueUniqueWork(START_WORK_PREFIX + target.id, ExistingWorkPolicy.KEEP, job)
        }
    }

    /** Notify once for an activation, including newly announced targets after the first sync. */
    fun notifyNewlyActive(context: Context, targets: List<ElusiveTarget>) {
        if (!isEnabled(context) || !hasPermission(context)) return
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_BASELINED, false)) return
        val notified = prefs.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty().toMutableSet()
        val now = Instant.now()
        for (target in targets) {
            val id = target.id.toString()
            if (id !in notified && target.status(now) == TargetStatus.ACTIVE) {
                showNotification(context, target)
                notified.add(id)
                prefs.edit().putStringSet(KEY_NOTIFIED, notified.toSet()).apply()
            }
        }
    }

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Неуловимые цели",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Уведомления, когда неуловимая цель становится активной"
            }
        )
    }

    private fun showNotification(context: Context, target: ElusiveTarget) {
        createChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("target_id", target.id)
        }
        val pending = PendingIntent.getActivity(
            context, target.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Цель стала активной")
            .setContentText(target.name)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "${target.name} — миссия доступна до окончания события."
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        if (hasPermission(context)) {
            NotificationManagerCompat.from(context).notify(target.id.toInt(), notification)
        }
    }
}

class TargetSyncWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!TargetAlerts.isEnabled(applicationContext)) return Result.success()
        return try {
            val targets = TargetsRepository(applicationContext).fetch()
            TargetAlerts.notifyNewlyActive(applicationContext, targets)
            TargetAlerts.syncSchedules(applicationContext, targets)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
