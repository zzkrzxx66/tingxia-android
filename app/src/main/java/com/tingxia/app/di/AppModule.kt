package com.tingxia.app.di

import android.content.Context
import androidx.room.Room
import com.tingxia.app.data.db.BookDao
import com.tingxia.app.data.db.ChapterDao
import com.tingxia.app.data.db.TingXiaDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TingXiaDatabase =
        Room.databaseBuilder(context, TingXiaDatabase::class.java, "tingxia.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideBookDao(db: TingXiaDatabase): BookDao = db.bookDao()

    @Provides
    fun provideChapterDao(db: TingXiaDatabase): ChapterDao = db.chapterDao()
}
