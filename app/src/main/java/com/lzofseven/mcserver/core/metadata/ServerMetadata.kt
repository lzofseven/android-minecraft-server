package com.lzofseven.mcserver.core.metadata

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Scanner

/**
 * Metadata about a Minecraft server, stored in .mcserver_version file
 * This is the SINGLE SOURCE OF TRUTH for server version
 */
@Serializable
data class ServerMetadata(
    val version: String,
    val type: String = "JAVA",
    val createdAt: String = getCurrentTimestamp(),
    val lastStarted: String? = null
) {
    companion object {
        private const val TAG = "ServerMetadata"
        const val FILENAME = ".mcserver_version"
        
        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
        
        /**
         * Save metadata to server directory
         */
        fun save(context: Context, serverPath: String, metadata: ServerMetadata) {
            try {
                val jsonString = json.encodeToString(metadata)
                if (serverPath.startsWith("content://")) {
                    val treeUri = Uri.parse(serverPath)
                    val docDir = DocumentFile.fromTreeUri(context, treeUri)
                        ?: throw Exception("Cannot access SAF directory")
                    
                    var fileDoc = docDir.findFile(FILENAME)
                    if (fileDoc == null) {
                        fileDoc = docDir.createFile("application/json", FILENAME)
                            ?: throw Exception("Cannot create metadata file")
                    }
                    
                    context.contentResolver.openOutputStream(fileDoc.uri)?.use { output ->
                        OutputStreamWriter(output).use { writer ->
                            writer.write(jsonString)
                        }
                    }
                } else {
                    val file = File(serverPath, FILENAME)
                    file.writeText(jsonString)
                }
                Log.d(TAG, "Saved metadata to $serverPath/$FILENAME")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save metadata", e)
                throw e
            }
        }
        
        /**
         * Load metadata from server directory
         * Returns null if file doesn't exist or is invalid
         */
        fun load(context: Context, serverPath: String): ServerMetadata? {
            return try {
                val jsonString = if (serverPath.startsWith("content://")) {
                    val treeUri = Uri.parse(serverPath)
                    val docDir = DocumentFile.fromTreeUri(context, treeUri) ?: return null
                    val fileDoc = docDir.findFile(FILENAME) ?: return null
                    
                    context.contentResolver.openInputStream(fileDoc.uri)?.use { input ->
                        Scanner(input).useDelimiter("\\A").next()
                    } ?: return null
                } else {
                    val file = File(serverPath, FILENAME)
                    if (!file.exists()) return null
                    file.readText()
                }
                
                val metadata = json.decodeFromString<ServerMetadata>(jsonString)
                Log.d(TAG, "Loaded metadata: $metadata")
                metadata
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load/parse metadata from $serverPath", e)
                null
            }
        }
        
        /**
         * Update last started timestamp
         */
        fun updateLastStarted(context: Context, serverPath: String) {
            val current = load(context, serverPath) ?: return
            val updated = current.copy(lastStarted = getCurrentTimestamp())
            save(context, serverPath, updated)
        }
        
        private fun getCurrentTimestamp(): String {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            return format.format(Date())
        }
    }
}
