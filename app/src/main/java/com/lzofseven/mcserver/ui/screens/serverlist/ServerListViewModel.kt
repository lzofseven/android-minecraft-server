package com.lzofseven.mcserver.ui.screens.serverlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lzofseven.mcserver.data.local.entity.MCServerEntity
import com.lzofseven.mcserver.data.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServerListViewModel @Inject constructor(
    private val repository: ServerRepository,
    private val realServerManager: com.lzofseven.mcserver.core.execution.RealServerManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {
    
    private val _icons = kotlinx.coroutines.flow.MutableStateFlow<Map<String, android.graphics.Bitmap?>>(emptyMap())
    val icons = _icons.asStateFlow()

    val onlineCount: StateFlow<Int> = realServerManager.runningServerCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val servers: StateFlow<List<MCServerEntity>> = repository.allServers
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _motds = kotlinx.coroutines.flow.MutableStateFlow<Map<String, String>>(emptyMap())
    val motds: kotlinx.coroutines.flow.StateFlow<Map<String, String>> = _motds.asStateFlow()

    init {
        viewModelScope.launch {
            repository.allServers.collect { serverList ->
                // Switch to IO for batch file/bitmap operations
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val newMotds = serverList.associate { server ->
                        server.id to try {
                            val props = com.lzofseven.mcserver.util.ServerPropertiesManager(context, server.uri ?: server.path).load()
                            props["motd"] ?: "A Minecraft Server"
                        } catch (e: Exception) {
                            "A Minecraft Server"
                        }
                    }
                    _motds.value = newMotds

                    val newIcons = serverList.associate { server ->
                        server.id to try {
                            val path = server.uri ?: server.path
                            if (path.startsWith("content://")) {
                                val uri = android.net.Uri.parse(path)
                                val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                                val iconDoc = rootDoc?.findFile("server-icon.png")
                                if (iconDoc != null && iconDoc.exists()) {
                                    context.contentResolver.openInputStream(iconDoc.uri)?.use { stream ->
                                        android.graphics.BitmapFactory.decodeStream(stream)
                                    }
                                } else null
                            } else {
                                val iconFile = java.io.File(path, "server-icon.png")
                                if (iconFile.exists()) {
                                    android.graphics.BitmapFactory.decodeFile(iconFile.absolutePath)
                                } else null
                            }
                        } catch (e: Exception) { null }
                    }
                    _icons.value = newIcons
                }
            }
        }
    }

    fun isServerRunning(serverId: String): Boolean {
        return realServerManager.isRunning(serverId)
    }

    fun deleteServer(server: MCServerEntity) {
        viewModelScope.launch {
            repository.deleteServer(server)
            // TODO: Deletar arquivos reais do disco também?
        }
    }
}
