package com.lzofseven.mcserver.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lzofseven.mcserver.data.local.dao.ServerDao
import com.lzofseven.mcserver.data.local.entity.MCServerEntity

@Database(entities = [MCServerEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
}
