package com.suportex.app.data.model

data class Session(
    val id: String = "",
    val requesterId: String = "",
    val technicianId: String? = null,
    val technicianName: String? = null,
    val status: String = "active", // active | ended
    val startedAt: Long = 0L,
    val sharing: Boolean = false,
    val remoteEnabled: Boolean = false,
    val callState: String = "idle" // idle | ringing | accepted | ended
)