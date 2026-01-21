package com.lzofseven.mcserver.ui.screens.mods

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.lzofseven.mcserver.data.repository.ServerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File

@HiltViewModel
class ModsViewModel @Inject constructor(
    private val repository: ServerRepository,
    private val modrinthRepository: com.lzofseven.mcserver.data.repository.ModrinthRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    val serverId: String = checkNotNull(savedStateHandle["serverId"])
    
    private val _installedContent = MutableStateFlow<List<InstalledContent>>(emptyList())
    val installedContent: StateFlow<List<InstalledContent>> = _installedContent.asStateFlow()
    
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab = _selectedTab.asStateFlow()
    
    // File Manager State
    private val _currentBrowserPath = MutableStateFlow<File?>(null)
    val currentBrowserPath = _currentBrowserPath.asStateFlow()
    
    private val _browserFiles = MutableStateFlow<List<File>>(emptyList())
    val browserFiles = _browserFiles.asStateFlow()

    private val _selectedSubFilter = MutableStateFlow<String?>(null)
    val selectedSubFilter = _selectedSubFilter.asStateFlow()
    
    private val _showDisabled = MutableStateFlow(false)
    val showDisabled = _showDisabled.asStateFlow()
    
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    private var clipboardFile: File? = null

    init {
        loadContent()
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
        _selectedSubFilter.value = null
        _showDisabled.value = false
        loadContent()
    }

    fun refresh() {
        loadContent()
    }

    fun loadContent() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val server = repository.getServerById(serverId) ?: return@launch
            val serverRoot = File(server.path)
            
            if (_selectedTab.value == 2) {
                // File Manager Logic
                val currentDir = _currentBrowserPath.value ?: serverRoot
                _currentBrowserPath.value = currentDir
                val files = (currentDir.listFiles() ?: emptyArray()).sortedWith(
                    compareBy({ !it.isDirectory }, { it.name.lowercase() })
                )
                _browserFiles.value = files
                return@launch
            }

            var items = when (_selectedTab.value) {
                0 -> loadFromDir(File(serverRoot, "mods"), "mod")
                1 -> loadFromDir(File(serverRoot, "plugins"), "plugin")
                else -> emptyList()
            }

            // Apply filters
            if (_selectedSubFilter.value != null) {
                items = items.filter { it.type == _selectedSubFilter.value }
            }
            if (_showDisabled.value) {
                items = items.filter { !it.isEnabled }
            }

            _installedContent.value = items
        }
    }

    fun navigateTo(file: File) {
        if (file.isDirectory) {
            _currentBrowserPath.value = file
            loadContent()
        }
    }

    fun navigateBack() {
        viewModelScope.launch {
            val server = repository.getServerById(serverId) ?: return@launch
            val serverRoot = File(server.path)
            val current = _currentBrowserPath.value ?: return@launch
            
            if (current.absolutePath != serverRoot.absolutePath) {
                _currentBrowserPath.value = current.parentFile
                loadContent()
            }
        }
    }

    fun deleteFile(file: File) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
            loadContent()
            _toastMessage.emit("Apagado: ${file.name}")
        }
    }

    fun renameFile(file: File, newName: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val newFile = File(file.parentFile, newName)
            if (file.renameTo(newFile)) {
                loadContent()
                _toastMessage.emit("Renomeado para $newName")
            } else {
                _toastMessage.emit("Erro ao renomear")
            }
        }
    }

    fun copyFile(file: File) {
        clipboardFile = file
        viewModelScope.launch { _toastMessage.emit("Copiado: ${file.name}") }
    }

    fun pasteFile() {
        val targetDir = _currentBrowserPath.value ?: return
        val source = clipboardFile ?: return
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                _toastMessage.emit("Colando...")
                val target = File(targetDir, source.name)
                if (source.isDirectory) {
                    source.copyRecursively(target, overwrite = true)
                } else {
                    source.copyTo(target, overwrite = true)
                }
                loadContent()
                _toastMessage.emit("Colado com sucesso!")
            } catch (e: Exception) {
                _toastMessage.emit("Erro ao colar: ${e.message}")
            }
        }
    }

    fun readFile(file: File): String {
        return try {
            file.readText()
        } catch (e: Exception) {
            "Erro ao ler arquivo: ${e.message}"
        }
    }

    fun saveFile(file: File, content: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                file.writeText(content)
                _toastMessage.emit("Arquivo salvo!")
            } catch (e: Exception) {
                _toastMessage.emit("Erro ao salvar: ${e.message}")
            }
        }
    }

    fun createFolder(name: String) {
        val current = _currentBrowserPath.value ?: return
        val newFolder = File(current, name)
        if (newFolder.mkdirs()) {
            loadContent()
        }
    }

    private val metadataCache = mutableMapOf<String, InstalledContent>()

    private suspend fun loadFromDir(dir: File, type: String): List<InstalledContent> {
        if (!dir.exists()) return emptyList()
        val files = (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && (it.name.endsWith(".jar") || it.name.endsWith(".jar.disabled") || it.name.endsWith(".phar") || it.name.endsWith(".phar.disabled")) }
        
        return files.map { file ->
            val fileName = file.name
            val isEnabled = !fileName.endsWith(".disabled")
            
            // Try cache first
            metadataCache[file.absolutePath]?.let { return@map it.copy(isEnabled = isEnabled) }

            // Extract local metadata
            val localMeta = extractMetadata(file)
            
            // Try to fetch from Modrinth API
            var iconUrl: String? = null
            var remoteTitle: String? = null
            var remoteDesc: String? = null
            
            try {
                val sha1 = calculateSHA1(file)
                val versionInfo = modrinthRepository.getVersionFromHash(sha1)
                versionInfo?.let { v ->
                    val project = modrinthRepository.getProject(v.projectId)
                    iconUrl = project.iconUrl
                    remoteTitle = project.title
                    remoteDesc = project.description
                }
            } catch (e: Exception) {
                // Silently fail API fetch
            }

            val item = InstalledContent(
                id = localMeta.id ?: fileName,
                name = remoteTitle ?: localMeta.name ?: (if (isEnabled) fileName else fileName.removeSuffix(".disabled")),
                author = localMeta.author ?: "Autor Desconhecido",
                version = localMeta.version ?: "N/A",
                fileName = fileName,
                type = type,
                isEnabled = isEnabled,
                fullPath = file.absolutePath,
                description = remoteDesc ?: localMeta.description,
                iconUrl = iconUrl
            )
            
            metadataCache[file.absolutePath] = item
            item
        }
    }

    private fun calculateSHA1(file: File): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-1")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead = input.read(buffer)
                while (bytesRead != -1) {
                    digest.update(buffer, 0, bytesRead)
                    bytesRead = input.read(buffer)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    private fun loadWorlds(dir: File): List<InstalledContent> {
        if (!dir.exists()) return emptyList()
        return (dir.listFiles() ?: emptyArray())
            .filter { it.isDirectory }
            .map { file ->
                InstalledContent(
                    id = file.name,
                    name = file.name,
                    fileName = file.name,
                    type = "world",
                    isEnabled = true,
                    fullPath = file.absolutePath
                )
            }
    }

    private data class JarMeta(val id: String? = null, val name: String? = null, val version: String? = null, val author: String? = null, val description: String? = null)

    private fun extractMetadata(file: File): JarMeta {
        try {
            java.util.zip.ZipFile(file).use { zip ->
                // Try Fabric
                zip.getEntry("fabric.mod.json")?.let { entry ->
                    val json = zip.getInputStream(entry).bufferedReader().readText()
                    val obj = org.json.JSONObject(json)
                    return JarMeta(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        version = obj.optString("version"),
                        author = obj.optJSONArray("authors")?.opt(0)?.toString() ?: obj.optJSONObject("authors")?.keys()?.next()?.toString(),
                        description = obj.optString("description")
                    )
                }
                // Try Bukkit/Spigot/Paper
                zip.getEntry("plugin.yml")?.let { entry ->
                    val yml = zip.getInputStream(entry).bufferedReader().readText()
                    // Simple regex for yaml parsing
                    val name = "name:\\s*(.*)".toRegex().find(yml)?.groupValues?.get(1)?.trim()
                    val version = "version:\\s*(.*)".toRegex().find(yml)?.groupValues?.get(1)?.trim()
                    val author = "author:\\s*(.*)".toRegex().find(yml)?.groupValues?.get(1)?.trim() ?: "main:\\s*(.*)".toRegex().find(yml)?.groupValues?.get(1)?.trim()
                    return JarMeta(name = name, version = version, author = author)
                }
                // Try Quilt
                zip.getEntry("quilt_loader.json")?.let { entry ->
                    // Similar to fabric
                }
            }
        } catch (e: Exception) {
            // Not a zip or error
        }
        return JarMeta()
    }

    fun toggleItem(item: InstalledContent) {
        viewModelScope.launch {
            val file = File(item.fullPath)
            if (!file.exists()) return@launch
            
            val newFile = if (item.isEnabled) {
                File(item.fullPath + ".disabled")
            } else {
                File(item.fullPath.removeSuffix(".disabled"))
            }
            
            if (file.renameTo(newFile)) {
                loadContent()
            }
        }
    }
    
    fun deleteItem(item: InstalledContent) {
        viewModelScope.launch {
            val file = File(item.fullPath)
            if (file.exists()) {
                if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }
                loadContent()
                _toastMessage.emit("${item.name} removido")
            }
        }
    }
}
