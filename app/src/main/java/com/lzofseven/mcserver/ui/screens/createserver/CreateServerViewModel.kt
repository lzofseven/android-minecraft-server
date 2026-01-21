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
    private val repository: ServerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateServerState())
    val uiState = _uiState.asStateFlow()

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun updateVersion(version: String) {
        _uiState.value = _uiState.value.copy(version = version)
    }

    fun updateType(type: ServerType) {
        _uiState.value = _uiState.value.copy(type = type)
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

    fun createServer(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            val newServer = MCServerEntity(
                name = state.name,
                version = state.version,
                type = state.type.name,
                path = state.path,
                ramAllocationMB = state.ramAllocation
            )
            repository.insertServer(newServer)
            
            // TODO: Save initial server.properties based on difficulty/gamemode/onlineMode
            // using ServerPropertiesManager. This can be done post-creation or during server init.
            
            onSuccess()
        }
    }
}

data class CreateServerState(
    val currentStep: Int = 0, // 0: Name/Type, 1: Version/RAM, 2: Path
    val name: String = "",
    val type: ServerType = ServerType.PAPER,
    val version: String = "1.20.1",
    val ramAllocation: Int = 2048,
    val path: String = "/storage/emulated/0/MinecraftServers/Server1",
    val gameMode: String = "Survival",
    val difficulty: String = "Normal",
    val onlineMode: Boolean = false // false = Cracked (Offline), true = Premium
)
