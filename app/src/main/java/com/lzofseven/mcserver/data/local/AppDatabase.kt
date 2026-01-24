package com.lzofseven.mcserver.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lzofseven.mcserver.data.local.dao.ServerDao
import com.lzofseven.mcserver.data.local.entity.MCServerEntity

@Database(entities = [MCServerEntity::class, com.lzofseven.mcserver.data.local.entity.AiConstructionEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun aiConstructionDao(): com.lzofseven.mcserver.data.local.dao.AiConstructionDao
}
