package com.xadras.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xadras.app.data.repository.AuthRepository
import com.xadras.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<Resource<Unit>?>(null)
    val authState: StateFlow<Resource<Unit>?> = _authState

    val isLoggedIn = authRepository.isLoggedIn()
    val username   = authRepository.getUsername()

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _authState.value = Resource.Error("Preenche todos os campos")
            return
        }
        viewModelScope.launch {
            _authState.value = Resource.Loading
            _authState.value = authRepository.login(username, password)
        }
    }

    fun register(username: String, email: String, password: String) {
        if (username.isBlank() || email.isBlank() || password.isBlank()) {
            _authState.value = Resource.Error("Preenche todos os campos")
            return
        }
        viewModelScope.launch {
            _authState.value = Resource.Loading
            _authState.value = authRepository.register(username, email, password)
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    fun resetState() { _authState.value = null }
}