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
import android.content.Context
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext

@HiltViewModel
class ModDetailsViewModel @Inject constructor(
    private val modrinthRepository: ModrinthRepository,
    private val serverRepository: ServerRepository,
    private val client: OkHttpClient,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val serverId: String = checkNotNull(savedStateHandle["serverId"])
    private val projectId: String = checkNotNull(savedStateHandle["projectId"])

    private val _project = MutableStateFlow<ModrinthProject?>(null)
    val project: StateFlow<ModrinthProject?> = _project.asStateFlow()

    private val _allVersions = MutableStateFlow<List<ModrinthVersion>>(emptyList())
    
    private val _versions = MutableStateFlow<List<ModrinthVersion>>(emptyList())
    val versions: StateFlow<List<ModrinthVersion>> = _versions.asStateFlow()

    private val _availableGameVersions = MutableStateFlow<List<String>>(emptyList())
    val availableGameVersions: StateFlow<List<String>> = _availableGameVersions.asStateFlow()

    private val _selectedGameVersion = MutableStateFlow<String?>(null)
    val selectedGameVersion: StateFlow<String?> = _selectedGameVersion.asStateFlow()
    
    private val _compatibleLoaders = MutableStateFlow<List<String>>(emptyList())
    val compatibleLoaders: StateFlow<List<String>> = _compatibleLoaders.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    private val _downloadProgressMap = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgressMap: StateFlow<Map<String, Float>> = _downloadProgressMap.asStateFlow()

    private val _showAlphaBeta = MutableStateFlow(false)
    val showAlphaBeta: StateFlow<Boolean> = _showAlphaBeta.asStateFlow()

    private val _selectedLoader = MutableStateFlow<String?>(null)
    val selectedLoader: StateFlow<String?> = _selectedLoader.asStateFlow()

    fun toggleAlphaBeta() {
        _showAlphaBeta.value = !_showAlphaBeta.value
        applyFilter()
    }

    fun setLoaderFilter(loader: String?) {
        _selectedLoader.value = loader
        applyFilter()
    }

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 1. Fetch Project
                val projectDeferred = viewModelScope.async { modrinthRepository.getProject(projectId) }
                
                // 2. Fetch ALL versions (no loader/gameVersion filter) to show full history
                val server = serverRepository.getServerById(serverId)
                val compatibleLoaders = if (server != null) {
                    when (server.type.lowercase()) {
                        "fabric" -> listOf("fabric")
                        "forge" -> listOf("forge")
                        "neoforge" -> listOf("neoforge")
                        "paper", "spigot", "bukkit" -> listOf("paper", "bukkit", "spigot")
                        "pocketmine", "bedrock" -> listOf("bedrock") // PocketMine uses 'pocketmine' usually but Modrinth uses 'bedrock'/'pocketmine'?
                        else -> null
                    }
                } else null
                
                _compatibleLoaders.value = compatibleLoaders ?: emptyList()

                val versionsDeferred = viewModelScope.async {
                    // Fetch EVERYTHING. We will flag incompatibility in UI.
                    modrinthRepository.getVersions(projectId, loaders = null, gameVersions = null)
                }

                _project.value = projectDeferred.await()
                val allVers = versionsDeferred.await()
                _allVersions.value = allVers
                
                // Extract unique game versions
                val gameVersions = allVers
                    .flatMap { it.gameVersions }
                    .distinct()
                    .filter { v ->
                        !v.contains("snapshot", ignoreCase = true) &&
                        !v.contains("pre", ignoreCase = true) &&
                        !v.contains("rc", ignoreCase = true) &&
                        !v.contains("alpha", ignoreCase = true) &&
                        !v.contains("beta", ignoreCase = true) &&
                        !v.contains("w", ignoreCase = true) &&
                        v.matches(Regex("^[0-9]+\\.[0-9]+(\\.[0-9]+)?$"))
                    }
                    .sortedByDescending { v ->
                        v.split(".").mapNotNull { it.toIntOrNull() }.joinToString(".") { String.format("%03d", it) }
                    }
                _availableGameVersions.value = gameVersions
                
                // Default to server's version if available
                val serverVersion = server?.version
                if (serverVersion != null && gameVersions.contains(serverVersion)) {
                    _selectedGameVersion.value = serverVersion
                }
                
                applyFilter()

            } catch (e: Exception) {
                e.printStackTrace()
                _toastMessage.emit("Erro ao carregar detalhes: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectGameVersion(version: String?) {
        _selectedGameVersion.value = version
        applyFilter()
    }

    private fun applyFilter() {
        val selected = _selectedGameVersion.value
        val loader = _selectedLoader.value
        val showAll = _showAlphaBeta.value

        var filteredList = _allVersions.value

        // Filter by Game Version
        if (selected != null) {
            filteredList = filteredList.filter { it.gameVersions.contains(selected) }
        }

        // Filter by Loader (NEW)
        if (loader != null) {
            filteredList = filteredList.filter { it.loaders.contains(loader) }
        }
        
        // Filter by Release Type
        if (!showAll) {
            filteredList = filteredList.filter { it.versionType == "release" }
        }
        
        _versions.value = filteredList
    }

    fun downloadVersion(version: ModrinthVersion) {
        viewModelScope.launch {
            val server = serverRepository.getServerById(serverId) ?: return@launch
            val project = _project.value ?: return@launch
            
            val metaManager = com.lzofseven.mcserver.util.ContentMetaManager(context, server.uri ?: server.path)

            if (version.files.isEmpty()) {
                _toastMessage.emit("Erro: Esta versão não possui arquivos.")
                return@launch
            }

            // Heuristic to pick the best file based on the selected loader
            val loaderFilter = _selectedLoader.value
            val file = if (loaderFilter != null) {
                // If user selected a loader, prioritize files containing that name
                version.files.find { it.filename.contains(loaderFilter, ignoreCase = true) } 
                    ?: version.files.find { it.primary } 
                    ?: version.files.first()
            } else {
                // Fallback to primary
                version.files.find { it.primary } ?: version.files.first()
            }

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
                        // Check if we should use SAF
                        if (server.uri != null) {
                            val rootUri = android.net.Uri.parse(server.uri)
                            val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
                            if (rootDoc != null && rootDoc.exists()) {
                                var targetFolderDoc = rootDoc.findFile(finalFolderName)
                                if (targetFolderDoc == null || !targetFolderDoc.isDirectory) {
                                    targetFolderDoc = rootDoc.createDirectory(finalFolderName)
                                }
                                
                                if (targetFolderDoc != null) {
                                    // Delete if exists
                                    targetFolderDoc.findFile(file.filename)?.delete()
                                    val newFileDoc = targetFolderDoc.createFile("application/java-archive", file.filename)
                                    
                                    newFileDoc?.let { doc ->
                                        context.contentResolver.openOutputStream(doc.uri)?.use { output ->
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
                                    } ?: throw Exception("Falha ao criar arquivo via SAF")
                                } else throw Exception("Falha ao acessar pasta $finalFolderName via SAF")
                            } else throw Exception("URI do servidor inválida ou inacessível")
                        } else {
                            // Direct File Fallback
                            val targetDir = java.io.File(server.path, finalFolderName)
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
                                            put(version.id, progress)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    _downloadProgressMap.value = _downloadProgressMap.value.toMutableMap().apply {
                        remove(version.id)
                    }
                    
                    // Save Metadata
                    metaManager.saveMetadata(
                        com.lzofseven.mcserver.util.ContentMetadata(
                            projectId = project.id,
                            title = project.title,
                            iconUrl = project.iconUrl,
                            version = version.versionNumber,
                            projectType = if (finalFolderName == "plugins") "plugin" else "mod",
                            filename = file.filename
                        )
                    )
                    
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
