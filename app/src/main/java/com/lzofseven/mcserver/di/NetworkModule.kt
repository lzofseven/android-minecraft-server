package com.lzofseven.mcserver.di

import com.lzofseven.mcserver.data.api.ModrinthService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = okhttp3.logging.HttpLoggingInterceptor().apply {
            level = okhttp3.logging.HttpLoggingInterceptor.Level.HEADERS
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(5, java.util.concurrent.TimeUnit.MINUTES)
            .readTimeout(5, java.util.concurrent.TimeUnit.MINUTES)
            .writeTimeout(5, java.util.concurrent.TimeUnit.MINUTES)
            .build()
    }

    @Provides
    @Singleton
    fun provideModrinthService(client: OkHttpClient): ModrinthService {
        return Retrofit.Builder()
            .baseUrl(ModrinthService.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ModrinthService::class.java)
    }
}
