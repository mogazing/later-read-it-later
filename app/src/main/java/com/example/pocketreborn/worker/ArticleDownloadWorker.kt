package com.example.pocketreborn.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pocketreborn.MainActivity
import com.example.pocketreborn.data.local.AppDatabase
import com.example.pocketreborn.data.repository.ArticleRepository
import com.example.pocketreborn.data.repository.SaveResult
import com.example.pocketreborn.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first

class ArticleDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val articleId = inputData.getString(KEY_ARTICLE_ID) ?: return Result.failure()

        val db = AppDatabase.getInstance(applicationContext)
        val repository = ArticleRepository(db.articleDao(), context = applicationContext)
        val prefsRepository = UserPreferencesRepository(applicationContext)
        val prefs = prefsRepository.userPreferencesFlow.first()

        createNotificationChannel()

        val showSavingNotif = prefs.masterNotificationsEnabled && prefs.articleSavingNotificationEnabled
        val showSavedNotif = prefs.masterNotificationsEnabled && prefs.articleSavedConfirmationEnabled

        if (showSavingNotif) {
            postNotification(
                notificationId = articleId.hashCode(),
                title = "Saving Article...",
                text = "Extracting article text and offline images",
                ongoing = true
            )
        }

        val result = when (val saveResult = repository.processQueuedArticle(articleId)) {
            is SaveResult.Queued -> Result.success()
            is SaveResult.Success -> {
                if (showSavedNotif) {
                    val article = db.articleDao().getArticleById(articleId)
                    postNotification(
                        notificationId = articleId.hashCode(),
                        title = "Article Saved for Offline Reading",
                        text = article?.title ?: "Saved to Later",
                        ongoing = false
                    )
                }
                Result.success()
            }
            is SaveResult.ExistingDuplicate -> Result.success()
            is SaveResult.Error -> if (saveResult.isRetryable && runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                if (saveResult.isRetryable) {
                    db.articleDao().updateDownloadState(
                        articleId,
                        "FAILED",
                        "Automatic retries were exhausted. ${saveResult.message}"
                    )
                }
                if (showSavedNotif) {
                    postNotification(
                        notificationId = articleId.hashCode(),
                        title = "Article Save Failed",
                        text = saveResult.message,
                        ongoing = false
                    )
                }
                Result.failure()
            }
        }

        if (showSavingNotif && result != Result.retry()) {
            cancelNotification(articleId.hashCode())
        }

        return result
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Article Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for article downloading and offline saving"
            }
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun postNotification(notificationId: Int, title: String, text: String, ongoing: Boolean) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentIntent(pendingIntent)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId, notification)
    }

    private fun cancelNotification(notificationId: Int) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notificationId)
    }

    companion object {
        const val KEY_ARTICLE_ID = "key_article_id"
        private const val MAX_RETRIES = 5
        private const val CHANNEL_ID = "later_download_channel"
    }
}
