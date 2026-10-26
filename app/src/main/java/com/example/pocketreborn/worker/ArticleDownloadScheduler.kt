package com.example.pocketreborn.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object ArticleDownloadScheduler {
    fun enqueue(context: Context, articleId: String) {
        val request = OneTimeWorkRequestBuilder<ArticleDownloadWorker>()
            .setInputData(workDataOf(ArticleDownloadWorker.KEY_ARTICLE_ID to articleId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "article-download-$articleId",
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
