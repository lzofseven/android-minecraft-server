package com.lzofseven.mcserver.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lzofseven.mcserver.data.model.ModrinthResult
import com.lzofseven.mcserver.data.repository.ModrinthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import okhttp3.OkHttpClient

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val modrinthRepository: ModrinthRepository,
    private val serverRepository: com.lzofseven.mcserver.data.repository.ServerRepository,
    private val client: OkHttpClient,
    @ApplicationContext private val context: Context,
    savedStateHandle: androidx.lifecycle.SavedStateHandle
) : ViewModel() {
    
    private val serverId: String = checkNotNull(savedStateHandle["serverId"])
    
    private val _searchResults = MutableStateFlow<List<ModrinthResult>>(emptyList())
    val searchResults: StateFlow<List<ModrinthResult>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedType = MutableStateFlow<String?>(null) // "mod", "plugin", etc
    val selectedType: StateFlow<String?> = _selectedType.asStateFlow()
    
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    private val _downloadProgressMap = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgressMap: StateFlow<Map<String, Float>> = _downloadProgressMap.asStateFlow()

    private val _selectedLoader = MutableStateFlow<String?>(null) // "fabric", "forge", "bukkit", etc
    val selectedLoader: StateFlow<String?> = _selectedLoader.asStateFlow()
    
    init {
        // Perform initial search to populate the list immediately
        search("")
    }
    
    fun search(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val server = serverRepository.getServerById(serverId)

                val isBedrock = server?.type?.equals("BEDROCK", ignoreCase = true) == true || 
                               server?.type?.equals("POCKETMINE", ignoreCase = true) == true
                
                val userLoader = _selectedLoader.value
                val baseCategories = if (isBedrock) listOf("bedrock") else emptyList()
                
                val categories = if (userLoader != null) {
                    baseCategories + userLoader
                } else {
                    baseCategories.ifEmpty { null }
                }
                
                _searchResults.value = modrinthRepository.search(
                    query = query, 
                    type = _selectedType.value,
                    categories = categories
                )
            } catch (e: Exception) {
                _toastMessage.emit("Erro ao buscar: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setFilter(type: String?) {
        _selectedType.value = type
        search("") // Refresh with new filter
    }

    fun setLoaderFilter(loader: String?) {
        _selectedLoader.value = loader
        search("")
    }
    
    fun downloadContent(item: ModrinthResult) {
        viewModelScope.launch {
            val server = serverRepository.getServerById(serverId) ?: return@launch
            _isLoading.value = true
            try {
                // Determine Loader & Version
                val loaders = when(server.type.lowercase()) {
                    "fabric" -> listOf("fabric")
                    "forge" -> listOf("forge")
                    "neoforge" -> listOf("neoforge")
                    "paper", "spigot", "bukkit" -> listOf("paper", "bukkit", "spigot") 
                    "pocketmine", "bedrock" -> listOf("bedrock")
                    else -> null
                }
                
                // 1. Fetch versions with filters
                val versions = modrinthRepository.getVersions(
                    projectId = item.projectId,
                    loaders = loaders,
                    gameVersions = listOf(server.version)
                ).filter { it.versionType == "release" }
                
                if (versions.isEmpty()) {
                    _toastMessage.emit("Nenhuma versão compatível encontrada para ${server.type} ${server.version}.")
                    return@launch
                }

                // 2. Select latest version
                // The API usually returns sorted, but we can double check logic if needed.
                val latestVersion = versions.first()
                
                if (latestVersion.files.isEmpty()) {
                    _toastMessage.emit("Erro: A versão encontrada não contém arquivos.")
                    return@launch
                }
                
                val file = latestVersion.files.find { it.primary } ?: latestVersion.files.first()

                // 3. Determine folder
                val folderName = when {
                    item.projectType == "mod" -> if (server.type.equals("paper", true) || server.type.equals("spigot", true)) "plugins" else "mods"
                    item.projectType == "plugin" -> "plugins"
                    else -> ""
                }
                
                // 4. Actual download
                val request = okhttp3.Request.Builder().url(file.url).build()
                val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    client.newCall(request).execute()
                }
                
                if (response.isSuccessful) {
                    val body = response.body ?: throw Exception("Body is null")
                    val totalBytes = body.contentLength()
                    var bytesDownloaded = 0L

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        if (server.uri != null) {
                            // SAF Logic
                            val rootUri = android.net.Uri.parse(server.uri)
                            val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, rootUri)
                            if (rootDoc != null && rootDoc.exists()) {
                                var targetFolderDoc = if (folderName.isNotEmpty()) rootDoc.findFile(folderName) else rootDoc
                                if (folderName.isNotEmpty() && (targetFolderDoc == null || !targetFolderDoc.isDirectory)) {
                                    targetFolderDoc = rootDoc.createDirectory(folderName)
                                }
                                
                                if (targetFolderDoc != null) {
                                    targetFolderDoc.findFile(file.filename)?.delete()
                                    val newFileDoc = targetFolderDoc.createFile("application/java-archive", file.filename)
                                    newFileDoc?.let { doc ->
                                        context.contentResolver.openOutputStream(doc.uri, "wt")?.use { output ->
                                            val buffer = ByteArray(8192)
                                            var bytesRead: Int
                                            val inputStream = body.byteStream()
                                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                                output.write(buffer, 0, bytesRead)
                                                bytesDownloaded += bytesRead
                                                if (totalBytes > 0) {
                                                    val progress = bytesDownloaded.toFloat() / totalBytes.toFloat()
                                                    _downloadProgressMap.value = _downloadProgressMap.value.toMutableMap().apply {
                                                        put(item.projectId, progress)
                                                    }
                                                }
                                            }
                                        }
                                    } ?: throw Exception("Falha ao criar arquivo via SAF")
                                } else throw Exception("Falha ao acessar pasta $folderName via SAF")
                            } else throw Exception("URI do servidor inválida")
                        } else {
                            // Traditional File Logic
                            val targetDir = if (folderName.isNotEmpty()) java.io.File(server.path, folderName) else java.io.File(server.path)
                            if (!targetDir.exists()) targetDir.mkdirs()
                            val targetFile = java.io.File(targetDir, file.filename)
                            
                            java.io.FileOutputStream(targetFile).use { output ->
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                val inputStream = body.byteStream()
                                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                    bytesDownloaded += bytesRead
                                    if (totalBytes > 0) {
                                        val progress = bytesDownloaded.toFloat() / totalBytes.toFloat()
                                        _downloadProgressMap.value = _downloadProgressMap.value.toMutableMap().apply {
                                            put(item.projectId, progress)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    _downloadProgressMap.value = _downloadProgressMap.value.toMutableMap().apply {
                        remove(item.projectId)
                    }
                    
                    // Save Metadata
                    val metaManager = com.lzofseven.mcserver.util.ContentMetaManager(context, server.uri ?: server.path)
                    metaManager.saveMetadata(
                        com.lzofseven.mcserver.util.ContentMetadata(
                            projectId = item.projectId,
                            title = item.title,
                            iconUrl = item.iconUrl,
                            version = latestVersion.versionNumber,
                            projectType = if (folderName == "plugins") "plugin" else "mod",
                            filename = file.filename
                        )
                    )
                    
                    _toastMessage.emit("Sucesso: ${file.filename} instalado! Reinicie o servidor.")
                } else {
                    _toastMessage.emit("Erro no download: Code ${response.code}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Explicitly show null message as string
                val msg = e.message ?: "Erro desconhecido (${e.javaClass.simpleName})"
                _toastMessage.emit("Falha no download: $msg")
            } finally {
                _isLoading.value = false
            }
        }
    }
}
