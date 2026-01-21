package com.lzofseven.mcserver.di

import android.content.Context
import androidx.room.Room
import com.lzofseven.mcserver.data.local.AppDatabase
import com.lzofseven.mcserver.data.local.dao.ServerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "mc_server_db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideServerDao(database: AppDatabase): ServerDao {
        return database.serverDao()
    }
}
