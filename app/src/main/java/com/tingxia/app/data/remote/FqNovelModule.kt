package com.tingxia.app.data.remote

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FqNovelModule {
    @Provides
    @Singleton
    fun provideFqNovelApi(): FqNovelApi = FqNovelApi()
}
