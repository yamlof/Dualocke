package org.example.project.data

// data/UserSession.kt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object UserSession {
    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    /** Returns the active user ID, or "guest" when no user is logged in. */
    fun activeUserId(): String = _currentUserId.value ?: "guest"

    fun setUser(userId: String) {
        _currentUserId.value = userId
    }

    fun clear() {
        _currentUserId.value = null
    }
}