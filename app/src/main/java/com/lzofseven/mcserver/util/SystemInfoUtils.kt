package com.lzofseven.mcserver.util

import android.app.ActivityManager
import android.content.Context
import android.os.Process

object SystemInfoUtils {

    data class RamInfo(
        val totalRamGB: Double,
        val availableRamGB: Double,
        val usedRamGB: Double,
        val appUsedRamMB: Long
    )

    fun getRamInfo(context: Context): RamInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalBytes = memoryInfo.totalMem
        val availableBytes = memoryInfo.availMem
        val usedBytes = totalBytes - availableBytes

        val totalGB = totalBytes / (1024.0 * 1024.0 * 1024.0)
        val availableGB = availableBytes / (1024.0 * 1024.0 * 1024.0)
        val usedGB = usedBytes / (1024.0 * 1024.0 * 1024.0)

        // Memory used by THIS app
        val memoryInfoArray = activityManager.getProcessMemoryInfo(intArrayOf(Process.myPid()))
        val appUsedMB = memoryInfoArray[0].totalPss / 1024L

        return RamInfo(
            totalRamGB = totalGB,
            availableRamGB = availableGB,
            usedRamGB = usedGB,
            appUsedRamMB = appUsedMB
        )
    }
}
