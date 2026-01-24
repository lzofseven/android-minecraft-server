package com.lzofseven.mcserver.util

import java.io.File
import java.util.Properties

object ServerPropertiesHelper {
    
    data class RconConfig(
        val enabled: Boolean,
        val port: Int,
        val password: String
    )

    fun getRconConfig(serverPath: String): RconConfig {
        val props = Properties()
        val file = File(serverPath, "server.properties")
        
        if (file.exists()) {
            try {
                file.inputStream().use { props.load(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        val enabled = props.getProperty("enable-rcon", "false").toBoolean()
        val port = props.getProperty("rcon.port", "25575").toIntOrNull() ?: 25575
        val password = props.getProperty("rcon.password", "")
        
        return RconConfig(enabled, port, password)
    }
    fun writeRconConfig(serverPath: String, port: Int, password: String) {
        val file = File(serverPath, "server.properties")
        if (!file.exists()) return

        val lines = file.readLines().toMutableList()
        
        fun updateOrAdd(key: String, value: String) {
            val index = lines.indexOfFirst { it.trim().startsWith("$key=") }
            if (index != -1) {
                lines[index] = "$key=$value"
            } else {
                lines.add("$key=$value")
            }
        }

        updateOrAdd("enable-rcon", "true")
        updateOrAdd("rcon.port", port.toString())
        updateOrAdd("rcon.password", password)

        file.writeText(lines.joinToString("\n"))
    }
    
    fun generateRconPassword(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..12)
            .map { chars.random() }
            .joinToString("")
    }
}
