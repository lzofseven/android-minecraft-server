package com.lzofseven.mcserver.ui.screens.players

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lzofseven.mcserver.data.model.PlayerEntry
import com.lzofseven.mcserver.data.model.WhitelistEntry
import com.lzofseven.mcserver.util.PlayerManager
import com.lzofseven.mcserver.data.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PlayersViewModel @Inject constructor(
    private val playerManager: PlayerManager,
    private val realServerManager: com.lzofseven.mcserver.core.execution.RealServerManager,
    private val repository: ServerRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    savedStateHandle: androidx.lifecycle.SavedStateHandle
) : ViewModel() {

    private val serverId: String = checkNotNull(savedStateHandle["serverId"])

    private val _whitelist = MutableStateFlow<List<WhitelistEntry>>(emptyList())
    val whitelist: StateFlow<List<WhitelistEntry>> = _whitelist.asStateFlow()

    private val _ops = MutableStateFlow<List<PlayerEntry>>(emptyList())
    val ops: StateFlow<List<PlayerEntry>> = _ops.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _onlinePlayers = realServerManager.getPlayerListFlow(serverId)
    val onlinePlayers: kotlinx.coroutines.flow.Flow<List<String>> = _onlinePlayers

    val filteredPlayers = kotlinx.coroutines.flow.combine(_onlinePlayers, _searchQuery) { players, query ->
        if (query.isBlank()) players else players.filter { it.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _gameMode = MutableStateFlow("Survival")
    val gameMode: StateFlow<String> = _gameMode.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ops.value = playerManager.getOps()
            _whitelist.value = playerManager.getWhitelist()
            
            val server = repository.getServerById(serverId)
            if (server != null) {
                val props = com.lzofseven.mcserver.util.ServerPropertiesManager(context, server.path).load()
                _gameMode.value = props["gamemode"] ?: "Survival"
            }
        }
    }

    fun addOp(name: String) {
        viewModelScope.launch {
            val newOps = _ops.value.toMutableList().apply {
                add(PlayerEntry(uuid = UUID.randomUUID().toString(), name = name))
            }
            playerManager.saveOps(newOps)
            _ops.value = newOps
        }
    }

    fun removeOp(player: PlayerEntry) {
        viewModelScope.launch {
            val newOps = _ops.value.filter { it.uuid != player.uuid }
            playerManager.saveOps(newOps)
            _ops.value = newOps
        }
    }

    fun addWhitelist(name: String) {
        viewModelScope.launch {
            val newList = _whitelist.value.toMutableList().apply {
                add(WhitelistEntry(uuid = UUID.randomUUID().toString(), name = name))
            }
            playerManager.saveWhitelist(newList)
            _whitelist.value = newList
        }
    }

    fun removeWhitelist(player: WhitelistEntry) {
        viewModelScope.launch {
            val newList = _whitelist.value.filter { it.uuid != player.uuid }
            playerManager.saveWhitelist(newList)
            _whitelist.value = newList
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun sendWhisper(targetPlayer: String, message: String) {
        realServerManager.sendCommand(serverId, "tell $targetPlayer $message")
    }
}
