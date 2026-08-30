package com.tingxia.app.data.remote

import com.tingxia.app.BuildConfig
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
    fun provideFqNovelApi(): FqNovelApi = FqNovelApi(
        baseUrl = BuildConfig.FQ_BASE_URL,
        apiToken = BuildConfig.FQ_API_TOKEN,
    )
}
