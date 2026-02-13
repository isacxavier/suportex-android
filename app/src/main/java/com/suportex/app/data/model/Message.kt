package com.suportex.app.data.model

data class Message(
    val id: String = "",
    val sessionId: String? = null,
    val from: String = "",
    val fromName: String? = null,
    val text: String? = null,
    val imageUrl: String? = null,
    val fileUrl: String? = null,
    val audioUrl: String? = null,
    val type: String = "text",
    val status: String = "sent",
    val createdAt: Long = System.currentTimeMillis()
)
