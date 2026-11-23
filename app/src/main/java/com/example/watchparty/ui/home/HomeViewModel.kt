package com.example.watchparty.ui.home

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel : ViewModel() {
    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    fun updateUserName(name: String) {
        _userName.value = name
    }

    fun isNameValid(name: String): Boolean {
        return name.trim().length >= 2
    }
}
