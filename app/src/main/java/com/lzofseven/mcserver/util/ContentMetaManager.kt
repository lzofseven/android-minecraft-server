package com.lzofseven.mcserver.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ContentMetadata(
    val projectId: String,
    val title: String,
    val iconUrl: String?,
    val version: String,
    val projectType: String, // "mod" or "plugin"
    val filename: String,
    val loader: String? = null
)

class ContentMetaManager(private val context: Context, private val serverPath: String) {
    private val gson = Gson()
    private val metaDirName = ".manager"
    private val metaFileName = "content_meta.json"

    private fun getMetaFileDoc(): DocumentFile? {
        if (!serverPath.startsWith("content://")) return null
        val rootUri = Uri.parse(serverPath)
        val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return null
        var managerDir = rootDoc.findFile(metaDirName)
        if (managerDir == null || !managerDir.isDirectory) {
            managerDir = rootDoc.createDirectory(metaDirName)
        }
        return managerDir?.findFile(metaFileName) ?: managerDir?.createFile("application/json", metaFileName)
    }

    private fun getMetaFile(): File? {
        if (serverPath.startsWith("content://")) return null
        val managerDir = File(serverPath, metaDirName)
        if (!managerDir.exists()) managerDir.mkdirs()
        return File(managerDir, metaFileName)
    }

    suspend fun loadMetadata(): Map<String, ContentMetadata> = withContext(Dispatchers.IO) {
        try {
            val json = if (serverPath.startsWith("content://")) {
                val rootUri = Uri.parse(serverPath)
                val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
                val managerDir = rootDoc?.findFile(metaDirName)
                val fileDoc = managerDir?.findFile(metaFileName)
                fileDoc?.let { context.contentResolver.openInputStream(it.uri)?.bufferedReader()?.use { it.readText() } }
            } else {
                val file = File(File(serverPath, metaDirName), metaFileName)
                if (file.exists()) file.readText() else null
            }

            if (json.isNullOrBlank()) return@withContext emptyMap()
            
            val type = object : TypeToken<Map<String, ContentMetadata>>() {}.type
            gson.fromJson<Map<String, ContentMetadata>>(json, type) ?: emptyMap()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }

    suspend fun saveMetadata(metadata: ContentMetadata) = withContext(Dispatchers.IO) {
        val currentMeta = loadMetadata().toMutableMap()
        currentMeta[metadata.filename] = metadata
        
        val json = gson.toJson(currentMeta)
        try {
            if (serverPath.startsWith("content://")) {
                val fileDoc = getMetaFileDoc()
                fileDoc?.let { doc ->
                    context.contentResolver.openOutputStream(doc.uri, "wt")?.use { it.writer().use { it.write(json) } }
                }
            } else {
                val file = getMetaFile()
                file?.writeText(json)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    suspend fun removeMetadata(filename: String) = withContext(Dispatchers.IO) {
        val currentMeta = loadMetadata().toMutableMap()
        if (currentMeta.remove(filename) != null) {
            val json = gson.toJson(currentMeta)
            try {
                if (serverPath.startsWith("content://")) {
                    val fileDoc = getMetaFileDoc()
                    fileDoc?.let { doc ->
                        context.contentResolver.openOutputStream(doc.uri, "wt")?.use { it.writer().use { it.write(json) } }
                    }
                } else {
                    val file = getMetaFile()
                    file?.writeText(json)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
