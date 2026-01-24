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
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

@HiltViewModel
class ModsViewModel @Inject constructor(
    private val repository: ServerRepository,
    private val modrinthRepository: com.lzofseven.mcserver.data.repository.ModrinthRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    val serverId: String = checkNotNull(savedStateHandle["serverId"])
    
    private val _installedContent = MutableStateFlow<List<InstalledContent>>(emptyList())
    val installedContent: StateFlow<List<InstalledContent>> = _installedContent.asStateFlow()
    
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab = _selectedTab.asStateFlow()
    
    // File Manager State - Using strings to handle both absolute paths and content URIs
    private val _currentBrowserPath = MutableStateFlow<String?>(null)
    val currentBrowserPath = _currentBrowserPath.asStateFlow()
    
    private val _browserFiles = MutableStateFlow<List<BrowserItem>>(emptyList())
    val browserFiles = _browserFiles.asStateFlow()
    
    private val _currentBrowserName = MutableStateFlow<String?>(null)
    val currentBrowserName = _currentBrowserName.asStateFlow()

    private val _selectedSubFilter = MutableStateFlow<String?>(null)
    val selectedSubFilter = _selectedSubFilter.asStateFlow()
    
    private val _showDisabled = MutableStateFlow(false)
    val showDisabled = _showDisabled.asStateFlow()
    
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    private var clipboardItem: BrowserItem? = null

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
            
            // File Browser Logic (Tab 2)
            if (_selectedTab.value == 2) {
                if (server.uri != null) {
                    // SAF Browser
                    val rootUri = android.net.Uri.parse(server.uri)
                    var currentPathStr = _currentBrowserPath.value
                    
                    if (currentPathStr == null) {
                        try {
                            val propsManager = com.lzofseven.mcserver.util.ServerPropertiesManager(context, server.uri)
                            val levelName = propsManager.load()["level-name"] ?: "world"
                            val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, rootUri)
                            val worldDoc = rootDoc?.findFile(levelName)
                            currentPathStr = worldDoc?.uri?.toString() ?: server.uri
                        } catch (e: Exception) {
                            currentPathStr = server.uri
                        }
                    }
                    
                    val currentUri = android.net.Uri.parse(currentPathStr)
                    val currentDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, currentUri)
                        ?: androidx.documentfile.provider.DocumentFile.fromTreeUri(context, rootUri)
                    
                    _currentBrowserPath.value = currentDir?.uri?.toString()
                    _currentBrowserName.value = if (currentDir?.uri == rootUri) "Raiz" else currentDir?.name
                    
                    val items = (currentDir?.listFiles() ?: emptyArray()).map { doc ->
                        BrowserItem(
                            name = doc.name ?: "unknown",
                            isDirectory = doc.isDirectory,
                            size = doc.length(),
                            extension = doc.name?.substringAfterLast('.', "") ?: "",
                            fullPath = doc.uri.toString(),
                            isSaf = true
                        )
                    }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                    
                    _browserFiles.value = items
                } else {
                    // Traditional File Browser
                    val serverRoot = File(server.path)
                    var currentPathStr = _currentBrowserPath.value
                    
                    if (currentPathStr == null) {
                        try {
                            val propsManager = com.lzofseven.mcserver.util.ServerPropertiesManager(context, server.path)
                            val levelName = propsManager.load()["level-name"] ?: "world"
                            val worldFile = File(serverRoot, levelName)
                            currentPathStr = if (worldFile.exists()) worldFile.absolutePath else server.path
                        } catch (e: Exception) {
                            currentPathStr = server.path
                        }
                    }
                    
                    val currentDir = File(currentPathStr)
                    
                    _currentBrowserPath.value = currentDir.absolutePath
                    _currentBrowserName.value = if (currentDir.absolutePath == serverRoot.absolutePath) "Raiz" else currentDir.name
                    
                    val items = (currentDir.listFiles() ?: emptyArray()).map { file ->
                        BrowserItem(
                            name = file.name,
                            isDirectory = file.isDirectory,
                            size = file.length(),
                            extension = file.extension,
                            fullPath = file.absolutePath,
                            isSaf = false
                        )
                    }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                    
                    _browserFiles.value = items
                }
                return@launch
            }

            val metaManager = com.lzofseven.mcserver.util.ContentMetaManager(context, server.uri ?: server.path)
            val metadataMap = metaManager.loadMetadata()

            var items = if (server.uri != null) {
                // SAF Logic (Mods/Plugins)
                val rootUri = android.net.Uri.parse(server.uri)
                val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, rootUri)
                
                when (_selectedTab.value) {
                    0 -> loadFromSaf(rootDoc?.findFile("mods"), "mod", metadataMap)
                    1 -> loadFromSaf(rootDoc?.findFile("plugins"), "plugin", metadataMap)
                    else -> emptyList()
                }
            } else {
                // File Logic (Mods/Plugins)
                val serverRoot = File(server.path)
                when (_selectedTab.value) {
                    0 -> loadFromDir(File(serverRoot, "mods"), "mod", metadataMap)
                    1 -> loadFromDir(File(serverRoot, "plugins"), "plugin", metadataMap)
                    else -> emptyList()
                }
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

    // Navigation methods (File Browser)
    fun navigateTo(item: BrowserItem) {
        if (item.isDirectory) {
            _currentBrowserPath.value = item.fullPath
            loadContent()
        }
    }

    fun navigateBack() {
        viewModelScope.launch {
            val server = repository.getServerById(serverId) ?: return@launch
            val currentPath = _currentBrowserPath.value ?: return@launch
            
            if (server.uri != null) {
                val rootUri = android.net.Uri.parse(server.uri)
                val currentUri = android.net.Uri.parse(currentPath)
                if (currentUri != rootUri) {
                    // For SAF, we need to get the parent. DocumentFile.getParentFile() might not works for tree?
                    // Usually we might need a custom stack.
                    // For simplicity, if it fails, go to root.
                    val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, currentUri)
                    val parent = doc?.parentFile
                    if (parent != null) {
                         _currentBrowserPath.value = parent.uri.toString()
                    } else {
                         _currentBrowserPath.value = server.uri
                    }
                    loadContent()
                }
            } else {
                val serverRoot = File(server.path)
                val current = File(currentPath)
                if (current.absolutePath != serverRoot.absolutePath) {
                    _currentBrowserPath.value = current.parentFile.absolutePath
                    loadContent()
                }
            }
        }
    }

    fun deleteFile(item: BrowserItem) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val server = repository.getServerById(serverId) ?: return@launch
            val metaManager = com.lzofseven.mcserver.util.ContentMetaManager(context, server.uri ?: server.path)
            
            try {
                if (item.isSaf) {
                    val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, Uri.parse(item.fullPath))
                    if (doc?.delete() == true) {
                        metaManager.removeMetadata(item.name)
                        loadContent()
                        _toastMessage.emit("Apagado")
                    }
                } else {
                    val file = File(item.fullPath)
                    if (file.exists()) {
                        if (file.isDirectory) file.deleteRecursively() else file.delete()
                        metaManager.removeMetadata(item.name)
                        loadContent()
                        _toastMessage.emit("Apagado")
                    }
                }
            } catch (e: Exception) {
                _toastMessage.emit("Erro ao apagar: ${e.message}")
            }
        }
    }

    fun renameFile(item: BrowserItem, newName: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val success = if (item.isSaf) {
                val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, android.net.Uri.parse(item.fullPath))
                doc?.renameTo(newName) ?: false
            } else {
                val file = File(item.fullPath)
                val newFile = File(file.parentFile, newName)
                file.renameTo(newFile)
            }
            
            if (success) {
                loadContent()
                _toastMessage.emit("Renomeado para $newName")
            } else {
                _toastMessage.emit("Erro ao renomear")
            }
        }
    }

    fun copyFile(item: BrowserItem) {
        clipboardItem = item
        viewModelScope.launch { 
            _toastMessage.emit("Copiado: ${item.name}") 
        }
    }

    fun pasteFile() {
        val targetPath = _currentBrowserPath.value ?: return
        val source = clipboardItem ?: return
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                _toastMessage.emit("Colando: ${source.name}...")
                
                val targetIsSaf = targetPath.startsWith("content://")
                
                if (source.isDirectory) {
                    copyDirectory(source, targetPath, targetIsSaf)
                } else {
                    copySingleFile(source, targetPath, targetIsSaf)
                }
                
                loadContent()
                _toastMessage.emit("Colado com sucesso!")
            } catch (e: Exception) {
                _toastMessage.emit("Erro ao colar: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private suspend fun copyDirectory(source: BrowserItem, targetParentPath: String, targetIsSaf: Boolean) {
        if (targetIsSaf) {
            val parentUri = Uri.parse(targetParentPath)
            val parentDoc = DocumentFile.fromTreeUri(context, parentUri)
            val newDir = parentDoc?.createDirectory(source.name) ?: throw Exception("Falha ao criar diretório SAF")
            
            // List children of source
            val children = if (source.isSaf) {
                val sourceDoc = DocumentFile.fromTreeUri(context, Uri.parse(source.fullPath))
                sourceDoc?.listFiles()?.map { doc ->
                    BrowserItem(doc.name ?: "", doc.isDirectory, doc.length(), doc.name?.substringAfterLast('.', "") ?: "", doc.uri.toString(), true)
                } ?: emptyList()
            } else {
                File(source.fullPath).listFiles()?.map { file ->
                    BrowserItem(file.name, file.isDirectory, file.length(), file.extension, file.absolutePath, false)
                } ?: emptyList()
            }
            
            for (child in children) {
                if (child.isDirectory) {
                    copyDirectory(child, newDir.uri.toString(), true)
                } else {
                    copySingleFile(child, newDir.uri.toString(), true)
                }
            }
        } else {
            val targetDir = File(targetParentPath, source.name)
            targetDir.mkdirs()
            
            val children = if (source.isSaf) {
                val sourceDoc = DocumentFile.fromTreeUri(context, Uri.parse(source.fullPath))
                sourceDoc?.listFiles()?.map { doc ->
                    BrowserItem(doc.name ?: "", doc.isDirectory, doc.length(), doc.name?.substringAfterLast('.', "") ?: "", doc.uri.toString(), true)
                } ?: emptyList()
            } else {
                File(source.fullPath).listFiles()?.map { file ->
                    BrowserItem(file.name, file.isDirectory, file.length(), file.extension, file.absolutePath, false)
                } ?: emptyList()
            }
            
            for (child in children) {
                if (child.isDirectory) {
                    copyDirectory(child, targetDir.absolutePath, false)
                } else {
                    copySingleFile(child, targetDir.absolutePath, false)
                }
            }
        }
    }

    private suspend fun copySingleFile(source: BrowserItem, targetParentPath: String, targetIsSaf: Boolean) {
        val inputStream = if (source.isSaf) {
            context.contentResolver.openInputStream(Uri.parse(source.fullPath))
        } else {
            File(source.fullPath).inputStream()
        } ?: throw Exception("Falha ao abrir stream de origem")

        val outputStream = if (targetIsSaf) {
            val parentUri = Uri.parse(targetParentPath)
            val parentDoc = DocumentFile.fromTreeUri(context, parentUri)
            val newFile = parentDoc?.findFile(source.name) ?: parentDoc?.createFile(getMimeType(source.extension), source.name)
            newFile?.let { context.contentResolver.openOutputStream(it.uri) }
        } else {
            val targetFile = File(targetParentPath, source.name)
            targetFile.outputStream()
        } ?: throw Exception("Falha ao abrir stream de destino")

        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun getMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "jar" -> "application/java-archive"
            "txt" -> "text/plain"
            "yml", "yaml" -> "text/x-yaml"
            "json" -> "application/json"
            "properties" -> "text/plain"
            "sh" -> "text/x-sh"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            else -> "application/octet-stream"
        }
    }

    fun readFile(item: BrowserItem): String {
        return try {
            if (item.isSaf) {
                context.contentResolver.openInputStream(android.net.Uri.parse(item.fullPath))?.use { it.bufferedReader().readText() } ?: ""
            } else {
                File(item.fullPath).readText()
            }
        } catch (e: Exception) {
            "Erro ao ler arquivo: ${e.message}"
        }
    }

    fun saveFile(item: BrowserItem, content: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (item.isSaf) {
                    context.contentResolver.openOutputStream(android.net.Uri.parse(item.fullPath))?.use { it.write(content.toByteArray()) }
                } else {
                    File(item.fullPath).writeText(content)
                }
                _toastMessage.emit("Arquivo salvo!")
            } catch (e: Exception) {
                _toastMessage.emit("Erro ao salvar: ${e.message}")
            }
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val currentPath = _currentBrowserPath.value ?: return@launch
            if (currentPath.startsWith("content://")) {
                val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, android.net.Uri.parse(currentPath))
                doc?.createDirectory(name)
            } else {
                val newFolder = File(currentPath, name)
                newFolder.mkdirs()
            }
            loadContent()
        }
    }

    private val metadataCache = mutableMapOf<String, InstalledContent>()

    private suspend fun loadFromSaf(
        dir: androidx.documentfile.provider.DocumentFile?, 
        type: String, 
        metadata: Map<String, com.lzofseven.mcserver.util.ContentMetadata>
    ): List<InstalledContent> {
        if (dir == null || !dir.exists()) return emptyList()
        
        val files = dir.listFiles()
            .filter { it.isFile && (it.name?.endsWith(".jar") == true || it.name?.endsWith(".jar.disabled") == true || it.name?.endsWith(".phar") == true) }
        
        return files.map { doc ->
            val fileName = doc.name ?: "unknown"
            val isEnabled = !fileName.endsWith(".disabled")
            val fullPath = doc.uri.toString() // Use URI as path for SAF
            
            // Try cache
            metadataCache[fullPath]?.let { return@map it.copy(isEnabled = isEnabled) }
            
            // Metadata
            val localMeta = extractMetadataSaf(doc.uri)
            
            // Modrinth (simplified)
            var iconUrl: String? = null
            var remoteTitle: String? = null
            var remoteDesc: String? = null
            
             try {
                // SHA1 calculation for SAF stream is expensive, skipping for now or TODO
                // If needed: openInputStream -> calculateSHA1 -> Modrinth
            } catch (e: Exception) {}

            // Priority: persistent metadata -> remote API -> local JAR meta -> filename
            val meta = metadata[fileName] ?: metadata[fileName.removeSuffix(".disabled")]

            val item = InstalledContent(
                id = meta?.projectId ?: localMeta.id ?: fileName,
                name = meta?.title ?: remoteTitle ?: localMeta.name ?: (if (isEnabled) fileName else fileName.removeSuffix(".disabled")),
                author = localMeta.author ?: "Autor Desconhecido",
                version = meta?.version ?: localMeta.version ?: "N/A",
                loader = meta?.loader,
                fileName = fileName,
                type = type,
                isEnabled = isEnabled,
                fullPath = fullPath,
                description = remoteDesc ?: localMeta.description,
                iconUrl = meta?.iconUrl ?: iconUrl
            )
            metadataCache[fullPath] = item
            item
        }
    }

    private suspend fun loadFromDir(
        dir: File, 
        type: String,
        metadata: Map<String, com.lzofseven.mcserver.util.ContentMetadata>
    ): List<InstalledContent> {
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

            // Priority: persistent metadata -> remote API -> local JAR meta -> filename
            val meta = metadata[fileName] ?: metadata[fileName.removeSuffix(".disabled")]

            val item = InstalledContent(
                id = meta?.projectId ?: localMeta.id ?: fileName,
                name = meta?.title ?: remoteTitle ?: localMeta.name ?: (if (isEnabled) fileName else fileName.removeSuffix(".disabled")),
                author = localMeta.author ?: "Autor Desconhecido",
                version = meta?.version ?: localMeta.version ?: "N/A",
                loader = meta?.loader,
                fileName = fileName,
                type = type,
                isEnabled = isEnabled,
                fullPath = file.absolutePath,
                description = remoteDesc ?: localMeta.description,
                iconUrl = meta?.iconUrl ?: iconUrl
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

    private fun extractMetadataSaf(uri: android.net.Uri): JarMeta {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                java.util.zip.ZipInputStream(stream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == "fabric.mod.json") {
                            // Read json
                            zip.readBytes() // Finding it confirms it's a mod
                            // Standard ZipInputStream doesn't have readBytes for entry easily without buffer loop
                            // Skipping deep read for now to keep distinct from File logic complexity
                            // Just finding it confirms it's a mod? No metadata extraction for SAF yet to save complexity
                        }
                        // To implement safely: need a loop reading into baos
                        entry = zip.nextEntry
                    }
                }
            }
        } catch (e: Exception) {}
        return JarMeta() // Placeholder for SAF generic
    }

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
                zip.getEntry("quilt_loader.json")?.let { _ ->
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
            if (item.fullPath.startsWith("content://")) {
                // SAF Logic
                try {
                    val uri = android.net.Uri.parse(item.fullPath)
                    val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                    if (doc != null && doc.exists()) {
                         val newName = if (item.isEnabled) doc.name + ".disabled" else doc.name?.removeSuffix(".disabled")
                         if (newName != null) {
                             if (doc.renameTo(newName)) {
                                 loadContent()
                             }
                         }
                    }
                } catch (e: Exception) { _toastMessage.emit("Erro SAF: ${e.message}") }
            } else {
                // File Logic
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
    }
    
    fun deleteItem(item: InstalledContent) {
        viewModelScope.launch {
             if (item.fullPath.startsWith("content://")) {
                // SAF Logic
                try {
                    val uri = android.net.Uri.parse(item.fullPath)
                    val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                    if (doc != null && doc.exists()) {
                         if (doc.delete()) {
                             val server = repository.getServerById(serverId)
                             if (server != null) {
                                 val metaManager = com.lzofseven.mcserver.util.ContentMetaManager(context, server.uri ?: server.path)
                                 metaManager.removeMetadata(item.fileName)
                             }
                             loadContent()
                             _toastMessage.emit("${item.name} removido")
                         }
                    }
                } catch (e: Exception) { _toastMessage.emit("Erro SAF: ${e.message}") }
            } else {
                val file = File(item.fullPath)
                if (file.exists()) {
                    if (file.isDirectory) {
                        file.deleteRecursively()
                    } else {
                        file.delete()
                    }
                    val server = repository.getServerById(serverId)
                    if (server != null) {
                        val metaManager = com.lzofseven.mcserver.util.ContentMetaManager(context, server.uri ?: server.path)
                        metaManager.removeMetadata(item.fileName)
                    }
                    loadContent()
                    _toastMessage.emit("${item.name} removido")
                }
            }
        }
    }
    fun importFile(uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val targetPath = _currentBrowserPath.value ?: return@launch
            try {
                // Get filename from Uri
                val fileName = getFileNameFromUri(uri) ?: "imported_file_${System.currentTimeMillis()}"
                _toastMessage.emit("Importando $fileName...")

                val inputStream = context.contentResolver.openInputStream(uri) ?: throw Exception("Falha ao abrir arquivo")
                
                if (targetPath.startsWith("content://")) {
                    val parentUri = android.net.Uri.parse(targetPath)
                    val parentDoc = DocumentFile.fromTreeUri(context, parentUri)
                    val existing = parentDoc?.findFile(fileName)
                    existing?.delete()
                    val newFile = parentDoc?.createFile(context.contentResolver.getType(uri) ?: "application/octet-stream", fileName)
                    newFile?.let { context.contentResolver.openOutputStream(it.uri)?.use { out -> inputStream.copyTo(out) } }
                } else {
                    val targetFile = File(targetPath, fileName)
                    targetFile.outputStream().use { out -> inputStream.copyTo(out) }
                }
                
                loadContent()
                _toastMessage.emit("Importado com sucesso!")
            } catch (e: Exception) {
                _toastMessage.emit("Erro ao importar: ${e.message}")
            }
        }
    }

    private fun getFileNameFromUri(uri: android.net.Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }
}
