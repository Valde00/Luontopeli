// viewmodel/WalkViewModel.kt
package com.example.luontopeli.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.luontopeli.data.local.dao.WalkSessionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WalkViewModel @Inject constructor(
    private val dao: WalkSessionDao
) : ViewModel() {

    private val _currentSession = MutableStateFlow<com.example.luontopeli.data.local.entity.WalkSession?>(null)
    val currentSession: StateFlow<com.example.luontopeli.data.local.entity.WalkSession?> = _currentSession

    private val _isWalking = MutableStateFlow(false)
    val isWalking: StateFlow<Boolean> = _isWalking

    init {
        viewModelScope.launch {
            val active = dao.getActiveSession()
            _currentSession.value = active
            _isWalking.value = active != null
        }
    }

    fun startWalk() {
        viewModelScope.launch {
            val newSession = com.example.luontopeli.data.local.entity.WalkSession()
            dao.insert(newSession)
            val persisted = dao.getActiveSession()
            _currentSession.value = persisted
            _isWalking.value = persisted != null
        }
    }

    fun stopWalk() {
        viewModelScope.launch {
            val s = _currentSession.value ?: return@launch
            val updated = s.copy(endTime = System.currentTimeMillis(), isActive = false)
            dao.update(updated)
            _currentSession.value = null
            _isWalking.value = false
        }
    }

    fun updateSteps(stepCount: Int, distanceMeters: Float) {
        viewModelScope.launch {
            val s = _currentSession.value ?: return@launch
            val updated = s.copy(stepCount = stepCount, distanceMeters = distanceMeters)
            dao.update(updated)
            val persisted = dao.getActiveSession()
            _currentSession.value = persisted
        }
    }

    fun updateDistance(distanceMeters: Float) {
        viewModelScope.launch {
            val s = _currentSession.value ?: return@launch
            val updated = s.copy(distanceMeters = distanceMeters)
            dao.update(updated)
            val persisted = dao.getActiveSession()
            _currentSession.value = persisted
        }
    }
}
