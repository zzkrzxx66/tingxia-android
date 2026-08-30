package com.tingxia.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [BookEntity::class, ChapterEntity::class, BookmarkEntity::class, ListenSessionEntity::class],
    version = 11,
    exportSchema = true,
)
abstract class TingXiaDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun listenSessionDao(): ListenSessionDao
}
