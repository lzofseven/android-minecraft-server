package com.lzofseven.mcserver.data.model

data class PlayerEntry(
    val uuid: String,
    val name: String,
    val level: Int = 4, // Para OP level
    val bypassesPlayerLimit: Boolean = false
)

data class WhitelistEntry(
    val uuid: String,
    val name: String
)
