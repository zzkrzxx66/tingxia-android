package com.tingxia.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [BookEntity::class, ChapterEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class TingXiaDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
}
