package com.lzofseven.mcserver.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ServerPropertiesManager(private val context: Context, private val path: String) {
    private val properties = Properties()

    private fun getInputStream() = if (path.startsWith("content://")) {
        val uri = Uri.parse(path)
        val docFile = DocumentFile.fromTreeUri(context, uri)
        val fileDoc = docFile?.findFile("server.properties")
        fileDoc?.let { context.contentResolver.openInputStream(it.uri) }
    } else {
        val file = File(path, "server.properties")
        if (file.exists()) file.inputStream() else null
    }

    private fun getOutputStream() = if (path.startsWith("content://")) {
        val uri = Uri.parse(path)
        val docFile = DocumentFile.fromTreeUri(context, uri)
        val fileDoc = docFile?.findFile("server.properties") ?: docFile?.createFile("text/plain", "server.properties")
        fileDoc?.let { context.contentResolver.openOutputStream(it.uri) }
    } else {
        val file = File(path, "server.properties")
        file.parentFile?.mkdirs()
        file.outputStream()
    }

    suspend fun load(): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            getInputStream()?.use { properties.load(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        properties.entries.associate { it.key.toString() to it.value.toString() }
    }

    suspend fun save(updates: Map<String, String>) = withContext(Dispatchers.IO) {
        updates.forEach { (key, value) ->
            properties.setProperty(key, value)
        }
        try {
            if (path.startsWith("content://")) {
                getOutputStream()?.use { 
                    properties.store(it, "Minecraft Server Properties - Updated by MC Server Manager") 
                }
            } else {
                val file = File(path, "server.properties")
                if (!file.parentFile.exists()) {
                    file.parentFile.mkdirs()
                }
                
                // Use FileOutputStream directly to ensure disk write
                java.io.FileOutputStream(file).use { fos ->
                    properties.store(fos, "Minecraft Server Properties - Updated by MC Server Manager")
                    fos.fd.sync() // Force sync to disk
                }
                android.util.Log.d("ServerPropertiesManager", "Saved properties to ${file.absolutePath}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("ServerPropertiesManager", "Failed to save properties: ${e.message}")
        }
    }
    
    fun getProperty(key: String, default: String): String {
        return properties.getProperty(key, default)
    }
}
