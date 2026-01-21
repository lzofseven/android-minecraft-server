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
import okhttp3.OkHttpClient

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val modrinthRepository: ModrinthRepository,
    private val serverRepository: com.lzofseven.mcserver.data.repository.ServerRepository,
    private val client: OkHttpClient,
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
    
    init {
        // Perform initial search to populate the list immediately
        search("")
    }
    
    fun search(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _searchResults.value = modrinthRepository.search(query, _selectedType.value)
            } catch (e: Exception) {
                // Log error or show snackbar
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
    
    fun downloadContent(item: ModrinthResult) {
        viewModelScope.launch {
            val server = serverRepository.getServerById(serverId) ?: return@launch
            _isLoading.value = true
            try {
                // Determine Loader & Version
                val loader = when(server.type.lowercase()) {
                    "fabric" -> "fabric"
                    "forge" -> "forge"
                    "neoforge" -> "neoforge"
                    "paper", "spigot", "bukkit" -> "paper" // Modrinth often uses 'paper' or 'bukkit' for plugins
                    else -> null // Vanilla or unknown
                }
                
                // 1. Fetch versions with filters
                val versions = modrinthRepository.getVersions(
                    projectId = item.projectId,
                    loader = loader,
                    gameVersion = server.version
                )
                
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
                    item.projectType == "mod" -> "mods"
                    item.projectType == "plugin" -> "plugins"
                    else -> "" // Root or downloads?
                }
                
                val targetDir = if (folderName.isNotEmpty()) java.io.File(server.path, folderName) else java.io.File(server.path)
                if (!targetDir.exists()) targetDir.mkdirs()
                
                val targetFile = java.io.File(targetDir, file.filename)
                
                // 4. Actual download
                val request = okhttp3.Request.Builder().url(file.url).build()
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val body = response.body
                    if (body == null) throw Exception("Corpo da resposta vazio (Body is null)")
                    
                    val totalBytes = body.contentLength()
                    var bytesDownloaded = 0L

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
                    _downloadProgressMap.value = _downloadProgressMap.value.toMutableMap().apply {
                        remove(item.projectId)
                    }
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
