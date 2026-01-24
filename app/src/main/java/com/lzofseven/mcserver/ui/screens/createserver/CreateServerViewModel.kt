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
        _uiState.value = _uiState.value.copy(name = name)
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

    fun updateJavaVersion(version: Int) {
        _uiState.value = _uiState.value.copy(javaVersion = version)
    }

    fun updateAutoStart(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoStart = enabled)
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

    fun updateAdvSettings(
        userId: String? = null, // Placeholder to match signature if needed
        allowFlight: Boolean? = null,
        pvp: Boolean? = null,
        spawnAnimals: Boolean? = null,
        spawnNpcs: Boolean? = null,
        generateStructures: Boolean? = null,
        allowNether: Boolean? = null,
        viewDistance: Int? = null,
        maxPlayers: Int? = null
    ) {
        var newState = _uiState.value
        if (allowFlight != null) newState = newState.copy(allowFlight = allowFlight)
        if (pvp != null) newState = newState.copy(pvp = pvp)
        if (spawnAnimals != null) newState = newState.copy(spawnAnimals = spawnAnimals)
        if (spawnNpcs != null) newState = newState.copy(spawnNpcs = spawnNpcs)
        if (generateStructures != null) newState = newState.copy(generateStructures = generateStructures)
        if (allowNether != null) newState = newState.copy(allowNether = allowNether)
        if (viewDistance != null) newState = newState.copy(viewDistance = viewDistance)
        if (maxPlayers != null) newState = newState.copy(maxPlayers = maxPlayers)
        _uiState.value = newState
    }

    // ... existing searchLibrary, etc ...

    fun createServer(context: android.content.Context, uri: String? = null, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            val newServer = MCServerEntity(
                name = state.name,
                version = state.version,
                type = state.type.name,
                path = state.path,
                uri = uri,
                ramAllocationMB = state.ramAllocation,
                javaVersion = state.javaVersion,
                autoStart = state.autoStart
            )
            repository.insertServer(newServer)
            
            // ... (directories creation logic same as before) ...
            if (state.path.startsWith("content://")) {
            } else {
                val serverDir = java.io.File(state.path)
                if (!serverDir.exists()) serverDir.mkdirs()
            }
            
            // Save version metadata
            try {
                val metadata = com.lzofseven.mcserver.core.metadata.ServerMetadata(
                    version = state.version,
                    type = state.type.name
                )
                com.lzofseven.mcserver.core.metadata.ServerMetadata.save(context, state.path, metadata)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // Save server.properties
            try {
                val propsManager = com.lzofseven.mcserver.util.ServerPropertiesManager(context, uri ?: state.path)
                val initialProps = mapOf(
                    "motd" to state.motd.replace('&', '§'),
                    "difficulty" to state.difficulty.lowercase(),
                    "gamemode" to state.gameMode.lowercase(),
                    "online-mode" to state.onlineMode.toString(),
                    "server-port" to "25565",
                    "max-players" to state.maxPlayers.toString(),
                    "view-distance" to state.viewDistance.toString(),
                    "allow-flight" to state.allowFlight.toString(),
                    "pvp" to state.pvp.toString(),
                    "spawn-animals" to state.spawnAnimals.toString(),
                    "spawn-npcs" to state.spawnNpcs.toString(),
                    "generate-structures" to state.generateStructures.toString(),
                    "allow-nether" to state.allowNether.toString()
                )
                propsManager.save(initialProps)

                // Save icon logic
                state.serverIconUri?.let { uri ->
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (originalBitmap != null) {
                        val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, 64, 64, true)
                        val iconFile = java.io.File(state.path, "server-icon.png")
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
                installMod(context, item, uri ?: state.path, state.type.name, state.version)
            }
            
            onSuccess()
        }
    }

    private suspend fun installMod(context: android.content.Context, item: com.lzofseven.mcserver.data.model.ModrinthResult, serverPath: String, serverType: String, serverVersion: String) {
        try {
            val loaders = when(serverType.lowercase()) {
                "fabric" -> listOf("fabric")
                "forge" -> listOf("forge")
                "neoforge" -> listOf("neoforge")
                "paper", "spigot", "bukkit" -> listOf("paper", "bukkit", "spigot")
                else -> null
            }
            
            val versions = modrinthRepository.getVersions(item.projectId, loaders, listOf(serverVersion))
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
                    
                    if (serverPath.startsWith("content://")) {
                        // SAF Download
                        val treeUri = android.net.Uri.parse(serverPath)
                        val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
                        
                        val targetDir = if (folderName.isNotEmpty()) {
                            docFile?.findFile(folderName) ?: docFile?.createDirectory(folderName)
                        } else docFile
                        
                        targetDir?.let { dir ->
                            val targetFileDoc = dir.findFile(file.filename) ?: dir.createFile("application/java-archive", file.filename)
                            targetFileDoc?.let { target ->
                                val request = okhttp3.Request.Builder().url(file.url).build()
                                val response = client.newCall(request).execute()
                                if (response.isSuccessful) {
                                    context.contentResolver.openOutputStream(target.uri)?.use { output ->
                                        response.body?.byteStream()?.copyTo(output)
                                    }
                                }
                            }
                        }
                    } else {
                        // Direct File Access
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
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

data class CreateServerState(
    val currentStep: Int = 0,
    val name: String = "",
    val type: ServerType = ServerType.PAPER,
    val availableVersions: List<String> = emptyList(),
    val version: String = "",
    val ramAllocation: Int = 2048,
    val path: String = "",
    val gameMode: String = "Survival",
    val difficulty: String = "Normal",
    val onlineMode: Boolean = false,
    val javaVersion: Int = 17,
    val autoStart: Boolean = false,
    val motd: String = "A Minecraft Server",
    val serverIconUri: android.net.Uri? = null,
    
    // Advanced Settings
    val allowFlight: Boolean = false,
    val pvp: Boolean = true,
    val spawnAnimals: Boolean = true,
    val spawnNpcs: Boolean = true,
    val generateStructures: Boolean = true,
    val allowNether: Boolean = true,
    val viewDistance: Int = 10,
    val maxPlayers: Int = 20,

    val queuedContent: List<com.lzofseven.mcserver.data.model.ModrinthResult> = emptyList(),
    val libraryResults: List<com.lzofseven.mcserver.data.model.ModrinthResult> = emptyList(),
    val isSearching: Boolean = false
)
