package com.lzofseven.mcserver.ui.screens.moddetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lzofseven.mcserver.data.model.ModrinthProject
import com.lzofseven.mcserver.data.model.ModrinthVersion
import com.lzofseven.mcserver.data.repository.ModrinthRepository
import com.lzofseven.mcserver.data.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltViewModel
class ModDetailsViewModel @Inject constructor(
    private val modrinthRepository: ModrinthRepository,
    private val serverRepository: ServerRepository,
    private val client: OkHttpClient,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val serverId: String = checkNotNull(savedStateHandle["serverId"])
    private val projectId: String = checkNotNull(savedStateHandle["projectId"])

    private val _project = MutableStateFlow<ModrinthProject?>(null)
    val project: StateFlow<ModrinthProject?> = _project.asStateFlow()

    private val _versions = MutableStateFlow<List<ModrinthVersion>>(emptyList())
    val versions: StateFlow<List<ModrinthVersion>> = _versions.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    private val _downloadProgressMap = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgressMap: StateFlow<Map<String, Float>> = _downloadProgressMap.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 1. Fetch Project
                val projectDeferred = viewModelScope.async { modrinthRepository.getProject(projectId) }
                
                // 2. Fetch Versions (Filtered by Server)
                val server = serverRepository.getServerById(serverId)
                val loader = if (server != null) {
                    when (server.type.lowercase()) {
                        "fabric" -> "fabric"
                        "forge" -> "forge"
                        "neoforge" -> "neoforge"
                        "paper", "spigot", "bukkit" -> "paper"
                        else -> null
                    }
                } else null
                
                val gameVersion = server?.version

                val versionsDeferred = viewModelScope.async {
                    modrinthRepository.getVersions(projectId, loader = loader, gameVersion = gameVersion)
                }

                _project.value = projectDeferred.await()
                _versions.value = versionsDeferred.await()

            } catch (e: Exception) {
                e.printStackTrace()
                _toastMessage.emit("Erro ao carregar detalhes: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun downloadVersion(version: ModrinthVersion) {
        viewModelScope.launch {
            val server = serverRepository.getServerById(serverId) ?: return@launch
            val project = _project.value ?: return@launch

            if (version.files.isEmpty()) {
                _toastMessage.emit("Erro: Esta versão não possui arquivos.")
                return@launch
            }

            val file = version.files.find { it.primary } ?: version.files.first()

            val folderName = when {
                project.loaders.contains("paper") || project.loaders.contains("bukkit") || project.loaders.contains("spigot") -> "plugins" // Heuristic
                project.loaders.contains("fabric") || project.loaders.contains("forge") -> "mods"
                else -> {
                    // Fallback using slug or known content types could be better, but sticking to existing logic
                    // Actually ModrinthProject doesn't have 'project_type' field in the model we saw?
                    // LibraryViewModel had it from ModrinthResult.
                    // We can infer from loaders or use a default.
                    "mods" 
                }
            }
            
            // Refined Logic based on Server Type if ambiguous
            val finalFolderName = if (folderName == "mods" && server.type.equals("paper", true)) "plugins" else folderName

            val targetDir = java.io.File(server.path, finalFolderName)
            if (!targetDir.exists()) targetDir.mkdirs()

            val targetFile = java.io.File(targetDir, file.filename)

            try {
                // Download Logic
                val request = okhttp3.Request.Builder().url(file.url).build()
                val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    client.newCall(request).execute()
                }

                if (response.isSuccessful) {
                    val body = response.body ?: throw Exception("Body is null")
                    val totalBytes = body.contentLength()
                    var bytesDownloaded = 0L

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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
                                        put(version.id, progress)
                                    }
                                }
                            }
                        }
                    }
                    _downloadProgressMap.value = _downloadProgressMap.value.toMutableMap().apply {
                        remove(version.id)
                    }
                    _toastMessage.emit("Download concluído: ${file.filename}")
                } else {
                    _toastMessage.emit("Erro no download: ${response.code}")
                }
            } catch (e: Exception) {
                _toastMessage.emit("Falha: ${e.message}")
            }
        }
    }
}
