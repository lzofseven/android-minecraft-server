package com.lzofseven.mcserver.ui.screens.createserver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lzofseven.mcserver.data.local.entity.MCServerEntity
import com.lzofseven.mcserver.data.repository.ServerRepository
import com.lzofseven.mcserver.ui.screens.config.ServerType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateServerViewModel @Inject constructor(
    private val repository: ServerRepository,
    private val modrinthRepository: com.lzofseven.mcserver.data.repository.ModrinthRepository,
    private val client: okhttp3.OkHttpClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateServerState())
    val uiState = _uiState.asStateFlow()

    init {
        // Initialize versions for default type
        val defaultVersions = com.lzofseven.mcserver.util.McVersionUtils.getSupportedVersions(CreateServerState().type.name)
        _uiState.value = _uiState.value.copy(availableVersions = defaultVersions, version = defaultVersions.firstOrNull() ?: "")
    }

    fun updateName(name: String) {
        val sanitizedName = name.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30)
        val defaultPath = "/storage/emulated/0/MCServers/$sanitizedName"
        _uiState.value = _uiState.value.copy(
            name = name,
            path = if (_uiState.value.path.isEmpty() || _uiState.value.path.startsWith("/storage/emulated/0/MCServers/")) defaultPath else _uiState.value.path
        )
    }

    fun updateVersion(version: String) {
        _uiState.value = _uiState.value.copy(version = version)
    }

    fun updateType(type: ServerType) {
        val versions = com.lzofseven.mcserver.util.McVersionUtils.getSupportedVersions(type.name)
        val defaultVersion = versions.firstOrNull() ?: ""
        _uiState.value = _uiState.value.copy(
            type = type,
            availableVersions = versions,
            version = defaultVersion
        )
    }

    fun updatePath(path: String) {
        _uiState.value = _uiState.value.copy(path = path)
    }
    
    fun updateRam(ram: Int) {
        _uiState.value = _uiState.value.copy(ramAllocation = ram)
    }
    
    fun updateGameMode(mode: String) {
        _uiState.value = _uiState.value.copy(gameMode = mode)
    }

    fun updateDifficulty(diff: String) {
        _uiState.value = _uiState.value.copy(difficulty = diff)
    }

    fun updateOnlineMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(onlineMode = enabled)
    }

    fun searchLibrary(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true)
            try {
                // Filter by type based on server engine if possible, but keep it open for now
                val results = modrinthRepository.search(query, version = _uiState.value.version)
                _uiState.value = _uiState.value.copy(libraryResults = results)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _uiState.value = _uiState.value.copy(isSearching = false)
            }
        }
    }

    fun loadTrendingMods() {
        if (_uiState.value.libraryResults.isNotEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true)
            try {
                // Load trending/popular mods for the server type
                val searchType = when(_uiState.value.type) {
                    ServerType.PAPER -> "plugin"
                    else -> "mod"
                }
                val results = modrinthRepository.search("", type = searchType, version = _uiState.value.version)
                _uiState.value = _uiState.value.copy(libraryResults = results)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _uiState.value = _uiState.value.copy(isSearching = false)
            }
        }
    }

    fun toggleModQueue(item: com.lzofseven.mcserver.data.model.ModrinthResult) {
        val currentQueue = _uiState.value.queuedContent.toMutableList()
        if (currentQueue.any { it.projectId == item.projectId }) {
            currentQueue.removeAll { it.projectId == item.projectId }
        } else {
            currentQueue.add(item)
        }
        _uiState.value = _uiState.value.copy(queuedContent = currentQueue)
    }

    fun updateMotd(motd: String) {
        _uiState.value = _uiState.value.copy(motd = motd)
    }

    fun updateServerIcon(uri: android.net.Uri?) {
        _uiState.value = _uiState.value.copy(serverIconUri = uri)
    }

    fun nextStep() {
        if (_uiState.value.currentStep < 3) {
            _uiState.value = _uiState.value.copy(currentStep = _uiState.value.currentStep + 1)
        }
    }

    fun previousStep() {
        if (_uiState.value.currentStep > 0) {
            _uiState.value = _uiState.value.copy(currentStep = _uiState.value.currentStep - 1)
        }
    }

    fun createServer(context: android.content.Context, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            val newServer = MCServerEntity(
                name = state.name,
                version = state.version,
                type = state.type.name,
                path = state.path,
                ramAllocationMB = state.ramAllocation
            )
            repository.insertServer(newServer)
            
            // Save initial server.properties
            try {
                val propsManager = com.lzofseven.mcserver.util.ServerPropertiesManager(context, state.path)
                val initialProps = mapOf(
                    "motd" to state.motd.replace('&', '§'),
                    "difficulty" to state.difficulty.lowercase(),
                    "gamemode" to state.gameMode.lowercase(),
                    "online-mode" to state.onlineMode.toString(),
                    "server-port" to "25565",
                    "max-players" to "20"
                )
                propsManager.save(initialProps)

                // Save icon if selected
                state.serverIconUri?.let { uri ->
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (originalBitmap != null) {
                        val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, 64, 64, true)
                        val iconFile = java.io.File(state.path, "server-icon.png")
                        // Ensure parent dir exists (it should be created by insertServer or the platform)
                        iconFile.parentFile?.mkdirs()
                        val outStream = java.io.FileOutputStream(iconFile)
                        scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outStream)
                        outStream.close()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // Download queued mods
            state.queuedContent.forEach { item ->
                installMod(context, item, state.path, state.type.name, state.version)
            }
            
            onSuccess()
        }
    }

    private suspend fun installMod(context: android.content.Context, item: com.lzofseven.mcserver.data.model.ModrinthResult, serverPath: String, serverType: String, serverVersion: String) {
        try {
            val loader = when(serverType.lowercase()) {
                "fabric" -> "fabric"
                "forge" -> "forge"
                "neoforge" -> "neoforge"
                "paper", "spigot", "bukkit" -> "paper"
                else -> null
            }
            
            val versions = modrinthRepository.getVersions(item.projectId, loader, serverVersion)
            if (versions.isNotEmpty()) {
                val latest = versions.first()
                if (latest.files.isNotEmpty()) {
                    val file = latest.files.find { it.primary } ?: latest.files.first()
                    
                    val folderName = when {
                        item.projectType == "mod" -> "mods"
                        item.projectType == "plugin" -> "plugins"
                        item.projectType == "shader" -> "shaderpacks"
                        else -> ""
                    }
                    
                    val targetDir = if (folderName.isNotEmpty()) java.io.File(serverPath, folderName) else java.io.File(serverPath)
                    if (!targetDir.exists()) targetDir.mkdirs()
                    
                    val targetFile = java.io.File(targetDir, file.filename)
                    
                    val request = okhttp3.Request.Builder().url(file.url).build()
                    val response = client.newCall(request).execute()
                    
                    if (response.isSuccessful) {
                        response.body?.byteStream()?.use { input ->
                            java.io.FileOutputStream(targetFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

data class CreateServerState(
    val currentStep: Int = 0, // 0: Name/Type, 1: Version/RAM, 2: Identity, 3: Library/Path
    val name: String = "",
    val type: ServerType = ServerType.PAPER,
    val availableVersions: List<String> = emptyList(),
    val version: String = "",
    val ramAllocation: Int = 2048,
    val path: String = "",
    val gameMode: String = "Survival",
    val difficulty: String = "Normal",
    val onlineMode: Boolean = false, // false = Cracked (Offline), true = Premium
    val motd: String = "A Minecraft Server",
    val serverIconUri: android.net.Uri? = null,
    val queuedContent: List<com.lzofseven.mcserver.data.model.ModrinthResult> = emptyList(),
    val libraryResults: List<com.lzofseven.mcserver.data.model.ModrinthResult> = emptyList(),
    val isSearching: Boolean = false
)
