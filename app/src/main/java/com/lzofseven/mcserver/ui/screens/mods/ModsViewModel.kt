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
    
    private val _installedMods = MutableStateFlow<List<InstalledMod>>(emptyList())
    val installedMods: StateFlow<List<InstalledMod>> = _installedMods.asStateFlow()
    
    init {
        loadMods()
    }

    fun loadMods() {
        viewModelScope.launch {
            val server = repository.getServerById(serverId) ?: return@launch
            val modsDir = File(server.path, "mods")
            val pluginsDir = File(server.path, "plugins")
            
            val modFiles = (modsDir.listFiles() ?: emptyArray()).map { it to "mod" }
            val pluginFiles = (pluginsDir.listFiles() ?: emptyArray()).map { it to "plugin" }
            
            val allItems = (modFiles + pluginFiles)
                .filter { it.first.isFile && (it.first.name.endsWith(".jar") || it.first.name.endsWith(".jar.disabled")) }
                .map { (file, type) ->
                    val isEnabled = !file.name.endsWith(".disabled")
                    val name = if (isEnabled) file.name else file.name.removeSuffix(".disabled")
                    InstalledMod(
                        name = name,
                        version = "N/A", // We don't store version info easily without parsing jar
                        loader = type,
                        fileName = file.name,
                        isEnabled = isEnabled,
                        fullPath = file.absolutePath
                    )
                }
            _installedMods.value = allItems
        }
    }

    fun toggleMod(mod: InstalledMod) {
        viewModelScope.launch {
            val file = File(mod.fullPath)
            if (!file.exists()) return@launch
            
            val newFile = if (mod.isEnabled) {
                File(mod.fullPath + ".disabled")
            } else {
                File(mod.fullPath.removeSuffix(".disabled"))
            }
            
            if (file.renameTo(newFile)) {
                loadMods() // Refresh list
            }
        }
    }
    
    fun deleteMod(mod: InstalledMod) {
        viewModelScope.launch {
            val file = File(mod.fullPath)
            if (file.exists() && file.delete()) {
                loadMods()
            }
        }
    }
}
