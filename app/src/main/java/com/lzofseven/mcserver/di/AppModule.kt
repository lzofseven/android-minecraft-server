package com.lzofseven.mcserver.di

import android.content.Context
import com.lzofseven.mcserver.util.PlayerManager
import com.lzofseven.mcserver.util.ServerInstaller
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideServerDir(@ApplicationContext context: Context): File {
        val dir = File(context.filesDir, "mcserver")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    @Provides
    @Singleton
    fun provideServerInstaller(client: OkHttpClient, @ApplicationContext context: Context): ServerInstaller {
        return ServerInstaller(client, context)
    }

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): androidx.work.WorkManager {
        return androidx.work.WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun providePlayerManager(serverDir: File): PlayerManager {
        return PlayerManager(serverDir)
    }
}
