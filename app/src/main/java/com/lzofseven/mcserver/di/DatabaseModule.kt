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
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add javaVersion column with default value 17
                database.execSQL("ALTER TABLE servers ADD COLUMN javaVersion INTEGER NOT NULL DEFAULT 17")
            }
        }

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "mc_server_db"
        )
        .addMigrations(MIGRATION_2_3)
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideServerDao(database: AppDatabase): ServerDao {
        return database.serverDao()
    }

    @Provides
    fun provideAiConstructionDao(database: AppDatabase): com.lzofseven.mcserver.data.local.dao.AiConstructionDao {
        return database.aiConstructionDao()
    }
}
