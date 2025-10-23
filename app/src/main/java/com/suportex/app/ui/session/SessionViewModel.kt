@file:Suppress("unused")

package com.suportex.app.ui.session

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SessionStatus { ONLINE, CHAT, SHARE, REMOTE, CALL }

data class SessionUiState(
    val technicianName: String = "Isac Xavier",
    val timer: String = "00:00:00",
    val status: SessionStatus = SessionStatus.CHAT,
    val remoteEnabled: Boolean = false,
    val calling: Boolean = false
)

class SessionViewModel : ViewModel() {

    private val _ui = MutableStateFlow(SessionUiState())
    val ui = _ui.asStateFlow()

    fun startShare() {
        // TODO: CHAME sua lógica existente de MediaProjection/WebRTC
        _ui.value = _ui.value.copy(status = SessionStatus.SHARE)
    }

    fun stopShare() {
        // TODO: pare a captura
        _ui.value = _ui.value.copy(status = SessionStatus.CHAT)
    }

    fun toggleRemote(enable: Boolean) {
        // TODO: abrir consentimento e habilitar AccessibilityService ao aceitar
        _ui.value = _ui.value.copy(
            remoteEnabled = enable,
            status = if (enable) SessionStatus.REMOTE else SessionStatus.CHAT
        )
    }

    fun startCall() { _ui.value = _ui.value.copy(calling = true, status = SessionStatus.CALL) }

    fun endCall() { _ui.value = _ui.value.copy(calling = false, status = SessionStatus.CHAT) }

    fun endSupport() { }
}