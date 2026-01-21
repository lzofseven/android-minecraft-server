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
import kotlinx.coroutines.launch
import java.io.File

@HiltViewModel
class ModsViewModel @Inject constructor(
    private val repository: ServerRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    val serverId: String = checkNotNull(savedStateHandle["serverId"])
    
    private val _installedContent = MutableStateFlow<List<InstalledContent>>(emptyList())
    val installedContent: StateFlow<List<InstalledContent>> = _installedContent.asStateFlow()
    
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab = _selectedTab.asStateFlow()
    
    // Sub-filters
    private val _selectedSubFilter = MutableStateFlow<String?>(null) // "mod", "shader", etc.
    val selectedSubFilter = _selectedSubFilter.asStateFlow()
    
    private val _showDisabled = MutableStateFlow(false)
    val showDisabled = _showDisabled.asStateFlow()
    
    private val _showUpdates = MutableStateFlow(false)
    val showUpdates = _showUpdates.asStateFlow()

    init {
        loadContent()
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
        _selectedSubFilter.value = null
        _showDisabled.value = false
        _showUpdates.value = false
        loadContent()
    }

    fun setSubFilter(filter: String?) {
        _selectedSubFilter.value = filter
        loadContent()
    }

    fun setShowDisabled(show: Boolean) {
        _showDisabled.value = show
        loadContent()
    }

    fun setShowUpdates(show: Boolean) {
        _showUpdates.value = show
        loadContent()
    }

    fun refresh() {
        loadContent()
    }

    fun loadContent() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val server = repository.getServerById(serverId) ?: return@launch
            var items = when (_selectedTab.value) {
                0 -> {
                    val mods = loadFromDir(File(server.path, "mods"), "mod")
                    val shaders = loadFromDir(File(server.path, "shaderpacks"), "shader")
                    val resourcePacks = loadFromDir(File(server.path, "resourcepacks"), "resourcepack")
                    mods + shaders + resourcePacks
                }
                1 -> loadFromDir(File(server.path, "plugins"), "plugin")
                2 -> loadWorlds(File(server.path, "worlds"))
                else -> emptyList()
            }

            // Apply filters
            if (_selectedSubFilter.value != null) {
                items = items.filter { it.type == _selectedSubFilter.value }
            }
            if (_showDisabled.value) {
                items = items.filter { !it.isEnabled }
            }
            if (_showUpdates.value) {
                // updates logic would go here if we had a manifest
                // items = items.filter { it.hasUpdate }
            }

            _installedContent.value = items
        }
    }

    private fun loadFromDir(dir: File, type: String): List<InstalledContent> {
        if (!dir.exists()) return emptyList()
        return (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && (it.name.endsWith(".jar") || it.name.endsWith(".jar.disabled") || it.name.endsWith(".phar") || it.name.endsWith(".phar.disabled")) }
            .map { file ->
                val isEnabled = !file.name.endsWith(".disabled")
                val fileName = file.name
                
                // Extract metadata
                val meta = extractMetadata(file)
                
                InstalledContent(
                    id = meta.id ?: fileName,
                    name = meta.name ?: (if (isEnabled) fileName else fileName.removeSuffix(".disabled")),
                    author = meta.author ?: "Autor Desconhecido",
                    version = meta.version ?: "N/A",
                    fileName = fileName,
                    type = type,
                    isEnabled = isEnabled,
                    fullPath = file.absolutePath,
                    description = meta.description
                )
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
            }
        }
    }
}
