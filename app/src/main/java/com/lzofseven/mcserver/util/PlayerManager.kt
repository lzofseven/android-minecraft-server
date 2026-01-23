package com.lzofseven.mcserver.util

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lzofseven.mcserver.data.model.PlayerEntry
import com.lzofseven.mcserver.data.model.WhitelistEntry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    suspend fun getOps(serverPath: String, serverUri: String?): List<PlayerEntry> = withContext(Dispatchers.IO) {
        try {
            val content = if (serverUri != null) {
                val uri = Uri.parse(serverUri)
                val rootDoc = DocumentFile.fromTreeUri(context, uri)
                val opsFile = rootDoc?.findFile("ops.json")
                if (opsFile != null) {
                    context.contentResolver.openInputStream(opsFile.uri)?.bufferedReader()?.use { it.readText() }
                } else null
            } else {
                val opsFile = File(serverPath, "ops.json")
                if (opsFile.exists()) opsFile.readText() else null
            }

            if (content.isNullOrBlank()) return@withContext emptyList()
            val type = object : TypeToken<List<PlayerEntry>>() {}.type
            gson.fromJson(content, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun saveOps(serverPath: String, serverUri: String?, ops: List<PlayerEntry>) = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(ops)
            if (serverUri != null) {
                val uri = Uri.parse(serverUri)
                val rootDoc = DocumentFile.fromTreeUri(context, uri)
                if (rootDoc != null) {
                    val opsFile = rootDoc.findFile("ops.json") ?: rootDoc.createFile("application/json", "ops.json")
                    opsFile?.let { doc ->
                        context.contentResolver.openOutputStream(doc.uri)?.use { out ->
                            out.write(json.toByteArray())
                        }
                    }
                }
            } else {
                val opsFile = File(serverPath, "ops.json")
                opsFile.writeText(json)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getWhitelist(serverPath: String, serverUri: String?): List<WhitelistEntry> = withContext(Dispatchers.IO) {
        try {
            val content = if (serverUri != null) {
                val uri = Uri.parse(serverUri)
                val rootDoc = DocumentFile.fromTreeUri(context, uri)
                val whiteFile = rootDoc?.findFile("whitelist.json")
                if (whiteFile != null) {
                    context.contentResolver.openInputStream(whiteFile.uri)?.bufferedReader()?.use { it.readText() }
                } else null
            } else {
                val whiteFile = File(serverPath, "whitelist.json")
                if (whiteFile.exists()) whiteFile.readText() else null
            }

            if (content.isNullOrBlank()) return@withContext emptyList()
            val type = object : TypeToken<List<WhitelistEntry>>() {}.type
            gson.fromJson(content, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun saveWhitelist(serverPath: String, serverUri: String?, list: List<WhitelistEntry>) = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(list)
            if (serverUri != null) {
                val uri = Uri.parse(serverUri)
                val rootDoc = DocumentFile.fromTreeUri(context, uri)
                if (rootDoc != null) {
                    val whiteFile = rootDoc.findFile("whitelist.json") ?: rootDoc.createFile("application/json", "whitelist.json")
                    whiteFile?.let { doc ->
                        context.contentResolver.openOutputStream(doc.uri)?.use { out ->
                            out.write(json.toByteArray())
                        }
                    }
                }
            } else {
                val whiteFile = File(serverPath, "whitelist.json")
                whiteFile.writeText(json)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
