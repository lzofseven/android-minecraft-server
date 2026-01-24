package com.lzofseven.mcserver.ui.screens.mods

data class InstalledContent(
    val id: String,
    val name: String,
    val author: String = "Desconhecido",
    val version: String = "N/A",
    val loader: String? = null,
    val fileName: String,
    val type: String, // "mod", "plugin", "world"
    val isEnabled: Boolean,
    val fullPath: String,
    val iconUrl: String? = null,
    val description: String? = null
)

data class BrowserItem(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val extension: String,
    val fullPath: String, // String representation of File path or content URI
    val isSaf: Boolean
)
