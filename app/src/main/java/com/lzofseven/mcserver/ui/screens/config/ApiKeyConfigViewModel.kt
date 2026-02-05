package com.lzofseven.mcserver.ui.screens.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lzofseven.mcserver.core.auth.ApiKeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ApiKeyConfigViewModel @Inject constructor(
    private val apiKeyManager: ApiKeyManager
) : ViewModel() {

    private val _apiKey = MutableStateFlow("")
    val apiKey = _apiKey.asStateFlow()

    init {
        viewModelScope.launch {
            apiKeyManager.apiKeyFlow.collect { storedKey ->
                if (!storedKey.isNullOrBlank()) {
                    _apiKey.value = storedKey
                }
            }
        }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            apiKeyManager.saveApiKey(key)
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            apiKeyManager.clearApiKey()
            _apiKey.value = ""
        }
    }
}
