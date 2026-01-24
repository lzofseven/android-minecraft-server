package com.lzofseven.mcserver.ui.screens.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.lzofseven.mcserver.core.execution.RealServerManager

@HiltViewModel
class ConsoleViewModel @Inject constructor(
    private val realServerManager: com.lzofseven.mcserver.core.execution.RealServerManager,
    savedStateHandle: androidx.lifecycle.SavedStateHandle
) : ViewModel() {
    
    private val serverId: String = checkNotNull(savedStateHandle["serverId"])

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()
    
    init {
        viewModelScope.launch {
            realServerManager.getConsoleFlow(serverId).collect { log ->
                val current = _logs.value.toMutableList()
                current.add(log)
                if (current.size > 500) {
                    _logs.value = current.takeLast(500)
                } else {
                    _logs.value = current
                }
            }
        }
    }
    
    fun sendCommand(command: String) {
        _logs.value = _logs.value + "> $command"
        realServerManager.sendCommand(serverId, command)
    }
    
    fun clearLogs() {
        _logs.value = emptyList()
    }
}
