package com.example.pocketreborn.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.pocketreborn.data.local.dao.ArticleDao
import com.example.pocketreborn.data.local.dao.FolderDao
import com.example.pocketreborn.data.local.dao.HighlightDao
import com.example.pocketreborn.data.local.entity.ArticleEntity
import com.example.pocketreborn.data.local.entity.ArticleImageEntity
import com.example.pocketreborn.data.local.entity.FolderEntity
import com.example.pocketreborn.data.local.entity.HighlightEntity

@Database(
    entities = [
        ArticleEntity::class,
        FolderEntity::class,
        HighlightEntity::class,
        ArticleImageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun articleDao(): ArticleDao
    abstract fun folderDao(): FolderDao
    abstract fun highlightDao(): HighlightDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "later_articles.db"
                )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
