package com.suportex.app.data.model

data class Message(
    val id: String = "",
    val fromId: String = "",
    val fromName: String? = null,
    val text: String? = null,
    val fileUrl: String? = null,
    val audioUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)