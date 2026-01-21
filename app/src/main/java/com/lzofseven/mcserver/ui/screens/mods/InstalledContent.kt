package com.lzofseven.mcserver.ui.screens.mods

data class InstalledContent(
    val id: String,
    val name: String,
    val author: String = "Desconhecido",
    val version: String = "N/A",
    val fileName: String,
    val type: String, // "mod", "plugin", "world"
    val isEnabled: Boolean,
    val fullPath: String,
    val iconUrl: String? = null,
    val description: String? = null
)
